package com.uplb.punla.ui.pomodoro

import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.ui.screens.freeSlotsFor
import com.uplb.punla.ui.screens.minutesBetween
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Free-Time Study Suggestions (Phase 1 of STUDY_SUGGESTIONS_AND_STREAKS.md)
 * — connects three things Punla already has without any new data model:
 * ScheduleScreen's free-slot detection, DeadlineDao's upcoming deadlines,
 * and the Pomodoro timer's course-tagged start. A nudge, not automation —
 * nothing gets scheduled without the person tapping it.
 */
data class StudySuggestion(
    val day: String,       // "Mon".."Sat" — matches ClassSession.day
    val dayLabel: String,  // "today" | "tomorrow", for display copy only
    val slotStart: String, // "HH:mm"
    val slotEnd: String,   // "HH:mm"
    val deadline: Deadline,
    val course: String?
)

private val DAY_ABBREV: Map<DayOfWeek, String> = mapOf(
    DayOfWeek.MONDAY to "Mon", DayOfWeek.TUESDAY to "Tue", DayOfWeek.WEDNESDAY to "Wed",
    DayOfWeek.THURSDAY to "Thu", DayOfWeek.FRIDAY to "Fri", DayOfWeek.SATURDAY to "Sat",
    DayOfWeek.SUNDAY to "Sun"
)

private fun daysUntil(d: Deadline, now: LocalDate): Long =
    runCatching { ChronoUnit.DAYS.between(now, LocalDate.parse(d.due)) }.getOrDefault(Long.MAX_VALUE)

/**
 * Pure matching function for a single [day] — reuses [freeSlotsFor] and
 * [minutesBetween] as-is rather than duplicating the gap math. Returns null
 * on a fully-booked day or when nothing is due soon (0-3 days out, the same
 * "soon" window the Dashboard already uses for its red deadline accent).
 */
fun suggestStudySlot(
    day: String,
    dayLabel: String,
    classes: List<ClassSession>,
    deadlines: List<Deadline>,
    pomodoroWorkMinutes: Int,
    now: LocalDate
): StudySuggestion? {
    val slots = freeSlotsFor(day, classes)
    val upcoming = deadlines
        .filter { !it.done }
        .filter { d -> daysUntil(d, now) in 0..3 }
        .sortedBy { daysUntil(it, now) }

    val slot = slots.firstOrNull { (s, e) -> minutesBetween(s, e) >= pomodoroWorkMinutes }
    val deadline = upcoming.firstOrNull()
    return if (slot != null && deadline != null) {
        StudySuggestion(day, dayLabel, slot.first, slot.second, deadline, deadline.course)
    } else null
}

/**
 * Tries today first, then tomorrow — a fully-booked today shouldn't hide a
 * perfectly good gap tomorrow. Used by the Dashboard's focus-session card.
 */
fun suggestStudySlotTodayOrTomorrow(
    classes: List<ClassSession>,
    deadlines: List<Deadline>,
    pomodoroWorkMinutes: Int,
    now: LocalDate = LocalDate.now()
): StudySuggestion? {
    val today = DAY_ABBREV[now.dayOfWeek] ?: return null
    suggestStudySlot(today, "today", classes, deadlines, pomodoroWorkMinutes, now)?.let { return it }
    val tomorrow = DAY_ABBREV[now.plusDays(1).dayOfWeek] ?: return null
    return suggestStudySlot(tomorrow, "tomorrow", classes, deadlines, pomodoroWorkMinutes, now)
}
