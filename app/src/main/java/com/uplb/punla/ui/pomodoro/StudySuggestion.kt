package com.uplb.punla.ui.pomodoro

import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.data.entity.StudySession
import com.uplb.punla.ml.StudySlotFeatures
import com.uplb.punla.ml.StudySlotModelState
import com.uplb.punla.ml.StudySlotPredictor
import com.uplb.punla.ml.studyTimeHistogram
import com.uplb.punla.ui.screens.freeSlotsFor
import com.uplb.punla.ui.screens.minutesBetween
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** A transparent, locally-ranked free-time study recommendation. */
data class StudySuggestion(
    val id: String,
    val date: LocalDate,
    val day: String,
    val dayLabel: String,
    val slotStart: String,
    val slotEnd: String,
    val deadline: Deadline,
    val course: String?,
    val urgencyDays: Int,
    val availableMinutes: Int,
    val matchLabel: String
)

private val DAY_ABBREV: Map<DayOfWeek, String> = mapOf(
    DayOfWeek.MONDAY to "Mon", DayOfWeek.TUESDAY to "Tue", DayOfWeek.WEDNESDAY to "Wed",
    DayOfWeek.THURSDAY to "Thu", DayOfWeek.FRIDAY to "Fri", DayOfWeek.SATURDAY to "Sat",
    DayOfWeek.SUNDAY to "Sun"
)

private fun daysUntil(d: Deadline, now: LocalDate): Long =
    runCatching { ChronoUnit.DAYS.between(now, LocalDate.parse(d.due)) }.getOrDefault(Long.MAX_VALUE)

private data class RankedSlot(val start: String, val end: String, val minutes: Int, val score: Double)

fun suggestStudySlot(
    day: String,
    dayLabel: String,
    classes: List<ClassSession>,
    deadlines: List<Deadline>,
    pomodoroWorkMinutes: Int,
    now: LocalDate,
    sessions: List<StudySession> = emptyList(),
    modelState: StudySlotModelState = StudySlotModelState(),
    currentStreak: Int = 0,
    date: LocalDate = now,
    nowTime: LocalTime = LocalTime.now()
): StudySuggestion? {
    val upcoming = deadlines
        .filter { !it.done }
        .filter { d -> daysUntil(d, now) in 0..3 }
        .sortedBy { daysUntil(it, now) }
    val deadline = upcoming.firstOrNull() ?: return null
    val urgency = daysUntil(deadline, now).toInt().coerceAtLeast(0)
    val histogram = if (sessions.size >= 10) studyTimeHistogram(sessions) else emptyMap()
    val recentRate = if (sessions.isEmpty()) 0.5f else sessions.take(20).count { it.completed }.toFloat() / sessions.take(20).size

    val ranked = freeSlotsFor(day, classes).mapNotNull { (start, end) ->
        val slotStart = runCatching { LocalTime.parse(start) }.getOrNull() ?: return@mapNotNull null
        val slotEnd = runCatching { LocalTime.parse(end) }.getOrNull() ?: return@mapNotNull null
        val effectiveStart = if (date == now && !slotStart.isAfter(nowTime)) {
            // Never recommend a time that already passed. Round an in-progress
            // free block up to the next quarter-hour so the suggestion remains
            // practical and matches Punla's schedule time increments.
            val roundedMinute = ((nowTime.minute + 14) / 15) * 15
            if (roundedMinute >= 60) nowTime.withMinute(0).withSecond(0).withNano(0).plusHours(1)
            else nowTime.withMinute(roundedMinute).withSecond(0).withNano(0)
        } else slotStart
        if (!effectiveStart.isBefore(slotEnd)) return@mapNotNull null
        val effectiveStartText = effectiveStart.toString().take(5)
        val minutes = minutesBetween(effectiveStartText, end)
        if (minutes < pomodoroWorkMinutes) return@mapNotNull null
        val hour = effectiveStart.hour
        val historyFit = histogram[hour]?.toDouble() ?: 0.5
        val urgencyFit = 1.0 / (1.0 + urgency)
        val durationFit = (minutes.toDouble() / pomodoroWorkMinutes.coerceAtLeast(1)).coerceIn(1.0, 3.0) / 3.0
        val heuristic = 0.45 * historyFit + 0.35 * urgencyFit + 0.20 * durationFit
        val features = StudySlotFeatures(
            hour = hour,
            dayOfWeek = date.dayOfWeek.value,
            urgencyDays = urgency,
            availableMinutes = minutes,
            plannedMinutes = pomodoroWorkMinutes,
            recentCompletionRate = recentRate,
            currentStreak = currentStreak
        )
        val score = if (modelState.sampleCount >= StudySlotPredictor.MIN_SAMPLES_FOR_PREDICTION) {
            0.55 * heuristic + 0.45 * StudySlotPredictor.probability(modelState, features)
        } else heuristic
        RankedSlot(effectiveStartText, end, minutes, score)
    }.maxByOrNull { it.score } ?: return null

    val match = when {
        modelState.sampleCount >= StudySlotPredictor.MIN_SAMPLES_FOR_PREDICTION && ranked.score >= 0.72 -> "Strong match"
        ranked.score >= 0.58 -> "Good match"
        else -> "Available slot"
    }
    return StudySuggestion(
        id = "${date}_${ranked.start}_${deadline.id}",
        date = date,
        day = day,
        dayLabel = dayLabel,
        slotStart = ranked.start,
        slotEnd = ranked.end,
        deadline = deadline,
        course = deadline.course,
        urgencyDays = urgency,
        availableMinutes = ranked.minutes,
        matchLabel = match
    )
}

fun suggestStudySlotTodayOrTomorrow(
    classes: List<ClassSession>,
    deadlines: List<Deadline>,
    pomodoroWorkMinutes: Int,
    now: LocalDate = LocalDate.now(),
    sessions: List<StudySession> = emptyList(),
    modelState: StudySlotModelState = StudySlotModelState(),
    currentStreak: Int = 0
): StudySuggestion? {
    val today = DAY_ABBREV[now.dayOfWeek] ?: return null
    suggestStudySlot(
        today, "today", classes, deadlines, pomodoroWorkMinutes, now,
        sessions, modelState, currentStreak, now
    )?.let { return it }
    val tomorrowDate = now.plusDays(1)
    val tomorrow = DAY_ABBREV[tomorrowDate.dayOfWeek] ?: return null
    return suggestStudySlot(
        tomorrow, "tomorrow", classes, deadlines, pomodoroWorkMinutes, now,
        sessions, modelState, currentStreak, tomorrowDate
    )
}
