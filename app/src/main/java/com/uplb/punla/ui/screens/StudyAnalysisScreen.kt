package com.uplb.punla.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uplb.punla.data.entity.StudySession
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ml.bestStudyHour
import com.uplb.punla.ml.sessionEarlyStopRate
import com.uplb.punla.ui.theme.LocalPunlaPalette
import com.uplb.punla.ui.theme.PunlaDisplay
import com.uplb.punla.ui.theme.PunlaMono
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class AnalysisRange(val label: String, val days: Int?) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    ALL_TIME("All time", null)
}

private fun StudySession.localDate(): LocalDate =
    Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatStudyHour(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}

private fun formatHoursMinutes(totalSeconds: Int): String {
    val totalMinutes = totalSeconds / 60
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/**
 * Roadmap Study Analysis (Phase 3) — a drill-down from the Pomodoro screen's
 * history icon, not a top-level destination. Everything here is hand-drawn
 * with Compose `Canvas`/`Box` rather than a charting library, consistent
 * with how `CampusMapView.kt`/`MapMarkerIcon.kt` already do custom drawing
 * in this codebase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyAnalysisScreen(vm: PunlaViewModel) {
    val allSessions by vm.studySessions.collectAsState()
    val streak by vm.currentStudyStreak.collectAsState()
    val dailyGoalMinutes = vm.dailyStudyGoalMinutes
    var range by remember { mutableStateOf(AnalysisRange.WEEK) }

    val today = remember { LocalDate.now() }
    val rangeStart = remember(range, today) {
        range.days?.let { today.minusDays((it - 1).toLong()) }
    }
    val sessionsInRange = remember(allSessions, rangeStart) {
        if (rangeStart == null) allSessions
        else allSessions.filter { !it.localDate().isBefore(rangeStart) }
    }

    val screenGutter = punlaScreenHorizontalPadding()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = screenGutter, vertical = 12.dp)
    ) {
        item {
            Text(
                "What your logged focus sessions add up to.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }

        // 3.1 — time range selector. A plain Row of toggle FilterChips
        // rather than Material3's SegmentedButton, which needs verifying
        // against this project's compose-bom before relying on it.
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalysisRange.entries.forEach { r ->
                    FilterChip(
                        selected = range == r,
                        onClick = { range = r },
                        label = { Text(r.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (allSessions.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Timer,
                    message = "No focus sessions logged yet — run a Pomodoro to see your stats here."
                )
            }
            return@LazyColumn
        }

        // 3.2 — summary row.
        item {
            val totalSeconds = sessionsInRange.sumOf { it.actualSeconds }
            val startedCount = sessionsInRange.size
            val completedCount = sessionsInRange.count { it.completed }
            val completionPct = if (startedCount > 0) (completedCount * 100 / startedCount) else 0
            val avgSeconds = if (startedCount > 0) totalSeconds / startedCount else 0

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalysisStatTile(
                    value = formatHoursMinutes(totalSeconds),
                    label = "Total focus time",
                    container = MaterialTheme.colorScheme.primaryContainer,
                    onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                AnalysisStatTile(
                    value = "$completedCount/$startedCount \u00b7 $completionPct%",
                    label = "Completion rate",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalysisStatTile(
                    value = formatHoursMinutes(avgSeconds),
                    label = "Average session",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                AnalysisStatTile(
                    value = if (streak > 0) "\uD83D\uDD25 $streak" else "0",
                    label = "Current streak (days)",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    onContainer = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            val bestHour = bestStudyHour(sessionsInRange)
            val earlyStop = sessionEarlyStopRate(sessionsInRange)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "PERSONAL PATTERNS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        bestHour?.let { "Your strongest logged start time is around ${formatStudyHour(it)}." }
                            ?: "Log at least three sessions around the same hour to identify a reliable focus window.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (sessionsInRange.size >= 3) {
                            "${(earlyStop * 100).toInt()}% of sessions ended early. This is shown as a neutral trend, not a penalty."
                        } else {
                            "Based on ${sessionsInRange.size} session${if (sessionsInRange.size == 1) "" else "s"}; more history will improve the pattern."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (vm.repo.studySlotModelState.sampleCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Study-slot learner: ${vm.repo.studySlotModelState.sampleCount}/${com.uplb.punla.ml.StudySlotPredictor.MIN_SAMPLES_FOR_PREDICTION} outcomes before predictive ranking activates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // 3.3 — time-per-course breakdown.
        item {
            Text(
                "TIME PER COURSE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            CourseBreakdown(sessionsInRange)
            Spacer(Modifier.height(20.dp))
        }

        // 3.4 — daily activity heatmap strip. Highest value, lowest effort
        // per the build guide, so it's kept even if everything else here
        // gets trimmed later.
        item {
            Text(
                "DAILY ACTIVITY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            val heatmapDays = when (range) {
                AnalysisRange.WEEK -> 7
                AnalysisRange.MONTH -> 30
                AnalysisRange.ALL_TIME -> 90
            }
            DailyHeatmap(
                sessions = allSessions,
                days = heatmapDays,
                dailyGoalMinutes = dailyGoalMinutes,
                today = today
            )
            Spacer(Modifier.height(20.dp))
        }

        // 3.5 — session log, reverse-chronological.
        item {
            Text(
                "SESSION LOG",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
        }

        if (sessionsInRange.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.History,
                    message = "Nothing logged in this range yet."
                )
            }
        } else {
            items(sessionsInRange.sortedByDescending { it.startedAt }, key = { it.id }) { session ->
                StudySessionRow(session = session, onDelete = { vm.deleteStudySession(session) })
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun AnalysisStatTile(
    value: String,
    label: String,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .background(container, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = PunlaMono, fontWeight = FontWeight.SemiBold),
                color = onContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = onContainer
            )
        }
    }
}

@Composable
private fun CourseBreakdown(sessions: List<StudySession>) {
    val palette = LocalPunlaPalette.current
    val accentColors = remember(palette) { listOf(palette.leaf, palette.maroon, palette.mango, palette.bark) }

    val byCourse = remember(sessions) {
        sessions
            .groupBy { it.courseCode?.takeIf { c -> c.isNotBlank() } ?: "Other" }
            .mapValues { (_, list) -> list.sumOf { it.actualSeconds } }
            .toList()
            .sortedByDescending { it.second }
    }

    if (byCourse.isEmpty()) {
        Text(
            "No sessions in this range.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val maxSeconds = byCourse.maxOf { it.second }.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        byCourse.forEachIndexed { index, (course, seconds) ->
            val fraction = (seconds.toFloat() / maxSeconds).coerceIn(0.03f, 1f)
            val color = accentColors[index % accentColors.size]
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(course, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                    Text(
                        formatHoursMinutes(seconds),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = PunlaMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(5.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(color, RoundedCornerShape(5.dp))
                    )
                }
            }
        }
    }
}

/**
 * GitHub-style activity heatmap for the last [days], drawn with a single
 * `Canvas` grid rather than [days] individual composables. Cell fill
 * intensity is scaled by that day's studied minutes relative to
 * [dailyGoalMinutes] — 0 renders as an outline-only cell, 1.0+ as a full
 * fill.
 */
@Composable
private fun DailyHeatmap(
    sessions: List<StudySession>,
    days: Int,
    dailyGoalMinutes: Int,
    today: LocalDate
) {
    val palette = LocalPunlaPalette.current
    val outlineColor = MaterialTheme.colorScheme.outline
    val fillColor = palette.leaf

    val secondsByDay = remember(sessions) {
        sessions.groupBy { it.localDate() }.mapValues { (_, list) -> list.sumOf { it.actualSeconds } }
    }
    val goalSeconds = (dailyGoalMinutes * 60).coerceAtLeast(1)
    val dayList = remember(days, today) {
        (0 until days).map { offset -> today.minusDays((days - 1 - offset).toLong()) }
    }
    val intensities = remember(dayList, secondsByDay, goalSeconds) {
        dayList.map { d -> ((secondsByDay[d] ?: 0) / goalSeconds.toFloat()).coerceIn(0f, 1f) }
    }

    val columns = if (days <= 7) days else 7
    val rows = (days + columns - 1) / columns
    val cellSize = 16.dp
    val cellGap = 4.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(cellSize * rows + cellGap * (rows - 1).coerceAtLeast(0))
    ) {
        val cellPx = cellSize.toPx()
        val gapPx = cellGap.toPx()
        intensities.forEachIndexed { index, intensity ->
            val col = index % columns
            val row = index / columns
            val x = col * (cellPx + gapPx)
            val y = row * (cellPx + gapPx)
            if (intensity <= 0f) {
                drawRoundRect(
                    color = outlineColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = Size(cellPx, cellPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            } else {
                drawRoundRect(
                    color = fillColor.copy(alpha = 0.25f + intensity * 0.75f),
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = Size(cellPx, cellPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun StudySessionRow(session: StudySession, onDelete: () -> Unit) {
    val dateLabel = remember(session.id) {
        session.localDate().format(DateTimeFormatter.ofPattern("MMM d"))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .shadow(0.5.dp, MaterialTheme.shapes.small, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.02f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.02f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.courseCode?.takeIf { it.isNotBlank() } ?: "Untagged",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    if (!session.completed) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "stopped early",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    "$dateLabel \u00b7 ${formatHoursMinutes(session.actualSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
