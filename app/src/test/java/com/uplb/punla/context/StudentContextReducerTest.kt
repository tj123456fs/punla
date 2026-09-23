package com.uplb.punla.context

import com.uplb.punla.data.BudgetPeriod
import com.uplb.punla.data.entity.ClassSession
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentContextReducerTest {
    private val zone = ZoneId.of("Asia/Manila")

    @Test
    fun classesOutsideTheTermAreNotCurrentOrNext() {
        val cls = ClassSession(id = "math", code = "MATH 27", day = "Tue", type = "lec", start = "10:00", end = "11:00")
        val start = java.time.LocalDate.of(2026, 9, 1)
        val end = java.time.LocalDate.of(2026, 9, 21)
        assertNull(StudentContextReducer.findCurrentClass(listOf(cls), LocalDateTime.of(2026, 9, 22, 10, 30), zone, start, end))
        assertNull(StudentContextReducer.findNextClass(listOf(cls), LocalDateTime.of(2026, 9, 22, 9, 0), zone, start, end))
    }

    @Test
    fun travelRouteExpiresAndDoesNotFollowADifferentOrigin() {
        val route = TravelRouteContext(14.16, 121.24, 14.17, 121.25, 500.0, "OSRM foot", 1000, 601.0)
        assertTrue(route.matches(14.16 to 121.24, 14.17 to 121.25, 2000))
        assertTrue(!route.matches(14.16 to 121.24, 14.17 to 121.25, 301001))
        assertTrue(!route.matches(14.18 to 121.24, 14.17 to 121.25, 2000))
        assertEquals(11, com.uplb.punla.data.walkingEtaMinutes(500.0, route.durationSeconds))
    }

    @Test
    fun currentClass_isDetectedInsideItsWindow() {
        val classes = listOf(
            ClassSession(
                id = "phys",
                code = "PHYS 51",
                day = "Tue",
                type = "lec",
                start = "13:00",
                end = "14:30",
                room = "PSLH"
            )
        )

        val current = StudentContextReducer.findCurrentClass(
            classes,
            LocalDateTime.of(2026, 9, 22, 13, 45),
            zone
        )

        assertEquals("phys", current?.sessionId)
        assertEquals("PSLH", current?.room)
    }

    @Test
    fun currentClass_isNullBeforeClassStarts() {
        val classes = listOf(
            ClassSession(
                id = "math",
                code = "MATH 27",
                day = "Tue",
                type = "lec",
                start = "10:00",
                end = "11:00"
            )
        )

        val current = StudentContextReducer.findCurrentClass(
            classes,
            LocalDateTime.of(2026, 9, 22, 9, 59),
            zone
        )

        assertNull(current)
    }


    @Test
    fun currentClass_keepsOvernightClassAfterMidnight() {
        val classes = listOf(
            ClassSession(
                id = "overnight",
                code = "NSTP 1",
                day = "Mon",
                type = "activity",
                start = "22:00",
                end = "01:00"
            )
        )

        val current = StudentContextReducer.findCurrentClass(
            classes,
            LocalDateTime.of(2026, 9, 22, 0, 30),
            zone
        )

        assertEquals("overnight", current?.sessionId)
        assertEquals("2026-09-21", current?.occurrenceDate)
    }

    @Test
    fun canonicalCourseCodes_dedupesIgnoringCase() {
        val inputs = StudentContextReducer.Inputs(
            classes = listOf(
                ClassSession(
                    id = "upper",
                    code = "CMSC 12",
                    day = "Tue",
                    type = "lec",
                    start = "08:00",
                    end = "09:00"
                ),
                ClassSession(
                    id = "lower",
                    code = "cmsc 12",
                    day = "Wed",
                    type = "lab",
                    start = "10:00",
                    end = "11:00"
                )
            ),
            deadlines = emptyList(),
            attendance = emptyList(),
            studySessions = emptyList(),
            expenses = emptyList(),
            expenseRules = emptyList(),
            decks = emptyList(),
            cards = emptyList(),
            quizzes = emptyList(),
            quizAttempts = emptyList(),
            mistakes = emptyList(),
            planItems = emptyList(),
            reviewProgress = emptyList()
        )

        assertEquals(listOf("CMSC 12"), StudentContextReducer.canonicalCourseCodes(inputs))
    }

    @Test
    fun weeklySafeToSpend_isUnsetWhenWeeklyBudgetIsZero() {
        val safe = StudentContextReducer.weeklySafeToSpend(
            weeklyBudget = 0.0,
            spentWeek = 250.0,
            daysRemaining = 4L,
            budgetPeriod = BudgetPeriod.WEEKLY
        )

        assertNull(safe)
    }

    @Test
    fun nextClass_rollsAcrossDays() {
        val classes = listOf(
            ClassSession(
                id = "wednesday",
                code = "AGRI 31",
                day = "Wed",
                type = "lec",
                start = "08:00",
                end = "09:00"
            )
        )

        val next = StudentContextReducer.findNextClass(
            classes,
            LocalDateTime.of(2026, 9, 22, 20, 0),
            zone
        )

        assertEquals("wednesday", next?.sessionId)
        assertEquals("2026-09-23", next?.occurrenceDate)
    }
    @Test
    fun freeMinutes_isZeroWhileCurrentlyInClass() {
        val now = LocalDateTime.of(2026, 9, 22, 13, 30)
        val current = ClassContext(
            sessionId = "current", code = "PHYS 51", type = "lec",
            occurrenceDate = "2026-09-22", startTime = "13:00", endTime = "14:30",
            startsAtEpochMillis = now.minusMinutes(30).atZone(zone).toInstant().toEpochMilli(),
            endsAtEpochMillis = now.plusHours(1).atZone(zone).toInstant().toEpochMilli()
        )
        val next = ClassContext(
            sessionId = "next", code = "MATH 27", type = "lec",
            occurrenceDate = "2026-09-22", startTime = "16:00", endTime = "17:00",
            startsAtEpochMillis = now.plusHours(2).plusMinutes(30).atZone(zone).toInstant().toEpochMilli(),
            endsAtEpochMillis = now.plusHours(3).plusMinutes(30).atZone(zone).toInstant().toEpochMilli()
        )

        assertEquals(
            0,
            StudentContextReducer.freeMinutesBeforeNextCommitment(current, next, now, zone)
        )
    }

    @Test
    fun travelBuffer_usesFreshOptInCampusLocation() {
        val now = LocalDateTime.of(2026, 9, 22, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val location = LocationContext(
            latitude = 14.164378759022,
            longitude = 121.241803648353,
            capturedAtEpochMillis = now
        )
        val next = ClassContext(
            sessionId = "math", code = "MATH 27", type = "lec", room = "MB 100",
            occurrenceDate = "2026-09-22", startTime = "13:00", endTime = "14:00",
            startsAtEpochMillis = now + 60 * 60 * 1000L,
            endsAtEpochMillis = now + 2 * 60 * 60 * 1000L
        )

        val minutes = StudentContextReducer.estimatedTravelBufferMinutes(next, location, now)

        assertNotNull(minutes)
        assertTrue(minutes!! in 1..30)
    }

    @Test
    fun staleLocation_isExcludedFromSharedContext() {
        val now = 2_000_000L
        val stale = LocationContext(
            latitude = 14.164,
            longitude = 121.242,
            capturedAtEpochMillis = now - (16L * 60L * 1000L)
        )

        assertTrue(!StudentContextReducer.isLocationFresh(stale, now))
    }

}
