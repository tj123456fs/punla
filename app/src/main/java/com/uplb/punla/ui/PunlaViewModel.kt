package com.uplb.punla.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uplb.punla.data.BackgroundStyle
import com.uplb.punla.data.BudgetPeriod
import com.uplb.punla.data.ChecklistDefaults
import com.uplb.punla.data.FontChoice
import com.uplb.punla.data.PunlaDatabase
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.data.RecurrenceEngine
import com.uplb.punla.data.RoutePlan
import com.uplb.punla.data.ThemeMode
import com.uplb.punla.data.ThemePreset
import com.uplb.punla.data.entity.ChecklistItem
import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.data.entity.DeadlineRule
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.data.entity.ExpenseRule
import com.uplb.punla.data.entity.GradeCourse
import com.uplb.punla.data.entity.Semester
import com.uplb.punla.data.entity.StudySession
import com.uplb.punla.widget.WidgetRefresher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class PunlaViewModel(app: Application) : AndroidViewModel(app) {
    private val db = PunlaDatabase.get(app)
    val repo = PunlaRepository(app)

    // Roadmap C — separate "has Room actually emitted yet" flags from the
    // lists themselves. A freshly-emptied list and the emptyList() default
    // stateIn() starts with look identical by content, so this is tracked
    // independently rather than inferred from the list — otherwise a
    // genuinely-empty table could never be told apart from "not loaded yet".
    private val _classesLoaded = MutableStateFlow(false)
    private val _expensesLoaded = MutableStateFlow(false)
    private val _deadlinesLoaded = MutableStateFlow(false)

    val classes: StateFlow<List<ClassSession>> = db.classSessionDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<Expense>> = db.expenseDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deadlines: StateFlow<List<Deadline>> = db.deadlineDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checklistItems: StateFlow<List<ChecklistItem>> = db.checklistDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * True once the first real Room emission has landed for classes,
     * expenses, and deadlines. Screens use this to withhold "No classes
     * scheduled" / "No expenses logged yet" style empty-state copy on cold
     * launch instead of flashing it for one frame before real data arrives.
     *
     * Collected independently in [init] (via `.first()` on the raw DAO
     * flows) rather than derived from [classes]/[expenses]/[deadlines]
     * themselves — those are WhileSubscribed(5000) and only run while some
     * screen is actively collecting them, so a screen that only observes
     * one of the three (e.g. Schedule only collects classes+deadlines,
     * never expenses) could otherwise wait forever for a flag no one is
     * updating.
     */
    val isDataReady: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        _classesLoaded, _expensesLoaded, _deadlinesLoaded
    ) { c, e, d -> c && e && d }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val semesters: StateFlow<List<Semester>> = db.gradesDao().observeSemesters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archives: StateFlow<List<com.uplb.punla.data.entity.Archive>> = db.gradesDao().observeArchives()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Every graded course across every semester — the running/cumulative
    // GWA (roadmap #1) is derived from this rather than from whichever
    // semester tab happens to be selected.
    val allCourses: StateFlow<List<GradeCourse>> = db.gradesDao().observeAllCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nextClassFlow: Flow<ClassSession?> = classes.map { list ->
        repo.nextClassFromList(list)
    }

    val nextDeadlineFlow: Flow<Deadline?> = deadlines.map { list ->
        list.filter { !it.done }.minByOrNull { it.due }
    }

    // ---- Campus multi-stop route plan ----
    // Set by CampusMapScreen's "Plan route" flow (nearest-neighbor + 2-opt
    // ordering over a few selected buildings, each leg's real walking route
    // already resolved at plan-creation time); read by CampusFullMapScreen
    // to draw the whole chained route. Living here (rather than as
    // navigation arguments) because it's a genuine object graph — a list of
    // legs each carrying an already-fetched [com.uplb.punla.data.WalkingRoute]
    // — not something worth serializing into a nav route string.
    private val _routePlan = MutableStateFlow<RoutePlan?>(null)
    val routePlan: StateFlow<RoutePlan?> = _routePlan

    fun setRoutePlan(plan: RoutePlan?) {
        _routePlan.value = plan
    }

    init {
        // Roadmap C — runs for the ViewModel's whole lifetime (not tied to
        // any particular screen being on-screen), so isDataReady converges
        // shortly after launch regardless of which tab the person opens
        // first or which of classes/expenses/deadlines that tab collects.
        viewModelScope.launch {
            db.classSessionDao().observeAll().first()
            _classesLoaded.value = true
        }
        viewModelScope.launch {
            db.expenseDao().observeAll().first()
            _expensesLoaded.value = true
        }
        viewModelScope.launch {
            db.deadlineDao().observeAll().first()
            _deadlinesLoaded.value = true
        }

        // Mirrors the web app's on-load generateRecurringExpenses()/
        // generateRecurringDeadlines() pass: catch up on any recurring
        // instances that should exist by now (e.g. the app wasn't opened
        // for a couple of weeks and several weekly items were missed).
        viewModelScope.launch {
            RecurrenceEngine.generateRecurringExpenses(db.expenseDao())
            RecurrenceEngine.generateRecurringDeadlines(db.deadlineDao())
            WidgetRefresher.refreshAll(getApplication())
        }

        // Seed the built-in pre-enrollment checklist once, the first time
        // the table is empty — same "only runs if nothing's there yet"
        // pattern as a migration seed, so it never overwrites items the
        // person has already checked off, edited, or deleted.
        viewModelScope.launch {
            if (db.checklistDao().count() == 0) {
                db.checklistDao().upsertAll(ChecklistDefaults.ITEMS)
            }
        }
    }

    // Compose-observable mirror of repo.themeMode; the persisted prefs value
    // alone wouldn't trigger recomposition when the person taps the toggle.
    var themeMode by mutableStateOf(repo.themeMode)
        private set

    fun updateThemeMode(mode: ThemeMode) {
        repo.themeMode = mode
        themeMode = mode
    }

    // Compose-observable mirrors of repo.themePreset / repo.customSeedColor,
    // same reasoning as themeMode above.
    var themePreset by mutableStateOf(repo.themePreset)
        private set

    var customSeedColor by mutableStateOf(repo.customSeedColor)
        private set

    fun updateThemePreset(preset: ThemePreset) {
        repo.themePreset = preset
        themePreset = preset
        // Palette changes affect the widgets' background bitmap too, not
        // just the in-app UI — refresh so they pick it up immediately
        // instead of waiting for the next data write.
        viewModelScope.launch { WidgetRefresher.refreshAll(getApplication()) }
    }

    /** Sets the custom seed color and switches the active preset to CUSTOM in one step. */
    fun updateCustomSeedColor(argb: Int) {
        repo.customSeedColor = argb
        customSeedColor = argb
        updateThemePreset(ThemePreset.CUSTOM) // also handles the widget refresh above
    }

    // Compose-observable mirror of repo.backgroundStyle, same reasoning as
    // themeMode/themePreset above.
    var backgroundStyle by mutableStateOf(repo.backgroundStyle)
        private set

    fun updateBackgroundStyle(style: BackgroundStyle) {
        repo.backgroundStyle = style
        backgroundStyle = style
        // Currently only the in-app Box modifier reacts automatically
        // (recomposition); widgets need an explicit nudge to pick up the
        // new style before their next data-triggered refresh.
        viewModelScope.launch { WidgetRefresher.refreshAll(getApplication()) }
    }

    // Compose-observable mirror of repo.fontChoice, same reasoning as
    // themeMode/themePreset above.
    var fontChoice by mutableStateOf(repo.fontChoice)
        private set

    fun updateFontChoice(choice: FontChoice) {
        repo.fontChoice = choice
        fontChoice = choice
    }

    var monthlyBudget by mutableStateOf(repo.monthlyBudget)
        private set

    var chedTarget by mutableStateOf(repo.chedTarget)
        private set

    var userName by mutableStateOf(repo.userName)
        private set

    var notificationsEnabled by mutableStateOf(repo.notificationsEnabled)
        private set

    // Compose-observable mirrors of the Weekly Budgeting settings, same
    // reasoning as themeMode/themePreset above.
    var budgetPeriod by mutableStateOf(repo.budgetPeriod)
        private set

    var weekStartDay by mutableStateOf(repo.weekStartDay)
        private set

    var weeklyBudgetOverride by mutableStateOf(repo.weeklyBudgetOverride)
        private set

    var weeklyRolloverEnabled by mutableStateOf(repo.weeklyRolloverEnabled)
        private set

    fun updateUserName(name: String) {
        repo.userName = name
        userName = name
    }

    fun toggleNotifications(enabled: Boolean) {
        repo.notificationsEnabled = enabled
        notificationsEnabled = enabled
    }

    fun updateBudgetPeriod(period: BudgetPeriod) {
        repo.budgetPeriod = period
        budgetPeriod = period
        viewModelScope.launch { WidgetRefresher.refreshAll(getApplication()) }
    }

    fun updateWeekStartDay(day: java.time.DayOfWeek) {
        repo.weekStartDay = day
        weekStartDay = day
        viewModelScope.launch { WidgetRefresher.refreshAll(getApplication()) }
    }

    /** Pass null to clear the override and go back to the auto-derived figure. */
    fun updateWeeklyBudgetOverride(amount: Double?) {
        repo.weeklyBudgetOverride = amount
        weeklyBudgetOverride = amount
        viewModelScope.launch { WidgetRefresher.refreshAll(getApplication()) }
    }

    fun updateWeeklyRolloverEnabled(enabled: Boolean) {
        repo.weeklyRolloverEnabled = enabled
        weeklyRolloverEnabled = enabled
        viewModelScope.launch { WidgetRefresher.refreshAll(getApplication()) }
    }

    // Currently selected semester tab on the Grades screen. Compose-observable.
    var selectedSemesterId by mutableStateOf<String?>(null)
        private set

    fun selectSemester(id: String) {
        selectedSemesterId = id
    }

    fun coursesFlow(semesterId: String): Flow<List<GradeCourse>> =
        db.gradesDao().observeCourses(semesterId)

    fun addSemester(label: String) = viewModelScope.launch {
        val semester = Semester(label = label)
        db.gradesDao().upsertSemester(semester)
        selectedSemesterId = semester.id
    }

    fun deleteSemester(semester: Semester) = viewModelScope.launch {
        db.gradesDao().deleteSemester(semester)
        if (selectedSemesterId == semester.id) selectedSemesterId = null
    }

    fun upsertCourse(course: GradeCourse) = viewModelScope.launch {
        db.gradesDao().upsertCourse(course)
    }

    fun deleteCourse(course: GradeCourse) = viewModelScope.launch {
        db.gradesDao().deleteCourse(course)
    }

    fun updateChedTarget(target: Double?) {
         repo.chedTarget = target
       chedTarget = target
    }

    fun addOrUpdateClass(session: ClassSession) = viewModelScope.launch {
        db.classSessionDao().upsert(session)
        WidgetRefresher.refreshAll(getApplication())
    }

    fun deleteClass(session: ClassSession) = viewModelScope.launch {
        db.classSessionDao().delete(session)
        WidgetRefresher.refreshAll(getApplication())
    }

    // Roadmap #4 — attendance tracking.
    fun incrementAbsence(session: ClassSession) = viewModelScope.launch {
        db.classSessionDao().incrementAbsence(session.id)
        WidgetRefresher.refreshAll(getApplication())
    }

    fun decrementAbsence(session: ClassSession) = viewModelScope.launch {
        db.classSessionDao().decrementAbsence(session.id)
        WidgetRefresher.refreshAll(getApplication())
    }

    /**
     * @param repeat null/blank = one-off expense. "weekly" or "monthly" also
     * creates an [ExpenseRule] so future occurrences are generated
     * automatically (see [RecurrenceEngine]) — mirrors the web app's
     * expense-form "Repeats" dropdown.
     */
    fun addExpense(expense: Expense, repeat: String? = null) = viewModelScope.launch {
        if (repeat.isNullOrBlank()) {
            db.expenseDao().upsert(expense)
        } else {
            val rule = ExpenseRule(
                amount = expense.amount,
                category = expense.category,
                note = expense.note,
                startDate = expense.date,
                repeat = repeat,
                lastGenerated = expense.date,
                isFixed = expense.isFixed
            )
            db.expenseDao().upsertRule(rule)
            db.expenseDao().upsert(expense.copy(ruleId = rule.id, isRecurring = true))
        }
        WidgetRefresher.refreshAll(getApplication())
    }

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        db.expenseDao().delete(expense)
        WidgetRefresher.refreshAll(getApplication())
    }

    /** Detaches all generated instances from the rule (they remain as regular
     * expenses) and removes the rule so no further occurrences are generated. */
    fun stopExpenseRecurrence(ruleId: String) = viewModelScope.launch {
        db.expenseDao().detachRecurrence(ruleId)
        db.expenseDao().deleteRule(ruleId)
        WidgetRefresher.refreshAll(getApplication())
    }

    fun setBudget(amount: Double) = viewModelScope.launch {
        repo.monthlyBudget = amount
        monthlyBudget = amount
        WidgetRefresher.refreshAll(getApplication())
    }

    /**
     * @param repeatWeekly also creates a [DeadlineRule] so the next occurrence
     * keeps getting generated automatically (see [RecurrenceEngine]) —
     * mirrors the web app's "Repeats weekly" checkbox.
     */
    fun addOrUpdateDeadline(deadline: Deadline, repeatWeekly: Boolean = false) = viewModelScope.launch {
        if (!repeatWeekly) {
            db.deadlineDao().upsert(deadline)
        } else {
            val rule = DeadlineRule(
                title = deadline.title,
                course = deadline.course,
                type = deadline.type,
                priority = deadline.priority,
                startDate = deadline.due,
                repeat = "weekly"
            )
            db.deadlineDao().upsertRule(rule)
            db.deadlineDao().upsert(deadline.copy(ruleId = rule.id, isRecurring = true))
        }
        WidgetRefresher.refreshAll(getApplication())
    }

    fun toggleDeadlineDone(deadline: Deadline) = viewModelScope.launch {
        db.deadlineDao().upsert(deadline.copy(done = !deadline.done))
        // A completed instance is what lets the next weekly occurrence
        // generate (see RecurrenceEngine.generateRecurringDeadlines), so
        // catch up immediately instead of waiting for the next app launch.
        if (deadline.ruleId != null) {
            RecurrenceEngine.generateRecurringDeadlines(db.deadlineDao())
        }
        WidgetRefresher.refreshAll(getApplication())
    }

    fun deleteDeadline(deadline: Deadline) = viewModelScope.launch {
        db.deadlineDao().delete(deadline)
        WidgetRefresher.refreshAll(getApplication())
    }

    /** Detaches all generated instances from the rule (they remain as regular
     * deadlines) and removes the rule so no further occurrences are generated. */
    fun stopDeadlineRecurrence(ruleId: String) = viewModelScope.launch {
        db.deadlineDao().detachRecurrence(ruleId)
        db.deadlineDao().deleteRule(ruleId)
        WidgetRefresher.refreshAll(getApplication())
    }

    // ---- Pre-enrollment checklist ----
    fun toggleChecklistItem(item: ChecklistItem) = viewModelScope.launch {
        db.checklistDao().upsert(item.copy(checked = !item.checked))
    }

    fun addChecklistItem(title: String, note: String? = null) = viewModelScope.launch {
        val nextOrder = (checklistItems.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
        db.checklistDao().upsert(
            ChecklistItem(title = title, note = note, isCustom = true, sortOrder = nextOrder)
        )
    }

    fun deleteChecklistItem(item: ChecklistItem) = viewModelScope.launch {
        db.checklistDao().delete(item)
    }

    /** Resets to the built-in default set, discarding any edits/custom
     * items — offered from the checklist screen as an "escape hatch" if
     * someone deletes something by mistake or wants a clean slate. */
    fun resetChecklistToDefaults() = viewModelScope.launch {
        db.checklistDao().clearAll()
        db.checklistDao().upsertAll(ChecklistDefaults.ITEMS)
    }

    val quotes = listOf(
        "Small steps every day still add up to a harvest.",
        "You don't have to see the whole semester, just today's row to hoe.",
        "Rest is part of the work, not a break from it.",
        "Progress, not perfection — turn in the draft, not the ideal.",
        "One deadline at a time. You've handled hard weeks before.",
        "Done is better than perfect when the clock is ticking.",
        "A slow class today can still be a passed class in June.",
        "Your effort compounds even on the days it doesn't feel like it.",
        "Ask for help early — it's cheaper than asking for an extension.",
        "You planted this schedule for a reason. Trust the plan you made.",
        "Tough labs make for good stories later. Keep going.",
        "Consistency beats intensity — show up again tomorrow.",
        "Nobody aces every exam. You just need to ace enough of them.",
        "Take the walk to class slow today. You've earned a breath.",
        "The syllabus doesn't know how capable you actually are.",
        "Every finished requirement is one less thing carrying weight."
    )

    fun getQuoteOfTheDay(): String {
        val calendar = java.util.Calendar.getInstance()
        val day = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        return quotes[day % quotes.size]
    }

    fun startNewSemester(label: String) = viewModelScope.launch {
        val currentClasses = classes.value
        val currentDeadlines = deadlines.value
        
        val classesJson = JSONArray().apply {
            currentClasses.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("code", c.code)
                    put("section", c.section)
                    put("title", c.title)
                    put("day", c.day)
                    put("type", c.type)
                    put("start", c.start)
                    put("end", c.end)
                    put("room", c.room)
                    put("instructor", c.instructor)
                })
            }
        }.toString()

        val deadlinesJson = JSONArray().apply {
            currentDeadlines.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id)
                    put("title", d.title)
                    put("course", d.course)
                    put("due", d.due)
                    put("type", d.type)
                    put("priority", d.priority)
                    put("done", d.done)
                    put("ruleId", d.ruleId)
                    put("isRecurring", d.isRecurring)
                })
            }
        }.toString()

        val archive = com.uplb.punla.data.entity.Archive(
            createdAt = System.currentTimeMillis(),
            label = label,
            scheduleJson = classesJson,
            deadlinesJson = deadlinesJson
        )

        db.gradesDao().insertArchive(archive)

        db.classSessionDao().clearAll()
        db.deadlineDao().clearAll()
        db.deadlineDao().clearAllRules()

        WidgetRefresher.refreshAll(getApplication())
    }

    var mapSearchQuery by mutableStateOf("")
        private set

    fun searchOnMap(query: String) {
        mapSearchQuery = query
    }

    // ---- Backup export/import (see BackupManager) ----

    sealed class BackupResult {
        data class Success(val message: String) : BackupResult()
        data class Failure(val message: String) : BackupResult()
    }

    var backupResult by mutableStateOf<BackupResult?>(null)
        private set

    // Roadmap #6 — "Last backed up X days ago" on Settings, plus the signal
    // the periodic BackupNudgeWorker reads to decide whether to nudge.
    var lastBackupAt by mutableStateOf(repo.lastBackupAt)
        private set

    fun clearBackupResult() {
        backupResult = null
    }

    fun exportBackup(uri: android.net.Uri) = viewModelScope.launch {
        runCatching {
            com.uplb.punla.data.BackupManager.exportTo(getApplication(), uri)
        }.onSuccess {
            repo.lastBackupAt = System.currentTimeMillis()
            lastBackupAt = repo.lastBackupAt
            backupResult = BackupResult.Success("Backup saved.")
        }.onFailure { e ->
            backupResult = BackupResult.Failure(e.message ?: "Couldn't save the backup.")
        }
    }

    fun importBackup(uri: android.net.Uri) = viewModelScope.launch {
        runCatching {
            com.uplb.punla.data.BackupManager.importFrom(getApplication(), uri)
        }.onSuccess {
            // Refresh the Compose-observable prefs mirrors so the UI reflects
            // what the import just wrote, without waiting for a re-launch.
            themeMode = repo.themeMode
            themePreset = repo.themePreset
            customSeedColor = repo.customSeedColor
            backgroundStyle = repo.backgroundStyle
            monthlyBudget = repo.monthlyBudget
            chedTarget = repo.chedTarget
            userName = repo.userName
            notificationsEnabled = repo.notificationsEnabled
            WidgetRefresher.refreshAll(getApplication())
            backupResult = BackupResult.Success("Backup restored.")
        }.onFailure { e ->
            backupResult = BackupResult.Failure(e.message ?: "Couldn't restore that backup.")
        }
    }

    // ---- Pomodoro timer ----
    val studySessions: StateFlow<List<StudySession>> = repo.observeStudySessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Study habits (roadmap Study Habits 2.3) ----
    private val dailyStudyGoalFlow = MutableStateFlow(repo.dailyStudyGoalMinutes)

    var dailyStudyGoalMinutes by mutableStateOf(repo.dailyStudyGoalMinutes)
        private set
    var weeklyStudyGoalMinutes by mutableStateOf(repo.weeklyStudyGoalMinutes)
        private set

    fun updateDailyStudyGoal(minutes: Int) {
        repo.dailyStudyGoalMinutes = minutes
        dailyStudyGoalMinutes = minutes
        dailyStudyGoalFlow.value = minutes
    }

    fun updateWeeklyStudyGoal(minutes: Int) {
        repo.weeklyStudyGoalMinutes = minutes
        weeklyStudyGoalMinutes = minutes
    }

    /** Today's studied minutes, recomputed whenever the session log changes. */
    val todayStudyMinutes: StateFlow<Int> = studySessions
        .map { repo.studySecondsOn(it, java.time.LocalDate.now()) / 60 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Consecutive-day streak of meeting the daily goal, see
     * [PunlaRepository.currentStudyStreak]. Reacts to both new sessions and
     * a changed daily goal, since the streak depends on both. */
    val currentStudyStreak: StateFlow<Int> = kotlinx.coroutines.flow.combine(
        studySessions, dailyStudyGoalFlow
    ) { sessions, _ -> repo.currentStudyStreak(sessions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Removes a logged focus block — used by the Study Analysis screen's
     * session log list (roadmap Study Analysis 3.5). */
    fun deleteStudySession(session: StudySession) = viewModelScope.launch {
        repo.deleteStudySession(session)
    }

    // ---- Free-time study suggestion (Free-Time Study Suggestions, Phase 1) ----
    var studySuggestionDismissedAt by mutableStateOf(repo.lastStudySuggestionDismissedAt)
        private set

    /** Dismisses today's Dashboard study-slot suggestion; it won't reappear
     * until tomorrow. See [PunlaRepository.lastStudySuggestionDismissedAt]. */
    fun dismissStudySuggestion() {
        val now = System.currentTimeMillis()
        repo.lastStudySuggestionDismissedAt = now
        studySuggestionDismissedAt = now
    }

    var pomodoroWorkMinutes by mutableStateOf(repo.pomodoroWorkMinutes)
        private set
    var pomodoroShortBreakMinutes by mutableStateOf(repo.pomodoroShortBreakMinutes)
        private set
    var pomodoroLongBreakMinutes by mutableStateOf(repo.pomodoroLongBreakMinutes)
        private set
    var pomodoroCyclesBeforeLongBreak by mutableStateOf(repo.pomodoroCyclesBeforeLongBreak)
        private set
    var pomodoroAutoStartNext by mutableStateOf(repo.pomodoroAutoStartNext)
        private set

    fun updatePomodoroWorkMinutes(minutes: Int) {
        repo.pomodoroWorkMinutes = minutes
        pomodoroWorkMinutes = minutes
    }

    fun updatePomodoroShortBreakMinutes(minutes: Int) {
        repo.pomodoroShortBreakMinutes = minutes
        pomodoroShortBreakMinutes = minutes
    }

    fun updatePomodoroLongBreakMinutes(minutes: Int) {
        repo.pomodoroLongBreakMinutes = minutes
        pomodoroLongBreakMinutes = minutes
    }

    fun updatePomodoroCyclesBeforeLongBreak(cycles: Int) {
        repo.pomodoroCyclesBeforeLongBreak = cycles
        pomodoroCyclesBeforeLongBreak = cycles
    }

    fun updatePomodoroAutoStartNext(enabled: Boolean) {
        repo.pomodoroAutoStartNext = enabled
        pomodoroAutoStartNext = enabled
    }

    var pomodoroState by mutableStateOf(com.uplb.punla.ui.pomodoro.PomodoroUiState())
        private set

    private var pomodoroJob: kotlinx.coroutines.Job? = null
    private var phaseDeadline: Long = 0L   // System.currentTimeMillis() target
    private var phaseStartedAt: Long = 0L  // for actualSeconds on early stop

    fun startPomodoroWork(courseCode: String?) {
        val minutes = repo.pomodoroWorkMinutes
        beginPhase(com.uplb.punla.ui.pomodoro.PomodoroPhase.WORK, minutes * 60, courseCode)
    }

    /**
     * Starts whichever phase is currently staged on [pomodoroState] — used by
     * the "Start Short break" / "Start Long break" / "Start Focus" button that
     * appears after a phase completes with auto-start-next off. Unlike
     * [startPomodoroWork], which always begins a fresh WORK phase from IDLE,
     * this reads `pomodoroState.phase` (already set by `onPhaseComplete()`)
     * and looks up the matching duration, so a staged SHORT_BREAK actually
     * starts a short break instead of silently starting another focus block.
     */
    fun startStagedPhase() {
        val phase = pomodoroState.phase
        if (phase == com.uplb.punla.ui.pomodoro.PomodoroPhase.IDLE) return
        val minutes = when (phase) {
            com.uplb.punla.ui.pomodoro.PomodoroPhase.WORK -> repo.pomodoroWorkMinutes
            com.uplb.punla.ui.pomodoro.PomodoroPhase.SHORT_BREAK -> repo.pomodoroShortBreakMinutes
            com.uplb.punla.ui.pomodoro.PomodoroPhase.LONG_BREAK -> repo.pomodoroLongBreakMinutes
            com.uplb.punla.ui.pomodoro.PomodoroPhase.IDLE -> 0
        }
        beginPhase(phase, minutes * 60, pomodoroState.courseCode)
    }

    /** Updates the picked course while idle (before a phase has started). */
    fun setPomodoroCourse(courseCode: String?) {
        if (pomodoroState.phase == com.uplb.punla.ui.pomodoro.PomodoroPhase.IDLE) {
            pomodoroState = pomodoroState.copy(courseCode = courseCode)
        }
    }

    private fun beginPhase(phase: com.uplb.punla.ui.pomodoro.PomodoroPhase, seconds: Int, courseCode: String?) {
        pomodoroJob?.cancel()
        phaseStartedAt = System.currentTimeMillis()
        phaseDeadline = phaseStartedAt + seconds * 1000L
        pomodoroState = pomodoroState.copy(
            phase = phase, remainingSeconds = seconds, totalSecondsForPhase = seconds,
            isRunning = true, courseCode = courseCode ?: pomodoroState.courseCode
        )
        tickPomodoro()
    }

    private fun tickPomodoro() {
        pomodoroJob = viewModelScope.launch {
            while (pomodoroState.isRunning) {
                val remainingMs = phaseDeadline - System.currentTimeMillis()
                if (remainingMs <= 0) {
                    onPhaseComplete()
                    break
                }
                pomodoroState = pomodoroState.copy(remainingSeconds = (remainingMs / 1000).toInt())
                kotlinx.coroutines.delay(250) // sub-second poll, cheap, keeps UI smooth
            }
        }
    }

    fun pausePomodoro() {
        pomodoroJob?.cancel()
        pomodoroState = pomodoroState.copy(isRunning = false)
    }

    fun resumePomodoro() {
        phaseDeadline = System.currentTimeMillis() + pomodoroState.remainingSeconds * 1000L
        pomodoroState = pomodoroState.copy(isRunning = true)
        tickPomodoro()
    }

    /** Stops early. Logs a StudySession with completed=false if the phase was
     * WORK and at least 60s of real elapsed time had passed (skip logging
     * accidental taps under a minute — not a meaningful session). */
    fun stopPomodoro() {
        pomodoroJob?.cancel()
        if (pomodoroState.phase == com.uplb.punla.ui.pomodoro.PomodoroPhase.WORK) {
            val actual = ((System.currentTimeMillis() - phaseStartedAt) / 1000).toInt()
            if (actual >= 60) logCompletedOrStoppedWork(actual, completed = false)
        }
        pomodoroState = com.uplb.punla.ui.pomodoro.PomodoroUiState()
    }

    private fun onPhaseComplete() {
        if (pomodoroState.phase == com.uplb.punla.ui.pomodoro.PomodoroPhase.WORK) {
            logCompletedOrStoppedWork(pomodoroState.totalSecondsForPhase, completed = true)
        }
        notifyPomodoroPhaseComplete(pomodoroState.phase)

        val nextCycle = if (pomodoroState.phase == com.uplb.punla.ui.pomodoro.PomodoroPhase.WORK) pomodoroState.cycleCount + 1 else pomodoroState.cycleCount
        val nextPhase = when (pomodoroState.phase) {
            com.uplb.punla.ui.pomodoro.PomodoroPhase.WORK ->
                if (nextCycle % repo.pomodoroCyclesBeforeLongBreak == 0) com.uplb.punla.ui.pomodoro.PomodoroPhase.LONG_BREAK
                else com.uplb.punla.ui.pomodoro.PomodoroPhase.SHORT_BREAK
            else -> com.uplb.punla.ui.pomodoro.PomodoroPhase.WORK
        }
        pomodoroState = pomodoroState.copy(cycleCount = nextCycle, isRunning = false)

        if (repo.pomodoroAutoStartNext) {
            val seconds = when (nextPhase) {
                com.uplb.punla.ui.pomodoro.PomodoroPhase.WORK -> repo.pomodoroWorkMinutes
                com.uplb.punla.ui.pomodoro.PomodoroPhase.SHORT_BREAK -> repo.pomodoroShortBreakMinutes
                com.uplb.punla.ui.pomodoro.PomodoroPhase.LONG_BREAK -> repo.pomodoroLongBreakMinutes
                com.uplb.punla.ui.pomodoro.PomodoroPhase.IDLE -> 0
            } * 60
            beginPhase(nextPhase, seconds, pomodoroState.courseCode)
        } else {
            pomodoroState = pomodoroState.copy(phase = nextPhase, remainingSeconds = 0, totalSecondsForPhase = 0)
        }
    }

    private fun logCompletedOrStoppedWork(actualSeconds: Int, completed: Boolean) {
        viewModelScope.launch {
            repo.logStudySession(
                StudySession(
                    courseCode = pomodoroState.courseCode,
                    startedAt = phaseStartedAt,
                    endedAt = System.currentTimeMillis(),
                    plannedMinutes = repo.pomodoroWorkMinutes,
                    actualSeconds = actualSeconds,
                    completed = completed,
                    cyclesInSession = pomodoroState.cycleCount + 1
                )
            )
        }
    }

    /** Mirrors ClassReminderWorker's notification pattern (channel created
     * lazily, POST_NOTIFICATIONS guarded on API 33+) but posted directly
     * from here since this only ever fires while the app is foregrounded —
     * the timer's tick loop lives in this ViewModel, not a Worker. */
    private fun notifyPomodoroPhaseComplete(justFinishedPhase: com.uplb.punla.ui.pomodoro.PomodoroPhase) {
        val (title, body) = when (justFinishedPhase) {
            com.uplb.punla.ui.pomodoro.PomodoroPhase.WORK -> "Focus block done" to "Time for a break."
            com.uplb.punla.ui.pomodoro.PomodoroPhase.SHORT_BREAK,
            com.uplb.punla.ui.pomodoro.PomodoroPhase.LONG_BREAK -> "Break's over" to "Ready for another round?"
            com.uplb.punla.ui.pomodoro.PomodoroPhase.IDLE -> return
        }
        if (!repo.notificationsEnabled) return

        val context = getApplication<Application>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return
        }

        val channelId = "punla_pomodoro_channel"
        val channel = android.app.NotificationChannel(
            channelId, "Focus Timer", android.app.NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Alerts when a focus or break interval finishes" }
        val sysManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        sysManager.createNotificationChannel(channel)

        val intent = android.content.Intent(context, com.uplb.punla.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(com.uplb.punla.MainActivity.EXTRA_START_ROUTE, "pomodoro")
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 9001, intent, android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.uplb.punla.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            androidx.core.app.NotificationManagerCompat.from(context).notify(9001, builder.build())
        } catch (e: SecurityException) {
            // Permission wasn't granted
        }
    }

    override fun onCleared() {
        pomodoroJob?.cancel()
        super.onCleared()
    }
}
