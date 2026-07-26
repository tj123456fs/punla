package com.uplb.punla.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ui.theme.PunlaDisplay
import com.uplb.punla.ui.theme.LocalPunlaPalette
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val TYPES = listOf("Requirement", "Exam", "Quiz", "Project", "Problem Set", "Paper", "Other")
private val PRIORITIES = listOf("High", "Medium", "Low")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlinesScreen(vm: PunlaViewModel, openFormOnStart: Boolean = false) {
    val deadlines by vm.deadlines.collectAsState()
    // Roadmap C — withhold "No deadlines logged yet" until Room's first
    // real emission, so it doesn't flash for a frame on cold launch.
    val dataReady by vm.isDataReady.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(0) } // 0 = List, 1 = Calendar

    LaunchedEffect(openFormOnStart) {
        if (openFormOnStart) showForm = true
    }

    // Calendar state
    var calendarCursor by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val pending = deadlines.filter { !it.done }.sortedBy { it.due }
    val done = deadlines.filter { it.done }.sortedByDescending { it.due }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showForm = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Default.Add, "Add deadline") }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            SegmentedControl(
                options = listOf("List", "Calendar"),
                selected = viewMode,
                onSelect = { viewMode = it },
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (viewMode == 0) {
                // LIST VIEW
                if (deadlines.isEmpty()) {
                    if (dataReady) {
                        EmptyState(
                            icon = Icons.Default.Flag,
                            message = "No deadlines logged yet. Tap + to add one.",
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (pending.isNotEmpty()) {
                            item { SectionLabel("Upcoming") }
                            items(pending, key = { it.id }) { d -> DeadlineRow(d, vm) }
                        }
                        if (done.isNotEmpty()) {
                            item { SectionLabel("Completed") }
                            items(done, key = { it.id }) { d -> DeadlineRow(d, vm) }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            } else {
                // CALENDAR VIEW
                DeadlineCalendarView(
                    deadlines = deadlines,
                    calendarCursor = calendarCursor,
                    selectedDate = selectedDate,
                    onCursorChange = { calendarCursor = it },
                    onDateSelect = { selectedDate = it },
                    vm = vm
                )
            }
        }
    }

    if (showForm) {
        DeadlineFormDialog(
            defaultDue = selectedDate.toString(),
            onDismiss = { showForm = false },
            onSave = { deadline, repeatWeekly -> vm.addOrUpdateDeadline(deadline, repeatWeekly); showForm = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlineCalendarView(
    deadlines: List<Deadline>,
    calendarCursor: YearMonth,
    selectedDate: LocalDate,
    onCursorChange: (YearMonth) -> Unit,
    onDateSelect: (LocalDate) -> Unit,
    vm: PunlaViewModel
) {
    val firstOfMonth = calendarCursor.atDay(1)
    val startPadding = (firstOfMonth.dayOfWeek.value % 7) // Sunday starts: Mon is 1 -> 1, Sun is 7 -> 0
    val daysInMonth = calendarCursor.lengthOfMonth()

    val monthLabel = calendarCursor.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val yearLabel = calendarCursor.year.toString()

    Column(Modifier.fillMaxWidth()) {
        // Calendar controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$monthLabel $yearLabel",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = PunlaDisplay),
                color = MaterialTheme.colorScheme.onBackground
            )
            Row {
                IconButton(onClick = { onCursorChange(calendarCursor.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous Month")
                }
                IconButton(onClick = { onCursorChange(calendarCursor.plusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next Month")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Days of week row
        val dows = listOf("S", "M", "T", "W", "T", "F", "S")
        Row(modifier = Modifier.fillMaxWidth()) {
            dows.forEach { dow ->
                Text(
                    text = dow,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Calendar Grid
        val totalCells = startPadding + daysInMonth
        val totalRows = (totalCells + 6) / 7

        for (row in 0 until totalRows) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - startPadding + 1
                    val hasDay = dayNum in 1..daysInMonth

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                    ) {
                        if (hasDay) {
                            val cellDate = calendarCursor.atDay(dayNum)
                            val isSelected = cellDate == selectedDate
                            val isToday = cellDate == LocalDate.now()

                            // Find deadlines on this day
                            val dayDeadlines = deadlines.filter {
                                !it.done && runCatching { LocalDate.parse(it.due) == cellDate }.getOrDefault(false)
                            }

                            // Compute highest urgency color
                            val dotColor = if (dayDeadlines.isNotEmpty()) {
                                val daysLeft = dayDeadlines.map { runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it.due)) }.getOrDefault(Long.MAX_VALUE) }.minOrNull() ?: Long.MAX_VALUE
                                when {
                                    daysLeft <= 3 -> MaterialTheme.colorScheme.error
                                    daysLeft <= 7 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            } else null

                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onDateSelect(cellDate) },
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                border = if (isToday && !isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (dotColor != null) {
                                        Spacer(Modifier.height(2.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(dotColor, CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Selected Date Deadlines List
        val selectedDeadlines = deadlines.filter {
            runCatching { LocalDate.parse(it.due) == selectedDate }.getOrDefault(false)
        }

        SectionLabel("Deadlines on ${selectedDate.toString()}")

        if (selectedDeadlines.isEmpty()) {
            Text(
                "No deadlines due on this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(selectedDeadlines, key = { it.id }) { d ->
                    DeadlineRow(d, vm)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// Deadlines row helper logic
private data class UrgencyStyle(val accent: Color, val badgeBg: Color, val badgeOn: Color)

@Composable
private fun urgencyStyle(days: Long?): UrgencyStyle {
    val danger = MaterialTheme.colorScheme.error
    val maroon = MaterialTheme.colorScheme.secondary
    val maroonBg = MaterialTheme.colorScheme.secondaryContainer
    val mango = MaterialTheme.colorScheme.tertiary
    val mangoBg = MaterialTheme.colorScheme.tertiaryContainer
    val leaf = MaterialTheme.colorScheme.primary
    val leafBg = MaterialTheme.colorScheme.primaryContainer
    return when {
        days == null -> UrgencyStyle(leaf, leafBg, leaf)
        days <= 3 -> UrgencyStyle(danger, maroonBg, maroon)
        days <= 7 -> UrgencyStyle(mango, mangoBg, mango)
        else -> UrgencyStyle(leaf, leafBg, leaf)
    }
}

@Composable
private fun priorityFlagColor(priority: String): Color? = when (priority) {
    "High" -> MaterialTheme.colorScheme.secondary
    "Medium" -> MaterialTheme.colorScheme.tertiary
    else -> null // Low — no flag
}

@Composable
private fun DeadlineRow(d: Deadline, vm: PunlaViewModel) {
    val haptics = LocalHapticFeedback.current
    val days = runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(d.due)) }.getOrNull()
    val urgency = urgencyStyle(days)
    val accent = if (d.done) MaterialTheme.colorScheme.outline else urgency.accent
    val countdownText = when {
        d.done -> null
        days == null -> null
        days < 0 -> "${-days}d overdue"
        days == 0L -> "Due today"
        else -> "${days}d left"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            AccentBar(accent)
            Row(
                Modifier.padding(vertical = 10.dp, horizontal = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.toggleDeadlineDone(d)
                    }) {
                        // UX_POLISH_NAV_GLASS_MOTION.md motion section — "a
                        // checkmark animating in on task completion." A plain
                        // AnimatedContent swap (scale+fade in on the new icon,
                        // scale+fade out on the old one) instead of the old
                        // instant icon-swap. Native `androidx.compose.animation`
                        // only — no new dependency, no toolchain change needed.
                        AnimatedContent(
                            targetState = d.done,
                            transitionSpec = {
                                (scaleIn(
                                    initialScale = 0.6f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                ) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut())
                            },
                            label = "deadlineDoneToggle"
                        ) { isDone ->
                            Icon(
                                if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                "Toggle done",
                                tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                d.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = PunlaDisplay, fontSize = 15.5.sp),
                                color = if (d.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            if (d.isRecurring) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Repeat,
                                    contentDescription = "Recurring",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!d.done) {
                                priorityFlagColor(d.priority)?.let { color ->
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Flag,
                                        contentDescription = "${d.priority} priority",
                                        modifier = Modifier.size(13.dp),
                                        tint = color
                                    )
                                }
                            }
                        }
                        Text(
                            listOfNotNull(d.course, d.type, "${d.priority} priority", d.due).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (countdownText != null) {
                        Tag(countdownText, urgency.badgeBg, urgency.badgeOn, mono = true)
                        Spacer(Modifier.height(6.dp))
                    }
                    Row {
                        if (d.ruleId != null) {
                            IconButton(onClick = { vm.stopDeadlineRecurrence(d.ruleId) }) {
                                Icon(Icons.Default.EventBusy, "Stop repeating", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { vm.deleteDeadline(d) }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineFormDialog(
    defaultDue: String,
    onDismiss: () -> Unit,
    onSave: (Deadline, Boolean) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var title by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("") }
    var due by remember { mutableStateOf(defaultDue) }
    var type by remember { mutableStateOf(TYPES[0]) }
    var priority by remember { mutableStateOf(PRIORITIES[1]) }
    var repeatWeekly by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add deadline") },
        text = {
            Column {
                PunlaField("Title", title, { title = it }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PunlaField("Course", course, { course = it }, placeholder = "Optional", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PunlaField("Due date (yyyy-MM-dd)", due, { due = it }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))

                PunlaDropdownField(
                    "Type",
                    type,
                    TYPES,
                    onSelect = { type = TYPES[it] },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                PunlaDropdownField(
                    "Priority",
                    priority,
                    PRIORITIES,
                    onSelect = { priority = PRIORITIES[it] },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { repeatWeekly = !repeatWeekly }
                ) {
                    Checkbox(checked = repeatWeekly, onCheckedChange = { repeatWeekly = it })
                    Text("Repeats weekly (e.g. weekly problem sets, quizzes)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSave(
                        Deadline(title = title, course = course.ifBlank { null }, due = due, type = type, priority = priority),
                        repeatWeekly
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
