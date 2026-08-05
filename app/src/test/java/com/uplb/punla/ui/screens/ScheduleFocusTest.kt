package com.uplb.punla.ui.screens

import com.uplb.punla.data.entity.ClassSession
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleFocusTest {
    private val monday = listOf(
        session("first", "08:00", "09:00"),
        session("middle", "10:00", "11:30"),
        session("last", "14:00", "15:00")
    )

    @Test fun `opens the class happening now`() {
        val focus = findScheduleFocus(monday, LocalDateTime.of(2026, 8, 3, 10, 45))
        assertEquals("Mon", focus.day)
        assertEquals("middle", focus.sessionId)
        assertEquals(ScheduleFocusKind.CURRENT, focus.kind)
    }

    @Test fun `opens the next class before or between classes`() {
        val before = findScheduleFocus(monday, LocalDateTime.of(2026, 8, 3, 7, 0))
        val between = findScheduleFocus(monday, LocalDateTime.of(2026, 8, 3, 12, 0))
        assertEquals("first", before.sessionId)
        assertEquals(ScheduleFocusKind.NEXT, before.kind)
        assertEquals("last", between.sessionId)
        assertEquals(ScheduleFocusKind.NEXT, between.kind)
    }

    @Test fun `opens the final class after the school day`() {
        val focus = findScheduleFocus(monday, LocalDateTime.of(2026, 8, 3, 18, 0))
        assertEquals("last", focus.sessionId)
        assertEquals(ScheduleFocusKind.LAST, focus.kind)
    }

    @Test fun `selects today even when today has no classes`() {
        val focus = findScheduleFocus(monday, LocalDateTime.of(2026, 8, 4, 10, 0))
        assertEquals("Tue", focus.day)
        assertNull(focus.sessionId)
        assertNull(focus.kind)
    }

    @Test fun `sunday has no unsupported fake schedule day`() {
        val focus = findScheduleFocus(monday, LocalDateTime.of(2026, 8, 9, 10, 0))
        assertNull(focus.day)
        assertNull(focus.sessionId)
    }

    @Test fun `class end is exclusive and next class can begin immediately`() {
        val adjacent = listOf(
            session("a", "08:00", "09:00"),
            session("b", "09:00", "10:00")
        )
        val focus = findScheduleFocus(adjacent, LocalDateTime.of(2026, 8, 3, 9, 0))
        assertEquals("b", focus.sessionId)
        assertEquals(ScheduleFocusKind.CURRENT, focus.kind)
    }

    private fun session(id: String, start: String, end: String) = ClassSession(
        id = id,
        code = id.uppercase(),
        day = "Mon",
        type = "lec",
        start = start,
        end = end
    )
}
