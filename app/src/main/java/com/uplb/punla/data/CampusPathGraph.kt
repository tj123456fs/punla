package com.uplb.punla.data

import android.content.Context
import android.util.Log
import org.json.JSONObject

private const val GRAPH_TAG = "CampusPathGraph"

/** A junction, gate, bridge end, entrance, or other point on a surveyed path. */
data class PathNode(
    val id: String,
    val lat: Double,
    val lon: Double,
    val label: String? = null
)

/** One real walkable path segment. [distanceMeters] may override straight-line length. */
data class PathEdge(
    val fromId: String,
    val toId: String,
    val distanceMeters: Double? = null
)

/** Immutable adjacency graph for the surveyed campus footpath network. */
class CampusPathGraph private constructor(
    val nodes: Map<String, PathNode>,
    internal val adjacency: Map<String, List<Neighbor>>,
    internal val edges: List<ResolvedEdge>,
    internal val bidirectional: Boolean
) {
    data class Neighbor(val nodeId: String, val distanceMeters: Double)

    internal data class ResolvedEdge(
        val fromId: String,
        val toId: String,
        val distanceMeters: Double
    )

    fun neighbors(nodeId: String): List<Neighbor> = adjacency[nodeId].orEmpty()

    companion object {
        fun build(
            nodes: List<PathNode>,
            edges: List<PathEdge>,
            bidirectional: Boolean = true
        ): CampusPathGraph {
            require(nodes.isNotEmpty()) { "Campus path graph must contain at least one node" }

            val duplicateIds = nodes.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
            require(duplicateIds.isEmpty()) { "Duplicate PathNode id(s): ${duplicateIds.joinToString()}" }

            nodes.forEach { node ->
                require(node.id.isNotBlank()) { "PathNode id must not be blank" }
                require(node.lat.isFinite() && node.lat in -90.0..90.0) { "Invalid latitude for ${node.id}" }
                require(node.lon.isFinite() && node.lon in -180.0..180.0) { "Invalid longitude for ${node.id}" }
            }

            val nodeMap = nodes.associateBy { it.id }
            val resolved = edges.map { edge ->
                require(edge.fromId != edge.toId) { "PathEdge may not connect a node to itself: ${edge.fromId}" }
                val from = nodeMap[edge.fromId]
                    ?: error("PathEdge references unknown node id: ${edge.fromId}")
                val to = nodeMap[edge.toId]
                    ?: error("PathEdge references unknown node id: ${edge.toId}")
                val straight = haversineMeters(from.lat, from.lon, to.lat, to.lon)
                val requested = edge.distanceMeters
                if (requested != null) {
                    require(requested.isFinite() && requested > 0.0) {
                        "PathEdge distance must be finite and > 0: ${edge.fromId} -> ${edge.toId}"
                    }
                }
                // A real walking segment cannot be shorter than the straight line between its endpoints.
                // Clamping preserves A*'s admissible straight-line heuristic even if a measured value is noisy.
                val distance = maxOf(requested ?: straight, straight)
                ResolvedEdge(edge.fromId, edge.toId, distance)
            }

            val adjacency = mutableMapOf<String, MutableList<Neighbor>>()
            nodes.forEach { adjacency[it.id] = mutableListOf() }

            fun link(fromId: String, toId: String, distance: Double) {
                adjacency.getOrPut(fromId) { mutableListOf() }
                    .add(Neighbor(toId, distance))
            }

            resolved.forEach { edge ->
                link(edge.fromId, edge.toId, edge.distanceMeters)
                if (bidirectional) link(edge.toId, edge.fromId, edge.distanceMeters)
            }

            return CampusPathGraph(
                nodes = nodeMap,
                adjacency = adjacency.mapValues { it.value.toList() },
                edges = resolved,
                bidirectional = bidirectional
            )
        }
    }
}

/** Parse graph JSON without touching Android assets; useful for tests and tooling. */
fun parseCampusPathGraphJson(text: String): CampusPathGraph {
    val json = JSONObject(text)
    val nodesJson = json.getJSONArray("nodes")
    val nodes = (0 until nodesJson.length()).map { i ->
        val n = nodesJson.getJSONObject(i)
        PathNode(
            id = n.getString("id"),
            lat = n.getDouble("lat"),
            lon = n.getDouble("lon"),
            label = n.optString("label").takeIf { it.isNotBlank() }
        )
    }

    val edgesJson = json.getJSONArray("edges")
    val edges = (0 until edgesJson.length()).map { i ->
        val e = edgesJson.getJSONObject(i)
        PathEdge(
            fromId = e.getString("from"),
            toId = e.getString("to"),
            distanceMeters = if (e.has("distanceMeters")) e.getDouble("distanceMeters") else null
        )
    }

    val bidirectional = if (json.has("bidirectional")) json.getBoolean("bidirectional") else true
    return CampusPathGraph.build(nodes, edges, bidirectional)
}

/**
 * Load the surveyed graph. Missing/malformed data returns null so callers can fall back to OSRM.
 * The package intentionally ships only campus_waypoints.example.json; real surveyed data must be
 * installed as campus_waypoints.json before this activates.
 */
fun loadCampusPathGraph(
    context: Context,
    assetName: String = "campus_waypoints.json"
): CampusPathGraph? = try {
    val text = context.assets.open(assetName).bufferedReader().use { it.readText() }
    parseCampusPathGraphJson(text)
} catch (e: Exception) {
    Log.w(GRAPH_TAG, "Failed to load campus path graph from asset \"$assetName\"", e)
    null
}
