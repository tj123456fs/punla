package com.uplb.punla.data

import com.uplb.punla.data.entity.WalkPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedCampusPathEngineTest {
    private var nextId = 0

    private fun ids(): String = "node_${nextId++}"

    private fun point(
        sequence: Int,
        lat: Double,
        lon: Double,
        segment: Int = 0,
        capturedAt: Long = sequence * 3_000L,
        accuracy: Float? = 5f
    ) = WalkPoint(
        sessionId = "walk",
        sequence = sequence,
        lat = lat,
        lon = lon,
        segment = segment,
        accuracyMeters = accuracy,
        capturedAt = capturedAt
    )

    private fun straightWalk(reversed: Boolean = false): List<WalkPoint> {
        val coords = listOf(
            14.1700 to 121.2400,
            14.1700 to 121.2401,
            14.1700 to 121.2402,
            14.1700 to 121.2403,
            14.1700 to 121.2404
        ).let { if (reversed) it.reversed() else it }
        return coords.mapIndexed { index, (lat, lon) -> point(index, lat, lon) }
    }

    @Test
    fun oneWalkSeedsButDoesNotBecomeTrustedRouting() {
        val first = LearnedCampusPathEngine.applyWalk(
            points = straightWalk(),
            existingNodes = emptyList(),
            existingEdges = emptyList(),
            observedAt = 1_000L,
            nodeIdFactory = ::ids
        )

        assertTrue(first.state.edges.isNotEmpty())
        assertNull(
            LearnedCampusPathEngine.buildTrustedGraph(
                first.state.nodes,
                first.state.edges
            )
        )
    }

    @Test
    fun reverseTraversalStrengthensTheSameUndirectedEdges() {
        val first = LearnedCampusPathEngine.applyWalk(
            straightWalk(),
            emptyList(),
            emptyList(),
            observedAt = 1_000L,
            nodeIdFactory = ::ids
        )
        val second = LearnedCampusPathEngine.applyWalk(
            straightWalk(reversed = true),
            first.state.nodes,
            first.state.edges,
            observedAt = 2_000L,
            nodeIdFactory = ::ids
        )

        assertEquals(first.state.edges.size, second.state.edges.size)
        assertTrue(second.state.edges.all { it.observationCount == 2 })
        assertNotNull(
            LearnedCampusPathEngine.buildTrustedGraph(
                second.state.nodes,
                second.state.edges
            )
        )
    }

    @Test
    fun pauseSegmentsAreNeverBridged() {
        val points = listOf(
            point(0, 14.1700, 121.2400, segment = 0),
            point(1, 14.1700, 121.2401, segment = 0),
            point(2, 14.1710, 121.2410, segment = 1),
            point(3, 14.1710, 121.2411, segment = 1)
        )
        val learned = LearnedCampusPathEngine.applyWalk(
            points,
            emptyList(),
            emptyList(),
            observedAt = 1_000L,
            nodeIdFactory = ::ids
        )
        val graph = LearnedCampusPathEngine.buildTrustedGraph(
            learned.state.nodes,
            learned.state.edges,
            minimumObservations = 1
        ) ?: error("expected low-confidence graph")

        val route = findLocalCampusRoute(
            graph,
            from = 14.1700 to 121.2400,
            to = 14.1710 to 121.2411,
            maxSnapMeters = 20.0
        )
        assertNull(route)
    }

    @Test
    fun crossingRecordedPathsShareAJunction() {
        val horizontal = listOf(
            point(0, 14.1700, 121.2400),
            point(1, 14.1700, 121.2402),
            point(2, 14.1700, 121.2404)
        )
        val first = LearnedCampusPathEngine.applyWalk(
            horizontal,
            emptyList(),
            emptyList(),
            observedAt = 1_000L,
            nodeIdFactory = ::ids
        )

        val vertical = listOf(
            point(0, 14.1698, 121.2402),
            point(1, 14.1700, 121.2402),
            point(2, 14.1702, 121.2402)
        )
        val second = LearnedCampusPathEngine.applyWalk(
            vertical,
            first.state.nodes,
            first.state.edges,
            observedAt = 2_000L,
            nodeIdFactory = ::ids
        )
        val graph = LearnedCampusPathEngine.buildTrustedGraph(
            second.state.nodes,
            second.state.edges,
            minimumObservations = 1
        ) ?: error("expected learned graph")

        val route = findLocalCampusRoute(
            graph,
            from = 14.1700 to 121.2400,
            to = 14.1702 to 121.2402,
            maxSnapMeters = 20.0
        )
        assertNotNull(route)
    }

    @Test
    fun poorAccuracyPointsDoNotSeedEdges() {
        val learned = LearnedCampusPathEngine.applyWalk(
            points = listOf(
                point(0, 14.1700, 121.2400, accuracy = 40f),
                point(1, 14.1700, 121.2401, accuracy = 40f)
            ),
            existingNodes = emptyList(),
            existingEdges = emptyList(),
            observedAt = 1_000L,
            nodeIdFactory = ::ids
        )

        assertEquals(0, learned.acceptedPointCount)
        assertTrue(learned.state.edges.isEmpty())
    }
}
