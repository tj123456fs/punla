package com.uplb.punla.notification

import com.uplb.punla.data.entity.ClassSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ClassDayTimelineTest {
    private val mondayClasses = listOf(
        classAt("MATH 27", "09:00", "10:00", "MB 101"),
        classAt("ENG 10", "11:30", "13:00", "CAS A1 306")
    )

    @Test
    fun beforeFirstClassStaysQuietUntilPreClassWindow() {
        val now = LocalDateTime.of(2026, 8, 3, 7, 0)
        val snapshot = ClassDayTimeline.evaluate(mondayClasses, now)

        assertTrue(snapshot.state is ClassDayTimeline.State.None)
        assertEquals(LocalDateTime.of(2026, 8, 3, 8, 30), snapshot.nextTransitionAt)
    }

    @Test
    fun preClassShowsNextMeeting() {
        val now = LocalDateTime.of(2026, 8, 3, 8, 45)
        val state = ClassDayTimeline.evaluate(mondayClasses, now).state

        assertTrue(state is ClassDayTimeline.State.PreClass)
        state as ClassDayTimeline.State.PreClass
        assertEquals("MATH 27", state.next.session.code)
        assertEquals(15, state.minutesUntilStart)
    }

    @Test
    fun ongoingUsesClassEndAsNextTransition() {
        val now = LocalDateTime.of(2026, 8, 3, 9, 20)
        val snapshot = ClassDayTimeline.evaluate(mondayClasses, now)

        assertTrue(snapshot.state is ClassDayTimeline.State.Ongoing)
        assertEquals(LocalDateTime.of(2026, 8, 3, 10, 0), snapshot.nextTransitionAt)
    }

    @Test
    fun gapBecomesBreakAndSuggestsFocus() {
        val now = LocalDateTime.of(2026, 8, 3, 10, 5)
        val snapshot = ClassDayTimeline.evaluate(mondayClasses, now)

        assertTrue(snapshot.state is ClassDayTimeline.State.Break)
        val state = snapshot.state as ClassDayTimeline.State.Break
        assertEquals("ENG 10", state.next.session.code)
        assertEquals(85, state.freeMinutes)
        assertEquals(2, state.focusSessions)
        assertEquals(LocalDateTime.of(2026, 8, 3, 11, 0), snapshot.nextTransitionAt)
    }

    @Test
    fun afterLastClassShowsTemporaryDoneState() {
        val now = LocalDateTime.of(2026, 8, 3, 13, 10)
        val snapshot = ClassDayTimeline.evaluate(mondayClasses, now)

        assertTrue(snapshot.state is ClassDayTimeline.State.Done)
        val state = snapshot.state as ClassDayTimeline.State.Done
        assertEquals(2, state.classesCompleted)
        assertEquals(LocalDateTime.of(2026, 8, 3, 13, 30), state.visibleUntil)
    }

    @Test
    fun doneCardDisappearsAndSchedulesNextWeek() {
        val now = LocalDateTime.of(2026, 8, 3, 14, 0)
        val snapshot = ClassDayTimeline.evaluate(mondayClasses, now)

        assertTrue(snapshot.state is ClassDayTimeline.State.None)
        assertEquals(LocalDateTime.of(2026, 8, 10, 8, 30), snapshot.nextTransitionAt)
    }

    @Test
    fun emptyScheduleHasNoTransition() {
        val snapshot = ClassDayTimeline.evaluate(emptyList(), LocalDateTime.of(2026, 8, 3, 9, 0))
        assertTrue(snapshot.state is ClassDayTimeline.State.None)
        assertNull(snapshot.nextTransitionAt)
    }

    private fun classAt(code: String, start: String, end: String, room: String) = ClassSession(
        id = "$code-$start",
        code = code,
        day = "Mon",
        type = "lec",
        start = start,
        end = end,
        room = room
    )
}
