package com.uplb.punla.data

import android.content.Context

/**
 * One routing entry point for campus walking directions.
 *
 * 1) surveyed offline graph, when campus_waypoints.json covers both endpoints;
 * 2) existing OSRM walking route;
 * 3) null, preserving each caller's existing straight-line fallback.
 */
object CampusRoutingResolver {
    @Volatile private var graphLoaded = false
    @Volatile private var cachedGraph: CampusPathGraph? = null

    fun graph(context: Context): CampusPathGraph? {
        if (graphLoaded) return cachedGraph
        return synchronized(this) {
            if (!graphLoaded) {
                val app = context.applicationContext
                val hasSurveyedAsset = runCatching {
                    app.assets.list("")?.contains("campus_waypoints.json") == true
                }.getOrDefault(false)
                cachedGraph = if (hasSurveyedAsset) loadCampusPathGraph(app) else null
                graphLoaded = true
            }
            cachedGraph
        }
    }

    fun localRoute(
        context: Context,
        from: Pair<Double, Double>,
        to: Pair<Double, Double>
    ): LocalWalkingRoute? = graph(context)?.let { findLocalCampusRoute(it, from, to) }

    suspend fun resolveWalkingRoute(
        context: Context,
        from: Pair<Double, Double>,
        to: Pair<Double, Double>
    ): WalkingRoute? {
        localRoute(context, from, to)?.let { local ->
            return WalkingRoute(
                points = local.points,
                distanceMeters = local.distanceMeters,
                durationSeconds = local.durationSeconds
            )
        }
        return fetchWalkingRoute(from, to)
    }

    /** Reset only exists for deterministic tests; normal app code never calls this. */
    internal fun resetForTests() {
        synchronized(this) {
            cachedGraph = null
            graphLoaded = false
        }
    }
}

suspend fun resolveWalkingRoute(
    context: Context,
    from: Pair<Double, Double>,
    to: Pair<Double, Double>
): WalkingRoute? = CampusRoutingResolver.resolveWalkingRoute(context, from, to)
