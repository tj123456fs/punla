package com.uplb.punla.data

import com.uplb.punla.data.dao.DeadlineDao
import com.uplb.punla.data.dao.ExpenseDao
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.data.entity.Expense
import java.time.LocalDate

/**
 * Ports the original web app's recurring-item "catch up" logic
 * (generateRecurringExpenses / generateRecurringDeadlines / addInterval in
 * index.html) to Room. Run once per app start (see PunlaViewModel.init) and
 * again right after a new recurring rule is created, so newly-due instances
 * exist as soon as they're relevant — the same as the web app's on-load pass.
 *
 * Expense rules ("weekly" | "monthly") walk forward from `lastGenerated`
 * and materialize every missed occurrence up to today, persisting the new
 * `lastGenerated` back onto the rule so the next run picks up where this
 * one left off.
 *
 * Deadline rules only ever repeat "weekly" and — matching the web app —
 * don't track their own cursor. Instead the next occurrence is derived from
 * the latest existing instance for that rule (or the rule's startDate if
 * none exist yet), and generation stops at an 8-week horizon so the
 * deadlines list doesn't fill up with things a month from now, and only
 * advances past an instance once it's done or already overdue.
 */
object RecurrenceEngine {
    private const val DEADLINE_HORIZON_DAYS = 56L // 8 weeks, matches web's RECUR_HORIZON_DAYS

    private fun addInterval(date: LocalDate, repeat: String): LocalDate = when (repeat) {
        "monthly" -> date.plusMonths(1)
        else -> date.plusWeeks(1) // "weekly" and any unrecognized value
    }

    suspend fun generateRecurringExpenses(dao: ExpenseDao) {
        val rules = dao.getAllRules()
        if (rules.isEmpty()) return
        val existing = dao.getAll()
        val today = LocalDate.now()

        for (rule in rules) {
            val cursorStart = runCatching { LocalDate.parse(rule.lastGenerated) }.getOrNull()
                ?: runCatching { LocalDate.parse(rule.startDate) }.getOrNull()
                ?: continue

            var next = addInterval(cursorStart, rule.repeat)
            var lastGenerated = rule.lastGenerated
            var guard = 0
            while (!next.isAfter(today) && guard < 200) {
                val nextStr = next.toString()
                val alreadyExists = existing.any { it.ruleId == rule.id && it.date == nextStr }
                if (!alreadyExists) {
                    dao.upsert(
                        Expense(
                            amount = rule.amount,
                            category = rule.category,
                            date = nextStr,
                            note = rule.note,
                            ruleId = rule.id,
                            isRecurring = true,
                            isFixed = rule.isFixed
                        )
                    )
                }
                lastGenerated = nextStr
                next = addInterval(next, rule.repeat)
                guard++
            }
            if (lastGenerated != rule.lastGenerated) {
                dao.upsertRule(rule.copy(lastGenerated = lastGenerated))
            }
        }
    }

    suspend fun generateRecurringDeadlines(dao: DeadlineDao) {
        val rules = dao.getAllRules()
        if (rules.isEmpty()) return
        var existing = dao.getAll()
        val today = LocalDate.now()
        val horizon = today.plusDays(DEADLINE_HORIZON_DAYS)

        for (rule in rules) {
            var lastDue = existing
                .filter { it.ruleId == rule.id }
                .mapNotNull { runCatching { LocalDate.parse(it.due) }.getOrNull() }
                .maxOrNull()
                ?: runCatching { LocalDate.parse(rule.startDate) }.getOrNull()
                ?: continue

            var guard = 0
            while (guard < 12) {
                val lastDueStr = lastDue.toString()
                val lastInstance = existing.find { it.ruleId == rule.id && it.due == lastDueStr }
                val shouldAdvance = lastInstance == null || lastInstance.done || lastDue.isBefore(today)
                if (!shouldAdvance) break

                val next = lastDue.plusWeeks(1)
                if (next.isAfter(horizon)) break
                val nextStr = next.toString()

                if (existing.none { it.ruleId == rule.id && it.due == nextStr }) {
                    val created = Deadline(
                        title = rule.title,
                        course = rule.course,
                        due = nextStr,
                        type = rule.type,
                        priority = rule.priority,
                        done = false,
                        ruleId = rule.id,
                        isRecurring = true
                    )
                    dao.upsert(created)
                    existing = existing + created
                }
                lastDue = next
                guard++
            }
        }
    }
}
