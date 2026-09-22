package com.uplb.punla.context

import com.uplb.punla.data.BudgetPeriod
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.data.entity.AttendanceRecord
import com.uplb.punla.data.entity.AttendanceStatus
import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.data.entity.ExpenseRule
import com.uplb.punla.data.entity.Flashcard
import com.uplb.punla.data.entity.FlashcardDeck
import com.uplb.punla.data.entity.MistakeRecord
import com.uplb.punla.data.entity.Quiz
import com.uplb.punla.data.entity.QuizAttempt
import com.uplb.punla.data.entity.StudyPlanItem
import com.uplb.punla.data.entity.StudyReviewProgress
import com.uplb.punla.data.entity.StudySession
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Pure aggregation logic for [StudentContextEngine]. */
internal object StudentContextReducer {
    private val dayNames = mapOf(
        DayOfWeek.MONDAY to "Mon",
        DayOfWeek.TUESDAY to "Tue",
        DayOfWeek.WEDNESDAY to "Wed",
        DayOfWeek.THURSDAY to "Thu",
        DayOfWeek.FRIDAY to "Fri",
        DayOfWeek.SATURDAY to "Sat",
        DayOfWeek.SUNDAY to "Sun"
    )
    private val displayTime = DateTimeFormatter.ofPattern("HH:mm")

    data class Inputs(
        val classes: List<ClassSession>,
        val deadlines: List<Deadline>,
        val attendance: List<AttendanceRecord>,
        val studySessions: List<StudySession>,
        val expenses: List<Expense>,
        val expenseRules: List<ExpenseRule>,
        val decks: List<FlashcardDeck>,
        val cards: List<Flashcard>,
        val quizzes: List<Quiz>,
        val quizAttempts: List<QuizAttempt>,
        val mistakes: List<MistakeRecord>,
        val planItems: List<StudyPlanItem>,
        val reviewProgress: List<StudyReviewProgress>
    )

    fun reduce(
        inputs: Inputs,
        repository: PunlaRepository,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): StudentState {
        val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDateTime()
        val today = now.toLocalDate()
        val current = findCurrentClass(inputs.classes, now, zoneId)
        val next = findNextClass(inputs.classes, now, zoneId)
        val freeMinutes = next?.let {
            Duration.between(now, Instant.ofEpochMilli(it.startsAtEpochMillis).atZone(zoneId).toLocalDateTime())
                .toMinutes()
                .coerceAtLeast(0L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }

        val pendingDeadlines = inputs.deadlines
            .asSequence()
            .filterNot { it.done }
            .mapNotNull { deadline ->
                val due = deadline.due.asLocalDateOrNull() ?: return@mapNotNull null
                deadline to due
            }
            .sortedBy { it.second }
            .toList()

        val deadlineContexts = pendingDeadlines.map { (deadline, due) ->
            DeadlineContext(
                id = deadline.id,
                title = deadline.title,
                courseCode = deadline.course,
                dueDate = due.toString(),
                type = deadline.type,
                priority = deadline.priority,
                daysUntil = ChronoUnit.DAYS.between(today, due),
                overdue = due.isBefore(today)
            )
        }

        val deadlineTasks = deadlineContexts.map {
            TaskContext(
                id = it.id,
                title = it.title,
                courseCode = it.courseCode,
                date = it.dueDate,
                kind = TaskKinds.DEADLINE,
                priority = it.priority,
                overdue = it.overdue
            )
        }
        val planTasks = inputs.planItems
            .asSequence()
            .filterNot { it.completed }
            .mapNotNull { item ->
                val planned = item.plannedDate.asLocalDateOrNull() ?: return@mapNotNull null
                TaskContext(
                    id = item.id,
                    title = item.title,
                    courseCode = item.courseCode,
                    date = planned.toString(),
                    kind = TaskKinds.STUDY_PLAN,
                    overdue = planned.isBefore(today)
                )
            }
            .toList()
        val pendingTasks = (deadlineTasks + planTasks).sortedBy { it.date }

        val study = studyContext(inputs, today, nowEpochMillis, zoneId)
        val attendance = attendanceContext(inputs.attendance, current, today)
        val budget = budgetContext(inputs.expenses, inputs.expenseRules, repository, today)
        val courses = courseContexts(inputs, today, nowEpochMillis, zoneId)

        return StudentState(
            generatedAtEpochMillis = nowEpochMillis,
            localDate = today.toString(),
            localTime = now.toLocalTime().format(displayTime),
            day = dayNames[now.dayOfWeek] ?: now.dayOfWeek.name,
            currentClass = current,
            nextClass = next,
            freeMinutesBeforeNextCommitment = freeMinutes,
            travelBufferMinutes = null,
            usableFreeMinutes = freeMinutes,
            pendingTasks = pendingTasks,
            upcomingDeadlines = deadlineContexts.filter { it.daysUntil in 0..7 },
            overdueWork = pendingTasks.filter { it.overdue },
            energy = EnergyLevel.UNKNOWN,
            study = study,
            attendance = attendance,
            budget = budget,
            courses = courses,
            location = null
        )
    }

    internal fun findCurrentClass(
        classes: List<ClassSession>,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ClassContext? = classes.asSequence()
        .filter { it.day == dayNames[now.dayOfWeek] }
        .mapNotNull { it.toOccurrence(now.toLocalDate(), zoneId) }
        .filter { occurrence ->
            nowEpoch(now, zoneId) >= occurrence.startsAtEpochMillis &&
                nowEpoch(now, zoneId) < occurrence.endsAtEpochMillis
        }
        .minByOrNull { it.startsAtEpochMillis }

    internal fun findNextClass(
        classes: List<ClassSession>,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ClassContext? {
        val nowMs = nowEpoch(now, zoneId)
        return (0L..7L).asSequence()
            .flatMap { offset ->
                val date = now.toLocalDate().plusDays(offset)
                val day = dayNames[date.dayOfWeek]
                classes.asSequence()
                    .filter { it.day == day }
                    .mapNotNull { it.toOccurrence(date, zoneId) }
            }
            .filter { it.startsAtEpochMillis > nowMs }
            .minByOrNull { it.startsAtEpochMillis }
    }

    private fun ClassSession.toOccurrence(date: LocalDate, zoneId: ZoneId): ClassContext? {
        val start = start.asLocalTimeOrNull() ?: return null
        val parsedEnd = end.asLocalTimeOrNull() ?: return null
        val startDateTime = LocalDateTime.of(date, start)
        var endDateTime = LocalDateTime.of(date, parsedEnd)
        if (!endDateTime.isAfter(startDateTime)) endDateTime = endDateTime.plusDays(1)
        return ClassContext(
            sessionId = id,
            code = code,
            title = title,
            section = section,
            type = type,
            room = room,
            occurrenceDate = date.toString(),
            startTime = start.toString(),
            endTime = parsedEnd.toString(),
            startsAtEpochMillis = nowEpoch(startDateTime, zoneId),
            endsAtEpochMillis = nowEpoch(endDateTime, zoneId)
        )
    }

    private fun studyContext(
        inputs: Inputs,
        today: LocalDate,
        nowEpochMillis: Long,
        zoneId: ZoneId
    ): StudyContext {
        val sevenDayStart = today.minusDays(6)
        val todaySeconds = inputs.studySessions.sumOf { session ->
            val date = Instant.ofEpochMilli(session.startedAt).atZone(zoneId).toLocalDate()
            if (date == today) session.actualSeconds.coerceAtLeast(0) else 0
        }
        val weekSeconds = inputs.studySessions.sumOf { session ->
            val date = Instant.ofEpochMilli(session.startedAt).atZone(zoneId).toLocalDate()
            if (!date.isBefore(sevenDayStart) && !date.isAfter(today)) session.actualSeconds.coerceAtLeast(0) else 0
        }
        val passingByQuiz = inputs.quizzes.associate { it.id to it.passingScore }
        val weakQuizzes = inputs.quizAttempts
            .groupBy { it.quizId }
            .mapNotNull { (quizId, attempts) ->
                val latest = attempts.maxByOrNull { it.completedAt } ?: return@mapNotNull null
                val passing = passingByQuiz[quizId] ?: 70
                if (latest.percent() < passing) quizId else null
            }
            .distinct()
            .size

        return StudyContext(
            minutesToday = todaySeconds / 60,
            minutesLast7Days = weekSeconds / 60,
            lastStudiedAtEpochMillis = inputs.studySessions.maxOfOrNull { it.endedAt },
            dueFlashcardCount = inputs.cards.count { it.isDue(nowEpochMillis) },
            weakFlashcardCount = inputs.cards.count { it.isWeak() },
            unresolvedMistakeCount = inputs.mistakes.count { !it.resolved },
            weakQuizCount = weakQuizzes,
            plannedItemsToday = inputs.planItems.count { !it.completed && it.plannedDate == today.toString() }
        )
    }

    private fun attendanceContext(
        records: List<AttendanceRecord>,
        currentClass: ClassContext?,
        today: LocalDate
    ): AttendanceContext {
        val todayRecords = records.filter { it.occurrenceDate == today.toString() }
        val currentStatus = currentClass?.let { current ->
            records.firstOrNull {
                it.sessionId == current.sessionId &&
                    it.occurrenceDate == current.occurrenceDate &&
                    it.scheduledStart == current.startTime
            }?.status
        }
        return AttendanceContext(
            attendedToday = todayRecords.count { it.status == AttendanceStatus.ATTENDED },
            absentToday = todayRecords.count { it.status == AttendanceStatus.ABSENT },
            currentClassStatus = currentStatus,
            totalRecordedAbsences = records.count { it.status == AttendanceStatus.ABSENT }
        )
    }

    private fun budgetContext(
        expenses: List<Expense>,
        rules: List<ExpenseRule>,
        repository: PunlaRepository,
        today: LocalDate
    ): BudgetContext {
        val validExpenses = expenses.filter { it.amount.isFinite() && it.amount >= 0.0 }
        val weekStart = repository.currentWeekStart(today)
        val weekEnd = weekStart.plusDays(6)
        val month = YearMonth.from(today)
        val monthlyBudget = repository.monthlyBudget
        val spentToday = repository.sumInRange(validExpenses, today, today)
        val spentWeek = repository.weeklyBudgetSpentFromList(validExpenses, weekStart)
        val weeklyBudget = repository.weeklyBudgetAmountFromList(validExpenses, weekStart)
        val spentMonth = repository.sumInRange(validExpenses, month.atDay(1), today)
        val monthlyFlexible = if (monthlyBudget > 0.0) {
            repository.monthlyFlexibleRemainingFromList(validExpenses, rules, today)
        } else 0.0
        val monthlySafe = if (monthlyBudget > 0.0) {
            repository.monthlySafeToSpendPerDayFromList(validExpenses, rules, today)
        } else null
        val weeklySafe = if (repository.budgetPeriod != BudgetPeriod.MONTHLY) {
            val daysRemaining = (ChronoUnit.DAYS.between(today, weekEnd) + 1L).coerceAtLeast(1L)
            (weeklyBudget - spentWeek) / daysRemaining
        } else null
        val safe = when (repository.budgetPeriod) {
            BudgetPeriod.MONTHLY -> monthlySafe ?: 0.0
            BudgetPeriod.WEEKLY -> weeklySafe ?: 0.0
            BudgetPeriod.BOTH -> listOfNotNull(monthlySafe, weeklySafe).minOrNull() ?: 0.0
        }

        return BudgetContext(
            spentToday = spentToday.finiteOrZero(),
            spentThisWeek = spentWeek.finiteOrZero(),
            weeklyBudget = weeklyBudget.finiteOrZero(),
            spentThisMonth = spentMonth.finiteOrZero(),
            monthlyBudget = monthlyBudget.finiteOrZero(),
            flexibleMonthlyRemaining = monthlyFlexible.finiteOrZero(),
            safeToSpendToday = safe.finiteOrZero()
        )
    }

    private fun courseContexts(
        inputs: Inputs,
        today: LocalDate,
        nowEpochMillis: Long,
        zoneId: ZoneId
    ): List<CourseContext> {
        val sevenDayStart = today.minusDays(6)
        val codes = buildSet {
            inputs.classes.mapTo(this) { it.code.trim() }
            inputs.deadlines.mapNotNullTo(this) { it.course?.trim()?.takeIf(String::isNotBlank) }
            inputs.studySessions.mapNotNullTo(this) { it.courseCode?.trim()?.takeIf(String::isNotBlank) }
            inputs.decks.mapNotNullTo(this) { it.courseCode?.trim()?.takeIf(String::isNotBlank) }
            inputs.quizzes.mapNotNullTo(this) { it.courseCode?.trim()?.takeIf(String::isNotBlank) }
            inputs.mistakes.mapNotNullTo(this) { it.courseCode?.trim()?.takeIf(String::isNotBlank) }
            inputs.planItems.mapNotNullTo(this) { it.courseCode?.trim()?.takeIf(String::isNotBlank) }
            inputs.reviewProgress.mapTo(this) { it.courseCode.trim() }
        }.filter { it.isNotBlank() }

        return codes.sortedWith(String.CASE_INSENSITIVE_ORDER).map { code ->
            val deadlines = inputs.deadlines.filter { !it.done && it.course.sameCode(code) }
            val studySeconds = inputs.studySessions.sumOf { session ->
                if (!session.courseCode.sameCode(code)) return@sumOf 0
                val date = Instant.ofEpochMilli(session.startedAt).atZone(zoneId).toLocalDate()
                if (!date.isBefore(sevenDayStart) && !date.isAfter(today)) session.actualSeconds.coerceAtLeast(0) else 0
            }
            val courseDeckIds = inputs.decks.filter { it.courseCode.sameCode(code) }.mapTo(hashSetOf()) { it.id }
            val weakCards = inputs.cards.count { it.deckId in courseDeckIds && it.isWeak() }
            val courseQuizIds = inputs.quizzes.filter { it.courseCode.sameCode(code) }.mapTo(hashSetOf()) { it.id }
            val latestAttempt = inputs.quizAttempts
                .asSequence()
                .filter { it.quizId in courseQuizIds }
                .maxByOrNull { it.completedAt }
            val reviewRows = inputs.reviewProgress.filter { it.courseCode.sameCode(code) }
            val completion = if (reviewRows.isEmpty()) null else {
                ((reviewRows.count { it.completed } * 100.0) / reviewRows.size).toInt().coerceIn(0, 100)
            }

            CourseContext(
                code = code,
                pendingDeadlineCount = deadlines.size,
                overdueDeadlineCount = deadlines.count { it.due.asLocalDateOrNull()?.isBefore(today) == true },
                studyMinutesLast7Days = studySeconds / 60,
                unresolvedMistakeCount = inputs.mistakes.count { !it.resolved && it.courseCode.sameCode(code) },
                weakFlashcardCount = weakCards,
                latestQuizPercent = latestAttempt?.percent(),
                reviewCompletionPercent = completion
            )
        }
    }

    private fun nowEpoch(value: LocalDateTime, zoneId: ZoneId): Long =
        value.atZone(zoneId).toInstant().toEpochMilli()

    private fun String.asLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
    private fun String.asLocalTimeOrNull(): LocalTime? = runCatching { LocalTime.parse(this) }.getOrNull()
    private fun String?.sameCode(other: String): Boolean = this?.trim()?.equals(other.trim(), ignoreCase = true) == true
    private fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0
}
