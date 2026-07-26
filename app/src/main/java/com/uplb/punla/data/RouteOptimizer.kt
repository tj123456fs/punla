package com.uplb.punla.data

/**
 * Multi-stop visiting order for a handful of buildings — "given I'm here and
 * need to visit these N places, what order should I go in?"
 *
 * This is a heuristic, not an exact solver: finding the *provably* shortest
 * order (true TSP) is NP-hard and overkill for what's realistically a
 * handful of stops on one compact campus. Nearest-neighbor construction
 * followed by 2-opt improvement is the standard, well-understood combination
 * for exactly this kind of small, practical routing problem — it won't
 * always find the absolute optimum, but it reliably removes the obviously
 * silly orderings (doubling back, crossing your own path) and runs
 * instantly even for a dozen-plus stops.
 *
 * Deliberately uses straight-line (haversine) distances for the ordering
 * decision itself, not real walking-route distances — the ordering only
 * needs to be "sensible for a compact campus," and computing real walking
 * routes for every pair of stops would be an O(n²) burst of calls against a
 * free, rate-limited public routing server for very little accuracy benefit
 * over straight-line at this scale. Once an order is decided, fetch real
 * walking routes only for the (n-1) sequential legs actually chosen — see
 * `CampusMapScreen.kt`'s route planner.
 */

/** One stop to visit, identified by name so callers can map the result back
 * to whatever they're tracking selection with (a `Set<String>` of building
 * names, in practice). */
data class RouteStop(val name: String, val lat: Double, val lon: Double)

/**
 * One leg of a multi-stop plan: walk from [from] to [stop]. [route] is the
 * real walking route fetched for this specific leg at plan-creation time —
 * null if that fetch failed, in which case renderers should fall back to a
 * straight line for just this leg rather than discarding the whole plan.
 */
data class RoutePlanLeg(
    val from: Pair<Double, Double>,
    val stop: RouteStop,
    val route: WalkingRoute?
)

/** A full multi-stop plan, legs in visiting order (as decided by
 * [optimizeStopOrder]), each leg already resolved to a real route where
 * possible. Built once when the user taps "Plan route" — nothing here
 * refetches on its own, so a plan stays exactly what was shown at the
 * moment it was created until explicitly re-planned or cleared. */
data class RoutePlan(val legs: List<RoutePlanLeg>) {
    val totalDistanceMeters: Double get() = legs.sumOf { leg ->
        leg.route?.distanceMeters ?: haversineMeters(leg.from.first, leg.from.second, leg.stop.lat, leg.stop.lon)
    }
    val totalDurationSeconds: Double get() = legs.sumOf { leg ->
        leg.route?.durationSeconds ?: (
            haversineMeters(leg.from.first, leg.from.second, leg.stop.lat, leg.stop.lon) / 75.0 * 60.0
        )
    }
}

/**
 * Returns [stops] reordered into a sensible visiting order starting from
 * [from]. Returns an empty list if [stops] is empty.
 */
fun optimizeStopOrder(from: Pair<Double, Double>, stops: List<RouteStop>): List<RouteStop> {
    if (stops.isEmpty()) return emptyList()
    if (stops.size == 1) return stops

    // --- Nearest-neighbor construction ---
    val remaining = stops.toMutableList()
    val ordered = mutableListOf<RouteStop>()
    var currentLat = from.first
    var currentLon = from.second
    while (remaining.isNotEmpty()) {
        val nearest = remaining.minByOrNull { haversineMeters(currentLat, currentLon, it.lat, it.lon) }!!
        ordered.add(nearest)
        remaining.remove(nearest)
        currentLat = nearest.lat
        currentLon = nearest.lon
    }

    // --- 2-opt improvement ---
    // Repeatedly try reversing a segment of the route; keep the reversal if
    // it shortens the total path (measured from `from`, through every stop,
    // in order). Stops once a full pass finds no improving swap.
    fun totalDistance(order: List<RouteStop>): Double {
        var total = 0.0
        var lat = from.first
        var lon = from.second
        for (stop in order) {
            total += haversineMeters(lat, lon, stop.lat, stop.lon)
            lat = stop.lat
            lon = stop.lon
        }
        return total
    }

    var improved = true
    var bestOrder = ordered
    var bestDistance = totalDistance(bestOrder)
    var guard = 0 // safety cap — 2-opt on this few stops converges almost
                  // immediately in practice, this just rules out any chance
                  // of looping forever on a pathological input.
    while (improved && guard < 200) {
        improved = false
        guard++
        for (i in 0 until bestOrder.size - 1) {
            for (j in i + 1 until bestOrder.size) {
                val candidate = bestOrder.toMutableList()
                // Reverse the segment between i and j (inclusive) — the
                // classic 2-opt move.
                var lo = i
                var hi = j
                while (lo < hi) {
                    val tmp = candidate[lo]
                    candidate[lo] = candidate[hi]
                    candidate[hi] = tmp
                    lo++
                    hi--
                }
                val candidateDistance = totalDistance(candidate)
                if (candidateDistance < bestDistance - 0.5) { // 0.5m tolerance — ignore floating-point noise
                    bestOrder = candidate
                    bestDistance = candidateDistance
                    improved = true
                }
            }
        }
    }

    return bestOrder
}
