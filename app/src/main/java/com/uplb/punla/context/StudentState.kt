package com.uplb.punla.context

/**
 * Shared, UI-agnostic snapshot of the student's current situation.
 *
 * Phase 1 deliberately keeps this model independent from Room entities so any
 * Punla screen can consume the same state without knowing how it was derived.
 */
data class StudentState(
    val generatedAtEpochMillis: Long,
    val localDate: String,
    val localTime: String,
    val day: String,
    val currentClass: ClassContext? = null,
    val nextClass: ClassContext? = null,
    val freeMinutesBeforeNextCommitment: Int? = null,
    val travelBufferMinutes: Int? = null,
    val usableFreeMinutes: Int? = freeMinutesBeforeNextCommitment,
    val pendingTasks: List<TaskContext> = emptyList(),
    val upcomingDeadlines: List<DeadlineContext> = emptyList(),
    val overdueWork: List<TaskContext> = emptyList(),
    val energy: EnergyLevel = EnergyLevel.UNKNOWN,
    val study: StudyContext = StudyContext(),
    val attendance: AttendanceContext = AttendanceContext(),
    val budget: BudgetContext = BudgetContext(),
    val courses: List<CourseContext> = emptyList(),
    val location: LocationContext? = null
) {
    companion object {
        fun empty(nowEpochMillis: Long = System.currentTimeMillis()) = StudentState(
            generatedAtEpochMillis = nowEpochMillis,
            localDate = "",
            localTime = "",
            day = ""
        )
    }
}

enum class EnergyLevel { UNKNOWN, DRAINED, OKAY, LOCKED_IN }

data class ClassContext(
    val sessionId: String,
    val code: String,
    val title: String? = null,
    val section: String? = null,
    val type: String,
    val room: String? = null,
    val occurrenceDate: String,
    val startTime: String,
    val endTime: String,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long
)

data class TaskContext(
    val id: String,
    val title: String,
    val courseCode: String? = null,
    val date: String,
    val kind: String,
    val priority: String? = null,
    val overdue: Boolean = false
)

data class DeadlineContext(
    val id: String,
    val title: String,
    val courseCode: String? = null,
    val dueDate: String,
    val type: String,
    val priority: String,
    val daysUntil: Long,
    val overdue: Boolean
)

data class StudyContext(
    val minutesToday: Int = 0,
    val minutesLast7Days: Int = 0,
    val lastStudiedAtEpochMillis: Long? = null,
    val dueFlashcardCount: Int = 0,
    val weakFlashcardCount: Int = 0,
    val unresolvedMistakeCount: Int = 0,
    val weakQuizCount: Int = 0,
    val plannedItemsToday: Int = 0
)

data class AttendanceContext(
    val attendedToday: Int = 0,
    val absentToday: Int = 0,
    val currentClassStatus: String? = null,
    val totalRecordedAbsences: Int = 0
)

data class BudgetContext(
    val spentToday: Double = 0.0,
    val spentThisWeek: Double = 0.0,
    val weeklyBudget: Double = 0.0,
    val spentThisMonth: Double = 0.0,
    val monthlyBudget: Double = 0.0,
    val flexibleMonthlyRemaining: Double = 0.0,
    val safeToSpendToday: Double = 0.0
)

data class CourseContext(
    val code: String,
    val pendingDeadlineCount: Int = 0,
    val overdueDeadlineCount: Int = 0,
    val studyMinutesLast7Days: Int = 0,
    val unresolvedMistakeCount: Int = 0,
    val weakFlashcardCount: Int = 0,
    val latestQuizPercent: Int? = null,
    val reviewCompletionPercent: Int? = null
)

/**
 * Optional, short-lived location context supplied by Punla's existing campus
 * location surfaces. The Student Context Engine never starts GPS on its own;
 * permission and collection remain explicitly user-driven.
 */
data class LocationContext(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val capturedAtEpochMillis: Long
)

object TaskKinds {
    const val DEADLINE = "DEADLINE"
    const val STUDY_PLAN = "STUDY_PLAN"
}
