package com.uplb.punla.data

import java.util.PriorityQueue
import kotlin.math.cos
import kotlin.math.sqrt

private const val QUERY_START_ID = "__query_start__"
private const val QUERY_GOAL_ID = "__query_goal__"
private const val SPLIT_START_ID = "__split_start__"
private const val SPLIT_GOAL_ID = "__split_goal__"
private const val WALKING_METERS_PER_MINUTE = 75.0
private const val ENDPOINT_EPSILON = 0.0001

/** A walking route found entirely offline over a surveyed [CampusPathGraph]. */
data class LocalWalkingRoute(
    val points: List<Pair<Double, Double>>,
    val distanceMeters: Double
)

val LocalWalkingRoute.durationSeconds: Double
    get() = distanceMeters / WALKING_METERS_PER_MINUTE * 60.0

private class LocalPlane(refLat: Double) {
    private val metersPerDegLat = 111_320.0
    private val metersPerDegLon = 111_320.0 * cos(Math.toRadians(refLat))

    fun toXY(lat: Double, lon: Double): Pair<Double, Double> =
        (lon * metersPerDegLon) to (lat * metersPerDegLat)

    fun toLatLon(x: Double, y: Double): Pair<Double, Double> =
        (y / metersPerDegLat) to (x / metersPerDegLon)
}

private data class Projection(val x: Double, val y: Double, val t: Double, val offsetMeters: Double)

private data class Snap(
    val edge: CampusPathGraph.ResolvedEdge,
    val t: Double,
    val snappedPoint: Pair<Double, Double>,
    val offsetMeters: Double
)

private fun projectOntoSegment(
    px: Double,
    py: Double,
    ax: Double,
    ay: Double,
    bx: Double,
    by: Double
): Projection {
    val abx = bx - ax
    val aby = by - ay
    val lengthSq = abx * abx + aby * aby
    if (lengthSq < 1e-9) {
        val dx = px - ax
        val dy = py - ay
        return Projection(ax, ay, 0.0, sqrt(dx * dx + dy * dy))
    }
    val t = (((px - ax) * abx) + ((py - ay) * aby)) / lengthSq
    val clampedT = t.coerceIn(0.0, 1.0)
    val cx = ax + clampedT * abx
    val cy = ay + clampedT * aby
    val dx = px - cx
    val dy = py - cy
    return Projection(cx, cy, clampedT, sqrt(dx * dx + dy * dy))
}

private fun snapToGraph(
    graph: CampusPathGraph,
    point: Pair<Double, Double>,
    maxSnapMeters: Double
): Snap? {
    if (graph.edges.isEmpty()) return null
    val (lat, lon) = point
    val plane = LocalPlane(lat)
    val (px, py) = plane.toXY(lat, lon)

    var best: Pair<CampusPathGraph.ResolvedEdge, Projection>? = null
    graph.edges.forEach { edge ->
        val from = graph.nodes.getValue(edge.fromId)
        val to = graph.nodes.getValue(edge.toId)
        val (ax, ay) = plane.toXY(from.lat, from.lon)
        val (bx, by) = plane.toXY(to.lat, to.lon)
        val projection = projectOntoSegment(px, py, ax, ay, bx, by)
        if (best == null || projection.offsetMeters < best!!.second.offsetMeters) {
            best = edge to projection
        }
    }

    val (edge, projection) = best ?: return null
    if (projection.offsetMeters > maxSnapMeters) return null
    val snappedPoint = plane.toLatLon(projection.x, projection.y)
    return Snap(edge, projection.t, snappedPoint, projection.offsetMeters)
}

private data class EdgeAnchor(val id: String, val t: Double, val point: Pair<Double, Double>)

/**
 * A* shortest path with endpoint-to-edge snapping.
 *
 * Start and goal split nodes are intentionally unique. If both endpoints snap to different
 * positions on the same physical edge, that edge is split into ordered segments containing
 * both projections. This fixes the common same-edge collision where two virtual nodes would
 * otherwise reuse the same id and overwrite each other's coordinates.
 */
fun findLocalCampusRoute(
    graph: CampusPathGraph,
    from: Pair<Double, Double>,
    to: Pair<Double, Double>,
    maxSnapMeters: Double = 60.0
): LocalWalkingRoute? {
    require(maxSnapMeters >= 0.0) { "maxSnapMeters must be >= 0" }
    val startSnap = snapToGraph(graph, from, maxSnapMeters) ?: return null
    val goalSnap = snapToGraph(graph, to, maxSnapMeters) ?: return null

    val coords = mutableMapOf<String, Pair<Double, Double>>()
    graph.nodes.forEach { (id, node) -> coords[id] = node.lat to node.lon }
    coords[QUERY_START_ID] = from
    coords[QUERY_GOAL_ID] = to

    val adjacency = mutableMapOf<String, MutableList<CampusPathGraph.Neighbor>>()
    graph.nodes.keys.forEach { adjacency[it] = mutableListOf() }
    adjacency[QUERY_START_ID] = mutableListOf()
    adjacency[QUERY_GOAL_ID] = mutableListOf()

    fun addDirected(a: String, b: String, distance: Double) {
        adjacency.getOrPut(a) { mutableListOf() }.add(CampusPathGraph.Neighbor(b, distance.coerceAtLeast(0.0)))
    }

    fun addUndirected(a: String, b: String, distance: Double) {
        addDirected(a, b, distance)
        addDirected(b, a, distance)
    }

    fun anchorFor(snap: Snap, splitId: String): EdgeAnchor {
        return when {
            snap.t <= ENDPOINT_EPSILON -> {
                val node = graph.nodes.getValue(snap.edge.fromId)
                EdgeAnchor(snap.edge.fromId, 0.0, node.lat to node.lon)
            }
            snap.t >= 1.0 - ENDPOINT_EPSILON -> {
                val node = graph.nodes.getValue(snap.edge.toId)
                EdgeAnchor(snap.edge.toId, 1.0, node.lat to node.lon)
            }
            else -> {
                coords[splitId] = snap.snappedPoint
                adjacency[splitId] = mutableListOf()
                EdgeAnchor(splitId, snap.t, snap.snappedPoint)
            }
        }
    }

    val startAnchor = anchorFor(startSnap, SPLIT_START_ID)
    val goalAnchor = anchorFor(goalSnap, SPLIT_GOAL_ID)

    // Rebuild every physical edge, splitting the edge wherever start/goal snapped onto it.
    graph.edges.forEach { edge ->
        val fromNode = graph.nodes.getValue(edge.fromId)
        val toNode = graph.nodes.getValue(edge.toId)
        val anchors = mutableListOf(
            EdgeAnchor(edge.fromId, 0.0, fromNode.lat to fromNode.lon),
            EdgeAnchor(edge.toId, 1.0, toNode.lat to toNode.lon)
        )
        if (edge == startSnap.edge && startAnchor.id !in setOf(edge.fromId, edge.toId)) anchors += startAnchor
        if (edge == goalSnap.edge && goalAnchor.id !in setOf(edge.fromId, edge.toId)) anchors += goalAnchor

        val ordered = anchors
            .distinctBy { it.id }
            .sortedWith(compareBy<EdgeAnchor> { it.t }.thenBy { it.id })

        ordered.zipWithNext().forEach { (a, b) ->
            val segmentDistance = edge.distanceMeters * (b.t - a.t).coerceAtLeast(0.0)
            if (graph.bidirectional) addUndirected(a.id, b.id, segmentDistance)
            else addDirected(a.id, b.id, segmentDistance)
        }
    }

    // The short off-path connector between the real query and the surveyed path is walkable
    // in either direction even if a rare campus edge itself is one-way.
    addUndirected(QUERY_START_ID, startAnchor.id, startSnap.offsetMeters)
    addUndirected(QUERY_GOAL_ID, goalAnchor.id, goalSnap.offsetMeters)

    val goalCoord = coords.getValue(QUERY_GOAL_ID)
    fun heuristic(nodeId: String): Double {
        val (lat, lon) = coords.getValue(nodeId)
        return haversineMeters(lat, lon, goalCoord.first, goalCoord.second)
    }

    data class QueueEntry(val nodeId: String, val fScore: Double)
    val gScore = mutableMapOf(QUERY_START_ID to 0.0)
    val cameFrom = mutableMapOf<String, String>()
    val open = PriorityQueue<QueueEntry>(compareBy { it.fScore })
    open.add(QueueEntry(QUERY_START_ID, heuristic(QUERY_START_ID)))
    val visited = mutableSetOf<String>()

    while (open.isNotEmpty()) {
        val current = open.poll().nodeId
        if (current == QUERY_GOAL_ID) break
        if (!visited.add(current)) continue
        adjacency[current].orEmpty().forEach { neighbor ->
            val tentative = (gScore[current] ?: Double.MAX_VALUE) + neighbor.distanceMeters
            if (tentative < (gScore[neighbor.nodeId] ?: Double.MAX_VALUE)) {
                gScore[neighbor.nodeId] = tentative
                cameFrom[neighbor.nodeId] = current
                open.add(QueueEntry(neighbor.nodeId, tentative + heuristic(neighbor.nodeId)))
            }
        }
    }

    val finalDistance = gScore[QUERY_GOAL_ID] ?: return null
    val pathIds = mutableListOf(QUERY_GOAL_ID)
    var walk = QUERY_GOAL_ID
    while (walk != QUERY_START_ID) {
        walk = cameFrom[walk] ?: return null
        pathIds += walk
    }
    pathIds.reverse()

    // Consecutive duplicate coordinates can occur for a zero-offset query exactly on a path.
    // Removing them keeps the MapLibre polyline tidy without changing route distance.
    val points = pathIds.map { coords.getValue(it) }.fold(mutableListOf<Pair<Double, Double>>()) { acc, p ->
        if (acc.lastOrNull() != p) acc += p
        acc
    }

    return LocalWalkingRoute(points = points, distanceMeters = finalDistance)
}
