package com.uplb.punla.ml

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/** Tiny inspectable online logistic-regression model for one person's data. */
data class StudySlotModelState(
    val weights: List<Double> = List(FEATURE_COUNT) { 0.0 },
    val bias: Double = 0.0,
    val sampleCount: Int = 0,
    val version: Int = 1
) {
    companion object { const val FEATURE_COUNT = 7 }
}

data class StudySlotFeatures(
    val hour: Int,
    val dayOfWeek: Int,
    val urgencyDays: Int,
    val availableMinutes: Int,
    val plannedMinutes: Int,
    val recentCompletionRate: Float,
    val currentStreak: Int
) {
    fun vector(): DoubleArray {
        val angle = 2.0 * PI * hour.coerceIn(0, 23) / 24.0
        return doubleArrayOf(
            sin(angle),
            cos(angle),
            if (dayOfWeek >= 6) 1.0 else 0.0,
            1.0 / (1.0 + urgencyDays.coerceAtLeast(0)),
            (availableMinutes.toDouble() / plannedMinutes.coerceAtLeast(1)).coerceIn(0.0, 3.0) / 3.0,
            recentCompletionRate.coerceIn(0f, 1f).toDouble(),
            ln(1.0 + currentStreak.coerceAtLeast(0)) / ln(31.0)
        )
    }
}

object StudySlotPredictor {
    const val MIN_SAMPLES_FOR_PREDICTION = 50
    private const val LEARNING_RATE = 0.08
    private const val L2 = 0.001

    fun probability(state: StudySlotModelState, features: StudySlotFeatures): Double {
        val x = features.vector()
        val z = state.bias + state.weights.zip(x.asList()).sumOf { (w, v) -> w * v }
        return 1.0 / (1.0 + exp(-z.coerceIn(-20.0, 20.0)))
    }

    fun update(state: StudySlotModelState, features: StudySlotFeatures, used: Boolean): StudySlotModelState {
        val x = features.vector()
        val prediction = probability(state, features)
        val error = (if (used) 1.0 else 0.0) - prediction
        val newWeights = state.weights.mapIndexed { i, w ->
            w + LEARNING_RATE * (error * x[i] - L2 * w)
        }
        return state.copy(
            weights = newWeights,
            bias = state.bias + LEARNING_RATE * error,
            sampleCount = state.sampleCount + 1
        )
    }
}
