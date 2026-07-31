package com.uplb.punla.ml

import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.data.entity.ExpenseRule
import com.uplb.punla.data.entity.StudySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UserPatternsTest {
    private fun session(hour: Int, completed: Boolean): StudySession {
        val start = LocalDate.of(2026, 7, 1).atTime(hour, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return StudySession(
            startedAt = start,
            endedAt = start + 25 * 60_000,
            plannedMinutes = 25,
            actualSeconds = if (completed) 1500 else 300,
            completed = completed
        )
    }

    @Test fun emptyInputsAreSafe() {
        assertTrue(studyTimeHistogram(emptyList()).isEmpty())
        assertEquals(0f, sessionEarlyStopRate(emptyList()))
        assertTrue(recurringExpenseCandidates(emptyList()).isEmpty())
    }

    @Test fun histogramUsesAttemptsAsDenominator() {
        val result = studyTimeHistogram(listOf(session(19, true), session(19, false)))
        assertEquals(0.5f, result.getValue(19))
    }

    @Test fun recurringPatternRequiresThreeDatesAndStableAmount() {
        val expenses = listOf(
            Expense(amount = 99.0, category = "Bills", date = "2026-05-01", note = "Spotify"),
            Expense(amount = 100.0, category = "Bills", date = "2026-06-01", note = "SPOTIFY"),
            Expense(amount = 99.0, category = "Bills", date = "2026-07-01", note = "Spotify")
        )
        val patterns = recurringExpenseCandidates(expenses)
        assertEquals(1, patterns.size)
        assertEquals(3, patterns.single().occurrences)
    }

    @Test fun recurringRuleIsNotSuggestedAgain() {
        val expenses = listOf(
            Expense(amount = 99.0, category = "Bills", date = "2026-05-01", note = "Spotify"),
            Expense(amount = 99.0, category = "Bills", date = "2026-06-01", note = "Spotify"),
            Expense(amount = 99.0, category = "Bills", date = "2026-07-01", note = "Spotify")
        )
        val rule = ExpenseRule(
            amount = 99.0,
            category = "Bills",
            note = "spotify",
            startDate = "2026-07-01",
            repeat = "monthly",
            lastGenerated = "2026-07-01"
        )
        assertTrue(recurringExpenseCandidates(expenses, listOf(rule)).isEmpty())
    }

    @Test fun attendanceProjectionNeverCrashesEarlyInTerm() {
        val c = ClassSession(code = "ABE 1", day = "Mon", type = "lec", start = "08:00", end = "09:00", absences = 1)
        val result = projectAttendanceRisk(
            c,
            allowedAbsences = 3,
            termStart = LocalDate.of(2026, 8, 3),
            termEnd = LocalDate.of(2026, 11, 14),
            today = LocalDate.of(2026, 8, 3)
        )
        assertTrue(result.projectedAbsences >= 0f)
    }
    @Test fun zeroAbsencesDoNotWarnOnFirstWeek() {
        val c = ClassSession(code = "ABE 1", day = "Mon", type = "lec", start = "08:00", end = "09:00", absences = 0)
        val result = projectAttendanceRisk(
            c,
            allowedAbsences = 3,
            termStart = LocalDate.of(2026, 8, 3),
            termEnd = LocalDate.of(2026, 11, 14),
            today = LocalDate.of(2026, 8, 3)
        )
        assertEquals("LOW", result.risk)
    }

}
