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
 * Two ways to score a candidate order, sharing the same nearest-neighbor +
 * 2-opt search ([bestVisitOrder] below):
 *
 * - [optimizeStopOrderReal] — real walking *time* from OSRM's Table service
 *   (one HTTP call for the whole matrix, see `fetchWalkingMatrix` in
 *   RoutingApi.kt). Preferred whenever that call succeeds, since it accounts
 *   for things straight-line distance can't — a river with one bridge, a
 *   fence with no gate nearby, a long one-way path — all of which show up on
 *   this campus (see the campus map screenshot that prompted this: Narra
 *   Bridge is the only crossing for a whole stretch of the river).
 * - [optimizeStopOrder] — straight-line (haversine) distance. Used when the
 *   Table service call fails for any reason (no connectivity, demo server
 *   down/rate-limited) — same "degrade gracefully, never block on the
 *   network" contract as the rest of this app's routing code.
 *
 * Once an order is decided, real walking routes are fetched only for the
 * (n-1) sequential legs actually chosen — see `CampusMapScreen.kt`'s route
 * planner — never for every possible pair.
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
 * [optimizeStopOrderReal] or, on fallback, [optimizeStopOrder]), each leg
 * already resolved to a real route where possible. Built once when the user
 * taps "Plan route" — nothing here refetches on its own, so a plan stays
 * exactly what was shown at the moment it was created until explicitly
 * re-planned or cleared. */
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
 * Shared nearest-neighbor + 2-opt search, parameterized by an arbitrary
 * pairwise [cost] (meters, seconds — whatever the caller is minimizing).
 * Point index `0` always means "the from point"; indices `1..stopCount`
 * mean "stop at 0-based index `i - 1`". Returns the visiting order as
 * 0-based stop indices (the "from" point is always the implicit start, so
 * it's never itself in the returned list).
 */
private fun bestVisitOrder(stopCount: Int, cost: (from: Int, to: Int) -> Double): List<Int> {
    if (stopCount <= 1) return (0 until stopCount).toList()

    // --- Nearest-neighbor construction ---
    val remaining = (0 until stopCount).toMutableList()
    val ordered = mutableListOf<Int>()
    var current = 0 // start at the "from" point
    while (remaining.isNotEmpty()) {
        val nearest = remaining.minByOrNull { cost(current, it + 1) }!!
        ordered.add(nearest)
        remaining.remove(nearest)
        current = nearest + 1
    }

    // --- 2-opt improvement ---
    // Repeatedly try reversing a segment of the route; keep the reversal if
    // it shortens the total path (measured from the "from" point, through
    // every stop, in order). Stops once a full pass finds no improving swap.
    fun totalCost(order: List<Int>): Double {
        var total = 0.0
        var from = 0
        for (stopIdx in order) {
            total += cost(from, stopIdx + 1)
            from = stopIdx + 1
        }
        return total
    }

    var improved = true
    var bestOrder = ordered
    var bestCost = totalCost(bestOrder)
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
                val candidateCost = totalCost(candidate)
                if (candidateCost < bestCost - 0.5) { // small tolerance — ignore floating-point noise
                    bestOrder = candidate
                    bestCost = candidateCost
                    improved = true
                }
            }
        }
    }

    return bestOrder
}

/**
 * Returns [stops] reordered into a sensible visiting order starting from
 * [from], scored by straight-line (haversine) distance. Returns an empty
 * list if [stops] is empty. This is the fallback path — prefer
 * [optimizeStopOrderReal] when a real walking-time matrix is available.
 */
fun optimizeStopOrder(from: Pair<Double, Double>, stops: List<RouteStop>): List<RouteStop> {
    if (stops.isEmpty()) return emptyList()
    if (stops.size == 1) return stops

    val order = bestVisitOrder(stops.size) { a, b ->
        val (latA, lonA) = if (a == 0) from else stops[a - 1].lat to stops[a - 1].lon
        val (latB, lonB) = if (b == 0) from else stops[b - 1].lat to stops[b - 1].lon
        haversineMeters(latA, lonA, latB, lonB)
    }
    return order.map { stops[it] }
}

/**
 * Returns [stops] reordered into a sensible visiting order, scored by real
 * walking *time* from [matrix] instead of straight-line distance. [matrix]
 * must be indexed exactly the way `fetchWalkingMatrix` built it: row/column
 * `0` is the "from" point, and row/column `i` (for `i` in `1..stops.size`)
 * is `stops[i - 1]` — i.e. [matrix] came from calling `fetchWalkingMatrix`
 * with `listOf(from) + stops.map { it.lat to it.lon }`, in that order.
 *
 * Preferred over [optimizeStopOrder] whenever the matrix fetch succeeds:
 * real walking time correctly penalizes pairs that look close as the crow
 * flies but require a long detour on foot (opposite sides of a river with
 * one bridge, a fenced-off shortcut, a one-way covered walk).
 */
fun optimizeStopOrderReal(stops: List<RouteStop>, matrix: Array<DoubleArray>): List<RouteStop> {
    if (stops.isEmpty()) return emptyList()
    if (stops.size == 1) return stops

    val order = bestVisitOrder(stops.size) { a, b -> matrix[a][b] }
    return order.map { stops[it] }
}
