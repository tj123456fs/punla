package com.uplb.punla.planning

import android.content.Context
import androidx.room.withTransaction
import com.uplb.punla.context.*
import com.uplb.punla.data.*
import com.uplb.punla.data.entity.*
import com.uplb.punla.diagnostics.PunlaDiagnostics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import java.util.UUID

data class CourseAttention(val course: String, val reasons: List<String>)
data class TimelineEntry(val id: String, val at: Long, val title: String, val detail: String, val destination: String,
                         val timeKnown: Boolean = true)
data class AttendanceSuggestion(val session: ClassSession, val date: LocalDate, val key: String)
data class OsSnapshot(
    val ready: Boolean = false, val context: StudentState = StudentState.empty(),
    val tasks: List<WorkItem> = emptyList(), val ranked: List<RankedWork> = emptyList(),
    val preferences: List<TaskPreferences> = emptyList(), val captures: List<InboxCapture> = emptyList(),
    val blocks: List<DayPlanBlock> = emptyList(), val life: List<LifeCommitment> = emptyList(),
    val settings: Map<String, String> = emptyMap(), val busy: List<TimeWindow> = emptyList(),
    val windows: List<TimeWindow> = emptyList(), val workload: List<Workload> = emptyList(),
    val attention: List<CourseAttention> = emptyList(), val suggestions: List<AutomationSuggestion> = emptyList(),
    val attendance: List<AttendanceSuggestion> = emptyList(), val timeline: List<TimelineEntry> = emptyList(),
    val agenda: List<AgendaEntry> = emptyList()
) {
    fun enabled(category: String) = settings["category:$category"] != "false"
    fun profile(id: String): TaskPreferences = preferences.firstOrNull { it.id == id }
        ?: tasks.firstOrNull { it.id == id }?.let {
            TaskPreferences(id, estimatedMinutes = it.estimateMinutes ?: 25, progress = it.progress,
                importance = it.importance, effort = it.effort)
        } ?: TaskPreferences(id)
}

/** Connected data and confirmed mutations; planning policy remains in pure engines. */
class StudentOsRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val db = PunlaDatabase.get(app)
    private val dao = db.studentOsDao()
    private val repo = PunlaRepository(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val contextEngine = StudentContextEngine.get(app)
    private val mutationMutex = kotlinx.coroutines.sync.Mutex()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private data class Saved(val profiles: List<TaskPreferences>, val captures: List<InboxCapture>,
        val blocks: List<DayPlanBlock>, val life: List<LifeCommitment>, val settings: List<OsSetting>)
    private data class Academic(val classes: List<ClassSession>, val deadlines: List<Deadline>,
        val plans: List<StudyPlanItem>, val attendance: List<AttendanceRecord>, val quizzes: List<Quiz>)
    private data class History(val sessions: List<StudySession>, val expenses: List<Expense>,
        val reviews: List<FlashcardReviewEvent>, val attempts: List<QuizAttempt>, val grades: List<GradeCourse>)
    private val saved = combine(dao.observePreferences(), dao.observeCaptures(), dao.observeBlocks(),
        dao.observeLife(), dao.observeSettings()) { a, b, c, d, e -> Saved(a, b, c, d, e) }
    private val academic = combine(db.classSessionDao().observeAll(), db.deadlineDao().observeAll(),
        db.studyMaterialDao().observePlanItems(), db.attendanceDao().observeAll(), db.quizDao().observeQuizzes()) {
        a, b, c, d, e -> Academic(a, b, c, d, e)
    }
    private val history = combine(db.studySessionDao().observeAll(), db.expenseDao().observeAll(),
        db.studyMaterialDao().observeFlashcardReviewEvents(), db.quizDao().observeAllAttempts(),
        db.gradesDao().observeAllCourses()) { a, b, c, d, e -> History(a, b, c, d, e) }
    val state: StateFlow<OsSnapshot> = combine(contextEngine.state, saved, academic, history) { c, s, a, h ->
        buildSnapshot(c, s, a, h)
    }.retryWhen { error, attempt ->
        if (error is CancellationException) throw error
        PunlaDiagnostics.warn(app, "StudentOS", "Could not refresh planning", error)
        _message.value = "Planning could not refresh. Your saved data is unchanged."
        delay(minOf(30000L, 1000L * (attempt + 1))); true
    }.stateIn(scope, SharingStarted.Eagerly, OsSnapshot())

    private fun buildSnapshot(c: StudentState, s: Saved, a: Academic, h: History): OsSnapshot {
        if (c.localDate.isBlank()) return OsSnapshot()
        val zone = ZoneId.systemDefault()
        val now = c.generatedAtEpochMillis
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val settings = s.settings.associate { it.key to it.value }
        val energyAt = settings["energyAt"]?.toLongOrNull() ?: 0
        val energy = if (now - energyAt in 0..(6 * 3600000L))
            runCatching { EnergyLevel.valueOf(settings["energy"].orEmpty()) }.getOrDefault(EnergyLevel.UNKNOWN)
            else EnergyLevel.UNKNOWN
        var ctx = c.copy(energy = energy)
        val profiles = s.profiles.associateBy { it.id }
        fun task(id: String, title: String, course: String?, due: String?, minutes: Int? = null): WorkItem {
            val p = profiles[id]
            val stats = c.courses.firstOrNull { it.code.equals(course, true) }
            val last = h.sessions.filter { it.courseCode?.equals(course, true) == true && it.actualSeconds > 0 }
                .maxOfOrNull { it.endedAt }
            return WorkItem(id, title, course,
                due?.let { runCatching { LocalDate.parse(it).atTime(LocalTime.parse(p?.dueTime ?: "23:59"))
                    .atZone(zone).toInstant().toEpochMilli() }.getOrNull() },
                p?.estimatedMinutes ?: minutes, (p?.progress ?: 0).takeIf { it < 100 } ?: 0, p?.importance ?: 3, p?.effort ?: 2,
                last, (stats?.weakFlashcardCount ?: 0) + (stats?.unresolvedMistakeCount ?: 0),
                p?.pinned ?: false, p?.dismissedUntil ?: 0)
        }
        val tasks = a.deadlines.filter { !it.done }.map {
            val t = task("deadline:${it.id}", it.title, it.course, it.due)
            if (profiles[t.id] == null) t.copy(importance = when (it.priority) { "High" -> 5; "Low" -> 1; else -> 3 }) else t
        } + a.plans.filter { !it.completed }.map { task("study:${it.id}", it.title, it.courseCode, null, it.minutes) }
        val dayStart = runCatching { LocalTime.parse(settings["dayStart"] ?: "08:00") }.getOrDefault(LocalTime.of(8, 0))
        val dayEnd = runCatching { LocalTime.parse(settings["dayEnd"] ?: "22:00") }.getOrDefault(LocalTime.of(22, 0))
        val travel = (settings["travelMinutes"]?.toIntOrNull() ?: 10).coerceIn(0, 120)
        var personalNow: String? = null
        val busy = mutableListOf<TimeWindow>()
        val attendance = mutableListOf<AttendanceSuggestion>()
        val timeline = mutableListOf<TimelineEntry>()
        val agenda = mutableListOf<AgendaEntry>()
        val windows = mutableListOf<TimeWindow>()
        for (offset in -7L..7L) {
            val date = today.plusDays(offset)
            if (!date.isBefore(repo.termStartDate) && !date.isAfter(repo.termEndDate)) {
                a.classes.filter { it.day.equals(date.dayOfWeek.name.take(3), true) }.forEach { cls ->
                    val start = at(date, cls.start, zone) ?: return@forEach
                    var end = at(date, cls.end, zone) ?: return@forEach
                    if (end <= start) end = at(date.plusDays(1), cls.end, zone) ?: return@forEach
                    val buffer = if (c.nextClass?.sessionId == cls.id && c.nextClass.occurrenceDate == date.toString())
                        maxOf(travel, c.travelBufferMinutes ?: travel) else travel
                    busy += TimeWindow(start - buffer * 60000L, end)
                    val key = AttendanceLog.occurrenceKey(cls.id, date, cls.start)
                    if (buffer > 0) agenda += AgendaEntry("travel:$key", start - buffer * 60000L, start,
                        "Travel to ${cls.code}", "Reserved travel buffer", "TRAVEL", "campus")
                    agenda += AgendaEntry("class:$key", start, end, cls.code, "Fixed class · ${cls.room.orEmpty()}", "CLASS", "schedule")
                    val record = a.attendance.firstOrNull { it.occurrenceKey == key }
                    if (end <= now && now - end <= 12 * 3600000L && record == null && settings["dismiss:$key"] == null)
                        attendance += AttendanceSuggestion(cls, date, key)
                    timeline += TimelineEntry("class:$key", start, cls.code,
                        "${cls.start}–${cls.end} · ${record?.status ?: if (end <= now) "Attendance unconfirmed" else "Scheduled"}", "schedule")
                }
            }
            s.life.filter { it.enabled && date.dayOfWeek.value.toString() in it.days.split(',') }.forEach {
                val start = at(date, it.startTime, zone) ?: return@forEach
                val endDate = if (it.endTime <= it.startTime) date.plusDays(1) else date
                val end = at(endDate, it.endTime, zone) ?: return@forEach
                busy += TimeWindow(start, end)
                if (now in start until end) personalNow = it.title
                agenda += AgendaEntry("life:${it.id}:$date", start, end, it.title, "Reserved · ${it.category}", "LIFE", "student-os?tab=4")
                timeline += TimelineEntry("life:${it.id}:$date", start, it.title, it.category, "student-os")
            }
        }
        val validIds = tasks.map { it.id }.toSet()
        val completedIds = a.deadlines.filter { it.done }.map { "deadline:${it.id}" }.toSet() +
            a.plans.filter { it.completed }.map { "study:${it.id}" }.toSet()
        val timerEnd = repo.pomodoroRuntimeDeadline
        if (repo.pomodoroRuntimeRunning && timerEnd > now) {
            busy += TimeWindow(now, timerEnd)
            val timerTitle = if (repo.pomodoroRuntimePhase == "WORK") "Focus session" else "Study break"
            personalNow = timerTitle
            agenda += AgendaEntry("active-focus", now, timerEnd, timerTitle, "Remaining timer time", "FOCUS", "pomodoro")
        }
        val visibleBlocks = s.blocks.map { b ->
            if (b.status != "PLANNED" || b.taskId in validIds) b
            else b.copy(status = if (b.taskId in completedIds) "DONE" else "SKIPPED")
        }
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            val start = maxOf(now, at(date, dayStart.toString(), zone)!!)
            val end = at(date, dayEnd.toString(), zone)!!
            windows += DayPlanner.freeWindows(TimeWindow(start, end), busy)
        }
        val opening = DayPlanner.freeWindows(TimeWindow(now, at(today, dayEnd.toString(), zone)!!), busy)
            .firstOrNull()?.takeIf { it.start == now }?.minutes ?: 0
        val available = minOf(opening, c.usableFreeMinutes ?: opening)
        ctx = ctx.copy(usableFreeMinutes = available, currentCommitmentTitle = personalNow,
            travelBufferMinutes = c.nextClass?.let { maxOf(travel, c.travelBufferMinutes ?: 0) })
        val ranked = PriorityEngine.rank(tasks, now, available, energy)
        val attention = c.courses.map { course ->
            CourseAttention(course.code, buildList {
                if (course.overdueDeadlineCount > 0) add("${course.overdueDeadlineCount} overdue deadlines")
                if (course.pendingDeadlineCount > 0) add("${course.pendingDeadlineCount} upcoming deadlines")
                if ((course.latestQuizPercent ?: 100) < 60) add("Latest quiz: ${course.latestQuizPercent}%")
                if (course.weakFlashcardCount + course.unresolvedMistakeCount > 0)
                    add("${course.weakFlashcardCount} weak cards · ${course.unresolvedMistakeCount} unresolved mistakes")
                a.classes.filter { it.code.equals(course.code, true) && it.absences >= it.allowedAbsences() - 1 }
                    .forEach { add("${it.absences} recorded absences in ${it.type}; check attendance") }
                h.grades.filter { it.code.equals(course.code, true) && (it.grade.equals("INC", true) || (it.grade.toDoubleOrNull() ?: 0.0) > 3.0) }
                    .forEach { add("Recorded grade ${it.grade}; check the semester in Grades") }
            })
        }.filter { it.reasons.isNotEmpty() }
        val workload = DayPlanner.workload(tasks, windows, now)
        val suggestions = buildList {
            attendance.forEach {
                val event = AutomationEvent(it.key, "CLASS_ENDED", it.session.id)
                if ("SUGGEST_ATTENDANCE" in AutomationEngine.actions(event)) add(AutomationSuggestion(it.key,
                    "attendance", "Confirm ${it.session.code} attendance", "The scheduled class ended. Attendance is still unconfirmed.", "schedule", it.session.id))
            }
            workload.filter { it.overloaded }.take(3).forEach { add(AutomationSuggestion("load:${it.task.id}:$today", "workload",
                "Review ${it.task.title}", "${it.cumulativeRequired} min required across deadlines; ${it.availableMinutes} min available in the next 8 days before this deadline.", "student-os", it.task.id)) }
            a.quizzes.filter { now - it.createdAt in 0..86400000L }.forEach {
                val event = AutomationEvent("quiz:${it.id}", "QUIZ_ADDED", it.id)
                if ("SUGGEST_REVIEW" in AutomationEngine.actions(event)) add(AutomationSuggestion(event.key, "study",
                    "Review ${it.title}", "A new quiz is available. Choose a review block in Plan.", "quizzes", it.courseCode))
            }
            if (c.budget.safeToSpendToday < 0 && (c.budget.weeklyBudget > 0 || c.budget.monthlyBudget > 0))
                add(AutomationSuggestion("budget:$today", "budget", "Check today's spending", "Spending is ahead of your budget settings.", "budget"))
        }.filter { settings["category:${it.category}"] != "false" && settings["dismiss:${it.id}"] == null }
        val historyStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        h.sessions.filter { it.startedAt >= historyStart }.forEach { timeline += TimelineEntry("focus:${it.id}", it.startedAt, "${it.actualSeconds / 60} min focused",
            it.courseCode ?: "Untagged", "pomodoro") }
        h.expenses.filter { it.date >= today.minusDays(6).toString() }.forEach { expense -> at(runCatching { LocalDate.parse(expense.date) }.getOrDefault(today), "12:00", zone)?.let {
            timeline += TimelineEntry("expense:${expense.id}", it, "₱${expense.amount} · ${expense.category}", "Time not recorded", "budget", timeKnown = false)
        } }
        h.reviews.filter { it.reviewedAt >= historyStart }.forEach { timeline += TimelineEntry("review:${it.id}", it.reviewedAt, "Flashcard reviewed", "${it.courseCode.orEmpty()} · ${it.rating}", "flashcards") }
        h.attempts.filter { it.completedAt >= historyStart }.forEach { timeline += TimelineEntry("quiz:${it.id}", it.completedAt, "Quiz: ${it.score}/${it.total}", "Completed attempt", "quizzes") }
        s.captures.filter { it.createdAt >= historyStart }.forEach { timeline += TimelineEntry("capture:${it.id}", it.createdAt, it.text.take(80), if (it.processed) "Capture processed" else "Inbox capture", "student-os") }
        visibleBlocks.filter { it.startAt >= historyStart }.forEach { timeline += TimelineEntry("block:${it.id}", it.startAt, it.title, "${it.window().minutes} min · ${it.status.lowercase()}", "student-os") }
        val visibleProfiles = s.profiles.map { if (it.id in validIds && it.progress == 100) it.copy(progress = 0) else it }
        return OsSnapshot(true, ctx, tasks, ranked, visibleProfiles, s.captures, visibleBlocks, s.life, settings,
            busy, windows, workload, attention, suggestions, attendance, timeline.sortedByDescending { it.at }, agenda)
    }

    private suspend fun freshSnapshot(): OsSnapshot = buildSnapshot(
        contextEngine.state.value.copy(generatedAtEpochMillis = System.currentTimeMillis()),
        Saved(dao.preferences(), dao.captures(), dao.blocks(), dao.life(), dao.settings()),
        Academic(db.classSessionDao().getAll(), db.deadlineDao().getAll(), db.studyMaterialDao().getPlanItems(),
            db.attendanceDao().getAll(), db.quizDao().getQuizzes()),
        History(db.studySessionDao().getAll(), db.expenseDao().getAll(), db.studyMaterialDao().getFlashcardReviewEvents(),
            db.quizDao().getAllAttempts(), db.gradesDao().getAllCourses()))

    private suspend fun markSourceComplete(taskId: String) {
        if (taskId.startsWith("deadline:")) db.deadlineDao().getAll().firstOrNull { "deadline:${it.id}" == taskId }
            ?.let { db.deadlineDao().upsert(it.copy(done = true)) }
        else db.studyMaterialDao().getPlanItems().firstOrNull { "study:${it.id}" == taskId }
            ?.let { db.studyMaterialDao().upsertPlanItem(it.copy(completed = true)) }
    }

    private suspend fun runWorkflow(event: AutomationEvent) {
        for (action in AutomationEngine.actions(event)) when (action) {
            "RECALCULATE_PRIORITIES", "REFRESH_CONTEXT" -> contextEngine.refreshNow()
            "REFRESH_TODAY" -> com.uplb.punla.widget.WidgetRefresher.refreshAll(app)
        }
    }

    private fun at(date: LocalDate, time: String, zone: ZoneId): Long? = runCatching {
        date.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()
    private fun mutate(block: suspend () -> Unit) = scope.launch {
        mutationMutex.lock()
        try { block(); contextEngine.refreshNow() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { _message.value = e.message ?: "Could not save this change."; PunlaDiagnostics.warn(app, "StudentOS", "Action failed", e) }
        finally { mutationMutex.unlock() }
    }
    fun clearMessage() { _message.value = null }
    fun checkIn(energy: EnergyLevel) = mutate {
        db.withTransaction { dao.save(OsSetting("energy", energy.name)); dao.save(OsSetting("energyAt", System.currentTimeMillis().toString())) }
        contextEngine.updateEnergy(energy)
    }
    fun setting(key: String, value: String) = mutate { dao.save(OsSetting(key, value)) }
    fun planningHours(start: String, end: String, travelMinutes: Int) = mutate {
        require(LocalTime.parse(start) < LocalTime.parse(end) && travelMinutes in 0..120) { "Check planning hours and travel minutes." }
        db.withTransaction {
            dao.save(OsSetting("dayStart", start)); dao.save(OsSetting("dayEnd", end))
            dao.save(OsSetting("travelMinutes", travelMinutes.toString()))
        }
    }
    fun dismiss(id: String) = setting("dismiss:$id", "true")
    fun savePreferences(item: TaskPreferences) = mutate {
        require(item.estimatedMinutes in 5..10080 && item.progress in 0..100 && item.effort in 1..3 && item.importance in 1..5) { "Check task estimates and progress." }
        LocalTime.parse(item.dueTime)
        db.withTransaction {
            dao.save(item)
            if (item.progress == 100) markSourceComplete(item.id)
        }
    }
    fun chooseFirst(task: WorkItem) = mutate {
        val profile = freshSnapshot().profile(task.id)
        db.withTransaction {
            dao.preferences().filter { it.pinned }.forEach { dao.save(it.copy(pinned = false)) }
            dao.save(profile.copy(pinned = true, dismissedUntil = 0))
        }
    }
    fun irrelevant(task: WorkItem) = mutate {
        val tomorrow = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.save(freshSnapshot().profile(task.id).copy(dismissedUntil = tomorrow, pinned = false))
    }
    fun capture(text: String, onResult: (String?) -> Unit = {}) = scope.launch {
        val error = try {
            require(text.isNotBlank()) { "Enter something to capture." }
            require(text.length <= 20000) { "Keep your capture under 20,000 characters." }
            dao.save(InboxCapture(text = text))
            null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            PunlaDiagnostics.warn(app, "StudentOS", "Capture failed", e)
            (e.message ?: "Could not save. Your draft is still here.").also { _message.value = it }
        }
        withContext(Dispatchers.Main) { onResult(error) }
    }
    fun discard(item: InboxCapture) = mutate { dao.save(item.copy(processed = true)) }
    fun convert(item: InboxCapture, kind: String, title: String, course: String?, date: String?, time: String, minutes: Int) = mutate {
        require(title.isNotBlank()) { "Enter a title." }; require(minutes in 5..10080) { "Estimate must be 5–10080 minutes." }
        if (kind == "Deadline") require(date != null) { "Choose a due date." }
        val parsedDate = date?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).toString() }
        LocalTime.parse(time)
        db.withTransaction {
            // Re-read inside transaction: double taps cannot duplicate a conversion.
            val current = dao.captures().firstOrNull { it.id == item.id } ?: error("This capture no longer exists.")
            check(!current.processed) { "This capture has already been processed." }
            when (kind) {
                "Deadline" -> {
                    val deadline = Deadline(title = title.trim(), course = course, due = parsedDate!!, type = "Task", priority = "Medium")
                    db.deadlineDao().upsert(deadline)
                    dao.save(TaskPreferences("deadline:${deadline.id}", minutes, dueTime = time))
                }
                "Task" -> db.studyMaterialDao().upsertPlanItem(StudyPlanItem(title = title.trim(), courseCode = course,
                    plannedDate = parsedDate ?: LocalDate.now().toString(), minutes = minutes, kind = StudyPlanKinds.FOCUS))
                else -> db.studyMaterialDao().upsertNote(StudyNote(title = title.trim(), courseCode = course, body = item.text +
                    (item.attachment?.let { "\n\nAttachment retained in Punla Inbox: $it" } ?: "")))
            }
            dao.save(current.copy(processed = true))
        }
        com.uplb.punla.worker.ReminderScheduler.scheduleDaily(app)
    }
    fun generatePlan() = mutate {
        val snap = freshSnapshot(); check(snap.ready) { "Wait for planning to load." }
        val now = System.currentTimeMillis(); val zone = ZoneId.systemDefault(); val today = LocalDate.now()
        val end = today.atTime(LocalTime.parse(snap.settings["dayEnd"] ?: "22:00")).atZone(zone).toInstant().toEpochMilli()
        val locks = snap.blocks.filter { (it.locked || it.startAt <= now) && it.status == "PLANNED" && it.endAt > now }
        val windows = snap.windows.filter { it.start < end }.flatMap {
            DayPlanner.freeWindows(TimeWindow(maxOf(now, it.start), minOf(end, it.end)), locks.map { b -> b.window() })
        }
        val plan = DayPlanner.generate(snap.tasks, windows, snap.context.energy,
            locks.groupBy { it.taskId }.mapValues { it.value.sumOf { b -> b.window().minutes } })
        db.withTransaction {
            dao.blocks().filter { !it.locked && it.status == "PLANNED" && it.startAt >= now && it.startAt < end }.forEach { dao.delete(it) }
            plan.blocks.forEach { dao.save(DayPlanBlock(taskId = it.taskId, title = it.title, startAt = it.window.start, endAt = it.window.end)) }
        }
        _message.value = if (plan.unallocated.isEmpty()) "Today's plan is ready." else "Plan ready. ${plan.unallocated.values.sum()} minutes still need another opening."
    }
    fun changeBlock(block: DayPlanBlock, start: Long = block.startAt, minutes: Int = block.window().minutes,
                    locked: Boolean = block.locked, status: String = block.status) = mutate {
        require(status in listOf("PLANNED", "DONE", "SKIPPED"))
        val snap = freshSnapshot()
        if (start != block.startAt || minutes != block.window().minutes) {
            val other = snap.blocks.filter { it.id != block.id && it.status == "PLANNED" }.map { it.window() }
            require(DayPlanner.canPlace(TimeWindow(start, start + minutes * 60000L), snap.busy + other, System.currentTimeMillis())) {
                "Choose 5–240 minutes in a free period, including travel and personal commitments."
            }
            val due = snap.tasks.firstOrNull { it.id == block.taskId }?.dueAt
            require(due == null || start + minutes * 60000L <= due) { "This block would finish after its deadline." }
        }
        db.withTransaction {
            val existing = dao.blocks().firstOrNull { it.id == block.id } ?: error("This block no longer exists.")
            if (status == "DONE" && existing.status != "DONE") {
                val p = snap.profile(block.taskId)
                val increment = kotlin.math.ceil(minutes * 100.0 / p.estimatedMinutes).toInt().coerceAtLeast(1)
                val progress = (p.progress + increment).coerceAtMost(100)
                dao.save(p.copy(progress = progress))
                if (progress == 100) markSourceComplete(block.taskId)
            }
            dao.save(existing.copy(startAt = start, endAt = start + minutes * 60000L, locked = locked, status = status))
        }
    }
    fun recover(block: DayPlanBlock, split: Boolean) = mutate {
        val snap = freshSnapshot(); val now = System.currentTimeMillis()
        check(snap.blocks.any { it.id == block.id && it.status == "PLANNED" }) { "This block has already been handled." }
        val task = snap.tasks.firstOrNull { it.id == block.taskId } ?: error("This task is no longer pending.")
        val occupied = snap.blocks.filter { it.id != block.id && it.status == "PLANNED" && it.endAt > now }
        val windows = snap.windows.flatMap { DayPlanner.freeWindows(it, occupied.map { b -> b.window() }) }
        val duration = minOf(block.window().minutes, task.remainingMinutes)
        val chosen = if (split) windows else windows.filter { it.minutes >= duration }
        val plan = DayPlanner.generate(listOf(task.copy(estimateMinutes = duration, progress = 0, dismissedUntil = 0)), chosen,
            snap.context.energy, maxBlockMinutes = if (split) 25 else duration)
        require(plan.blocks.sumOf { it.window.minutes } >= duration) { "No complete recovery fits before the deadline. Try moving or reducing the task." }
        db.withTransaction {
            dao.save(block.copy(status = "SKIPPED", locked = false))
            plan.blocks.forEach { dao.save(DayPlanBlock(taskId = it.taskId, title = it.title, startAt = it.window.start, endAt = it.window.end)) }
        }
    }
    fun completeTask(task: WorkItem) = mutate {
        db.withTransaction {
            markSourceComplete(task.id)
            dao.save(state.value.profile(task.id).copy(progress = 100, pinned = false))
            dao.blocks().filter { it.taskId == task.id && it.status == "PLANNED" }.forEach { dao.save(it.copy(status = "DONE")) }
        }
        runWorkflow(AutomationEvent("completed:${task.id}", "TASK_COMPLETED", task.id))
    }
    fun saveLife(item: LifeCommitment) = mutate {
        require(item.title.isNotBlank()); LocalTime.parse(item.startTime); LocalTime.parse(item.endTime)
        require(item.startTime != item.endTime && item.days.split(',').all { it.toIntOrNull() in 1..7 }) { "Check times and days (1=Monday through 7=Sunday)." }
        dao.save(item)
    }
    fun deleteLife(item: LifeCommitment) = mutate { dao.delete(item) }
    fun confirmAttendance(item: AttendanceSuggestion, status: String) = mutate {
        require(AttendanceStatus.isValid(status))
        repo.setAttendance(AttendanceLog.forOccurrence(item.session, item.date, status, "confirmed_suggestion"))
        com.uplb.punla.widget.WidgetRefresher.refreshAll(app)
    }
    companion object {
        @Volatile private var instance: StudentOsRepository? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: StudentOsRepository(context).also { instance = it }
        }
    }
}
