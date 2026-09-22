package com.uplb.punla.context

import com.uplb.punla.data.entity.ClassSession
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudentContextReducerTest {
    private val zone = ZoneId.of("Asia/Manila")

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
}
