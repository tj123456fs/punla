package com.uplb.punla.context

import com.uplb.punla.data.BudgetPeriod
import com.uplb.punla.data.CampusDirectory
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.data.haversineMeters
import com.uplb.punla.data.walkingEtaMinutes
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
        val reviewProgress: List<StudyReviewProgress>,
        val energy: EnergyLevel = EnergyLevel.UNKNOWN,
        val location: LocationContext? = null
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
        // A student who is currently in class does not have a free block right
        // now, even if another class is several hours away. This keeps the
        // shared context honest for downstream recommendation logic.
        val freeMinutes = freeMinutesBeforeNextCommitment(current, next, now, zoneId)
        val activeLocation = inputs.location?.takeIf { isLocationFresh(it, nowEpochMillis) }
        val travelBuffer = if (current == null) {
            estimatedTravelBufferMinutes(next, activeLocation, nowEpochMillis)
        } else {
            null
        }
        val usableFreeMinutes = freeMinutes?.let {
            (it - (travelBuffer ?: 0)).coerceAtLeast(0)
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
            travelBufferMinutes = travelBuffer,
            usableFreeMinutes = usableFreeMinutes,
            pendingTasks = pendingTasks,
            upcomingDeadlines = deadlineContexts.filter { it.daysUntil in 0..7 },
            overdueWork = pendingTasks.filter { it.overdue },
            energy = inputs.energy,
            study = study,
            attendance = attendance,
            budget = budget,
            courses = courses,
            location = activeLocation
        )
    }

    internal fun findCurrentClass(
        classes: List<ClassSession>,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ClassContext? {
        val nowMs = nowEpoch(now, zoneId)
        // Include yesterday as a candidate because a class stored as e.g.
        // Mon 22:00-01:00 is still current after midnight on Tuesday.
        return sequenceOf(now.toLocalDate(), now.toLocalDate().minusDays(1))
            .flatMap { date ->
                val day = dayNames[date.dayOfWeek]
                classes.asSequence()
                    .filter { it.day == day }
                    .mapNotNull { it.toOccurrence(date, zoneId) }
            }
            .filter { occurrence ->
                nowMs >= occurrence.startsAtEpochMillis &&
                    nowMs < occurrence.endsAtEpochMillis
            }
            .minByOrNull { it.startsAtEpochMillis }
    }

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
        val daysRemainingInWeek = (ChronoUnit.DAYS.between(today, weekEnd) + 1L).coerceAtLeast(1L)
        val weeklySafe = weeklySafeToSpend(
            weeklyBudget = weeklyBudget,
            spentWeek = spentWeek,
            daysRemaining = daysRemainingInWeek,
            budgetPeriod = repository.budgetPeriod
        )
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
        val codes = canonicalCourseCodes(inputs)

        return codes.map { code ->
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


    internal fun canonicalCourseCodes(inputs: Inputs): List<String> {
        val codesByKey = linkedMapOf<String, String>()

        fun add(raw: String?) {
            val display = raw?.trim()?.takeIf { it.isNotBlank() } ?: return
            val key = display.uppercase(java.util.Locale.ROOT)
            codesByKey.putIfAbsent(key, display)
        }

        inputs.classes.forEach { add(it.code) }
        inputs.deadlines.forEach { add(it.course) }
        inputs.studySessions.forEach { add(it.courseCode) }
        inputs.decks.forEach { add(it.courseCode) }
        inputs.quizzes.forEach { add(it.courseCode) }
        inputs.mistakes.forEach { add(it.courseCode) }
        inputs.planItems.forEach { add(it.courseCode) }
        inputs.reviewProgress.forEach { add(it.courseCode) }

        return codesByKey.values.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    internal fun weeklySafeToSpend(
        weeklyBudget: Double,
        spentWeek: Double,
        daysRemaining: Long,
        budgetPeriod: BudgetPeriod
    ): Double? = if (weeklyBudget > 0.0 && budgetPeriod != BudgetPeriod.MONTHLY) {
        (weeklyBudget - spentWeek) / daysRemaining.coerceAtLeast(1L)
    } else {
        null
    }

    internal fun freeMinutesBeforeNextCommitment(
        currentClass: ClassContext?,
        nextClass: ClassContext?,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int? {
        if (currentClass != null) return 0
        val next = nextClass ?: return null
        return Duration.between(
            now,
            Instant.ofEpochMilli(next.startsAtEpochMillis).atZone(zoneId).toLocalDateTime()
        ).toMinutes()
            .coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    internal fun isLocationFresh(
        location: LocationContext,
        nowEpochMillis: Long
    ): Boolean {
        val age = nowEpochMillis - location.capturedAtEpochMillis
        return age in 0..LOCATION_MAX_AGE_MILLIS
    }

    internal fun estimatedTravelBufferMinutes(
        nextClass: ClassContext?,
        location: LocationContext?,
        nowEpochMillis: Long
    ): Int? {
        val freshLocation = location?.takeIf { isLocationFresh(it, nowEpochMillis) } ?: return null
        val destination = CampusDirectory.findBuildingForRoom(nextClass?.room) ?: return null
        val meters = haversineMeters(
            freshLocation.latitude,
            freshLocation.longitude,
            destination.lat,
            destination.lon
        )
        if (!meters.isFinite() || meters < 0.0) return null
        // The context layer uses the same conservative campus walking estimate
        // already used by Dashboard/Map. Network route fetching remains a UI
        // concern; this local estimate is instant, offline, and deterministic.
        return walkingEtaMinutes(meters)
    }

    private const val LOCATION_MAX_AGE_MILLIS = 15L * 60L * 1000L

    private fun nowEpoch(value: LocalDateTime, zoneId: ZoneId): Long =
        value.atZone(zoneId).toInstant().toEpochMilli()

    private fun String.asLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
    private fun String.asLocalTimeOrNull(): LocalTime? = runCatching { LocalTime.parse(this) }.getOrNull()
    private fun String?.sameCode(other: String): Boolean = this?.trim()?.equals(other.trim(), ignoreCase = true) == true
    private fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0
}
