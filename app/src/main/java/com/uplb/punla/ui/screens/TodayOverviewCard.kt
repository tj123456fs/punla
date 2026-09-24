package com.uplb.punla.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.uplb.punla.context.StudentState

/** One decision first; the surrounding day remains available at a glance. */
@Composable
internal fun TodayOverviewCard(
    state: StudentState,
    recommendationTitle: String?,
    recommendationDetail: String?,
    recommendationCourse: String?,
    onOpenSchedule: () -> Unit,
    onOpenDeadlines: () -> Unit,
    onOpenStudy: () -> Unit,
    onOpenMap: () -> Unit,
    onStartFocus: (String?) -> Unit,
    modifier: Modifier = Modifier,
    focusRunning: Boolean = false
) {
    if (state.localDate.isBlank()) {
        Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Text("Loading your day…", Modifier.padding(24.dp))
        }
        return
    }
    val now = deriveTodayNowPresentation(state)
    val primary = if (focusRunning && now.kind == TodayNowKind.COMMITMENT)
        TodayRecommendationPresentation("Your focus is in progress", now.title, TodayAction.FOCUS)
        else deriveTodayPrimaryPresentation(state, recommendationTitle, recommendationDetail)
    fun act(action: TodayAction) {
        when (action) {
            TodayAction.NONE -> Unit
            TodayAction.SCHEDULE -> onOpenSchedule()
            TodayAction.MAP -> onOpenMap()
            TodayAction.FOCUS -> onStartFocus(recommendationCourse)
            TodayAction.STUDY -> onOpenStudy()
            TodayAction.DEADLINES -> onOpenDeadlines()
        }
    }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(now.statusLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(primary.title, style = MaterialTheme.typography.headlineSmall)
            Text(primary.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (primary.action != TodayAction.NONE) {
                Button(onClick = { act(primary.action) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("today-primary")) {
                    Text(when (primary.action) {
                        TodayAction.MAP -> "Open campus map"
                        TodayAction.FOCUS -> if (focusRunning) "Return to focus" else "Start focus"
                        TodayAction.STUDY -> "Open study"
                        TodayAction.DEADLINES -> "Review deadlines"
                        else -> "View schedule"
                    })
                }
            }
            HorizontalDivider()
            val next = state.nextClass
            TodayBrief("NEXT", next?.let { "${it.code} · ${formatTodayClock(it.startTime)}" } ?: "No upcoming class",
                next?.let { listOfNotNull(relativeDayLabel(state.localDate, it.occurrenceDate), it.room?.takeIf(String::isNotBlank)).joinToString(" · ") }.orEmpty(),
                next != null, onOpenSchedule)
            val later = state.upcomingDeadlines.firstOrNull()
            TodayBrief("LATER", later?.title ?: "Nothing due in the next 7 days",
                later?.let { listOfNotNull(it.courseCode, when(it.daysUntil) { 0L -> "Due today"; 1L -> "Due tomorrow"; else -> "Due in ${it.daysUntil} days" }).joinToString(" · ") }.orEmpty(),
                later != null, onOpenDeadlines)
        }
    }
}

@Composable
private fun TodayBrief(label: String, title: String, detail: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
