package com.uplb.punla.context

import android.content.Context
import com.uplb.punla.data.PunlaDatabase
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.diagnostics.PunlaDiagnostics
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Phase 1 shared student-state layer.
 *
 * The engine only aggregates and normalizes context. It intentionally does not
 * rank tasks or decide what the student should do; that belongs to a later
 * decision layer.
 */
class StudentContextEngine private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val db = PunlaDatabase.get(appContext)
    private val repository = PunlaRepository(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _state = MutableStateFlow(StudentState.empty())
    val state: StateFlow<StudentState> = _state.asStateFlow()

    init {
        scope.launch {
            val schedule = combine(
                db.classSessionDao().observeAll(),
                db.deadlineDao().observeAll(),
                db.attendanceDao().observeAll()
            ) { classes, deadlines, attendance ->
                ScheduleSources(classes, deadlines, attendance)
            }

            val activity = combine(
                db.studySessionDao().observeAll(),
                db.expenseDao().observeAll(),
                db.expenseDao().observeRules()
            ) { studySessions, expenses, expenseRules ->
                ActivitySources(studySessions, expenses, expenseRules)
            }

            val learning = combine(
                db.flashcardDao().observeDecks(),
                db.flashcardDao().observeAllCards(),
                db.quizDao().observeQuizzes(),
                db.quizDao().observeAllAttempts(),
                db.studyMaterialDao().observeMistakes()
            ) { decks, cards, quizzes, quizAttempts, mistakes ->
                LearningSources(decks, cards, quizzes, quizAttempts, mistakes)
            }

            val planning = combine(
                db.studyMaterialDao().observePlanItems(),
                db.studyMaterialDao().observeReviewProgress()
            ) { planItems, reviewProgress ->
                PlanningSources(planItems, reviewProgress)
            }

            val clock = merge(
                minuteTicker(),
                refreshRequests.map { System.currentTimeMillis() }
            )

            combine(schedule, activity, learning, planning, clock) {
                    scheduleSources,
                    activitySources,
                    learningSources,
                    planningSources,
                    now ->
                StudentContextReducer.Inputs(
                    classes = scheduleSources.classes,
                    deadlines = scheduleSources.deadlines,
                    attendance = scheduleSources.attendance,
                    studySessions = activitySources.studySessions,
                    expenses = activitySources.expenses,
                    expenseRules = activitySources.expenseRules,
                    decks = learningSources.decks,
                    cards = learningSources.cards,
                    quizzes = learningSources.quizzes,
                    quizAttempts = learningSources.quizAttempts,
                    mistakes = learningSources.mistakes,
                    planItems = planningSources.planItems,
                    reviewProgress = planningSources.reviewProgress
                ) to now
            }.collect { (inputs, now) ->
                runCatching {
                    StudentContextReducer.reduce(
                        inputs = inputs,
                        repository = repository,
                        nowEpochMillis = now,
                        zoneId = ZoneId.systemDefault()
                    )
                }.onSuccess { snapshot ->
                    _state.value = snapshot
                }.onFailure { error ->
                    PunlaDiagnostics.warn(
                        appContext,
                        "StudentContext",
                        "Could not refresh shared student context",
                        error
                    )
                }
            }
        }
    }

    /**
     * Forces a recompute for preference-only changes that do not emit a Room
     * Flow update (for example budget settings). Database writes refresh
     * automatically and the minute ticker keeps time-derived fields current.
     */
    fun refreshNow() {
        refreshRequests.tryEmit(Unit)
    }

    private fun minuteTicker(): Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }

    private data class ScheduleSources(
        val classes: List<com.uplb.punla.data.entity.ClassSession>,
        val deadlines: List<com.uplb.punla.data.entity.Deadline>,
        val attendance: List<com.uplb.punla.data.entity.AttendanceRecord>
    )

    private data class ActivitySources(
        val studySessions: List<com.uplb.punla.data.entity.StudySession>,
        val expenses: List<com.uplb.punla.data.entity.Expense>,
        val expenseRules: List<com.uplb.punla.data.entity.ExpenseRule>
    )

    private data class LearningSources(
        val decks: List<com.uplb.punla.data.entity.FlashcardDeck>,
        val cards: List<com.uplb.punla.data.entity.Flashcard>,
        val quizzes: List<com.uplb.punla.data.entity.Quiz>,
        val quizAttempts: List<com.uplb.punla.data.entity.QuizAttempt>,
        val mistakes: List<com.uplb.punla.data.entity.MistakeRecord>
    )

    private data class PlanningSources(
        val planItems: List<com.uplb.punla.data.entity.StudyPlanItem>,
        val reviewProgress: List<com.uplb.punla.data.entity.StudyReviewProgress>
    )

    companion object {
        @Volatile private var INSTANCE: StudentContextEngine? = null

        fun get(context: Context): StudentContextEngine =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: StudentContextEngine(context).also { INSTANCE = it }
            }
    }
}
