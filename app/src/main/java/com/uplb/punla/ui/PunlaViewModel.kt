package com.uplb.punla.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.room.withTransaction
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
import com.uplb.punla.data.entity.AttendanceLog
import com.uplb.punla.data.entity.AttendanceRecord
import com.uplb.punla.data.entity.AttendanceStatus
import com.uplb.punla.data.entity.ChecklistItem
import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.data.entity.DeadlineRule
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.data.entity.ExpenseRule
import com.uplb.punla.data.entity.GradeCourse
import com.uplb.punla.data.entity.Flashcard
import com.uplb.punla.data.entity.FlashcardDeck
import com.uplb.punla.data.entity.FlashcardRating
import com.uplb.punla.data.entity.FlashcardReviewScheduler
import com.uplb.punla.data.entity.FlashcardReviewEvent
import com.uplb.punla.data.entity.FlashcardTypes
import com.uplb.punla.data.entity.ClozeText
import com.uplb.punla.data.entity.Quiz
import com.uplb.punla.data.entity.QuizQuestion
import com.uplb.punla.data.entity.QuizQuestionTypes
import com.uplb.punla.data.entity.QuizAttempt
import com.uplb.punla.data.entity.JsonImportRecord
import com.uplb.punla.data.entity.Semester
import com.uplb.punla.data.entity.StudySession
import com.uplb.punla.data.entity.StudySuggestionEvent
import com.uplb.punla.data.entity.StudyTopic
import com.uplb.punla.data.entity.StudyNote
import com.uplb.punla.data.entity.FormulaReference
import com.uplb.punla.data.entity.MistakeRecord
import com.uplb.punla.data.entity.MistakeSourceTypes
import com.uplb.punla.data.entity.StudyConfidence
import com.uplb.punla.data.entity.StudyGoal
import com.uplb.punla.data.entity.StudyGoalTypes
import com.uplb.punla.data.entity.StudyPlanItem
import com.uplb.punla.data.entity.StudyReviewProgress
import com.uplb.punla.data.entity.QuizAnswerResult
import com.uplb.punla.data.entity.QuestionBankItem
import com.uplb.punla.data.StudyEngine
import com.uplb.punla.data.StudyJsonBundle
import com.uplb.punla.widget.WidgetRefresher
import com.uplb.punla.worker.ClassDayNotificationScheduler
import com.uplb.punla.ml.StudySlotFeatures
import com.uplb.punla.ml.StudySlotPredictor
import com.uplb.punla.ui.pomodoro.StudySuggestion
import com.uplb.punla.pomodoro.PomodoroAlarmScheduler
import com.uplb.punla.pomodoro.PomodoroRunningNotification
import com.uplb.punla.pomodoro.PomodoroCompletionCoordinator
import com.uplb.punla.assistant.AssistantSnapshot
import com.uplb.punla.assistant.LocalAssistant
import com.uplb.punla.data.AssistantApi
import com.uplb.punla.data.AssistantApiResult
import kotlinx.coroutines.CancellationException
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

    val attendanceRecords: StateFlow<List<AttendanceRecord>> = repo.observeAttendanceRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<Expense>> = db.expenseDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseRules: StateFlow<List<ExpenseRule>> = db.expenseDao().observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deadlines: StateFlow<List<Deadline>> = db.deadlineDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checklistItems: StateFlow<List<ChecklistItem>> = db.checklistDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val flashcardDecks: StateFlow<List<FlashcardDeck>> = db.flashcardDao().observeDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val flashcards: StateFlow<List<Flashcard>> = db.flashcardDao().observeAllCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quizzes: StateFlow<List<Quiz>> = db.quizDao().observeQuizzes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quizQuestions: StateFlow<List<QuizQuestion>> = db.quizDao().observeAllQuestions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quizAttempts: StateFlow<List<QuizAttempt>> = db.quizDao().observeAllAttempts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val studyTopics: StateFlow<List<StudyTopic>> = db.studyMaterialDao().observeTopics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val studyNotes: StateFlow<List<StudyNote>> = db.studyMaterialDao().observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val formulaReferences: StateFlow<List<FormulaReference>> = db.studyMaterialDao().observeFormulas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mistakeRecords: StateFlow<List<MistakeRecord>> = db.studyMaterialDao().observeMistakes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val studyGoals: StateFlow<List<StudyGoal>> = db.studyMaterialDao().observeGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val studyPlanItems: StateFlow<List<StudyPlanItem>> = db.studyMaterialDao().observePlanItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val studyReviewProgress: StateFlow<List<StudyReviewProgress>> = db.studyMaterialDao().observeReviewProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val flashcardReviewEvents: StateFlow<List<FlashcardReviewEvent>> = db.studyMaterialDao().observeFlashcardReviewEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quizAnswerResults: StateFlow<List<QuizAnswerResult>> = db.studyMaterialDao().observeAnswerResults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val questionBank: StateFlow<List<QuestionBankItem>> = db.studyMaterialDao().observeQuestionBank()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun flashcardsFlow(deckId: String): Flow<List<Flashcard>> = db.flashcardDao().observeCards(deckId)
    fun quizQuestionsFlow(quizId: String): Flow<List<QuizQuestion>> = db.quizDao().observeQuestions(quizId)
    fun quizAttemptsFlow(quizId: String): Flow<List<QuizAttempt>> = db.quizDao().observeAttempts(quizId)

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

    var classDayNotificationEnabled by mutableStateOf(repo.classDayNotificationEnabled)
        private set

    var morningAgendaEnabled by mutableStateOf(repo.morningAgendaEnabled)
        private set

    var quietHoursEnabled by mutableStateOf(repo.quietHoursEnabled)
        private set
    var studyRemindersEnabled by mutableStateOf(repo.studyRemindersEnabled)
        private set

    var termStartDate by mutableStateOf(repo.termStartDate)
        private set
    var termEndDate by mutableStateOf(repo.termEndDate)
        private set
    var cloudAssistantEnabled by mutableStateOf(repo.cloudAssistantEnabled)
        private set
    var assistantModel by mutableStateOf(repo.assistantModel)
        private set
    var assistantApiKeyConfigured by mutableStateOf(!repo.assistantApiKey.isNullOrBlank())
        private set
    var preferredReminderHour by mutableStateOf(repo.preferredReminderHour)
        private set
    var dismissedExpensePatternKeys by mutableStateOf(repo.dismissedExpensePatternKeys)
        private set

    fun updateTermDates(start: java.time.LocalDate, end: java.time.LocalDate) {
        repo.termStartDate = start
        repo.termEndDate = end
        termStartDate = start
        termEndDate = end
    }

    fun updateCloudAssistantEnabled(enabled: Boolean) {
        repo.cloudAssistantEnabled = enabled
        cloudAssistantEnabled = enabled
    }

    fun updateAssistantModel(model: String) {
        repo.assistantModel = model.ifBlank { "claude-haiku-4-5" }
        assistantModel = repo.assistantModel
    }

    fun updateAssistantApiKey(apiKey: String) {
        repo.assistantApiKey = apiKey
        assistantApiKeyConfigured = !repo.assistantApiKey.isNullOrBlank()
    }

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

    var categoryBudgetLimits by mutableStateOf(repo.categoryBudgetLimits)
        private set

    fun updateUserName(name: String) {
        repo.userName = name
        userName = name
    }

    fun toggleNotifications(enabled: Boolean) {
        repo.notificationsEnabled = enabled
        notificationsEnabled = enabled
        if (enabled && repo.pomodoroTimerNotification && repo.pomodoroRuntimeRunning) {
            PomodoroRunningNotification.showFromRepository(getApplication<Application>())
        } else if (!enabled) {
            PomodoroRunningNotification.cancel(getApplication<Application>())
        }

        if (enabled && repo.classDayNotificationEnabled) {
            ClassDayNotificationScheduler.ensureScheduled(getApplication())
        } else {
            ClassDayNotificationScheduler.cancel(getApplication())
        }
    }

    fun updateClassDayNotificationEnabled(enabled: Boolean) {
        repo.classDayNotificationEnabled = enabled
        classDayNotificationEnabled = enabled
        if (enabled && repo.notificationsEnabled) {
            ClassDayNotificationScheduler.ensureScheduled(getApplication())
        } else {
            ClassDayNotificationScheduler.cancel(getApplication())
        }
    }

    fun updateMorningAgendaEnabled(enabled: Boolean) {
        repo.morningAgendaEnabled = enabled
        morningAgendaEnabled = enabled
        com.uplb.punla.worker.ReminderScheduler.scheduleDaily(getApplication(), updateExisting = true)
    }

    fun updateQuietHoursEnabled(enabled: Boolean) {
        repo.quietHoursEnabled = enabled
        quietHoursEnabled = enabled
        com.uplb.punla.worker.ReminderScheduler.scheduleDaily(getApplication(), updateExisting = true)
    }

    fun updateStudyRemindersEnabled(enabled: Boolean) {
        repo.studyRemindersEnabled = enabled
        studyRemindersEnabled = enabled
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

    fun updateCategoryBudgetLimits(limits: Map<String, Double>) {
        repo.categoryBudgetLimits = limits.filterValues { it > 0.0 && it.isFinite() }
        categoryBudgetLimits = repo.categoryBudgetLimits
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
        ClassDayNotificationScheduler.refresh(getApplication())
    }

    fun deleteClass(session: ClassSession) = viewModelScope.launch {
        repo.deleteClassWithAttendance(session)
        WidgetRefresher.refreshAll(getApplication())
        ClassDayNotificationScheduler.refresh(getApplication())
    }

    // Per-occurrence attendance history. ATTENDED and ABSENT overwrite the
    // same deterministic row, so users can correct a mistaken tap safely.
    fun logAttendance(
        session: ClassSession,
        status: String,
        date: java.time.LocalDate = java.time.LocalDate.now(),
        source: String = "app"
    ) = viewModelScope.launch {
        if (!AttendanceStatus.isValid(status)) return@launch
        repo.setAttendance(AttendanceLog.forOccurrence(session, date, status, source))
        WidgetRefresher.refreshAll(getApplication())
        ClassDayNotificationScheduler.refresh(getApplication())
    }

    fun clearAttendance(record: AttendanceRecord) = viewModelScope.launch {
        repo.clearAttendance(record.occurrenceKey)
        WidgetRefresher.refreshAll(getApplication())
        ClassDayNotificationScheduler.refresh(getApplication())
    }

    // Retained for old UI/data compatibility. New attendance controls should
    // prefer [logAttendance] so there is an auditable dated record.
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
            // A backdated recurring expense may already have additional due
            // occurrences. Generate them immediately instead of waiting for
            // the next cold launch.
            RecurrenceEngine.generateRecurringExpenses(db.expenseDao())
        }
        WidgetRefresher.refreshAll(getApplication())
    }

    fun updateExpense(expense: Expense) = viewModelScope.launch {
        db.expenseDao().upsert(expense)
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

    // ---- Flashcards + quiz study tools ----
    fun upsertFlashcardDeck(deck: FlashcardDeck) = viewModelScope.launch {
        db.flashcardDao().upsertDeck(deck.copy(updatedAt = System.currentTimeMillis()))
    }

    fun deleteFlashcardDeck(deck: FlashcardDeck) = viewModelScope.launch { db.flashcardDao().deleteDeck(deck) }

    private suspend fun importResult(label: String, block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e("PunlaImport", "$label failed", error)
        Result.failure(error)
    }

    /**
     * Imports are awaited by the UI instead of fire-and-forget. A failed Room
     * transaction is rolled back and returned as a Result, so bad data or a
     * storage/schema problem can never terminate the app process.
     */
    suspend fun importFlashcardDeck(
        deck: FlashcardDeck,
        cards: List<Flashcard>,
        contentId: String? = null
    ): Result<Unit> = importResult("Flashcard deck import") {
        db.withTransaction {
            db.flashcardDao().importDeck(deck, cards)
            if (!contentId.isNullOrBlank()) db.jsonImportDao().upsert(
                JsonImportRecord(com.uplb.punla.data.PunlaJsonFileIds.FLASHCARD_DECK, contentId, destinationId = deck.id)
            )
        }
    }

    suspend fun importFlashcardsIntoDeck(
        deck: FlashcardDeck,
        cards: List<Flashcard>,
        contentId: String? = null
    ): Result<Unit> = importResult("Flashcard card import") {
        db.withTransaction {
            if (cards.isNotEmpty()) {
                db.flashcardDao().upsertCards(cards)
                db.flashcardDao().upsertDeck(deck.copy(updatedAt = System.currentTimeMillis()))
            }
            if (!contentId.isNullOrBlank()) db.jsonImportDao().upsert(
                JsonImportRecord(com.uplb.punla.data.PunlaJsonFileIds.FLASHCARD_DECK, contentId, destinationId = deck.id)
            )
        }
    }

    suspend fun checkJsonImport(fileType: String, contentId: String): Result<Boolean> = try {
        Result.success(db.jsonImportDao().exists(fileType, contentId))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e("PunlaImport", "Import history check failed", error)
        Result.failure(error)
    }

    fun upsertFlashcard(card: Flashcard) = viewModelScope.launch {
        db.flashcardDao().upsertCard(card.copy(updatedAt = System.currentTimeMillis()))
        flashcardDecks.value.firstOrNull { it.id == card.deckId }?.let {
            db.flashcardDao().upsertDeck(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun addFlashcards(cards: List<Flashcard>) = viewModelScope.launch {
        if (cards.isEmpty()) return@launch
        db.flashcardDao().upsertCards(cards)
        flashcardDecks.value.firstOrNull { it.id == cards.first().deckId }?.let {
            db.flashcardDao().upsertDeck(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteFlashcard(card: Flashcard) = viewModelScope.launch { db.flashcardDao().deleteCard(card) }

    fun toggleFlashcardStar(card: Flashcard) = upsertFlashcard(card.copy(starred = !card.starred))

    fun rateFlashcard(card: Flashcard, rating: FlashcardRating) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val reviewed = FlashcardReviewScheduler.reviewed(card, rating, now)
        db.withTransaction {
            db.flashcardDao().upsertCard(reviewed)
            val deck = flashcardDecks.value.firstOrNull { it.id == card.deckId }
            db.studyMaterialDao().upsertFlashcardReviewEvent(
                FlashcardReviewEvent(
                    cardId = card.id,
                    deckId = card.deckId,
                    courseCode = deck?.courseCode,
                    rating = rating.name,
                    reviewedAt = now
                )
            )
            val existing = mistakeRecords.value.firstOrNull {
                !it.resolved && it.sourceType == MistakeSourceTypes.FLASHCARD && it.sourceId == card.id
            }
            if (rating == FlashcardRating.AGAIN) {
                db.studyMaterialDao().upsertMistake(
                    existing?.copy(
                        prompt = card.front,
                        correctAnswer = card.back,
                        explanation = card.hint,
                        missedAt = now,
                        retryAt = now + 10L * 60L * 1000L,
                        timesMissed = existing.timesMissed + 1,
                        resolved = false
                    ) ?: MistakeRecord(
                        sourceType = MistakeSourceTypes.FLASHCARD,
                        sourceId = card.id,
                        courseCode = deck?.courseCode,
                        topicTag = card.tagList().firstOrNull(),
                        prompt = card.front,
                        correctAnswer = card.back,
                        explanation = card.hint,
                        missedAt = now,
                        retryAt = now + 10L * 60L * 1000L
                    )
                )
            } else if (rating == FlashcardRating.GOOD && existing != null) {
                db.studyMaterialDao().upsertMistake(existing.copy(resolved = true))
            }
            advanceStudyGoals(
                goalType = StudyGoalTypes.FLASHCARDS,
                amount = 1,
                courseCode = deck?.courseCode,
                topicTags = card.tagList()
            )
        }
    }

    // ---- Quizzes ----
    fun upsertQuiz(quiz: Quiz) = viewModelScope.launch {
        db.quizDao().upsertQuiz(quiz.copy(updatedAt = System.currentTimeMillis()))
    }

    fun deleteQuiz(quiz: Quiz) = viewModelScope.launch { db.quizDao().deleteQuiz(quiz) }

    fun upsertQuizQuestion(question: QuizQuestion) = viewModelScope.launch {
        db.quizDao().upsertQuestion(question.copy(updatedAt = System.currentTimeMillis()))
        quizzes.value.firstOrNull { it.id == question.quizId }?.let {
            db.quizDao().upsertQuiz(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteQuizQuestion(question: QuizQuestion) = viewModelScope.launch { db.quizDao().deleteQuestion(question) }

    suspend fun importQuiz(
        quiz: Quiz,
        questions: List<QuizQuestion>,
        contentId: String? = null
    ): Result<Unit> = importResult("Quiz import") {
        db.withTransaction {
            db.quizDao().upsertQuiz(quiz)
            db.quizDao().upsertQuestions(questions)
            if (!contentId.isNullOrBlank()) db.jsonImportDao().upsert(
                JsonImportRecord(com.uplb.punla.data.PunlaJsonFileIds.QUIZ, contentId, destinationId = quiz.id)
            )
        }
    }

    fun recordQuizAttempt(attempt: QuizAttempt) = viewModelScope.launch { db.quizDao().insertAttempt(attempt) }

    /** Stores an attempt, per-question confidence results, and mistake notebook entries atomically. */
    suspend fun recordQuizAttemptWithResults(
        attempt: QuizAttempt,
        answers: List<QuizAnswerResult>,
        questionLookup: Map<String, QuizQuestion>,
        quiz: Quiz
    ): Result<Unit> = runCatching {
        db.withTransaction {
            db.quizDao().insertAttempt(attempt)
            db.studyMaterialDao().upsertAnswerResults(answers)

            // Fetch once for the whole attempt. Besides avoiding an O(answers ×
            // mistakes) query pattern, keeping a mutable lookup ensures later
            // answers in this transaction see the state we just wrote.
            val activeQuizMistakes = db.studyMaterialDao().getMistakes()
                .filter { !it.resolved && it.sourceType == MistakeSourceTypes.QUIZ }
                .associateBy { it.sourceId }
                .toMutableMap()

            answers.forEach { result ->
                val q = questionLookup[result.questionId] ?: return@forEach
                val existing = activeQuizMistakes[q.id]

                when {
                    // A confident correct retry clears an existing notebook item.
                    result.correct && result.confidence == StudyConfidence.CONFIDENT -> {
                        if (existing != null) {
                            val resolved = existing.copy(
                                userAnswer = result.userAnswer,
                                confidence = result.confidence,
                                resolved = true
                            )
                            db.studyMaterialDao().upsertMistake(resolved)
                            activeQuizMistakes.remove(q.id)
                        }
                    }

                    // Correct-but-unsure/guessed answers stay in weak review, but
                    // they do not increment "times missed" because they were not misses.
                    result.correct -> {
                        val retryDelay = if (result.confidence == StudyConfidence.GUESSED) {
                            12L * 60L * 60L * 1000L
                        } else {
                            18L * 60L * 60L * 1000L
                        }
                        val weakRecord = existing?.copy(
                            userAnswer = result.userAnswer,
                            correctAnswer = result.correctAnswer,
                            explanation = q.explanation,
                            confidence = result.confidence,
                            missedAt = result.answeredAt,
                            retryAt = result.answeredAt + retryDelay,
                            resolved = false
                        ) ?: MistakeRecord(
                            sourceType = MistakeSourceTypes.QUIZ,
                            sourceId = q.id,
                            courseCode = quiz.courseCode,
                            topicTag = q.tagList().firstOrNull(),
                            prompt = q.prompt,
                            userAnswer = result.userAnswer,
                            correctAnswer = result.correctAnswer,
                            explanation = q.explanation,
                            confidence = result.confidence,
                            missedAt = result.answeredAt,
                            retryAt = result.answeredAt + retryDelay,
                            timesMissed = 0
                        )
                        db.studyMaterialDao().upsertMistake(weakRecord)
                        activeQuizMistakes[q.id] = weakRecord
                    }

                    // Wrong answers increment true miss history and re-enter review.
                    else -> {
                        val retryDelay = when (result.confidence) {
                            StudyConfidence.GUESSED -> 12L * 60L * 60L * 1000L
                            StudyConfidence.UNSURE -> 18L * 60L * 60L * 1000L
                            else -> 24L * 60L * 60L * 1000L
                        }
                        val missedRecord = existing?.copy(
                            userAnswer = result.userAnswer,
                            correctAnswer = result.correctAnswer,
                            explanation = q.explanation,
                            confidence = result.confidence,
                            missedAt = result.answeredAt,
                            retryAt = result.answeredAt + retryDelay,
                            timesMissed = existing.timesMissed + 1,
                            resolved = false
                        ) ?: MistakeRecord(
                            sourceType = MistakeSourceTypes.QUIZ,
                            sourceId = q.id,
                            courseCode = quiz.courseCode,
                            topicTag = q.tagList().firstOrNull(),
                            prompt = q.prompt,
                            userAnswer = result.userAnswer,
                            correctAnswer = result.correctAnswer,
                            explanation = q.explanation,
                            confidence = result.confidence,
                            missedAt = result.answeredAt,
                            retryAt = result.answeredAt + retryDelay
                        )
                        db.studyMaterialDao().upsertMistake(missedRecord)
                        activeQuizMistakes[q.id] = missedRecord
                    }
                }
            }

            val answeredTags = answers.mapNotNull { result -> questionLookup[result.questionId] }
                .flatMap { it.tagList() }
            advanceStudyGoals(
                goalType = StudyGoalTypes.QUESTIONS,
                amount = answers.size,
                courseCode = quiz.courseCode,
                topicTags = answeredTags
            )
            advanceStudyGoals(
                goalType = StudyGoalTypes.SCORE,
                amount = attempt.percent(),
                courseCode = quiz.courseCode,
                topicTags = answeredTags,
                useMaximum = true
            )
        }
    }

    /** Turns missed quiz questions into a fresh flashcard deck for spaced repetition. */
    fun createFlashcardsFromQuizMistakes(quiz: Quiz, questions: List<QuizQuestion>, onCreated: (FlashcardDeck) -> Unit = {}) = viewModelScope.launch {
        if (questions.isEmpty()) return@launch
        val now = System.currentTimeMillis()
        val deck = FlashcardDeck(
            name = "${quiz.title} — Mistakes",
            courseCode = quiz.courseCode,
            topicId = quiz.topicId,
            description = "Created from ${questions.size} missed quiz question${if (questions.size == 1) "" else "s"}.",
            createdAt = now,
            updatedAt = now
        )
        val cards = questions.map { q ->
            Flashcard(
                deckId = deck.id,
                front = q.prompt,
                back = q.correctAnswer,
                hint = q.explanation,
                tags = q.tags,
                starred = true,
                imageUri = q.imageUri,
                createdAt = now,
                updatedAt = now
            )
        }
        db.flashcardDao().importDeck(deck, cards)
        onCreated(deck)
    }

    // ---- Study System 3.1 ----
    fun upsertStudyTopic(item: StudyTopic) = viewModelScope.launch { db.studyMaterialDao().upsertTopic(item.copy(updatedAt = System.currentTimeMillis())) }
    fun deleteStudyTopic(item: StudyTopic) = viewModelScope.launch {
        db.withTransaction {
            db.studyMaterialDao().clearChildParentReferences(item.id, System.currentTimeMillis())
            db.flashcardDao().clearTopicAssociation(item.id)
            db.quizDao().clearTopicAssociation(item.id)
            db.studyMaterialDao().deleteReviewProgressForTopic(item.id)
            db.studyMaterialDao().deleteTopic(item)
        }
    }
    fun upsertStudyNote(item: StudyNote) = viewModelScope.launch { db.studyMaterialDao().upsertNote(item.copy(updatedAt = System.currentTimeMillis())) }
    fun deleteStudyNote(item: StudyNote) = viewModelScope.launch { db.studyMaterialDao().deleteNote(item) }
    fun upsertFormula(item: FormulaReference) = viewModelScope.launch { db.studyMaterialDao().upsertFormula(item.copy(updatedAt = System.currentTimeMillis())) }
    fun deleteFormula(item: FormulaReference) = viewModelScope.launch { db.studyMaterialDao().deleteFormula(item) }
    fun upsertStudyGoal(item: StudyGoal) = viewModelScope.launch { db.studyMaterialDao().upsertGoal(item.copy(updatedAt = System.currentTimeMillis())) }
    fun deleteStudyGoal(item: StudyGoal) = viewModelScope.launch { db.studyMaterialDao().deleteGoal(item) }

    /** Advances active goals from real study activity. Global goals accept any
     * course; course/topic goals only accept matching work. SCORE stores the
     * best percentage instead of adding percentages together. */
    private suspend fun advanceStudyGoals(
        goalType: String,
        amount: Int,
        courseCode: String? = null,
        topicTags: Collection<String> = emptyList(),
        useMaximum: Boolean = false
    ) {
        if (amount <= 0) return
        val now = System.currentTimeMillis()
        val normalizedTags = topicTags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        db.studyMaterialDao().getGoals()
            .filter { goal ->
                !goal.completed &&
                    goal.goalType == goalType &&
                    (goal.courseCode.isNullOrBlank() || goal.courseCode.equals(courseCode, true)) &&
                    (goal.topicTag.isNullOrBlank() || goal.topicTag.trim().lowercase() in normalizedTags)
            }
            .forEach { goal ->
                val progress = if (useMaximum) maxOf(goal.progressValue, amount)
                else goal.progressValue + amount
                db.studyMaterialDao().upsertGoal(
                    goal.copy(
                        progressValue = progress.coerceAtMost(goal.targetValue),
                        completed = progress >= goal.targetValue,
                        updatedAt = now
                    )
                )
            }
    }
    fun upsertStudyPlanItem(item: StudyPlanItem) = viewModelScope.launch { db.studyMaterialDao().upsertPlanItem(item.copy(updatedAt = System.currentTimeMillis())) }
    fun deleteStudyPlanItem(item: StudyPlanItem) = viewModelScope.launch { db.studyMaterialDao().deletePlanItem(item) }
    fun setReviewCompleted(courseCode: String, topicId: String?, completed: Boolean) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        db.studyMaterialDao().upsertReviewProgress(
            StudyReviewProgress(
                id = StudyReviewProgress.key(courseCode, topicId),
                courseCode = courseCode,
                topicId = topicId,
                completed = completed,
                completedAt = if (completed) now else null,
                updatedAt = now
            )
        )
    }
    fun resolveMistake(item: MistakeRecord, resolved: Boolean = true) = viewModelScope.launch { db.studyMaterialDao().upsertMistake(item.copy(resolved = resolved)) }
    fun deleteMistake(item: MistakeRecord) = viewModelScope.launch { db.studyMaterialDao().deleteMistake(item.id) }
    fun upsertQuestionBankItem(item: QuestionBankItem) = viewModelScope.launch { db.studyMaterialDao().upsertBankItem(item.copy(updatedAt = System.currentTimeMillis())) }
    fun deleteQuestionBankItem(item: QuestionBankItem) = viewModelScope.launch { db.studyMaterialDao().deleteBankItem(item) }

    /** Imports a complete ChatGPT/Punla study bundle atomically. */
    suspend fun importStudyBundle(bundle: StudyJsonBundle): Result<Unit> = importResult("Study bundle import") {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val effectiveCourse = bundle.courseCode?.takeIf { it.isNotBlank() } ?: "General"
            // JSON topic keys are case-insensitive by contract. Normalize once
            // so `Photosynthesis` and a parent/topic reference `photosynthesis`
            // still link to the same imported node.
            val topicIds = bundle.topics.associate {
                it.key.lowercase() to java.util.UUID.randomUUID().toString()
            }
            fun topicId(key: String?): String? = key?.lowercase()?.let(topicIds::get)
            val topicRows = bundle.topics.map { t ->
                StudyTopic(
                    id = topicIds.getValue(t.key.lowercase()),
                    courseCode = effectiveCourse,
                    name = t.name,
                    parentTopicId = topicId(t.parentKey),
                    examDate = t.examDate,
                    priority = t.priority,
                    sortOrder = t.sortOrder,
                    createdAt = now, updatedAt = now
                )
            }
            if (topicRows.isNotEmpty()) db.studyMaterialDao().upsertTopics(topicRows)
            val notes = bundle.notes.map { n ->
                StudyNote(courseCode = effectiveCourse, topicId = topicId(n.topicKey), title = n.title, body = n.body, tags = n.tags, createdAt = now, updatedAt = now)
            }
            if (notes.isNotEmpty()) db.studyMaterialDao().upsertNotes(notes)
            val formulas = bundle.formulas.map { f ->
                FormulaReference(courseCode = effectiveCourse, topicId = topicId(f.topicKey), title = f.title, expression = f.expression, variables = f.variables, units = f.units, workedExample = f.workedExample, createdAt = now, updatedAt = now)
            }
            if (formulas.isNotEmpty()) db.studyMaterialDao().upsertFormulas(formulas)
            bundle.decks.forEach { d ->
                val deck = FlashcardDeck(name = d.name, courseCode = effectiveCourse, topicId = topicId(d.topicKey), description = d.description ?: bundle.description, createdAt = now, updatedAt = now)
                val cards = d.cards.map { c ->
                    Flashcard(
                        deckId = deck.id,
                        front = c.front,
                        back = c.back,
                        hint = c.hint,
                        tags = c.tags,
                        starred = c.starred,
                        reverseEnabled = c.reverseEnabled,
                        cardType = c.cardType,
                        imageUri = c.imageUri,
                        occlusionJson = c.occlusionJson,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                db.flashcardDao().importDeck(deck, cards)
            }
            bundle.quizzes.forEach { q ->
                val quiz = Quiz(title = q.title, courseCode = effectiveCourse, topicId = topicId(q.topicKey), description = q.description ?: bundle.description, passingScore = q.passingScore, createdAt = now, updatedAt = now)
                val qs = q.questions.map { x ->
                    QuizQuestion(
                        quizId = quiz.id,
                        type = x.type,
                        prompt = x.prompt,
                        optionsJson = QuizQuestion.encodeOptions(x.options),
                        correctAnswer = x.correctAnswer,
                        explanation = x.explanation,
                        tags = x.tags,
                        metadataJson = x.metadataJson,
                        imageUri = x.imageUri,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                db.quizDao().upsertQuiz(quiz); db.quizDao().upsertQuestions(qs)
            }
            db.jsonImportDao().upsert(JsonImportRecord(bundle.fileId, bundle.contentId, destinationId = effectiveCourse))
        }
    }

    /** Converts explicit reviewer lines into cards. Supported lines: Question :: Answer, plus {{cloze}} lines. */
    fun createFlashcardsFromNote(note: StudyNote, onCreated: (FlashcardDeck?) -> Unit = {}) = viewModelScope.launch {
        val parsed = note.body.lines().mapNotNull { raw ->
            val line = raw.trim().removePrefix("- ").removePrefix("• ")
            when {
                line.contains("::") -> {
                    val parts = line.split("::", limit = 2).map { it.trim() }
                    if (parts.size == 2 && parts.all { it.isNotBlank() }) Triple(parts[0], parts[1], FlashcardTypes.BASIC) else null
                }
                ClozeText.hasCloze(line) -> Triple(line, ClozeText.answers(line).joinToString(" / "), FlashcardTypes.CLOZE)
                else -> null
            }
        }.distinctBy { it.first.trim().lowercase() to it.second.trim().lowercase() }
        if (parsed.isEmpty()) { onCreated(null); return@launch }
        val now = System.currentTimeMillis()
        val deck = FlashcardDeck(
            name = "${note.title} — Reviewer",
            courseCode = note.courseCode,
            topicId = note.topicId,
            description = "Created from Punla reviewer note '${note.title}'.",
            createdAt = now,
            updatedAt = now
        )
        val cards = parsed.map { (front, back, type) ->
            Flashcard(deckId = deck.id, front = front, back = back, cardType = type, tags = note.tags, createdAt = now, updatedAt = now)
        }
        db.flashcardDao().importDeck(deck, cards)
        onCreated(deck)
    }

    /** Creates identification questions from the same explicit reviewer pairs/cloze lines. */
    fun createQuizFromNote(note: StudyNote, onCreated: (Quiz?) -> Unit = {}) = viewModelScope.launch {
        val parsed = note.body.lines().mapNotNull { raw ->
            val line = raw.trim().removePrefix("- ").removePrefix("• ")
            when {
                line.contains("::") -> line.split("::", limit = 2).map { it.trim() }.takeIf { it.size == 2 && it.all { part -> part.isNotBlank() } }?.let { it[0] to it[1] }
                ClozeText.hasCloze(line) -> ClozeText.question(line) to ClozeText.answers(line).joinToString(" / ")
                else -> null
            }
        }.distinctBy { it.first.trim().lowercase() to it.second.trim().lowercase() }
        if (parsed.isEmpty()) { onCreated(null); return@launch }
        val now = System.currentTimeMillis()
        val quiz = Quiz(
            title = "${note.title} — Recall Quiz",
            courseCode = note.courseCode,
            topicId = note.topicId,
            description = "Generated from Punla reviewer note '${note.title}'.",
            passingScore = 70,
            shuffleQuestions = true,
            shuffleChoices = false,
            createdAt = now,
            updatedAt = now
        )
        val questions = parsed.map { (prompt, answer) ->
            QuizQuestion(quizId = quiz.id, type = QuizQuestionTypes.IDENTIFICATION, prompt = prompt, correctAnswer = answer, tags = note.tags, createdAt = now, updatedAt = now)
        }
        db.withTransaction { db.quizDao().upsertQuiz(quiz); db.quizDao().upsertQuestions(questions) }
        onCreated(quiz)
    }

    /** Copies every quiz question into the reusable question bank without changing the quiz. */
    fun addQuizToQuestionBank(quiz: Quiz, questions: List<QuizQuestion>) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        db.studyMaterialDao().upsertBankItems(questions.map { q ->
            QuestionBankItem(
                id = "quiz:${q.id}", courseCode = quiz.courseCode, type = q.type, prompt = q.prompt, optionsJson = q.optionsJson,
                correctAnswer = q.correctAnswer, explanation = q.explanation, tags = q.tags, metadataJson = q.metadataJson,
                imageUri = q.imageUri, createdAt = now, updatedAt = now
            )
        })
    }

    /** Atomically refreshes the reusable bank from every current quiz. */
    fun syncQuizzesToQuestionBank(onComplete: (Int) -> Unit = {}) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val quizById = quizzes.value.associateBy { it.id }
        val existingById = questionBank.value.associateBy { it.id }
        val items = quizQuestions.value.mapNotNull { q ->
            val quiz = quizById[q.quizId] ?: return@mapNotNull null
            val id = "quiz:${q.id}"
            QuestionBankItem(
                id = id,
                courseCode = quiz.courseCode,
                type = q.type,
                prompt = q.prompt,
                optionsJson = q.optionsJson,
                correctAnswer = q.correctAnswer,
                explanation = q.explanation,
                tags = q.tags,
                metadataJson = q.metadataJson,
                imageUri = q.imageUri,
                createdAt = existingById[id]?.createdAt ?: now,
                updatedAt = now
            )
        }
        db.studyMaterialDao().upsertBankItems(items)
        onComplete(items.size)
    }

    /** Generates an interleaved practice test from the question bank and existing quiz questions. */
    fun createPracticeTest(
        courseCode: String?,
        count: Int = 20,
        recallOnly: Boolean = false,
        onCreated: (Quiz) -> Unit = {}
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val bank = questionBank.value.filter {
            courseCode.isNullOrBlank() || it.courseCode.equals(courseCode, true)
        }
        val fromQuizzes = quizQuestions.value.mapNotNull { q ->
            val sourceQuiz = quizzes.value.firstOrNull { it.id == q.quizId } ?: return@mapNotNull null
            if (!courseCode.isNullOrBlank() && !sourceQuiz.courseCode.equals(courseCode, true)) return@mapNotNull null
            QuestionBankItem(
                id = "live:${q.id}",
                courseCode = sourceQuiz.courseCode,
                type = q.type,
                prompt = q.prompt,
                optionsJson = q.optionsJson,
                correctAnswer = q.correctAnswer,
                explanation = q.explanation,
                tags = q.tags,
                metadataJson = q.metadataJson,
                imageUri = q.imageUri
            )
        }

        // Current quiz rows win over a potentially stale bank snapshot.
        var sourcePool = (fromQuizzes + bank).distinctBy {
            it.prompt.trim().lowercase() to it.correctAnswer.trim().lowercase()
        }

        // Recall mode must have a scalar answer. Multi-select/order/matching
        // store JSON structures, which would otherwise create impossible
        // identification questions that expect raw JSON text.
        if (recallOnly) {
            sourcePool = sourcePool.filter {
                it.type in setOf(
                    QuizQuestionTypes.MULTIPLE_CHOICE,
                    QuizQuestionTypes.TRUE_FALSE,
                    QuizQuestionTypes.IDENTIFICATION,
                    QuizQuestionTypes.NUMERIC,
                    QuizQuestionTypes.IMAGE_IDENTIFICATION
                )
            }
        }

        val pool = sourcePool.shuffled().take(count.coerceIn(5, 100))
        if (pool.isEmpty()) return@launch

        val quiz = Quiz(
            title = if (recallOnly) "Recall Practice" else "Practice Test",
            courseCode = courseCode,
            description = "Generated from Punla's question bank.",
            passingScore = 70,
            shuffleQuestions = true,
            shuffleChoices = !recallOnly,
            createdAt = now,
            updatedAt = now
        )
        val qs = pool.map { item ->
            val recallType = when {
                !recallOnly -> item.type
                item.type == QuizQuestionTypes.NUMERIC -> QuizQuestionTypes.NUMERIC
                item.type == QuizQuestionTypes.IMAGE_IDENTIFICATION -> QuizQuestionTypes.IMAGE_IDENTIFICATION
                else -> QuizQuestionTypes.IDENTIFICATION
            }
            QuizQuestion(
                quizId = quiz.id,
                type = recallType,
                prompt = item.prompt,
                optionsJson = if (recallOnly) "[]" else item.optionsJson,
                correctAnswer = item.correctAnswer,
                explanation = item.explanation,
                tags = item.tags,
                metadataJson = item.metadataJson,
                imageUri = item.imageUri,
                createdAt = now,
                updatedAt = now
            )
        }
        db.withTransaction {
            db.quizDao().upsertQuiz(quiz)
            db.quizDao().upsertQuestions(qs)
        }
        onCreated(quiz)
    }

    /** Builds/rebuilds a daily exam plan from a course target and its known tags/topics. */
    fun generateExamPlan(courseCode: String, examDate: java.time.LocalDate, minutesPerDay: Int = 50) = viewModelScope.launch {
        val topicNames = buildList {
            addAll(studyTopics.value.filter { it.courseCode.equals(courseCode, true) }.map { it.name })
            addAll(flashcards.value.filter { c -> flashcardDecks.value.firstOrNull { it.id == c.deckId }?.courseCode.equals(courseCode, true) }.flatMap { it.tagList() })
            addAll(quizQuestions.value.filter { q -> quizzes.value.firstOrNull { it.id == q.quizId }?.courseCode.equals(courseCode, true) }.flatMap { it.tagList() })
        }.distinct()
        db.withTransaction {
            db.studyMaterialDao().clearGeneratedExamPlan(courseCode)
            db.studyMaterialDao().upsertPlanItems(StudyEngine.generateExamPlan(courseCode, examDate, topicNames, minutesPerDay))
        }
    }

    /** Creates an identification quiz from the selected flashcard deck. */
    fun createQuizFromFlashcards(deck: FlashcardDeck, cards: List<Flashcard>, onCreated: (Quiz) -> Unit = {}) = viewModelScope.launch {
        val usable = cards.filter { it.front.isNotBlank() && it.back.isNotBlank() }
        if (usable.isEmpty()) return@launch
        val now = System.currentTimeMillis()
        val quiz = Quiz(
            title = "${deck.name} Quiz",
            courseCode = deck.courseCode,
            topicId = deck.topicId,
            description = "Generated from ${usable.size} Punla flashcards.",
            createdAt = now,
            updatedAt = now
        )
        val questions = usable.map { card ->
            val isCloze = card.cardType == FlashcardTypes.CLOZE && ClozeText.hasCloze(card.front)
            val prompt = if (isCloze) ClozeText.question(card.front) else card.front
            val answer = if (isCloze) ClozeText.answers(card.front).joinToString(" / ").ifBlank { card.back } else card.back
            QuizQuestion(
                quizId = quiz.id,
                type = if (!card.imageUri.isNullOrBlank()) QuizQuestionTypes.IMAGE_IDENTIFICATION else QuizQuestionTypes.IDENTIFICATION,
                prompt = prompt,
                correctAnswer = answer,
                explanation = if (isCloze) listOf(ClozeText.revealed(card.front), card.back).filter { it.isNotBlank() }.joinToString("\n\n") else card.hint,
                tags = card.tags,
                imageUri = card.imageUri,
                createdAt = now,
                updatedAt = now
            )
        }
        db.withTransaction {
            db.quizDao().upsertQuiz(quiz)
            db.quizDao().upsertQuestions(questions)
        }
        onCreated(quiz)
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
        ClassDayNotificationScheduler.refresh(getApplication())
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
            categoryBudgetLimits = repo.categoryBudgetLimits
            chedTarget = repo.chedTarget
            userName = repo.userName
            notificationsEnabled = repo.notificationsEnabled
            classDayNotificationEnabled = repo.classDayNotificationEnabled
            morningAgendaEnabled = repo.morningAgendaEnabled
            quietHoursEnabled = repo.quietHoursEnabled
            studyRemindersEnabled = repo.studyRemindersEnabled
            dailyStudyGoalMinutes = repo.dailyStudyGoalMinutes
            weeklyStudyGoalMinutes = repo.weeklyStudyGoalMinutes
            dailyStudyGoalFlow.value = repo.dailyStudyGoalMinutes
            budgetPeriod = repo.budgetPeriod
            weekStartDay = repo.weekStartDay
            weeklyBudgetOverride = repo.weeklyBudgetOverride
            weeklyRolloverEnabled = repo.weeklyRolloverEnabled
            termStartDate = repo.termStartDate
            termEndDate = repo.termEndDate
            cloudAssistantEnabled = repo.cloudAssistantEnabled
            assistantModel = repo.assistantModel
            assistantApiKeyConfigured = !repo.assistantApiKey.isNullOrBlank()
            preferredReminderHour = repo.preferredReminderHour
            dismissedExpensePatternKeys = repo.dismissedExpensePatternKeys
            WidgetRefresher.refreshAll(getApplication())
            if (repo.notificationsEnabled && repo.classDayNotificationEnabled) {
                ClassDayNotificationScheduler.ensureScheduled(getApplication())
            } else {
                ClassDayNotificationScheduler.cancel(getApplication())
            }
            com.uplb.punla.worker.ReminderScheduler.scheduleDaily(getApplication(), updateExisting = true)
            backupResult = BackupResult.Success("Backup restored.")
        }.onFailure { e ->
            backupResult = BackupResult.Failure(e.message ?: "Couldn't restore that backup.")
        }
    }

    // ---- Pomodoro timer ----
    val studySessions: StateFlow<List<StudySession>> = repo.observeStudySessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val studySuggestionEvents: StateFlow<List<StudySuggestionEvent>> = repo.observeStudySuggestionEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notificationEvents = repo.observeNotificationEvents()
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

    private val shownSuggestionIds = mutableSetOf<String>()

    private fun suggestionFeatures(suggestion: StudySuggestion): StudySlotFeatures {
        val recent = studySessions.value.take(20)
        val completionRate = if (recent.isEmpty()) 0.5f else recent.count { it.completed }.toFloat() / recent.size
        return StudySlotFeatures(
            hour = suggestion.slotStart.substringBefore(':').toIntOrNull() ?: 12,
            dayOfWeek = suggestion.date.dayOfWeek.value,
            urgencyDays = suggestion.urgencyDays,
            availableMinutes = suggestion.availableMinutes,
            plannedMinutes = repo.pomodoroWorkMinutes,
            recentCompletionRate = completionRate,
            currentStreak = currentStudyStreak.value
        )
    }

    fun recordStudySuggestionShown(suggestion: StudySuggestion) {
        if (!shownSuggestionIds.add(suggestion.id)) return
        viewModelScope.launch {
            repo.logStudySuggestionEvent(
                StudySuggestionEvent(
                    suggestionId = suggestion.id,
                    outcome = "SHOWN",
                    slotHour = suggestion.slotStart.substringBefore(':').toIntOrNull() ?: 12,
                    dayOfWeek = suggestion.date.dayOfWeek.value,
                    urgencyDays = suggestion.urgencyDays,
                    availableMinutes = suggestion.availableMinutes,
                    deadlineId = suggestion.deadline.id,
                    courseCode = suggestion.course
                )
            )
        }
    }

    /** Dismisses today's Dashboard study-slot suggestion and learns a local negative outcome. */
    fun dismissStudySuggestion(suggestion: StudySuggestion? = null) {
        val now = System.currentTimeMillis()
        repo.lastStudySuggestionDismissedAt = now
        studySuggestionDismissedAt = now
        if (suggestion != null) {
            repo.studySlotModelState = StudySlotPredictor.update(repo.studySlotModelState, suggestionFeatures(suggestion), used = false)
            viewModelScope.launch {
                repo.logStudySuggestionEvent(
                    StudySuggestionEvent(
                        suggestionId = suggestion.id,
                        outcome = "DISMISSED",
                        slotHour = suggestion.slotStart.substringBefore(':').toIntOrNull() ?: 12,
                        dayOfWeek = suggestion.date.dayOfWeek.value,
                        urgencyDays = suggestion.urgencyDays,
                        availableMinutes = suggestion.availableMinutes,
                        deadlineId = suggestion.deadline.id,
                        courseCode = suggestion.course
                    )
                )
            }
        }
    }

    fun acceptStudySuggestion(suggestion: StudySuggestion) {
        repo.pendingStudySuggestionId = suggestion.id
        val features = suggestionFeatures(suggestion)
        val expiresAt = runCatching {
            java.time.LocalDateTime.of(suggestion.date, java.time.LocalTime.parse(suggestion.slotEnd))
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis() + java.util.concurrent.TimeUnit.HOURS.toMillis(4))
        // Persist the feature snapshot until the timer actually starts. Merely
        // opening the Pomodoro screen is not yet a positive training label.
        repo.pendingStudySuggestionFeatures = listOf(
            features.hour,
            features.dayOfWeek,
            features.urgencyDays,
            features.availableMinutes,
            features.plannedMinutes,
            features.recentCompletionRate,
            features.currentStreak,
            expiresAt
        ).joinToString("|")
    }

    private fun activatePendingStudySuggestion() {
        val suggestionId = repo.pendingStudySuggestionId ?: return
        val parts = repo.pendingStudySuggestionFeatures?.split('|')
        if (parts == null || parts.size != 8) {
            repo.pendingStudySuggestionId = null
            repo.pendingStudySuggestionFeatures = null
            return
        }
        val expiresAt = parts[7].toLongOrNull() ?: 0L
        if (System.currentTimeMillis() > expiresAt + java.util.concurrent.TimeUnit.HOURS.toMillis(1)) {
            repo.pendingStudySuggestionId = null
            repo.pendingStudySuggestionFeatures = null
            return
        }
        val features = StudySlotFeatures(
            hour = parts[0].toIntOrNull() ?: 12,
            dayOfWeek = parts[1].toIntOrNull() ?: 1,
            urgencyDays = parts[2].toIntOrNull() ?: 7,
            availableMinutes = parts[3].toIntOrNull() ?: repo.pomodoroWorkMinutes,
            plannedMinutes = parts[4].toIntOrNull() ?: repo.pomodoroWorkMinutes,
            recentCompletionRate = parts[5].toFloatOrNull() ?: 0.5f,
            currentStreak = parts[6].toIntOrNull() ?: 0
        )
        repo.studySlotModelState = StudySlotPredictor.update(repo.studySlotModelState, features, used = true)
        // Clearing only the feature snapshot makes this idempotent while the
        // suggestion id remains available to link the eventual session row.
        repo.pendingStudySuggestionFeatures = null
        viewModelScope.launch {
            repo.latestStudySuggestionEvent(suggestionId)?.let { source ->
                repo.logStudySuggestionEvent(
                    source.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        occurredAt = System.currentTimeMillis(),
                        outcome = "STARTED",
                        sessionId = null
                    )
                )
            }
        }
    }

    fun dismissExpensePattern(key: String) {
        dismissedExpensePatternKeys = dismissedExpensePatternKeys + key
        repo.dismissedExpensePatternKeys = dismissedExpensePatternKeys
    }

    fun createRecurringRuleFromPattern(pattern: com.uplb.punla.ml.ExpensePattern) = viewModelScope.launch {
        val latest = expenses.value.filter { it.id in pattern.expenseIds }.maxByOrNull { it.date } ?: return@launch
        val rule = ExpenseRule(
            amount = pattern.typicalAmount,
            category = pattern.category,
            note = latest.note,
            startDate = latest.date,
            repeat = if ((pattern.cadenceDays ?: 30) <= 10) "weekly" else "monthly",
            lastGenerated = latest.date,
            isFixed = latest.isFixed
        )
        db.expenseDao().upsertRule(rule)
        db.expenseDao().upsert(latest.copy(ruleId = rule.id, isRecurring = true))
        dismissExpensePattern(pattern.key)
        WidgetRefresher.refreshAll(getApplication())
    }

    fun resetLearnedRecommendations() = viewModelScope.launch {
        repo.clearStudySuggestionEvents()
        repo.studySlotModelState = com.uplb.punla.ml.StudySlotModelState()
        repo.pendingStudySuggestionId = null
        repo.pendingStudySuggestionFeatures = null
        repo.dismissedExpensePatternKeys = emptySet()
        repo.lastStudySuggestionDismissedAt = null
        dismissedExpensePatternKeys = emptySet()
        studySuggestionDismissedAt = null
        shownSuggestionIds.clear()
    }

    fun useLearnedReminderHour(hour: Int) {
        repo.preferredReminderHour = hour
        preferredReminderHour = hour
        com.uplb.punla.worker.ReminderScheduler.scheduleDaily(getApplication(), updateExisting = true)
    }

    fun resetNotificationLearning() = viewModelScope.launch {
        repo.clearNotificationEvents()
        repo.preferredReminderHour = null
        preferredReminderHour = null
        com.uplb.punla.worker.ReminderScheduler.scheduleDaily(getApplication(), updateExisting = true)
    }

    suspend fun askCloudAssistant(query: String): AssistantApiResult {
        if (!repo.cloudAssistantEnabled) return AssistantApiResult.Failure("Cloud assistant is disabled in Settings.")
        val key = repo.assistantApiKey ?: return AssistantApiResult.Failure("Add your API key in Settings first.")
        if (!repo.consumeAssistantCall()) {
            return AssistantApiResult.Failure("Today's 10-call cloud limit has been reached. Local commands still work.")
        }
        val snapshot = AssistantSnapshot(classes.value, deadlines.value, expenses.value, studySessions.value, repo)
        return AssistantApi.ask(key, repo.assistantModel, query, LocalAssistant.compactCloudContext(snapshot, query))
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
    var pomodoroPictureInPicture by mutableStateOf(repo.pomodoroPictureInPicture)
        private set
    var pomodoroTimerNotification by mutableStateOf(repo.pomodoroTimerNotification)
        private set
    var pomodoroAlarmSoundEnabled by mutableStateOf(repo.pomodoroAlarmSoundEnabled)
        private set
    var pomodoroAlarmVibrationEnabled by mutableStateOf(repo.pomodoroAlarmVibrationEnabled)
        private set
    var pomodoroWorkSoundUri by mutableStateOf(repo.pomodoroWorkSoundUri)
        private set
    var pomodoroBreakSoundUri by mutableStateOf(repo.pomodoroBreakSoundUri)
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

    fun updatePomodoroPictureInPicture(enabled: Boolean) {
        repo.pomodoroPictureInPicture = enabled
        pomodoroPictureInPicture = enabled
    }

    fun updatePomodoroTimerNotification(enabled: Boolean) {
        repo.pomodoroTimerNotification = enabled
        pomodoroTimerNotification = enabled
        if (enabled && pomodoroState.isRunning) {
            PomodoroRunningNotification.showFromRepository(getApplication<Application>())
        } else if (!enabled) {
            PomodoroRunningNotification.cancel(getApplication<Application>())
        }
    }

    fun updatePomodoroAlarmSoundEnabled(enabled: Boolean) {
        repo.pomodoroAlarmSoundEnabled = enabled
        pomodoroAlarmSoundEnabled = enabled
    }

    fun updatePomodoroAlarmVibrationEnabled(enabled: Boolean) {
        repo.pomodoroAlarmVibrationEnabled = enabled
        pomodoroAlarmVibrationEnabled = enabled
    }

    fun updatePomodoroWorkSoundUri(uri: String?) {
        repo.pomodoroWorkSoundUri = uri
        pomodoroWorkSoundUri = uri
    }

    fun updatePomodoroBreakSoundUri(uri: String?) {
        repo.pomodoroBreakSoundUri = uri
        pomodoroBreakSoundUri = uri
    }

    var pomodoroState by mutableStateOf(com.uplb.punla.ui.pomodoro.PomodoroUiState())
        private set

    private var pomodoroJob: kotlinx.coroutines.Job? = null
    private var phaseDeadline: Long = 0L   // System.currentTimeMillis() target
    private var phaseStartedAt: Long = 0L  // for actualSeconds on early stop

    init {
        restorePomodoroRuntime()
    }

    /**
     * Reconciles the in-memory countdown with its persisted wall-clock
     * deadline. MainActivity calls this on resume, which covers app switches,
     * the notification shade, and process recreation without restarting the
     * timer from its original duration.
     */
    fun syncPomodoroClock() {
        // A background AlarmManager receiver may have advanced the persisted
        // phase while this Activity was paused or in PiP. Reload before doing
        // any arithmetic against stale in-memory values.
        val storedPhase = repo.pomodoroRuntimePhase
        if (storedPhase != pomodoroState.phase.name ||
            repo.pomodoroRuntimeRunning != pomodoroState.isRunning ||
            (repo.pomodoroRuntimeRunning && repo.pomodoroRuntimeDeadline != phaseDeadline)
        ) {
            restorePomodoroRuntime()
            return
        }
        if (!pomodoroState.isRunning) return
        val remainingMs = phaseDeadline - System.currentTimeMillis()
        if (remainingMs <= 0L) {
            pomodoroJob?.cancel()
            onPhaseComplete()
            return
        }
        val remainingSeconds = ((remainingMs + 999L) / 1000L).toInt()
        if (remainingSeconds != pomodoroState.remainingSeconds) {
            pomodoroState = pomodoroState.copy(remainingSeconds = remainingSeconds)
        }
        // Replacing the same PendingIntent is cheap and upgrades an inexact
        // fallback immediately if the user just granted exact-alarm access.
        PomodoroAlarmScheduler.schedule(getApplication<Application>(), phaseDeadline)
        PomodoroRunningNotification.showFromRepository(getApplication<Application>())
        if (pomodoroJob?.isActive != true) tickPomodoro()
    }

    private fun restorePomodoroRuntime() {
        pomodoroJob?.cancel()
        val storedPhase = repo.pomodoroRuntimePhase
        if (storedPhase == null) {
            phaseDeadline = 0L
            phaseStartedAt = 0L
            pomodoroState = com.uplb.punla.ui.pomodoro.PomodoroUiState()
            return
        }
        val phase = runCatching {
            com.uplb.punla.ui.pomodoro.PomodoroPhase.valueOf(storedPhase)
        }.getOrNull() ?: run {
            repo.clearPomodoroRuntime()
            pomodoroState = com.uplb.punla.ui.pomodoro.PomodoroUiState()
            return
        }
        if (phase == com.uplb.punla.ui.pomodoro.PomodoroPhase.IDLE) {
            repo.clearPomodoroRuntime()
            pomodoroState = com.uplb.punla.ui.pomodoro.PomodoroUiState()
            return
        }

        phaseDeadline = repo.pomodoroRuntimeDeadline
        phaseStartedAt = repo.pomodoroRuntimeStartedAt
        val running = repo.pomodoroRuntimeRunning
        val remaining = if (running) {
            (((phaseDeadline - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L).toInt()
        } else {
            repo.pomodoroRuntimeRemainingSeconds.coerceAtLeast(0)
        }

        pomodoroState = com.uplb.punla.ui.pomodoro.PomodoroUiState(
            phase = phase,
            remainingSeconds = remaining,
            totalSecondsForPhase = repo.pomodoroRuntimeTotalSeconds.coerceAtLeast(0),
            isRunning = running,
            cycleCount = repo.pomodoroRuntimeCycleCount.coerceAtLeast(0),
            courseCode = repo.pomodoroRuntimeCourseCode
        )

        if (running) {
            if (remaining <= 0) {
                onPhaseComplete()
            } else {
                PomodoroAlarmScheduler.schedule(getApplication<Application>(), phaseDeadline)
                PomodoroRunningNotification.showFromRepository(getApplication<Application>())
                tickPomodoro()
            }
        }
    }

    private fun persistPomodoroRuntime() {
        if (pomodoroState.phase == com.uplb.punla.ui.pomodoro.PomodoroPhase.IDLE) {
            repo.clearPomodoroRuntime()
            return
        }
        repo.savePomodoroRuntime(
            phase = pomodoroState.phase.name,
            deadline = phaseDeadline,
            startedAt = phaseStartedAt,
            remainingSeconds = pomodoroState.remainingSeconds,
            totalSeconds = pomodoroState.totalSecondsForPhase,
            running = pomodoroState.isRunning,
            cycleCount = pomodoroState.cycleCount,
            courseCode = pomodoroState.courseCode
        )
    }

    fun startPomodoroWork(courseCode: String?) {
        activatePendingStudySuggestion()
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
        persistPomodoroRuntime()
        PomodoroAlarmScheduler.schedule(getApplication<Application>(), phaseDeadline)
        PomodoroRunningNotification.showFromRepository(getApplication<Application>())
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
                pomodoroState = pomodoroState.copy(remainingSeconds = ((remainingMs + 999L) / 1000L).toInt())
                kotlinx.coroutines.delay(250) // sub-second poll, cheap, keeps UI smooth
            }
        }
    }

    fun pausePomodoro() {
        pomodoroJob?.cancel()
        PomodoroAlarmScheduler.cancel(getApplication<Application>())
        PomodoroRunningNotification.cancel(getApplication<Application>())
        if (pomodoroState.isRunning) {
            val remainingMs = phaseDeadline - System.currentTimeMillis()
            val remainingSeconds = (((remainingMs.coerceAtLeast(0L)) + 999L) / 1000L).toInt()
            pomodoroState = pomodoroState.copy(isRunning = false, remainingSeconds = remainingSeconds)
        }
        persistPomodoroRuntime()
    }

    fun resumePomodoro() {
        if (pomodoroState.phase == com.uplb.punla.ui.pomodoro.PomodoroPhase.IDLE || pomodoroState.remainingSeconds <= 0) return
        phaseDeadline = System.currentTimeMillis() + pomodoroState.remainingSeconds * 1000L
        pomodoroState = pomodoroState.copy(isRunning = true)
        persistPomodoroRuntime()
        PomodoroAlarmScheduler.schedule(getApplication<Application>(), phaseDeadline)
        PomodoroRunningNotification.showFromRepository(getApplication<Application>())
        tickPomodoro()
    }

    /** Stops early. Logs a StudySession with completed=false if the phase was
     * WORK and at least 60s of real elapsed time had passed (skip logging
     * accidental taps under a minute — not a meaningful session). */
    fun stopPomodoro() {
        pomodoroJob?.cancel()
        PomodoroAlarmScheduler.cancel(getApplication<Application>())
        PomodoroRunningNotification.cancel(getApplication<Application>())
        if (
            pomodoroState.phase == com.uplb.punla.ui.pomodoro.PomodoroPhase.WORK &&
            pomodoroState.totalSecondsForPhase > 0 &&
            phaseStartedAt > 0L
        ) {
            val actual = ((System.currentTimeMillis() - phaseStartedAt) / 1000).toInt()
            if (actual >= 60) {
                logCompletedOrStoppedWork(actual, completed = false)
            } else {
                // Do not let an accidental sub-minute start attach the old
                // suggestion to a later, unrelated focus session.
                clearPendingSuggestionAsStopped()
            }
        }
        pomodoroState = com.uplb.punla.ui.pomodoro.PomodoroUiState()
        repo.clearPomodoroRuntime()
    }

    private fun clearPendingSuggestionAsStopped() {
        val suggestionId = repo.pendingStudySuggestionId ?: return
        repo.pendingStudySuggestionId = null
        repo.pendingStudySuggestionFeatures = null
        viewModelScope.launch {
            repo.latestStudySuggestionEvent(suggestionId)?.let { source ->
                repo.logStudySuggestionEvent(
                    source.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        occurredAt = System.currentTimeMillis(),
                        outcome = "STOPPED",
                        sessionId = null
                    )
                )
            }
        }
    }

    private fun onPhaseComplete() {
        val expectedDeadline = phaseDeadline
        pomodoroJob?.cancel()
        viewModelScope.launch {
            PomodoroCompletionCoordinator.complete(getApplication<Application>(), expectedDeadline)
            // Whether this call won the race or the AlarmManager receiver did,
            // the repository now contains the authoritative next phase.
            restorePomodoroRuntime()
        }
    }

    private fun logCompletedOrStoppedWork(actualSeconds: Int, completed: Boolean) {
        // Capture the phase values before launching. onPhaseComplete() may
        // immediately stage or auto-start the next phase, which mutates these
        // fields before the coroutine gets a chance to run.
        val courseCode = pomodoroState.courseCode
        val startedAt = phaseStartedAt
        val plannedMinutes = repo.pomodoroWorkMinutes
        val cyclesInSession = pomodoroState.cycleCount + 1
        val suggestionId = repo.pendingStudySuggestionId
        viewModelScope.launch {
            val session = StudySession(
                courseCode = courseCode,
                startedAt = startedAt,
                endedAt = System.currentTimeMillis(),
                plannedMinutes = plannedMinutes,
                actualSeconds = actualSeconds,
                completed = completed,
                cyclesInSession = cyclesInSession,
                endReason = if (completed) "COMPLETED" else "STOPPED_EARLY",
                suggestionId = suggestionId
            )
            repo.logStudySession(session)
            advanceStudyGoals(
                goalType = StudyGoalTypes.MINUTES,
                amount = actualSeconds / 60,
                courseCode = courseCode
            )
            if (suggestionId != null) {
                val source = repo.latestStudySuggestionEvent(suggestionId)
                if (source != null) {
                    repo.logStudySuggestionEvent(
                        source.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            occurredAt = System.currentTimeMillis(),
                            outcome = if (completed) "COMPLETED" else "STOPPED",
                            sessionId = session.id
                        )
                    )
                }
                repo.pendingStudySuggestionId = null
                repo.pendingStudySuggestionFeatures = null
            }
        }
    }


    override fun onCleared() {
        pomodoroJob?.cancel()
        super.onCleared()
    }
}
