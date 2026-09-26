package com.uplb.punla.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkGpsFilterTest {
    private fun sample(
        lat: Double,
        lon: Double,
        accuracy: Float? = 5f,
        capturedAt: Long
    ) = WalkGpsSample(lat, lon, accuracy, capturedAt)

    @Test
    fun stationaryAccuracyJitterIsIgnored() {
        val previous = sample(14.1700, 121.2400, accuracy = 10f, capturedAt = 100_000L)
        val candidate = sample(14.1700, 121.24002, accuracy = 10f, capturedAt = 103_000L)

        assertTrue(
            WalkGpsFilter.evaluate(previous, candidate, nowMillis = 103_000L) is WalkGpsDecision.Ignore
        )
    }

    @Test
    fun normalWalkingDistanceIsAccepted() {
        val previous = sample(14.1700, 121.2400, accuracy = 8f, capturedAt = 100_000L)
        val candidate = sample(14.1700, 121.24008, accuracy = 8f, capturedAt = 106_000L)

        val decision = WalkGpsFilter.evaluate(previous, candidate, nowMillis = 106_000L)
        assertTrue(decision is WalkGpsDecision.Accept)
        decision as WalkGpsDecision.Accept
        assertTrue(decision.addedDistanceMeters > 5.0)
        assertEquals(false, decision.startNewSegment)
    }

    @Test
    fun implausibleWalkingSpeedIsIgnored() {
        val previous = sample(14.1700, 121.2400, capturedAt = 100_000L)
        val candidate = sample(14.1700, 121.24035, capturedAt = 103_000L)

        assertTrue(
            WalkGpsFilter.evaluate(previous, candidate, nowMillis = 103_000L) is WalkGpsDecision.Ignore
        )
    }

    @Test
    fun providerGapStartsFreshSegmentWithoutAddingDistance() {
        val previous = sample(14.1700, 121.2400, capturedAt = 100_000L)
        val candidate = sample(14.1710, 121.2410, capturedAt = 121_000L)

        val decision = WalkGpsFilter.evaluate(previous, candidate, nowMillis = 121_000L)
        assertTrue(decision is WalkGpsDecision.Accept)
        decision as WalkGpsDecision.Accept
        assertEquals(0.0, decision.addedDistanceMeters, 0.0)
        assertEquals(true, decision.startNewSegment)
    }

    @Test
    fun staleOrAccuracylessFixIsIgnored() {
        val stale = sample(14.1700, 121.2400, capturedAt = 100_000L)
        assertTrue(
            WalkGpsFilter.evaluate(null, stale, nowMillis = 121_000L) is WalkGpsDecision.Ignore
        )

        val noAccuracy = sample(14.1700, 121.2400, accuracy = null, capturedAt = 121_000L)
        assertTrue(
            WalkGpsFilter.evaluate(null, noAccuracy, nowMillis = 121_000L) is WalkGpsDecision.Ignore
        )
    }
}
