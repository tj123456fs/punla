package com.uplb.punla.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.uplb.punla.planning.AgendaEntry
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
internal fun PlannerDates(today: LocalDate, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val dates = remember(today) { (-1L..7L).map { today.plusDays(it) } }
    val listState = rememberLazyListState()

    // Keep the selected day fully visible after a tap or restored navigation.
    // Compact date labels also leave an intentional next-item peek on narrow
    // phones instead of clipping a long "Tomorrow" chip mid-word.
    LaunchedEffect(selected, dates) {
        val selectedIndex = dates.indexOf(selected)
        if (selectedIndex >= 0) {
            listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(dates, key = { it.toString() }) { date ->
            FilterChip(
                selected = date == selected,
                onClick = { onSelect(date) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("date:$date"),
                label = {
                    Text(
                        if (date == today) "Today"
                        else date.format(DateTimeFormatter.ofPattern("EEE d"))
                    )
                }
            )
        }
    }
}

@Composable
internal fun PlannerAgendaRow(entry: AgendaEntry, date: LocalDate, now: Long, onOpen: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(entry.start).atZone(zone)
    val end = Instant.ofEpochMilli(entry.end).atZone(zone)
    val format = DateTimeFormatter.ofPattern("h:mm a")
    val active = now in entry.start until entry.end && entry.kind != "FREE" && entry.detail !in listOf("Done", "Skipped")
    val colors = MaterialTheme.colorScheme
    val tint = when(entry.kind) { "STUDY", "FOCUS" -> colors.primaryContainer; "TRAVEL" -> colors.tertiaryContainer; else -> colors.surface }
    Surface(onClick = onOpen, enabled = entry.blockId != null || entry.route.isNotEmpty(),
        color = tint, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().testTag("agenda:${entry.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(buildString {
                if (active) append("NOW · ")
                append(if (start.toLocalDate() < date) "From yesterday" else start.format(format))
                append(" – ")
                append(if (end.toLocalDate() > date) "next day ${end.format(format)}" else end.format(format))
            }, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(entry.title, style = MaterialTheme.typography.titleMedium)
            Text("${entry.detail} · ${(entry.end - entry.start) / 60000} min", style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
            if (entry.blockId != null) Text("View block", style = MaterialTheme.typography.labelMedium, color = colors.primary)
        }
    }
}
