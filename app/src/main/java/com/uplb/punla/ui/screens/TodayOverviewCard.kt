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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uplb.punla.context.StudentState

/**
 * Phase 2C Today surface. StudentState remains the source of truth; this layer
 * only turns that shared context into readable, actionable presentation states.
 *
 * Layout: NOW is the strongest block (large title + one filled action button).
 * Suggestion, NEXT and LATER are compact rows so the card scans top to bottom.
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
    if (state.localDate.isBlank()) {
        Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Text("Loading your day…", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
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
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(9.dp))
                Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))

            Crossfade(
                targetState = now,
                animationSpec = tween(durationMillis = 220),
                label = "today_now_state"
            ) { shown ->
                TodayNowBlock(shown) { runAction(shown.action) }
            }

            if (todayShowsRecommendation(now, recommendation)) {
                Spacer(Modifier.height(4.dp))
                Crossfade(
                    targetState = recommendation,
                    animationSpec = tween(durationMillis = 220),
                    label = "today_recommendation"
                ) { shown ->
                    TodayCompactRow(
                        label = "SUGGESTED",
                        icon = recommendationIcon(shown.action),
                        title = shown.title,
                        detail = shown.detail,
                        actionLabel = shortActionLabel(shown.action),
                        enabled = shown.action != TodayAction.NONE,
                        onClick = { runAction(shown.action) }
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            val nextTitle = next?.let { "${it.code} · ${formatTodayClock(it.startTime)}" } ?: "No upcoming class"
            val nextDetail = next?.let {
                val day = relativeDayLabel(state.localDate, it.occurrenceDate)
                listOfNotNull(day, it.room?.takeIf(String::isNotBlank), state.travelBufferMinutes?.let { minutes -> "$minutes min travel · ${state.travelRouteSource}" }).joinToString(" · ")
            } ?: "Nothing else is scheduled in the current look-ahead."
            TodayCompactRow(
                label = "NEXT",
                icon = Icons.Default.School,
                title = nextTitle,
                detail = nextDetail,
                actionLabel = if (next != null) "Schedule" else null,
                enabled = next != null,
                onClick = onOpenSchedule
            )

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
            TodayCompactRow(
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

/** Strongest part of the card: status chip, large title, detail, and one filled button. */
@Composable
private fun TodayNowBlock(now: TodayNowPresentation, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        TodayStatusChip(now.kind, now.statusLabel)
        Spacer(Modifier.height(10.dp))
        Text(
            now.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            now.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val buttonLabel = todayPrimaryActionLabel(now.kind, now.action)
        if (buttonLabel != null) {
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(nowIcon(now.kind), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(buttonLabel, style = MaterialTheme.typography.labelLarge)
            }
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
        TodayNowKind.DAY_COMPLETE, TodayNowKind.COMMITMENT -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Surface(shape = RoundedCornerShape(999.dp), color = container, contentColor = content) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/** Quiet secondary row: small icon, label, one-line title, short detail, optional chevron. */
@Composable
private fun TodayCompactRow(
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
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = actionLabel, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (enabled) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun nowIcon(kind: TodayNowKind): ImageVector = when (kind) {
    TodayNowKind.LOADING -> Icons.Default.Schedule
    TodayNowKind.IN_CLASS -> Icons.Default.School
    TodayNowKind.FREE_TIME -> Icons.Default.Timer
    TodayNowKind.LEAVE_SOON -> Icons.Default.NearMe
    TodayNowKind.NEXT_SOON -> Icons.Default.Schedule
    TodayNowKind.COMMITMENT -> Icons.Default.Schedule
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

/** Short noun label used as the screen-reader click label on compact rows. */
private fun shortActionLabel(action: TodayAction): String? = when (action) {
    TodayAction.NONE -> null
    TodayAction.SCHEDULE -> "Open schedule"
    TodayAction.MAP -> "Open map"
    TodayAction.FOCUS -> "Start focus"
    TodayAction.STUDY -> "Open study"
    TodayAction.DEADLINES -> "Open deadlines"
}
