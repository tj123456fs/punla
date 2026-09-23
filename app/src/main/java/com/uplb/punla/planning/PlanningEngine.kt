package com.uplb.punla.planning

import com.uplb.punla.context.EnergyLevel
import java.time.*
import java.util.UUID
import kotlin.math.ceil

/** Pure decision layer. Times are instants; the caller owns the local calendar. */
data class WorkItem(
    val id: String, val title: String, val course: String? = null,
    val dueAt: Long? = null, val estimateMinutes: Int? = null,
    val progress: Int = 0, val importance: Int = 3, val effort: Int = 2,
    val lastWorkedAt: Long? = null, val weaknessCount: Int = 0,
    val pinned: Boolean = false, val dismissedUntil: Long = 0
) {
    val remainingMinutes: Int get() = ceil((estimateMinutes ?: 25).coerceIn(5, 10080) *
        (100 - progress.coerceIn(0, 100)) / 100.0).toInt()
}
data class RankedWork(val task: WorkItem, val reasons: List<String>, internal val score: Int)
data class TimeWindow(val start: Long, val end: Long) {
    val minutes: Int get() = ((end - start).coerceAtLeast(0) / 60000L).toInt()
    fun overlaps(other: TimeWindow) = start < other.end && other.start < end
}
data class PlannedWork(val taskId: String, val title: String, val window: TimeWindow)
data class PlanResult(val blocks: List<PlannedWork>, val unallocated: Map<String, Int>)
data class Workload(val task: WorkItem, val availableMinutes: Int, val cumulativeRequired: Int) {
    val overloaded: Boolean get() = cumulativeRequired > availableMinutes
}

object PriorityEngine {
    fun rank(tasks: List<WorkItem>, now: Long, availableMinutes: Int?, energy: EnergyLevel): List<RankedWork> =
        tasks.filter { it.progress < 100 && it.dismissedUntil <= now }.map { task ->
            val reasons = mutableListOf<String>()
            val hours = task.dueAt?.let { (it - now) / 3600000.0 }
            var score = when {
                hours == null -> 0
                hours < 0 -> 120.also { reasons += "Overdue" }
                hours <= 24 -> 90.also { reasons += "Due within 24 hours" }
                hours <= 72 -> 60.also { reasons += "Due within 3 days" }
                hours <= 168 -> 30.also { reasons += "Due this week" }
                else -> 5
            }
            score += task.importance.coerceIn(1, 5) * 5
            if (availableMinutes != null) {
                if (task.remainingMinutes <= availableMinutes) {
                    score += 25
                    reasons += "Fits your $availableMinutes-minute opening"
                } else {
                    score -= 15
                    reasons += "Start with a shorter work block"
                }
            }
            if (task.estimateMinutes == null) reasons += "25-minute starting estimate; adjust in Plan"
            if (task.weaknessCount > 0) {
                score += task.weaknessCount.coerceAtMost(5) * 4
                reasons += "${task.weaknessCount} weak study items in this course"
            }
            task.lastWorkedAt?.let {
                val days = ((now - it).coerceAtLeast(0) / 86400000).toInt()
                if (days >= 3) { score += days.coerceAtMost(7) * 2; reasons += "No work on this course for $days days" }
            }
            val matched = when (energy) {
                EnergyLevel.DRAINED -> task.effort == 1
                EnergyLevel.OKAY -> task.effort == 2
                EnergyLevel.LOCKED_IN -> task.effort == 3
                EnergyLevel.UNKNOWN -> false
            }
            if (matched) { score += 20; reasons += "Matches your energy check-in" }
            if (task.pinned) reasons.add(0, "Your manual first choice")
            if (reasons.isEmpty()) reasons += "Pending work; you can choose another task"
            RankedWork(task, reasons, score)
        }.sortedWith(compareByDescending<RankedWork> { it.task.pinned }
            .thenByDescending { it.score }.thenBy { it.task.dueAt ?: Long.MAX_VALUE }.thenBy { it.task.id })
}

object DayPlanner {
    /** Subtract a union of commitments, including travel, locks and personal routines. */
    fun freeWindows(bounds: TimeWindow, busy: List<TimeWindow>): List<TimeWindow> {
        if (bounds.end <= bounds.start) return emptyList()
        val result = mutableListOf<TimeWindow>()
        var cursor = bounds.start
        busy.filter { it.end > it.start && it.overlaps(bounds) }.sortedBy { it.start }.forEach {
            val start = it.start.coerceAtLeast(bounds.start)
            val end = it.end.coerceAtMost(bounds.end)
            if (start > cursor) result += TimeWindow(cursor, start)
            cursor = maxOf(cursor, end)
        }
        if (cursor < bounds.end) result += TimeWindow(cursor, bounds.end)
        return result
    }

    fun generate(tasks: List<WorkItem>, windows: List<TimeWindow>, energy: EnergyLevel,
                 reservedMinutes: Map<String, Int> = emptyMap(), maxBlockMinutes: Int = 50): PlanResult {
        val remaining = tasks.associate { it.id to
            (it.remainingMinutes - (reservedMinutes[it.id] ?: 0)).coerceAtLeast(0) }.toMutableMap()
        val blocks = mutableListOf<PlannedWork>()
        for (window in windows.sortedBy { it.start }) {
            var cursor = window.start
            while ((window.end - cursor) / 60000 >= 5) {
                val candidates = tasks.filter { (remaining[it.id] ?: 0) > 0 &&
                    (it.dueAt == null || it.dueAt - cursor >= 5 * 60000L) }
                val task = PriorityEngine.rank(candidates, cursor, ((window.end - cursor) / 60000).toInt(), energy)
                    .firstOrNull()?.task ?: break
                val limit = minOf(window.end, task.dueAt ?: window.end)
                val minutes = minOf(maxOf(5, remaining.getValue(task.id)), ((limit - cursor) / 60000).toInt(),
                    if (energy == EnergyLevel.DRAINED) minOf(maxBlockMinutes, 25) else maxBlockMinutes)
                if (minutes < 5) { remaining[task.id] = 0; continue }
                val end = cursor + minutes * 60000L
                blocks += PlannedWork(task.id, task.title, TimeWindow(cursor, end))
                remaining[task.id] = (remaining.getValue(task.id) - minutes).coerceAtLeast(0)
                cursor = end + 5 * 60000L
            }
        }
        return PlanResult(blocks, remaining.filterValues { it > 0 })
    }

    fun canPlace(window: TimeWindow, busy: List<TimeWindow>, now: Long): Boolean =
        window.start >= now && window.minutes in 5..240 && busy.none { it.overlaps(window) }

    fun workload(tasks: List<WorkItem>, available: List<TimeWindow>, now: Long): List<Workload> {
        var required = 0
        return tasks.filter { it.progress < 100 && it.dueAt != null }.sortedBy { it.dueAt }.map {
            required += it.remainingMinutes
            val capacity = available.sumOf { w ->
                TimeWindow(maxOf(w.start, now), minOf(w.end, it.dueAt!!)).minutes
            }
            Workload(it, capacity, required)
        }
    }
}

data class CaptureDraft(val title: String, val course: String?, val date: String?,
                        val time: String?, val warning: String?)
object CaptureParser {
    fun parse(text: String, today: LocalDate, courses: List<String>): CaptureDraft {
        val clean = text.trim().take(20000)
        val lower = clean.lowercase()
        var warning: String? = null
        val explicit = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b").find(clean)?.value
        val date = if (explicit != null) runCatching { LocalDate.parse(explicit) }.getOrElse {
            warning = "Check the date; it could not be read."; null
        } else when {
            Regex("\\btomorrow\\b").containsMatchIn(lower) -> today.plusDays(1)
            Regex("\\btoday\\b").containsMatchIn(lower) -> today
            else -> DayOfWeek.values().firstOrNull {
                Regex("\\b${it.name.lowercase()}\\b").containsMatchIn(lower)
            }?.let { day ->
                warning = "Confirm which ${day.name.lowercase()} you mean."
                today.plusDays(((day.value - today.dayOfWeek.value + 7) % 7).toLong())
            }
        }
        val clock = Regex("(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b").find(clean)
        val clock24 = Regex("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").find(clean)
        val time = if (clock != null) {
            val h = clock.groupValues[1].toInt(); val m = clock.groupValues[2].ifEmpty { "0" }.toInt()
            if (h !in 1..12 || m !in 0..59) { warning = "Check the time."; null }
            else LocalTime.of(h % 12 + if (clock.groupValues[3].equals("pm", true)) 12 else 0, m).toString()
        } else clock24?.let { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()).toString() }
        val course = courses.sortedByDescending { it.length }.firstOrNull {
            Regex("(?i)(?<![a-z0-9])${Regex.escape(it)}(?![a-z0-9])").containsMatchIn(clean)
        }
        if (date == null && warning == null) warning = "Choose a due date if this is a deadline."
        return CaptureDraft(clean.lineSequence().firstOrNull().orEmpty().take(200), course, date?.toString(), time, warning)
    }
}

data class StudyStep(val label: String, val minutes: Int, val destination: String)
object StudyIntelligence {
    fun session(minutes: Int, weakCards: Int, mistakes: Int): List<StudyStep> {
        val total = minutes.coerceIn(5, 120)
        val recall = maxOf(1, total / 8)
        val cards = if (weakCards > 0) total / 4 else 0
        val retry = if (mistakes > 0) total * 3 / 8 else 0
        return listOf(StudyStep("Review concept notes", total - recall - cards - retry, "study?section=Notes"),
            StudyStep("Review weak flashcards", cards, "flashcards"),
            StudyStep("Retry missed questions", retry, "study?section=Mistakes"),
            StudyStep("Close notes and recall the main ideas", recall, "pomodoro")).filter { it.minutes > 0 }
    }
}

data class AutomationEvent(val key: String, val kind: String, val subjectId: String)
data class AutomationSuggestion(val id: String, val category: String, val title: String, val reason: String,
                                val destination: String, val subjectId: String? = null)
object AutomationEngine {
    fun actions(event: AutomationEvent): List<String> = when (event.kind) {
        "CLASS_ENDED" -> listOf("SUGGEST_ATTENDANCE", "REFRESH_CONTEXT")
        "QUIZ_ADDED" -> listOf("SUGGEST_REVIEW", "REFRESH_CONTEXT")
        "TASK_COMPLETED" -> listOf("RECALCULATE_PRIORITIES", "REFRESH_TODAY")
        else -> emptyList()
    }
}
