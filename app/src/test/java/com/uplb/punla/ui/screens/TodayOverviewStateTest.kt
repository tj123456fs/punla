package com.uplb.punla.ui.screens

import com.uplb.punla.context.ClassContext
import com.uplb.punla.context.StudentState
import com.uplb.punla.context.StudyContext
import com.uplb.punla.context.TaskContext
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayOverviewStateTest {
    private val nextToday = ClassContext(
        sessionId = "class-1",
        code = "PHYS 51",
        title = "Physics",
        section = "A",
        type = "lec",
        room = "PH A-1",
        occurrenceDate = "2026-09-22",
        startTime = "11:00",
        endTime = "12:00",
        startsAtEpochMillis = 0L,
        endsAtEpochMillis = 0L
    )

    @Test fun inClassWinsNowState() {
        val state = base().copy(currentClass = nextToday.copy(startTime = "09:00", endTime = "10:30"))
        assertEquals(TodayNowKind.IN_CLASS, deriveTodayNowPresentation(state).kind)
    }

    @Test fun freeTimeUsesUsableMinutes() {
        val state = base().copy(nextClass = nextToday, freeMinutesBeforeNextCommitment = 60, travelBufferMinutes = 10, usableFreeMinutes = 50)
        val shown = deriveTodayNowPresentation(state)
        assertEquals(TodayNowKind.FREE_TIME, shown.kind)
        assertEquals(TodayAction.FOCUS, shown.action)
    }

    @Test fun travelBufferTriggersLeaveSoon() {
        val state = base().copy(nextClass = nextToday, freeMinutesBeforeNextCommitment = 12, travelBufferMinutes = 10, usableFreeMinutes = 2)
        val shown = deriveTodayNowPresentation(state)
        assertEquals(TodayNowKind.LEAVE_SOON, shown.kind)
        assertEquals(TodayAction.MAP, shown.action)
    }

    @Test fun tomorrowClassMeansDayComplete() {
        val state = base().copy(nextClass = nextToday.copy(occurrenceDate = "2026-09-23"))
        assertEquals(TodayNowKind.DAY_COMPLETE, deriveTodayNowPresentation(state).kind)
    }

    @Test fun realStudySuggestionStaysPrimaryRecommendation() {
        val shown = deriveTodayRecommendationPresentation(base(), "Focus on Exercise 5", "MATH 27 · 10:30–11:00")
        assertEquals(TodayAction.FOCUS, shown.action)
        assertEquals("Focus on Exercise 5", shown.title)
    }

    @Test fun overdueWorkFallsBackToStudy() {
        val state = base().copy(
            overdueWork = listOf(TaskContext("d1", "Problem set", "PHYS 51", "2026-09-21", "DEADLINE", overdue = true))
        )
        val shown = deriveTodayRecommendationPresentation(state, null, null)
        assertEquals(TodayAction.STUDY, shown.action)
    }

    @Test fun dueReviewItemsFillFreeBlock() {
        val state = base().copy(
            nextClass = nextToday,
            freeMinutesBeforeNextCommitment = 60,
            usableFreeMinutes = 60,
            study = StudyContext(dueFlashcardCount = 3, unresolvedMistakeCount = 2)
        )
        val shown = deriveTodayRecommendationPresentation(state, null, null)
        assertEquals(TodayAction.STUDY, shown.action)
    }

    private fun base() = StudentState(
        generatedAtEpochMillis = 0L,
        localDate = "2026-09-22",
        localTime = "10:00",
        day = "Tue"
    )
}
