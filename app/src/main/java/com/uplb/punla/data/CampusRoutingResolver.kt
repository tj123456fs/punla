package com.uplb.punla.data

import android.content.Context

/**
 * One routing entry point for campus walking directions.
 *
 * 1) surveyed offline graph, when campus_waypoints.json covers both endpoints;
 * 2) repeated paths learned locally from the user's recorded walks;
 * 3) existing OSRM walking route;
 * 4) null, preserving each caller's existing straight-line fallback.
 */
object CampusRoutingResolver {
    @Volatile var recentRoute: com.uplb.punla.context.TravelRouteContext? = null
        private set
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
        val local = localRoute(context, from, to)
        val learned = if (local == null) {
            LearnedCampusPathRepository.loadTrustedGraph(context)
                ?.let { findLocalCampusRoute(it, from, to) }
        } else {
            null
        }
        val route = local?.let { WalkingRoute(it.points, it.distanceMeters, it.durationSeconds, "Offline campus") }
            ?: learned?.let { WalkingRoute(it.points, it.distanceMeters, it.durationSeconds, "Learned campus") }
            ?: fetchWalkingRoute(from, to)
        if (route != null) {
            recentRoute = com.uplb.punla.context.TravelRouteContext(from.first, from.second, to.first, to.second,
                route.distanceMeters, route.source, System.currentTimeMillis(), route.durationSeconds)
            com.uplb.punla.context.StudentContextEngine.get(context).refreshNow()
        }
        return route
    }

    /** Reset only exists for deterministic tests; normal app code never calls this. */
    internal fun resetForTests() {
        synchronized(this) {
            cachedGraph = null
            graphLoaded = false
            recentRoute = null
        }
    }
}

suspend fun resolveWalkingRoute(
    context: Context,
    from: Pair<Double, Double>,
    to: Pair<Double, Double>
): WalkingRoute? = CampusRoutingResolver.resolveWalkingRoute(context, from, to)
