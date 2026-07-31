package com.uplb.punla.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySlotPredictorTest {
    private val features = StudySlotFeatures(
        hour = 19,
        dayOfWeek = 2,
        urgencyDays = 1,
        availableMinutes = 60,
        plannedMinutes = 25,
        recentCompletionRate = 0.8f,
        currentStreak = 4
    )

    @Test fun newModelIsNeutral() {
        assertEquals(0.5, StudySlotPredictor.probability(StudySlotModelState(), features), 0.0001)
    }

    @Test fun positiveUpdatesIncreaseProbability() {
        val initial = StudySlotModelState()
        val learned = (1..10).fold(initial) { state, _ -> StudySlotPredictor.update(state, features, used = true) }
        assertTrue(StudySlotPredictor.probability(learned, features) > 0.5)
        assertEquals(10, learned.sampleCount)
    }

    @Test fun negativeUpdatesDecreaseProbability() {
        val initial = StudySlotModelState()
        val learned = (1..10).fold(initial) { state, _ -> StudySlotPredictor.update(state, features, used = false) }
        assertTrue(StudySlotPredictor.probability(learned, features) < 0.5)
    }
}
