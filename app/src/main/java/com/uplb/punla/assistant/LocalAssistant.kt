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

// Intent routing is English-only by design, but keywords still need token
// boundaries so words such as "classical" do not accidentally mean "class".
internal fun containsAssistantKeyword(text: String, vararg keywords: String): Boolean =
    keywords.any { keyword ->
        Regex("""(?<![A-Za-z0-9_])${Regex.escape(keyword)}(?![A-Za-z0-9_])""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
    }

private val assistantCurrencyAmount = Regex(
    """(?:₱\s*|(?<![A-Za-z0-9_])php(?![A-Za-z0-9_])\s*)(\d+(?:\.\d{1,2})?)""",
    RegexOption.IGNORE_CASE
)
private val assistantPlainAmount = Regex("""\d+(?:\.\d{1,2})?""")
private val assistantAddKeyword = Regex("""(?<![A-Za-z0-9_])add(?![A-Za-z0-9_])""", RegexOption.IGNORE_CASE)

internal fun parseAssistantExpenseAmount(query: String): Double? {
    // A currency marker is the strongest amount signal. Without one, use the
    // first number after the explicit "add" command rather than an earlier date.
    assistantCurrencyAmount.find(query)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }
    val add = assistantAddKeyword.find(query) ?: return null
    return assistantPlainAmount.find(query, add.range.last + 1)?.value?.toDoubleOrNull()
}

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

        if (containsAssistantKeyword(q, "class", "classes", "schedule", "schedules", "subject", "subjects")) {
            val date = if (containsAssistantKeyword(q, "tomorrow")) data.today.plusDays(1) else data.today
            val day = dayNames[date.dayOfWeek]
            val classes = data.classes.filter { it.day == day }.sortedBy { it.start }
            return LocalAssistantAnswer(
                if (classes.isEmpty()) "You have no classes scheduled ${if (date == data.today) "today" else "tomorrow"}."
                else classes.joinToString(prefix = "${if (date == data.today) "Today" else "Tomorrow"}: ", separator = "; ") {
                    "${it.code} ${it.start}–${it.end}${it.room?.let { room -> " at $room" } ?: ""}"
                }
            )
        }

        if (containsAssistantKeyword(q, "deadline", "deadlines", "due", "requirement", "requirements", "assignment", "assignments")) {
            val pending = data.deadlines.filter { !it.done }.sortedBy { it.due }
            val selected = when {
                containsAssistantKeyword(q, "week", "weekly") -> pending.filter { runCatching { LocalDate.parse(it.due) }.getOrNull() in data.today..data.today.plusDays(7) }
                else -> pending.take(5)
            }
            return LocalAssistantAnswer(
                if (selected.isEmpty()) "You have no pending deadlines in that period."
                else selected.joinToString(prefix = "Upcoming: ", separator = "; ") { d ->
                    "${d.title}${d.course?.let { " ($it)" } ?: ""} — ${runCatching { LocalDate.parse(d.due).format(readableDate) }.getOrDefault(d.due)}"
                }
            )
        }

        if (containsAssistantKeyword(q, "spend", "spent", "expense", "expenses", "budget", "budgets", "money")) {
            val range = when {
                containsAssistantKeyword(q, "week", "weekly") -> {
                    val start = data.today.with(TemporalAdjusters.previousOrSame(data.repo.weekStartDay))
                    start to start.plusDays(6)
                }
                else -> YearMonth.from(data.today).atDay(1) to YearMonth.from(data.today).atEndOfMonth()
            }
            val category = listOf("food", "transport", "school", "shopping", "health", "bills", "other")
                .firstOrNull { containsAssistantKeyword(q, it) }
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

        if (containsAssistantKeyword(q, "absence", "absences", "attendance", "absent")) {
            val matched = data.classes.filter { c -> q.contains(c.code.lowercase()) }
                .ifEmpty { data.classes.filter { it.absences > 0 || containsAssistantKeyword(q, "all") }.ifEmpty { data.classes } }
            return LocalAssistantAnswer(
                if (matched.isEmpty()) "There are no classes to check yet."
                else matched.distinctBy { it.id }.joinToString(separator = "; ") {
                    val left = (it.allowedAbsences() - it.absences).coerceAtLeast(0)
                    "${it.code}: ${it.absences}/${it.allowedAbsences()} absences, $left remaining before the estimated limit"
                }
            )
        }

        if (containsAssistantKeyword(q, "study", "focus", "pomodoro", "free time", "when should")) {
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
        val includePlanning = q.isBlank() || containsAssistantKeyword(
            q, "plan", "plans", "planning", "week", "weekly", "overwhelmed", "behind", "prioritize"
        )
        val includeSchedule = includePlanning || containsAssistantKeyword(
            q, "class", "classes", "schedule", "schedules", "subject", "subjects", "room", "rooms", "today", "tomorrow"
        )
        val includeDeadlines = includePlanning || containsAssistantKeyword(
            q, "deadline", "deadlines", "due", "assignment", "assignments", "requirement", "requirements", "task", "tasks"
        )
        val includeSpending = containsAssistantKeyword(
            q, "spend", "spent", "expense", "expenses", "budget", "budgets", "money", "cost", "costs"
        )
        val includeAttendance = containsAssistantKeyword(q, "absence", "absences", "attendance", "absent", "cut", "cuts")
        val includeStudy = includePlanning || containsAssistantKeyword(
            q, "study", "focus", "pomodoro", "habit", "habits", "streak", "streaks"
        )

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
        if (!(containsAssistantKeyword(q, "start") && containsAssistantKeyword(q, "focus", "pomodoro"))) return null
        val minutes = Regex("(\\d{1,3})\\s*(?:minute|min)").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 25
        return AssistantAction.StartFocus(minutes.coerceIn(5, 180))
    }

    private fun parseExpense(q: String): AssistantAction.AddExpense? {
        val hasCurrencyMarker = q.contains("₱") || containsAssistantKeyword(q, "php")
        if (!(containsAssistantKeyword(q, "add") &&
                (containsAssistantKeyword(q, "expense", "expenses", "spent") || hasCurrencyMarker))) return null
        val amount = parseAssistantExpenseAmount(q) ?: return null
        val category = when {
            containsAssistantKeyword(q, "food", "meal", "meals") -> "Food"
            containsAssistantKeyword(q, "transport", "transportation", "fare", "fares") -> "Transportation"
            containsAssistantKeyword(q, "school", "academic") -> "School"
            containsAssistantKeyword(q, "bill", "bills", "subscription", "subscriptions") -> "Bills"
            containsAssistantKeyword(q, "health", "medicine", "medicines") -> "Health"
            containsAssistantKeyword(q, "shopping") -> "Shopping"
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
