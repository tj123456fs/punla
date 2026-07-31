package com.uplb.punla.assistant

import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.data.entity.StudySession
import com.uplb.punla.data.entity.allowedAbsences
import com.uplb.punla.ml.bestStudyHour
import com.uplb.punla.ui.pomodoro.StudySuggestion
import com.uplb.punla.ui.pomodoro.suggestStudySlotTodayOrTomorrow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

sealed interface AssistantAction {
    data class StartFocus(val minutes: Int, val course: String? = null) : AssistantAction
    data class AddExpense(val amount: Double, val category: String, val note: String?) : AssistantAction
}

data class LocalAssistantAnswer(
    val text: String,
    val handled: Boolean = true,
    val action: AssistantAction? = null,
    val suggestion: StudySuggestion? = null
)

data class AssistantSnapshot(
    val classes: List<ClassSession>,
    val deadlines: List<Deadline>,
    val expenses: List<Expense>,
    val sessions: List<StudySession>,
    val repo: PunlaRepository,
    val today: LocalDate = LocalDate.now()
)

private val dayNames = mapOf(
    DayOfWeek.MONDAY to "Mon", DayOfWeek.TUESDAY to "Tue", DayOfWeek.WEDNESDAY to "Wed",
    DayOfWeek.THURSDAY to "Thu", DayOfWeek.FRIDAY to "Fri", DayOfWeek.SATURDAY to "Sat", DayOfWeek.SUNDAY to "Sun"
)
private val readableDate = DateTimeFormatter.ofPattern("MMM d")

object LocalAssistant {
    fun answer(rawQuery: String, data: AssistantSnapshot): LocalAssistantAnswer {
        val q = rawQuery.lowercase().trim()
        if (q.isBlank()) return LocalAssistantAnswer("Ask about your schedule, deadlines, budget, attendance, or study habits.")

        parseFocus(q)?.let { return LocalAssistantAnswer("Ready to start a ${it.minutes}-minute focus session.", action = it) }
        parseExpense(q)?.let {
            return LocalAssistantAnswer(
                "I found a ₱${"%.2f".format(it.amount)} ${it.category} expense${it.note?.let { n -> " for $n" } ?: ""}.",
                action = it
            )
        }

        if (listOf("class", "schedule", "subject").any(q::contains)) {
            val date = if (q.contains("tomorrow")) data.today.plusDays(1) else data.today
            val day = dayNames[date.dayOfWeek]
            val classes = data.classes.filter { it.day == day }.sortedBy { it.start }
            return LocalAssistantAnswer(
                if (classes.isEmpty()) "You have no classes scheduled ${if (date == data.today) "today" else "tomorrow"}."
                else classes.joinToString(prefix = "${if (date == data.today) "Today" else "Tomorrow"}: ", separator = "; ") {
                    "${it.code} ${it.start}–${it.end}${it.room?.let { room -> " at $room" } ?: ""}"
                }
            )
        }

        if (listOf("deadline", "due", "requirement", "assignment").any(q::contains)) {
            val pending = data.deadlines.filter { !it.done }.sortedBy { it.due }
            val selected = when {
                q.contains("week") -> pending.filter { runCatching { LocalDate.parse(it.due) }.getOrNull() in data.today..data.today.plusDays(7) }
                else -> pending.take(5)
            }
            return LocalAssistantAnswer(
                if (selected.isEmpty()) "You have no pending deadlines in that period."
                else selected.joinToString(prefix = "Upcoming: ", separator = "; ") { d ->
                    "${d.title}${d.course?.let { " ($it)" } ?: ""} — ${runCatching { LocalDate.parse(d.due).format(readableDate) }.getOrDefault(d.due)}"
                }
            )
        }

        if (listOf("spend", "spent", "expense", "budget", "money").any(q::contains)) {
            val range = when {
                q.contains("week") -> {
                    val start = data.today.with(TemporalAdjusters.previousOrSame(data.repo.weekStartDay))
                    start to start.plusDays(6)
                }
                else -> YearMonth.from(data.today).atDay(1) to YearMonth.from(data.today).atEndOfMonth()
            }
            val category = listOf("food", "transport", "school", "shopping", "health", "bills", "other")
                .firstOrNull { q.contains(it) }
            val matching = data.expenses.filter {
                val date = runCatching { LocalDate.parse(it.date) }.getOrNull()
                date != null && !date.isBefore(range.first) && !date.isAfter(range.second) &&
                    (category == null || it.category.lowercase().contains(category))
            }
            val total = matching.sumOf { it.amount }
            return LocalAssistantAnswer(
                "You spent ₱${"%.2f".format(total)}${category?.let { " on $it" } ?: ""} " +
                    "from ${range.first.format(readableDate)} to ${range.second.format(readableDate)} across ${matching.size} entr${if (matching.size == 1) "y" else "ies"}."
            )
        }

        if (listOf("absence", "attendance", "absent").any(q::contains)) {
            val matched = data.classes.filter { c -> q.contains(c.code.lowercase()) }
                .ifEmpty { data.classes.filter { it.absences > 0 || q.contains("all") }.ifEmpty { data.classes } }
            return LocalAssistantAnswer(
                if (matched.isEmpty()) "There are no classes to check yet."
                else matched.distinctBy { it.id }.joinToString(separator = "; ") {
                    val left = (it.allowedAbsences() - it.absences).coerceAtLeast(0)
                    "${it.code}: ${it.absences}/${it.allowedAbsences()} absences, $left remaining before the estimated limit"
                }
            )
        }

        if (listOf("study", "focus", "pomodoro", "free time", "when should").any(q::contains)) {
            val suggestion = suggestStudySlotTodayOrTomorrow(
                data.classes, data.deadlines, data.repo.pomodoroWorkMinutes, data.today, data.sessions
            )
            val bestHour = bestStudyHour(data.sessions)
            return when {
                suggestion != null -> LocalAssistantAnswer(
                    "A good open slot is ${suggestion.dayLabel} at ${suggestion.slotStart}–${suggestion.slotEnd} for ${suggestion.deadline.title}.",
                    suggestion = suggestion,
                    action = AssistantAction.StartFocus(data.repo.pomodoroWorkMinutes, suggestion.course)
                )
                bestHour != null -> LocalAssistantAnswer("Your strongest logged study hour is around ${formatHour(bestHour)}.")
                else -> LocalAssistantAnswer("I need more study history or an upcoming deadline before I can personalize a study time.")
            }
        }

        return LocalAssistantAnswer("That needs the optional cloud assistant, or a more specific local command.", handled = false)
    }

    fun compactCloudContext(data: AssistantSnapshot, rawQuery: String = ""): String {
        val q = rawQuery.lowercase()
        val includePlanning = q.isBlank() || listOf("plan", "week", "overwhelmed", "behind", "prioritize").any(q::contains)
        val includeSchedule = includePlanning || listOf("class", "schedule", "subject", "room", "today", "tomorrow").any(q::contains)
        val includeDeadlines = includePlanning || listOf("deadline", "due", "assignment", "requirement", "task").any(q::contains)
        val includeSpending = listOf("spend", "spent", "expense", "budget", "money", "cost").any(q::contains)
        val includeAttendance = listOf("absence", "attendance", "absent", "cut").any(q::contains)
        val includeStudy = includePlanning || listOf("study", "focus", "pomodoro", "habit", "streak").any(q::contains)

        val sections = mutableListOf("Today: ${data.today}")
        if (includeSchedule) {
            val schedule = data.classes.sortedWith(compareBy<ClassSession> { it.day }.thenBy { it.start })
                .joinToString(" | ") { "${it.day} ${it.start}-${it.end} ${it.code}${it.room?.let { r -> " @$r" } ?: ""}" }
            sections += "Schedule: ${schedule.ifBlank { "No classes saved" }}"
        }
        if (includeDeadlines) {
            val deadlines = data.deadlines.filter { !it.done }.sortedBy { it.due }.take(12)
                .joinToString(" | ") { "${it.due}: ${it.title}${it.course?.let { c -> " [$c]" } ?: ""}" }
            sections += "Pending deadlines: ${deadlines.ifBlank { "None" }}"
        }
        if (includeSpending) {
            val month = YearMonth.from(data.today)
            val monthExpenses = data.expenses.filter {
                runCatching { YearMonth.from(LocalDate.parse(it.date)) == month }.getOrDefault(false)
            }
            val monthSpend = monthExpenses.sumOf { it.amount }
            val categoryTotals = monthExpenses.groupBy { it.category }.mapValues { (_, values) -> values.sumOf { it.amount } }
            sections += "This-month spending total: ${"%.2f".format(monthSpend)} PHP"
            sections += "This-month category totals: $categoryTotals"
        }
        if (includeAttendance) {
            val attendance = data.classes.joinToString(" | ") { "${it.code}: ${it.absences}/${it.allowedAbsences()}" }
            sections += "Attendance counts: ${attendance.ifBlank { "No classes saved" }}"
        }
        if (includeStudy) {
            val completed = data.sessions.count { it.completed }
            sections += "Study sessions: ${data.sessions.size} started, $completed completed"
            bestStudyHour(data.sessions)?.let { sections += "Strongest logged study hour: ${formatHour(it)}" }
        }
        // An unfamiliar query gets only high-level planner facts, never raw
        // expense rows or conversation history. This keeps cloud requests
        // compact even after years of personal data accumulate.
        if (sections.size == 1) {
            sections += "Planner summary: ${data.classes.size} classes, ${data.deadlines.count { !it.done }} pending deadlines, ${data.sessions.size} study sessions"
        }
        return sections.joinToString("\n")
    }

    private fun parseFocus(q: String): AssistantAction.StartFocus? {
        if (!(q.contains("start") && (q.contains("focus") || q.contains("pomodoro")))) return null
        val minutes = Regex("(\\d{1,3})\\s*(?:minute|min)").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 25
        return AssistantAction.StartFocus(minutes.coerceIn(5, 180))
    }

    private fun parseExpense(q: String): AssistantAction.AddExpense? {
        if (!(q.contains("add") && (q.contains("expense") || q.contains("spent") || q.contains("₱") || q.contains("php")))) return null
        val amount = Regex("(?:₱|php\\s*)?(\\d+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)
            .find(q)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val category = when {
            q.contains("food") || q.contains("meal") -> "Food"
            q.contains("transport") || q.contains("fare") -> "Transportation"
            q.contains("school") || q.contains("academic") -> "School"
            q.contains("bill") || q.contains("subscription") -> "Bills"
            q.contains("health") || q.contains("medicine") -> "Health"
            q.contains("shopping") -> "Shopping"
            else -> "Other"
        }
        val note = q.substringAfter(" for ", "").takeIf { it.isNotBlank() }
        return AssistantAction.AddExpense(amount, category, note)
    }

    private fun formatHour(hour: Int): String = when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}
