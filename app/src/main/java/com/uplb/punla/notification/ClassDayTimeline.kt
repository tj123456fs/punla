package com.uplb.punla.notification

import com.uplb.punla.data.entity.ClassSession
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Pure schedule state machine for Punla's single evolving class-day notification.
 *
 * The renderer/worker deliberately depends on this model instead of scattering
 * time comparisons across notification code. Keeping this pure also makes the
 * boundary behavior (pre-class -> ongoing -> break -> done) easy to regression-test.
 */
object ClassDayTimeline {
    const val DEFAULT_PRE_CLASS_MINUTES = 30L
    const val DEFAULT_DONE_VISIBLE_MINUTES = 30L

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val dayNames = mapOf(
        DayOfWeek.MONDAY to "Mon",
        DayOfWeek.TUESDAY to "Tue",
        DayOfWeek.WEDNESDAY to "Wed",
        DayOfWeek.THURSDAY to "Thu",
        DayOfWeek.FRIDAY to "Fri",
        DayOfWeek.SATURDAY to "Sat",
        DayOfWeek.SUNDAY to "Sun"
    )

    data class Occurrence(
        val session: ClassSession,
        val start: LocalDateTime,
        val end: LocalDateTime
    )

    sealed interface State {
        data object None : State

        data class PreClass(
            val next: Occurrence,
            val minutesUntilStart: Long
        ) : State

        data class Ongoing(
            val current: Occurrence,
            val nextToday: Occurrence?
        ) : State

        data class Break(
            val previous: Occurrence,
            val next: Occurrence,
            val freeMinutes: Long,
            val focusSessions: Int
        ) : State

        data class Done(
            val classesCompleted: Int,
            val lastClass: Occurrence,
            val visibleUntil: LocalDateTime
        ) : State
    }

    data class Snapshot(
        val state: State,
        /** Next moment when the notification's semantic state can change. */
        val nextTransitionAt: LocalDateTime?
    )

    fun evaluate(
        classes: List<ClassSession>,
        now: LocalDateTime = LocalDateTime.now(),
        preClassMinutes: Long = DEFAULT_PRE_CLASS_MINUTES,
        doneVisibleMinutes: Long = DEFAULT_DONE_VISIBLE_MINUTES
    ): Snapshot {
        if (classes.isEmpty()) return Snapshot(State.None, null)

        val todayOccurrences = occurrencesOn(classes, now.toLocalDate())
        val ongoing = todayOccurrences
            .filter { !now.isBefore(it.start) && now.isBefore(it.end) }
            .minByOrNull { it.start }

        if (ongoing != null) {
            val nextToday = todayOccurrences.firstOrNull { it.start >= ongoing.end }
            return Snapshot(
                state = State.Ongoing(ongoing, nextToday),
                nextTransitionAt = ongoing.end
            )
        }

        val nextToday = todayOccurrences.firstOrNull { it.start.isAfter(now) }
        val previousToday = todayOccurrences.lastOrNull { !it.end.isAfter(now) }

        if (nextToday != null) {
            val preClassAt = nextToday.start.minusMinutes(preClassMinutes)
            if (!now.isBefore(preClassAt)) {
                return Snapshot(
                    state = State.PreClass(
                        next = nextToday,
                        minutesUntilStart = Duration.between(now, nextToday.start).toMinutes().coerceAtLeast(0)
                    ),
                    nextTransitionAt = nextToday.start
                )
            }

            if (previousToday != null) {
                val freeMinutes = Duration.between(now, nextToday.start).toMinutes().coerceAtLeast(0)
                // One classic 25-minute focus block plus a five-minute reset.
                val focusSessions = (freeMinutes / 30L).toInt().coerceAtLeast(0)
                return Snapshot(
                    state = State.Break(previousToday, nextToday, freeMinutes, focusSessions),
                    nextTransitionAt = preClassAt
                )
            }

            // Before the first class, Punla stays quiet until the useful
            // pre-class window rather than occupying the shade all morning.
            return Snapshot(State.None, preClassAt)
        }

        if (previousToday != null) {
            val visibleUntil = previousToday.end.plusMinutes(doneVisibleMinutes)
            if (now.isBefore(visibleUntil)) {
                return Snapshot(
                    state = State.Done(
                        classesCompleted = todayOccurrences.count { !it.end.isAfter(now) },
                        lastClass = previousToday,
                        visibleUntil = visibleUntil
                    ),
                    nextTransitionAt = visibleUntil
                )
            }
        }

        val nextOccurrence = nextOccurrenceAfter(classes, now)
        return Snapshot(
            state = State.None,
            nextTransitionAt = nextOccurrence?.start?.minusMinutes(preClassMinutes)
        )
    }

    fun occurrencesOn(classes: List<ClassSession>, date: LocalDate): List<Occurrence> {
        val day = dayNames[date.dayOfWeek] ?: return emptyList()
        return classes.asSequence()
            .filter { it.day.equals(day, ignoreCase = true) }
            .mapNotNull { session -> occurrence(session, date) }
            .sortedBy { it.start }
            .toList()
    }

    fun nextOccurrenceAfter(
        classes: List<ClassSession>,
        now: LocalDateTime = LocalDateTime.now()
    ): Occurrence? {
        // Include today and the next seven dates so a weekly class on the
        // same weekday can still be found after today's meeting has ended.
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            val candidate = occurrencesOn(classes, date).firstOrNull { it.start.isAfter(now) }
            if (candidate != null) return candidate
        }
        return null
    }

    private fun occurrence(session: ClassSession, date: LocalDate): Occurrence? {
        val startTime = parseTime(session.start) ?: return null
        val endTime = parseTime(session.end) ?: return null
        val start = date.atTime(startTime)
        var end = date.atTime(endTime)
        if (!end.isAfter(start)) end = end.plusDays(1)
        return Occurrence(session, start, end)
    }

    private fun parseTime(raw: String): LocalTime? =
        runCatching { LocalTime.parse(raw, timeFormat) }.getOrNull()
}
