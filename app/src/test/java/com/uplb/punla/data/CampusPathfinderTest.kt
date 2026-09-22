package com.uplb.punla.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusPathfinderTest {
    @Test
    fun sameEdgeEndpoints_useDistinctSplitsAndDirectSegment() {
        val graph = CampusPathGraph.build(
            nodes = listOf(
                PathNode("a", 14.1600, 121.2400),
                PathNode("b", 14.1600, 121.2410)
            ),
            edges = listOf(PathEdge("a", "b"))
        )
        val from = 14.1600 to 121.24025
        val to = 14.1600 to 121.24075

        val route = findLocalCampusRoute(graph, from, to)

        assertNotNull(route)
        route!!
        assertEquals(from.first, route.points.first().first, 1e-8)
        assertEquals(from.second, route.points.first().second, 1e-8)
        assertEquals(to.first, route.points.last().first, 1e-8)
        assertEquals(to.second, route.points.last().second, 1e-8)
        assertTrue(route.distanceMeters in 45.0..70.0)
    }

    @Test
    fun splitUsesResolvedEdgeDistanceNotFreshHaversine() {
        val graph = CampusPathGraph.build(
            nodes = listOf(
                PathNode("a", 14.1600, 121.2400),
                PathNode("b", 14.1600, 121.2410)
            ),
            edges = listOf(PathEdge("a", "b", distanceMeters = 200.0))
        )

        val route = findLocalCampusRoute(
            graph,
            14.1600 to 121.24025,
            14.1600 to 121.24075
        )

        assertNotNull(route)
        assertTrue(route!!.distanceMeters in 98.0..102.0)
    }

    @Test
    fun routeAcrossBarrier_usesOnlyConnectedBridgeChain() {
        val bridgeWest = PathNode("bridge_w", 14.1600, 121.2408)
        val bridgeEast = PathNode("bridge_e", 14.1600, 121.2412)
        val graph = CampusPathGraph.build(
            nodes = listOf(
                PathNode("west", 14.1600, 121.2400),
                bridgeWest,
                bridgeEast,
                PathNode("east", 14.1600, 121.2420)
            ),
            edges = listOf(
                PathEdge("west", "bridge_w"),
                PathEdge("bridge_w", "bridge_e"),
                PathEdge("bridge_e", "east")
            )
        )

        val route = findLocalCampusRoute(
            graph,
            14.1600 to 121.2400,
            14.1600 to 121.2420
        )

        assertNotNull(route)
        val points = route!!.points
        assertTrue(points.any { kotlin.math.abs(it.first - bridgeWest.lat) < 1e-8 && kotlin.math.abs(it.second - bridgeWest.lon) < 1e-8 })
        assertTrue(points.any { kotlin.math.abs(it.first - bridgeEast.lat) < 1e-8 && kotlin.math.abs(it.second - bridgeEast.lon) < 1e-8 })
    }

    @Test
    fun outsideCoverage_returnsNullInsteadOfForcingBadSnap() {
        val graph = CampusPathGraph.build(
            nodes = listOf(
                PathNode("a", 14.1600, 121.2400),
                PathNode("b", 14.1600, 121.2410)
            ),
            edges = listOf(PathEdge("a", "b"))
        )

        val route = findLocalCampusRoute(
            graph,
            14.1700 to 121.2500,
            14.1600 to 121.2405,
            maxSnapMeters = 60.0
        )

        assertNull(route)
    }

    @Test
    fun duplicateNodeIds_areRejected() {
        val result = runCatching {
            CampusPathGraph.build(
                nodes = listOf(
                    PathNode("same", 14.1600, 121.2400),
                    PathNode("same", 14.1601, 121.2401)
                ),
                edges = emptyList()
            )
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
