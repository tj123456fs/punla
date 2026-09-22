package com.uplb.punla.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uplb.punla.context.StudentState

/**
 * Phase 2C Today surface. StudentState remains the source of truth; this layer
 * only turns that shared context into readable, actionable presentation states.
 */
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
    modifier: Modifier = Modifier
) {
    val now = deriveTodayNowPresentation(state)
    val recommendation = deriveTodayRecommendationPresentation(state, recommendationTitle, recommendationDetail)
    val next = state.nextClass
    val later = state.upcomingDeadlines.firstOrNull()

    fun runAction(action: TodayAction) {
        when (action) {
            TodayAction.NONE -> Unit
            TodayAction.SCHEDULE -> onOpenSchedule()
            TodayAction.MAP -> onOpenMap()
            TodayAction.FOCUS -> onStartFocus(recommendationCourse)
            TodayAction.STUDY -> onOpenStudy()
            TodayAction.DEADLINES -> onOpenDeadlines()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "One glance at what matters right now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TodayStatusChip(now.kind, now.statusLabel)
            }
            Spacer(Modifier.height(10.dp))

            Crossfade(
                targetState = now,
                animationSpec = tween(durationMillis = 220),
                label = "today_now_state"
            ) { shown ->
                TodayContextRow(
                    label = "NOW",
                    icon = nowIcon(shown.kind),
                    title = shown.title,
                    detail = shown.detail,
                    actionLabel = actionLabel(shown.action),
                    enabled = shown.action != TodayAction.NONE,
                    onClick = { runAction(shown.action) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val nextTitle = next?.let { "${it.code} · ${formatTodayClock(it.startTime)}" } ?: "No upcoming class"
            val nextDetail = next?.let {
                val day = relativeDayLabel(state.localDate, it.occurrenceDate)
                listOfNotNull(day, it.room?.takeIf(String::isNotBlank)).joinToString(" · ")
            } ?: "Nothing else is scheduled in the current look-ahead."
            TodayContextRow(
                label = "NEXT",
                icon = Icons.Default.School,
                title = nextTitle,
                detail = nextDetail,
                actionLabel = if (next != null) "Schedule" else null,
                enabled = next != null,
                onClick = onOpenSchedule
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Crossfade(
                targetState = recommendation,
                animationSpec = tween(durationMillis = 220),
                label = "today_recommendation"
            ) { shown ->
                TodayContextRow(
                    label = "RECOMMENDED",
                    icon = recommendationIcon(shown.action),
                    title = shown.title,
                    detail = shown.detail,
                    actionLabel = actionLabel(shown.action),
                    enabled = shown.action != TodayAction.NONE,
                    onClick = { runAction(shown.action) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val laterTitle = later?.let { deadline ->
                listOfNotNull(deadline.courseCode?.takeIf(String::isNotBlank), deadline.title).joinToString(" · ")
            } ?: "Nothing due in the next 7 days"
            val laterDetail = later?.let {
                val dueText = when (it.daysUntil) {
                    0L -> "Due today"
                    1L -> "Due tomorrow"
                    else -> "Due in ${it.daysUntil} days"
                }
                val more = (state.upcomingDeadlines.size - 1).coerceAtLeast(0)
                if (more > 0) "$dueText · +$more more this week" else dueText
            } ?: "Your near-term deadline window is clear."
            TodayContextRow(
                label = "LATER",
                icon = Icons.Default.Flag,
                title = laterTitle,
                detail = laterDetail,
                actionLabel = if (later != null) "Deadlines" else null,
                enabled = later != null,
                onClick = onOpenDeadlines
            )
        }
    }
}

@Composable
private fun TodayStatusChip(kind: TodayNowKind, label: String) {
    val container: Color
    val content: Color
    when (kind) {
        TodayNowKind.LOADING -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
        TodayNowKind.IN_CLASS -> {
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
        }
        TodayNowKind.FREE_TIME -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            content = MaterialTheme.colorScheme.onTertiaryContainer
        }
        TodayNowKind.LEAVE_SOON -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
        }
        TodayNowKind.NEXT_SOON -> {
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
        }
        TodayNowKind.DAY_COMPLETE -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Surface(shape = RoundedCornerShape(999.dp), color = container, contentColor = content) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TodayContextRow(
    label: String,
    icon: ImageVector,
    title: String,
    detail: String,
    actionLabel: String?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(2.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (enabled && actionLabel != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(actionLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(3.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

private fun nowIcon(kind: TodayNowKind): ImageVector = when (kind) {
    TodayNowKind.LOADING -> Icons.Default.Schedule
    TodayNowKind.IN_CLASS -> Icons.Default.School
    TodayNowKind.FREE_TIME -> Icons.Default.Timer
    TodayNowKind.LEAVE_SOON -> Icons.Default.NearMe
    TodayNowKind.NEXT_SOON -> Icons.Default.Schedule
    TodayNowKind.DAY_COMPLETE -> Icons.Default.CheckCircle
}

private fun recommendationIcon(action: TodayAction): ImageVector = when (action) {
    TodayAction.MAP -> Icons.Default.NearMe
    TodayAction.FOCUS -> Icons.Default.Timer
    TodayAction.STUDY -> Icons.Default.School
    TodayAction.DEADLINES -> Icons.Default.Flag
    TodayAction.SCHEDULE -> Icons.Default.Schedule
    TodayAction.NONE -> Icons.Default.AutoAwesome
}

private fun actionLabel(action: TodayAction): String? = when (action) {
    TodayAction.NONE -> null
    TodayAction.SCHEDULE -> "Schedule"
    TodayAction.MAP -> "Map"
    TodayAction.FOCUS -> "Focus"
    TodayAction.STUDY -> "Study"
    TodayAction.DEADLINES -> "Deadlines"
}
