package com.uplb.punla.data

import com.uplb.punla.data.entity.LearnedPathEdge
import com.uplb.punla.data.entity.LearnedPathNode
import com.uplb.punla.data.entity.WalkPoint
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sqrt

data class LearnedCampusGraphState(
    val nodes: List<LearnedPathNode>,
    val edges: List<LearnedPathEdge>
)

data class LearnedWalkLearningResult(
    val state: LearnedCampusGraphState,
    val acceptedPointCount: Int,
    val observedEdgeCount: Int
)

private data class LearnedGeoPoint(val lat: Double, val lon: Double)
private data class SessionEdgeSamples(
    val fromNodeId: String,
    val toNodeId: String,
    val distances: MutableList<Double> = mutableListOf()
)

/**
 * Turns completed recorder tracks into a conservative, repeated-observation graph.
 *
 * Raw walks remain untouched. A single walk can seed low-confidence geometry,
 * but routing only trusts an edge after it has been seen in two separate walks.
 */
object LearnedCampusPathEngine {
    const val MIN_TRUSTED_OBSERVATIONS = 2

    private const val MAX_PROCESSING_ACCURACY_METERS = 30f
    private const val LEGACY_GAP_SPLIT_MILLIS = 15_000L
    private const val SIMPLIFY_TOLERANCE_METERS = 3.5
    private const val MAX_SEGMENT_LENGTH_METERS = 8.0
    private const val NODE_MERGE_RADIUS_METERS = 6.0
    private const val MIN_EDGE_SAMPLE_METERS = 0.5
    private const val MAX_EDGE_SAMPLE_METERS = 20.0

    fun applyWalk(
        points: List<WalkPoint>,
        existingNodes: List<LearnedPathNode>,
        existingEdges: List<LearnedPathEdge>,
        observedAt: Long,
        nodeIdFactory: () -> String = { UUID.randomUUID().toString() }
    ): LearnedWalkLearningResult {
        val usable = points
            .asSequence()
            .sortedBy { it.sequence }
            .filter { point ->
                point.lat.isFinite() && point.lat in -90.0..90.0 &&
                    point.lon.isFinite() && point.lon in -180.0..180.0 &&
                    (point.accuracyMeters == null || point.accuracyMeters <= MAX_PROCESSING_ACCURACY_METERS)
            }
            .toList()

        val segments = splitSegments(usable)
        if (segments.isEmpty()) {
            return LearnedWalkLearningResult(
                LearnedCampusGraphState(existingNodes, existingEdges),
                acceptedPointCount = usable.size,
                observedEdgeCount = 0
            )
        }

        val workingNodes = existingNodes.associateBy { it.id }.toMutableMap()
        val originalNodeIds = workingNodes.keys.toSet()
        val samplesByNode = mutableMapOf<String, MutableList<LearnedGeoPoint>>()
        val sessionEdges = linkedMapOf<String, SessionEdgeSamples>()

        segments.forEach { rawSegment ->
            if (rawSegment.size < 2) return@forEach
            val simplified = simplify(rawSegment.map { LearnedGeoPoint(it.lat, it.lon) })
            val geometry = densify(simplified)
            if (geometry.size < 2) return@forEach

            val matched = geometry.map { point ->
                val node = nearestNode(workingNodes.values, point)
                    ?: LearnedPathNode(
                        id = nodeIdFactory(),
                        lat = point.lat,
                        lon = point.lon,
                        observationCount = 0,
                        firstSeenAt = observedAt,
                        lastSeenAt = observedAt
                    ).also { workingNodes[it.id] = it }

                samplesByNode.getOrPut(node.id) { mutableListOf() }.add(point)
                node.id to point
            }

            matched.zipWithNext().forEach { (a, b) ->
                if (a.first == b.first) return@forEach
                val distance = haversineMeters(a.second.lat, a.second.lon, b.second.lat, b.second.lon)
                if (distance !in MIN_EDGE_SAMPLE_METERS..MAX_EDGE_SAMPLE_METERS) return@forEach
                val (fromId, toId) = normalizedPair(a.first, b.first)
                val id = edgeId(fromId, toId)
                sessionEdges.getOrPut(id) { SessionEdgeSamples(fromId, toId) }
                    .distances.add(distance)
            }
        }

        if (sessionEdges.isEmpty()) {
            return LearnedWalkLearningResult(
                LearnedCampusGraphState(existingNodes, existingEdges),
                acceptedPointCount = usable.size,
                observedEdgeCount = 0
            )
        }

        val usedNodeIds = sessionEdges.values
            .flatMap { listOf(it.fromNodeId, it.toNodeId) }
            .toSet()

        val finalNodes = existingNodes.associateBy { it.id }.toMutableMap()
        usedNodeIds.forEach { nodeId ->
            val base = workingNodes.getValue(nodeId)
            val samples = samplesByNode[nodeId].orEmpty()
            if (samples.isEmpty()) return@forEach

            val sampleLat = samples.map { it.lat }.average()
            val sampleLon = samples.map { it.lon }.average()
            val priorCount = base.observationCount.coerceAtLeast(0)
            val nextCount = priorCount + 1

            finalNodes[nodeId] = base.copy(
                lat = if (priorCount == 0) sampleLat else (base.lat * priorCount + sampleLat) / nextCount,
                lon = if (priorCount == 0) sampleLon else (base.lon * priorCount + sampleLon) / nextCount,
                observationCount = nextCount,
                firstSeenAt = if (priorCount == 0 || nodeId !in originalNodeIds) observedAt else base.firstSeenAt,
                lastSeenAt = observedAt
            )
        }

        val finalEdges = existingEdges.associateBy { it.id }.toMutableMap()
        sessionEdges.forEach { (id, observed) ->
            val from = finalNodes[observed.fromNodeId] ?: return@forEach
            val to = finalNodes[observed.toNodeId] ?: return@forEach
            val straight = haversineMeters(from.lat, from.lon, to.lat, to.lon)
            val sessionDistance = maxOf(observed.distances.average(), straight)
            val previous = finalEdges[id]
            val priorCount = previous?.observationCount?.coerceAtLeast(0) ?: 0
            val nextCount = priorCount + 1
            val averagedDistance = if (previous == null || priorCount == 0) {
                sessionDistance
            } else {
                (previous.distanceMeters * priorCount + sessionDistance) / nextCount
            }

            finalEdges[id] = LearnedPathEdge(
                id = id,
                fromNodeId = observed.fromNodeId,
                toNodeId = observed.toNodeId,
                distanceMeters = averagedDistance,
                observationCount = nextCount,
                firstSeenAt = previous?.firstSeenAt ?: observedAt,
                lastSeenAt = observedAt
            )
        }

        return LearnedWalkLearningResult(
            state = LearnedCampusGraphState(
                nodes = finalNodes.values.toList(),
                edges = finalEdges.values.toList()
            ),
            acceptedPointCount = usable.size,
            observedEdgeCount = sessionEdges.size
        )
    }

    fun buildTrustedGraph(
        nodes: List<LearnedPathNode>,
        edges: List<LearnedPathEdge>,
        minimumObservations: Int = MIN_TRUSTED_OBSERVATIONS
    ): CampusPathGraph? {
        val trustedEdges = edges.filter { it.observationCount >= minimumObservations }
        if (trustedEdges.isEmpty()) return null

        val nodeIds = trustedEdges.flatMap { listOf(it.fromNodeId, it.toNodeId) }.toSet()
        val pathNodes = nodes
            .filter { it.id in nodeIds }
            .map { PathNode(id = it.id, lat = it.lat, lon = it.lon) }

        if (pathNodes.size < 2) return null
        return runCatching {
            CampusPathGraph.build(
                nodes = pathNodes,
                edges = trustedEdges.map {
                    PathEdge(
                        fromId = it.fromNodeId,
                        toId = it.toNodeId,
                        distanceMeters = it.distanceMeters
                    )
                },
                bidirectional = true
            )
        }.getOrNull()
    }

    internal fun edgeId(a: String, b: String): String {
        val (from, to) = normalizedPair(a, b)
        return "$from::$to"
    }

    private fun normalizedPair(a: String, b: String): Pair<String, String> =
        if (a <= b) a to b else b to a

    private fun nearestNode(
        nodes: Collection<LearnedPathNode>,
        point: LearnedGeoPoint
    ): LearnedPathNode? {
        var best: LearnedPathNode? = null
        var bestDistance = NODE_MERGE_RADIUS_METERS
        nodes.forEach { node ->
            val distance = haversineMeters(node.lat, node.lon, point.lat, point.lon)
            if (distance <= bestDistance) {
                best = node
                bestDistance = distance
            }
        }
        return best
    }

    private fun splitSegments(points: List<WalkPoint>): List<List<WalkPoint>> {
        if (points.isEmpty()) return emptyList()
        val result = mutableListOf<MutableList<WalkPoint>>()
        var current = mutableListOf<WalkPoint>()
        var previous: WalkPoint? = null

        points.forEach { point ->
            val prior = previous
            val split = prior != null && (
                point.segment != prior.segment ||
                    point.capturedAt <= prior.capturedAt ||
                    point.capturedAt - prior.capturedAt > LEGACY_GAP_SPLIT_MILLIS
                )
            if (split && current.isNotEmpty()) {
                result += current
                current = mutableListOf()
            }
            current += point
            previous = point
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    private fun simplify(points: List<LearnedGeoPoint>): List<LearnedGeoPoint> {
        if (points.size <= 2) return points
        val start = points.first()
        val end = points.last()
        var maxDistance = -1.0
        var maxIndex = -1

        for (i in 1 until points.lastIndex) {
            val distance = pointToSegmentDistance(points[i], start, end)
            if (distance > maxDistance) {
                maxDistance = distance
                maxIndex = i
            }
        }

        if (maxDistance <= SIMPLIFY_TOLERANCE_METERS || maxIndex < 0) {
            return listOf(start, end)
        }

        val left = simplify(points.subList(0, maxIndex + 1))
        val right = simplify(points.subList(maxIndex, points.size))
        return left.dropLast(1) + right
    }

    private fun densify(points: List<LearnedGeoPoint>): List<LearnedGeoPoint> {
        if (points.size <= 1) return points
        val out = mutableListOf(points.first())
        points.zipWithNext().forEach { (a, b) ->
            val distance = haversineMeters(a.lat, a.lon, b.lat, b.lon)
            val steps = ceil(distance / MAX_SEGMENT_LENGTH_METERS).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                val t = step.toDouble() / steps
                out += LearnedGeoPoint(
                    lat = a.lat + (b.lat - a.lat) * t,
                    lon = a.lon + (b.lon - a.lon) * t
                )
            }
        }
        return out
    }

    private fun pointToSegmentDistance(
        point: LearnedGeoPoint,
        a: LearnedGeoPoint,
        b: LearnedGeoPoint
    ): Double {
        val refLat = (point.lat + a.lat + b.lat) / 3.0
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLon = metersPerDegreeLat * cos(Math.toRadians(refLat))

        val px = (point.lon - a.lon) * metersPerDegreeLon
        val py = (point.lat - a.lat) * metersPerDegreeLat
        val bx = (b.lon - a.lon) * metersPerDegreeLon
        val by = (b.lat - a.lat) * metersPerDegreeLat
        val lengthSq = bx * bx + by * by

        if (lengthSq < 1e-9) return sqrt(px * px + py * py)
        val t = ((px * bx + py * by) / lengthSq).coerceIn(0.0, 1.0)
        val dx = px - bx * t
        val dy = py - by * t
        return sqrt(dx * dx + dy * dy)
    }
}
