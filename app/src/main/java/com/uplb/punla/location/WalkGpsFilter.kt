package com.uplb.punla.location

import com.uplb.punla.data.haversineMeters
import kotlin.math.max
import kotlin.math.min

internal data class WalkGpsSample(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float?,
    val capturedAt: Long
)

internal sealed interface WalkGpsDecision {
    data object Ignore : WalkGpsDecision

    data class Accept(
        val addedDistanceMeters: Double,
        val startNewSegment: Boolean
    ) : WalkGpsDecision
}

/**
 * Pure GPS acceptance policy for campus walks.
 *
 * It keeps raw recording useful without letting horizontal-accuracy jitter,
 * stale cached fixes, impossible walking speeds, or provider outages inflate
 * distance and learned path geometry.
 */
internal object WalkGpsFilter {
    private const val MAX_ACCEPTED_ACCURACY_METERS = 35f
    private const val MAX_FIX_AGE_MILLIS = 20_000L
    private const val MAX_FUTURE_SKEW_MILLIS = 5_000L
    private const val GAP_SPLIT_MILLIS = 15_000L
    private const val MIN_MOVEMENT_METERS = 1.5
    private const val MAX_JITTER_GATE_METERS = 7.0
    private const val ACCURACY_GATE_FACTOR = 0.25
    private const val MAX_PLAUSIBLE_WALK_SPEED_MPS = 5.5

    fun evaluate(
        previous: WalkGpsSample?,
        candidate: WalkGpsSample,
        nowMillis: Long
    ): WalkGpsDecision {
        if (!candidate.lat.isFinite() || candidate.lat !in -90.0..90.0) return WalkGpsDecision.Ignore
        if (!candidate.lon.isFinite() || candidate.lon !in -180.0..180.0) return WalkGpsDecision.Ignore

        val accuracy = candidate.accuracyMeters
        if (accuracy == null || !accuracy.isFinite() || accuracy <= 0f || accuracy > MAX_ACCEPTED_ACCURACY_METERS) {
            return WalkGpsDecision.Ignore
        }

        val ageMillis = nowMillis - candidate.capturedAt
        if (ageMillis > MAX_FIX_AGE_MILLIS || ageMillis < -MAX_FUTURE_SKEW_MILLIS) {
            return WalkGpsDecision.Ignore
        }

        if (previous == null) {
            return WalkGpsDecision.Accept(addedDistanceMeters = 0.0, startNewSegment = false)
        }

        val elapsedMillis = candidate.capturedAt - previous.capturedAt
        if (elapsedMillis <= 0L) return WalkGpsDecision.Ignore

        // Do not bridge a provider outage. The new fix can seed the next segment,
        // but the missing interval contributes zero distance and no learned edge.
        if (elapsedMillis > GAP_SPLIT_MILLIS) {
            return WalkGpsDecision.Accept(addedDistanceMeters = 0.0, startNewSegment = true)
        }

        val distance = haversineMeters(previous.lat, previous.lon, candidate.lat, candidate.lon)
        if (!distance.isFinite()) return WalkGpsDecision.Ignore

        val previousAccuracy = previous.accuracyMeters
            ?.takeIf { it.isFinite() && it > 0f }
            ?: accuracy
        val jitterGate = max(
            MIN_MOVEMENT_METERS,
            min(
                MAX_JITTER_GATE_METERS,
                (previousAccuracy + accuracy) * ACCURACY_GATE_FACTOR
            )
        )
        if (distance < jitterGate) return WalkGpsDecision.Ignore

        val speedMetersPerSecond = distance / (elapsedMillis / 1000.0)
        if (!speedMetersPerSecond.isFinite() || speedMetersPerSecond > MAX_PLAUSIBLE_WALK_SPEED_MPS) {
            return WalkGpsDecision.Ignore
        }

        return WalkGpsDecision.Accept(
            addedDistanceMeters = distance,
            startNewSegment = false
        )
    }
}
