package com.uplb.punla.ml

import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.ui.pomodoro.suggestStudySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class StudySuggestionTest {
    @Test fun todaySuggestionNeverUsesAnElapsedStartTime() {
        val today = LocalDate.of(2026, 7, 31)
        val suggestion = suggestStudySlot(
            day = "Fri",
            dayLabel = "today",
            classes = emptyList(),
            deadlines = listOf(
                Deadline(
                    title = "Design report",
                    due = today.plusDays(1).toString(),
                    type = "Academic",
                    priority = "high"
                )
            ),
            pomodoroWorkMinutes = 25,
            now = today,
            date = today,
            nowTime = LocalTime.of(10, 7)
        )
        assertNotNull(suggestion)
        assertEquals("10:15", suggestion!!.slotStart)
    }
}
