package com.uplb.punla.ui.screens

import com.uplb.punla.data.entity.ClassSession
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/** Visual meaning attached to the class Punla scrolls into view on Schedule open. */
internal enum class ScheduleFocusKind {
    CURRENT,
    NEXT,
    LAST
}

/** The day and class that should be shown when the Schedule list is opened. */
internal data class ScheduleFocus(
    val day: String?,
    val sessionId: String?,
    val kind: ScheduleFocusKind?
)

internal fun scheduleDayCode(dayOfWeek: DayOfWeek): String? = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> null // The current schedule model intentionally supports Mon-Sat.
}

/**
 * Selects the most useful class for the current moment:
 * 1. the class happening now;
 * 2. otherwise the next class today;
 * 3. otherwise the final class today, so the list opens at the completed end of the day.
 */
internal fun findScheduleFocus(
    classes: List<ClassSession>,
    now: LocalDateTime
): ScheduleFocus {
    val day = scheduleDayCode(now.dayOfWeek) ?: return ScheduleFocus(null, null, null)
    val todayClasses = classes
        .filter { it.day == day }
        .sortedBy { it.start }

    if (todayClasses.isEmpty()) return ScheduleFocus(day, null, null)

    val currentTime = now.toLocalTime()
    val current = todayClasses.firstOrNull { session ->
        val start = session.start.toLocalTimeOrNull() ?: return@firstOrNull false
        val end = session.end.toLocalTimeOrNull() ?: return@firstOrNull false
        !currentTime.isBefore(start) && currentTime.isBefore(end)
    }
    if (current != null) return ScheduleFocus(day, current.id, ScheduleFocusKind.CURRENT)

    val next = todayClasses.firstOrNull { session ->
        val start = session.start.toLocalTimeOrNull() ?: return@firstOrNull false
        currentTime.isBefore(start)
    }
    if (next != null) return ScheduleFocus(day, next.id, ScheduleFocusKind.NEXT)

    return ScheduleFocus(day, todayClasses.last().id, ScheduleFocusKind.LAST)
}

private fun String.toLocalTimeOrNull(): LocalTime? = runCatching {
    val pieces = split(":")
    LocalTime.of(
        pieces.getOrNull(0)?.toInt() ?: return null,
        pieces.getOrNull(1)?.toInt() ?: return null
    )
}.getOrNull()
