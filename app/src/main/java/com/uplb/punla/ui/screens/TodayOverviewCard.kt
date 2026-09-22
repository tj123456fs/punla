package com.uplb.punla.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uplb.punla.context.StudentState
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Phase 2A's compact Home answer to four questions: what is happening now,
 * what is next, what existing study logic recommends, and what is due later.
 * It consumes StudentState; it does not create a second scheduling engine.
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
    onStartFocus: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val loading = state.localDate.isBlank()
    val current = state.currentClass
    val next = state.nextClass
    val later = state.upcomingDeadlines.firstOrNull()

    val nowTitle = when {
        loading -> "Loading today…"
        current != null -> current.code
        state.usableFreeMinutes != null && state.usableFreeMinutes > 0 -> "Free for ${formatDuration(state.usableFreeMinutes)}"
        else -> "No class right now"
    }
    val nowDetail = when {
        loading -> "Punla is building your current context."
        current != null -> buildString {
            append("Until ${formatClock(current.endTime)}")
            current.room?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        state.travelBufferMinutes != null && state.travelBufferMinutes > 0 && next != null ->
            "${state.travelBufferMinutes} min travel buffer reserved before ${next.code}."
        next != null -> "Your next scheduled class is ${next.code}."
        else -> "Your schedule is clear for now."
    }

    val nextTitle = next?.let { "${it.code} · ${formatClock(it.startTime)}" } ?: "No upcoming class"
    val nextDetail = next?.let {
        val dayPrefix = when {
            state.localDate.isBlank() -> null
            it.occurrenceDate == state.localDate -> "Today"
            runCatching { LocalDate.parse(it.occurrenceDate) == LocalDate.parse(state.localDate).plusDays(1) }.getOrDefault(false) -> "Tomorrow"
            else -> it.occurrenceDate
        }
        listOfNotNull(dayPrefix, it.room?.takeIf(String::isNotBlank)).joinToString(" · ").ifBlank { "Schedule" }
    } ?: "Nothing else is scheduled in the current look-ahead."

    val recommendedTitle = recommendationTitle ?: when {
        state.overdueWork.isNotEmpty() -> "Catch-up work is waiting"
        else -> "No study block suggested right now"
    }
    val recommendedDetail = recommendationDetail ?: when {
        state.overdueWork.isNotEmpty() -> "Open Study Hub to choose which overdue item to tackle."
        else -> "Punla will surface a focus block when a real free slot matches your workload."
    }

    val laterTitle = later?.let { deadline ->
        listOfNotNull(deadline.courseCode?.takeIf(String::isNotBlank), deadline.title).joinToString(" · ")
    } ?: "Nothing due in the next 7 days"
    val laterDetail = later?.let {
        when (it.daysUntil) {
            0L -> "Due today"
            1L -> "Due tomorrow"
            else -> "Due in ${it.daysUntil} days"
        }
    } ?: "Your near-term deadline window is clear."

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "One glance at what matters right now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            TodayContextRow(
                label = "NOW",
                icon = Icons.Default.Schedule,
                title = nowTitle,
                detail = nowDetail,
                enabled = !loading,
                onClick = onOpenSchedule
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TodayContextRow(
                label = "NEXT",
                icon = Icons.Default.School,
                title = nextTitle,
                detail = nextDetail,
                enabled = next != null,
                onClick = onOpenSchedule
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TodayContextRow(
                label = "RECOMMENDED",
                icon = Icons.Default.AutoAwesome,
                title = recommendedTitle,
                detail = recommendedDetail,
                enabled = recommendationTitle != null || state.overdueWork.isNotEmpty(),
                onClick = {
                    if (recommendationTitle != null) onStartFocus(recommendationCourse) else onOpenStudy()
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TodayContextRow(
                label = "LATER",
                icon = Icons.Default.Flag,
                title = laterTitle,
                detail = laterDetail,
                enabled = later != null,
                onClick = onOpenDeadlines
            )
        }
    }
}

@Composable
private fun TodayContextRow(
    label: String,
    icon: ImageVector,
    title: String,
    detail: String,
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
    }
}

private val todayClockFormat = DateTimeFormatter.ofPattern("h:mm a")

private fun formatClock(raw: String): String =
    runCatching { LocalTime.parse(raw).format(todayClockFormat) }.getOrDefault(raw)

private fun formatDuration(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val remainder = safe % 60
    return when {
        hours <= 0 -> "$remainder min"
        remainder == 0 -> "${hours}h"
        else -> "${hours}h ${remainder}m"
    }
}
