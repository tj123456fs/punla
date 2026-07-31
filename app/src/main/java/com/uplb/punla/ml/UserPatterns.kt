package com.uplb.punla.ml

import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.data.entity.ExpenseRule
import com.uplb.punla.data.entity.NotificationEvent
import com.uplb.punla.data.entity.StudySession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Completion rate by local hour. Hours without attempts are omitted. */
fun studyTimeHistogram(sessions: List<StudySession>): Map<Int, Float> = sessions
    .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).hour }
    .mapValues { (_, attempts) ->
        if (attempts.isEmpty()) 0f else attempts.count { it.completed }.toFloat() / attempts.size
    }

fun sessionEarlyStopRate(sessions: List<StudySession>): Float =
    if (sessions.isEmpty()) 0f else sessions.count { it.endReason == "STOPPED_EARLY" || !it.completed }.toFloat() / sessions.size

fun normalizeMerchant(raw: String): String = raw
    .lowercase()
    .trim()
    .replace(Regex("[^a-z0-9 ]"), "")
    .replace(Regex("\\s+"), " ")

private fun Expense.patternLabel(): String =
    normalizeMerchant(note.orEmpty()).ifBlank { category.trim().lowercase() }

data class ExpensePattern(
    val key: String,
    val label: String,
    val category: String,
    val typicalAmount: Double,
    val occurrences: Int,
    val cadenceDays: Int?,
    val expenseIds: List<String>
)

/**
 * Conservative recurring detection: at least three separate dates, a stable
 * label/category, and amounts within max(₱5, 5%) of the group's median.
 */
fun recurringExpenseCandidates(
    expenses: List<Expense>,
    existingRules: List<ExpenseRule> = emptyList(),
    dismissedKeys: Set<String> = emptySet()
): List<ExpensePattern> {
    val existing = existingRules.map { normalizeMerchant(it.note.orEmpty()).ifBlank { it.category.lowercase() } }.toSet()
    return expenses
        .filter { !it.isRecurring && it.ruleId == null && !it.note.isNullOrBlank() }
        .groupBy { "${it.category.lowercase()}|${it.patternLabel()}" }
        .mapNotNull { (key, group) ->
            if (key in dismissedKeys || group.size < 3) return@mapNotNull null
            val label = group.first().patternLabel()
            if (label in existing) return@mapNotNull null
            val dated = group.mapNotNull { e -> runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { it to e } }
                .distinctBy { it.first }
                .sortedBy { it.first }
            if (dated.size < 3) return@mapNotNull null
            val amounts = dated.map { it.second.amount }.sorted()
            val median = amounts[amounts.size / 2]
            val tolerance = max(5.0, median * 0.05)
            val stable = dated.filter { abs(it.second.amount - median) <= tolerance }
            if (stable.size < 3) return@mapNotNull null
            if (ChronoUnit.DAYS.between(stable.first().first, stable.last().first) < 14) return@mapNotNull null
            val gaps = stable.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.first, b.first).toInt() }
            val sortedGaps = gaps.sorted()
            val cadence = sortedGaps.takeIf { it.isNotEmpty() }?.get(sortedGaps.size / 2)
            if (cadence != null) {
                val gapTolerance = max(4.0, cadence * 0.35)
                if (gaps.any { abs(it - cadence) > gapTolerance }) return@mapNotNull null
            }
            ExpensePattern(
                key = key,
                label = group.first().note?.trim().takeUnless { it.isNullOrBlank() } ?: group.first().category,
                category = group.first().category,
                typicalAmount = median,
                occurrences = stable.size,
                cadenceDays = cadence,
                expenseIds = stable.map { it.second.id }
            )
        }
        .sortedByDescending { it.occurrences }
}

/** Absences accumulated per elapsed teaching week. */
fun absenceVelocity(session: ClassSession, termStart: LocalDate, today: LocalDate = LocalDate.now()): Float {
    val elapsedWeeks = max(1L, ChronoUnit.WEEKS.between(termStart, today) + 1)
    return session.absences.toFloat() / elapsedWeeks
}

data class AttendanceProjection(
    val velocityPerWeek: Float,
    val projectedAbsences: Float,
    val allowedAbsences: Int,
    val risk: String,
    val explanation: String
)

fun projectAttendanceRisk(
    session: ClassSession,
    allowedAbsences: Int,
    termStart: LocalDate,
    termEnd: LocalDate,
    today: LocalDate = LocalDate.now()
): AttendanceProjection {
    val totalWeeks = max(1L, ChronoUnit.WEEKS.between(termStart, termEnd) + 1)
    val elapsedWeeks = max(1L, ChronoUnit.WEEKS.between(termStart, today.coerceAtMost(termEnd)) + 1)
    // A modest 5% prior over four virtual weeks keeps a fresh term from
    // showing a warning with zero absences, while still reacting to a real
    // early pattern. Projection includes absences already accumulated.
    val priorWeeks = 4f
    val priorAbsenceRate = 0.05f
    val smoothedRate = (session.absences + priorAbsenceRate * priorWeeks) / (elapsedWeeks + priorWeeks)
    val remainingWeeks = (totalWeeks - elapsedWeeks).coerceAtLeast(0L)
    val projected = session.absences + smoothedRate * remainingWeeks
    val risk = when {
        session.absences >= allowedAbsences -> "OVER_LIMIT"
        projected >= allowedAbsences && (elapsedWeeks >= 3 || session.absences >= 2) -> "HIGH"
        session.absences > 0 && projected >= allowedAbsences * 0.75f -> "WATCH"
        else -> "LOW"
    }
    return AttendanceProjection(
        velocityPerWeek = absenceVelocity(session, termStart, today),
        projectedAbsences = projected,
        allowedAbsences = allowedAbsences,
        risk = risk,
        explanation = "${session.absences} absence${if (session.absences == 1) "" else "s"} so far; about ${projected.roundToInt()} projected by term end."
    )
}

/** Open rate by local notification hour. Only FIRED rows form the denominator. */
fun notificationEngagement(events: List<NotificationEvent>): Map<Int, Float> {
    val firedByKey = events.filter { it.outcome == "FIRED" }.associateBy { it.notificationKey }
    val openedKeys = events.filter { it.outcome == "OPENED" || it.outcome == "ACTION_USED" }.map { it.notificationKey }.toSet()
    return firedByKey.values.groupBy { it.localHour }.mapValues { (_, fired) ->
        if (fired.isEmpty()) 0f else fired.count { it.notificationKey in openedKeys }.toFloat() / fired.size
    }
}

fun bestStudyHour(sessions: List<StudySession>, minimumAttempts: Int = 3): Int? = sessions
    .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).hour }
    .filterValues { it.size >= minimumAttempts }
    .maxByOrNull { (_, attempts) ->
        attempts.count { it.completed }.toDouble() / attempts.size + attempts.sumOf { it.actualSeconds } / 100000.0
    }?.key
