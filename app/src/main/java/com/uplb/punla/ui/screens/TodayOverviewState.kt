package com.uplb.punla.ui.screens

import com.uplb.punla.context.StudentState
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal enum class TodayNowKind {
    LOADING,
    IN_CLASS,
    FREE_TIME,
    LEAVE_SOON,
    NEXT_SOON,
    DAY_COMPLETE,
    COMMITMENT
}

internal enum class TodayAction {
    NONE,
    SCHEDULE,
    MAP,
    FOCUS,
    STUDY,
    DEADLINES
}

internal data class TodayNowPresentation(
    val kind: TodayNowKind,
    val statusLabel: String,
    val title: String,
    val detail: String,
    val action: TodayAction = TodayAction.NONE
)

internal data class TodayRecommendationPresentation(
    val title: String,
    val detail: String,
    val action: TodayAction
)

internal fun deriveTodayNowPresentation(state: StudentState): TodayNowPresentation {
    if (state.localDate.isBlank()) {
        return TodayNowPresentation(
            kind = TodayNowKind.LOADING,
            statusLabel = "SYNCING",
            title = "Loading today…",
            detail = "Punla is building your current context."
        )
    }

    state.currentClass?.let { current ->
        val room = current.room?.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
        return TodayNowPresentation(
            kind = TodayNowKind.IN_CLASS,
            statusLabel = "IN CLASS",
            title = current.code,
            detail = "Until ${formatTodayClock(current.endTime)}$room",
            action = TodayAction.SCHEDULE
        )
    }

    state.currentCommitmentTitle?.let {
        return TodayNowPresentation(TodayNowKind.COMMITMENT, "RESERVED TIME", it,
            "Your plan protects this time. Open Plan to change the commitment.", TodayAction.NONE)
    }

    val next = state.nextClass
    val nextIsToday = next?.occurrenceDate == state.localDate
    if (next != null && nextIsToday) {
        val free = state.freeMinutesBeforeNextCommitment
        val travel = state.travelBufferMinutes?.coerceAtLeast(0) ?: 0
        val usable = state.usableFreeMinutes
        val room = next.room?.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()

        if (travel > 0 && free != null && free <= travel + 5) {
            return TodayNowPresentation(
                kind = TodayNowKind.LEAVE_SOON,
                statusLabel = "LEAVE SOON",
                title = "Head to ${next.code}",
                detail = "${formatTodayClock(next.startTime)}$room · about $travel min travel",
                action = TodayAction.MAP
            )
        }

        if (usable != null && usable >= 20) {
            return TodayNowPresentation(
                kind = TodayNowKind.FREE_TIME,
                statusLabel = "FREE TIME",
                title = "Free for ${formatTodayDuration(usable)}",
                detail = "Before ${next.code} at ${formatTodayClock(next.startTime)}$room",
                action = TodayAction.FOCUS
            )
        }

        return TodayNowPresentation(
            kind = TodayNowKind.NEXT_SOON,
            statusLabel = "NEXT SOON",
            title = "${next.code} starts soon",
            detail = "${formatTodayClock(next.startTime)}$room",
            action = TodayAction.SCHEDULE
        )
    }

    val nextDetail = next?.let {
        val day = relativeDayLabel(state.localDate, it.occurrenceDate)
        val room = it.room?.takeIf(String::isNotBlank)?.let { room -> " · $room" }.orEmpty()
        "Next: ${it.code} · $day ${formatTodayClock(it.startTime)}$room"
    } ?: "No more classes are scheduled in the current look-ahead."

    return TodayNowPresentation(
        kind = TodayNowKind.DAY_COMPLETE,
        statusLabel = "DAY COMPLETE",
        title = "Classes are done for today",
        detail = nextDetail,
        action = if (next != null) TodayAction.SCHEDULE else TodayAction.NONE
    )
}

internal fun deriveTodayRecommendationPresentation(
    state: StudentState,
    recommendationTitle: String?,
    recommendationDetail: String?
): TodayRecommendationPresentation {
    val now = deriveTodayNowPresentation(state)
    val next = state.nextClass
    if (now.kind == TodayNowKind.LEAVE_SOON && next != null) {
        return TodayRecommendationPresentation(
            title = "Head to ${next.code}",
            detail = state.travelBufferMinutes?.takeIf { it > 0 }
                ?.let { "Protect the ~$it min travel buffer so you arrive on time." }
                ?: "Your next class is close enough that travel should come first.",
            action = TodayAction.MAP
        )
    }

    if (now.kind == TodayNowKind.IN_CLASS) {
        return TodayRecommendationPresentation(
            title = "Stay with ${state.currentClass?.code ?: "your class"}",
            detail = "Punla will reassess your study window after class ends.",
            action = TodayAction.NONE
        )
    }

    if (now.kind == TodayNowKind.COMMITMENT) {
        return TodayRecommendationPresentation("Keep time for ${state.currentCommitmentTitle}",
            "Your study plan resumes in your next available opening.", TodayAction.NONE)
    }

    if (state.usableFreeMinutes != null && state.usableFreeMinutes < 5) {
        return TodayRecommendationPresentation("No study block fits right now", "Open Plan to choose a later opening.", TodayAction.NONE)
    }

    if (!recommendationTitle.isNullOrBlank()) {
        return TodayRecommendationPresentation(
            title = recommendationTitle,
            detail = recommendationDetail?.takeIf(String::isNotBlank)
                ?: "A real free slot matches your current workload.",
            action = TodayAction.FOCUS
        )
    }


    val overdue = state.overdueWork.firstOrNull()
    if (overdue != null) {
        return TodayRecommendationPresentation(
            title = "Catch up on ${overdue.title}",
            detail = listOfNotNull(overdue.courseCode?.takeIf(String::isNotBlank), "Overdue").joinToString(" · "),
            action = TodayAction.STUDY
        )
    }

    val reviewReady = state.study.dueFlashcardCount +
        state.study.unresolvedMistakeCount +
        state.study.plannedItemsToday
    if (reviewReady > 0 && (now.kind == TodayNowKind.FREE_TIME || now.kind == TodayNowKind.DAY_COMPLETE)) {
        val detail = buildList {
            if (state.study.dueFlashcardCount > 0) add("${state.study.dueFlashcardCount} cards")
            if (state.study.unresolvedMistakeCount > 0) add("${state.study.unresolvedMistakeCount} mistakes")
            if (state.study.plannedItemsToday > 0) add("${state.study.plannedItemsToday} planned")
        }.joinToString(" · ")
        return TodayRecommendationPresentation(
            title = if (now.kind == TodayNowKind.FREE_TIME) "Use this free block for review" else "Quick review before you wrap up",
            detail = detail.ifBlank { "$reviewReady study items are ready." },
            action = TodayAction.STUDY
        )
    }

    return TodayRecommendationPresentation(
        title = "You're clear for now",
        detail = when (now.kind) {
            TodayNowKind.FREE_TIME -> "Use the free block however you need; Punla has no urgent study item to push."
            TodayNowKind.DAY_COMPLETE -> "No urgent study item needs your attention right now."
            else -> "Punla will update this when your schedule or workload changes."
        },
        action = TodayAction.NONE
    )
}

internal fun relativeDayLabel(todayRaw: String, occurrenceRaw: String): String {
    val today = runCatching { LocalDate.parse(todayRaw) }.getOrNull()
    val occurrence = runCatching { LocalDate.parse(occurrenceRaw) }.getOrNull()
    if (today == null || occurrence == null) return occurrenceRaw
    return when (occurrence) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> occurrence.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}

private val todayStateClockFormat = DateTimeFormatter.ofPattern("h:mm a")

internal fun formatTodayClock(raw: String): String =
    runCatching { LocalTime.parse(raw).format(todayStateClockFormat) }.getOrDefault(raw)

internal fun formatTodayDuration(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val remainder = safe % 60
    return when {
        hours <= 0 -> "$remainder min"
        remainder == 0 -> "${hours}h"
        else -> "${hours}h ${remainder}m"
    }
}
