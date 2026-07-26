package com.uplb.punla.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.allowedAbsences
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ui.theme.LocalPunlaPalette
import com.uplb.punla.ui.theme.PunlaDisplay
import com.uplb.punla.ui.theme.PunlaMono

private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val DAY_FULL = mapOf(
    "Mon" to "Monday", "Tue" to "Tuesday", "Wed" to "Wednesday",
    "Thu" to "Thursday", "Fri" to "Friday", "Sat" to "Saturday"
)
private val TYPE_LABELS = listOf("lec" to "Lecture", "lab" to "Laboratory")

// Roadmap — dropdown time picker. 15-minute increments, 6:00AM–9:45PM,
// covers the university's typical class-block range without free typing.
private val TIME_OPTIONS: List<String> = buildList {
    for (h in 6..21) {
        for (m in listOf(0, 15, 30, 45)) {
            add("${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}")
        }
    }
}


// web: GRID_START_HOUR / GRID_END_HOUR / GRID_HOUR_PX
private const val GRID_START_HOUR = 7
private const val GRID_END_HOUR = 21
private val GRID_HOUR_DP = 40.dp

/**
 * Feature plan — Free-Time Finder. Gaps of at least 30 minutes between
 * classes on [day], within [dayStart]/[dayEnd]. Reuses the same
 * day/start/end overlap shape as the conflict-detection check below.
 */
internal fun freeSlotsFor(
    day: String,
    classes: List<ClassSession>,
    dayStart: String = "07:00",
    dayEnd: String = "20:00"
): List<Pair<String, String>> {
    val sorted = classes.filter { it.day == day }.sortedBy { it.start }
    val gaps = mutableListOf<Pair<String, String>>()
    var cursor = dayStart
    for (c in sorted) {
        if (cursor < c.start) gaps += cursor to c.start
        if (c.end > cursor) cursor = c.end
    }
    if (cursor < dayEnd) gaps += cursor to dayEnd
    return gaps.filter { (s, e) -> minutesBetween(s, e) >= 30 } // drop slivers
}

/** Safely parses "HH:mm" into (hour, minute), defaulting missing/malformed
 * parts to 0 instead of crashing (e.g. blank string, "7" with no colon). */
private fun parseTime(t: String): Pair<Int, Int> {
    val parts = t.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return h to m
}

internal fun minutesBetween(start: String, end: String): Int {
    val (sh, sm) = parseTime(start)
    val (eh, em) = parseTime(end)
    return (eh * 60 + em) - (sh * 60 + sm)
}

/** web: fmtTime() — "07:00" -> "7:00AM" */
internal fun fmtTime(t: String): String {
    if (t.isBlank()) return ""
    val parts = t.split(":").map { it.toIntOrNull() ?: 0 }
    val h = parts.getOrElse(0) { 0 }
    val m = parts.getOrElse(1) { 0 }
    val ap = if (h >= 12) "PM" else "AM"
    var hh = h % 12
    if (hh == 0) hh = 12
    return "$hh:${m.toString().padStart(2, '0')}$ap"
}

@Composable
fun ScheduleScreen(vm: PunlaViewModel, openFormOnStart: Boolean = false, onStudyHere: (String?) -> Unit = {}) {
    val classes by vm.classes.collectAsState()
    val deadlines by vm.deadlines.collectAsState()
    // Roadmap C — withhold the "No classes scheduled" empty state until
    // Room's first real emission, so it doesn't flash on cold launch.
    val dataReady by vm.isDataReady.collectAsState()

    var scheduleDay by remember { mutableStateOf(DAYS[0]) }
    var viewMode by remember { mutableStateOf(0) } // 0 = list, 1 = weekly grid
    var showForm by remember { mutableStateOf(false) }
    var editingClass by remember { mutableStateOf<ClassSession?>(null) }

    LaunchedEffect(openFormOnStart) {
        if (openFormOnStart) { editingClass = null; showForm = true }
    }

    fun openAdd() {
        if (showForm && editingClass == null) { showForm = false } else {
            editingClass = null; showForm = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // web: .seg — List / Weekly grid switcher
        SegmentedControl(
            options = listOf("List", "Weekly grid"),
            selected = viewMode,
            onSelect = { viewMode = it },
            modifier = Modifier.padding(bottom = 14.dp)
        )

        if (viewMode == 1) {
            InlineAddButton(
                label = "Add class",
                open = showForm,
                onClick = { openAdd() },
                modifier = Modifier.align(Alignment.End).padding(bottom = 12.dp)
            )
            if (showForm) {
                ClassFormCard(
                    editing = editingClass,
                    allClasses = classes,
                    defaultDay = scheduleDay,
                    onCancel = { showForm = false; editingClass = null },
                    onSubmit = { candidate ->
                        vm.addOrUpdateClass(candidate); showForm = false; editingClass = null
                    },
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }
            ScheduleGrid(classes, Modifier.weight(1f))
        } else {
            // web: .day-pills
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DAYS.forEach { d ->
                    DayPill(
                        label = d,
                        active = scheduleDay == d,
                        hasDot = classes.any { it.day == d },
                        onClick = { scheduleDay = d }
                    )
                }
            }

            // Feature plan — Free-Time Finder chips, right under the day pills.
            // Free-Time Study Suggestions (Phase 1) — a "Study here?" chip on
            // a slot that also matches an upcoming deadline, for people who
            // browse this day view rather than the Dashboard card.
            FreeTimeRow(
                day = scheduleDay,
                classes = classes,
                deadlines = deadlines,
                pomodoroWorkMinutes = vm.pomodoroWorkMinutes,
                onStudyHere = onStudyHere
            )

            InlineAddButton(
                label = "Add class",
                open = showForm,
                onClick = { openAdd() },
                modifier = Modifier.align(Alignment.End).padding(bottom = 12.dp)
            )

            if (showForm) {
                ClassFormCard(
                    editing = editingClass,
                    allClasses = classes,
                    defaultDay = scheduleDay,
                    onCancel = { showForm = false; editingClass = null },
                    onSubmit = { candidate ->
                        vm.addOrUpdateClass(candidate); showForm = false; editingClass = null
                    },
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }

            val items = classes.filter { it.day == scheduleDay }.sortedBy { it.start }
            if (items.isEmpty()) {
                if (dataReady) {
                    EmptyState(
                        icon = Icons.Default.CalendarMonth,
                        message = "No classes scheduled for ${DAY_FULL[scheduleDay]}.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(items, key = { it.id }) { c ->
                        ClassCard(
                            c = c,
                            deadlines = deadlines,
                            onEdit = { editingClass = it; showForm = true },
                            onDelete = { vm.deleteClass(it) },
                            onMarkAbsent = { vm.incrementAbsence(it) },
                            onUndoAbsence = { vm.decrementAbsence(it) }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/**
 * Feature plan — Free-Time Finder chips: "Free: 9:00AM–10:30AM" style tags
 * for every 30+ minute gap in [day]'s schedule. Renders nothing on a fully
 * booked day (no gaps left over 30 minutes).
 */
@Composable
private fun FreeTimeRow(
    day: String,
    classes: List<ClassSession>,
    deadlines: List<com.uplb.punla.data.entity.Deadline> = emptyList(),
    pomodoroWorkMinutes: Int = 25,
    onStudyHere: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val slots = remember(day, classes) { freeSlotsFor(day, classes) }
    if (slots.isEmpty()) return

    // Free-Time Study Suggestions (Phase 1) — reuses the same matching
    // function the Dashboard card uses, just scoped to this screen's
    // currently-selected day instead of today/tomorrow.
    val suggestion = remember(day, classes, deadlines, pomodoroWorkMinutes) {
        com.uplb.punla.ui.pomodoro.suggestStudySlot(
            day, day, classes, deadlines, pomodoroWorkMinutes, java.time.LocalDate.now()
        )
    }

    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Free:",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        slots.forEach { (s, e) ->
            Tag(
                "${fmtTime(s)}\u2013${fmtTime(e)}",
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.secondary,
                mono = true
            )
        }
        if (suggestion != null) {
            Tag(
                "Study here?",
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onStudyHere(suggestion.course) }
            )
        }
    }
}

/** web: classCard() — a `.card.class-card` row for one scheduled session. */
@Composable
private fun ClassCard(
    c: ClassSession,
    deadlines: List<com.uplb.punla.data.entity.Deadline>,
    onEdit: (ClassSession) -> Unit,
    onDelete: (ClassSession) -> Unit,
    onMarkAbsent: (ClassSession) -> Unit = {},
    onUndoAbsence: (ClassSession) -> Unit = {}
) {
    val isLab = c.type == "lab"
    val accent = if (isLab) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val badgeContainer = if (isLab) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val badgeOnContainer = if (isLab) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    val linked = deadlines
        .filter { !it.done && it.course != null && it.course.trim().equals(c.code.trim(), ignoreCase = true) }
        .sortedBy { it.due }
        .take(3)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            AccentBar(accent)
            Column(Modifier.padding(14.dp).fillMaxWidth()) {
                // .class-row-top
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "${fmtTime(c.start)} \u2013 ${fmtTime(c.end)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PunlaMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            c.code,
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = PunlaDisplay),
                            modifier = Modifier.padding(top = 2.dp, bottom = 1.dp)
                        )
                    }
                    // .row-actions
                    Row {
                        IconButton(onClick = { onEdit(c) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, "Edit class", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { onDelete(c) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, "Delete class", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                val subtitle = buildString {
                    append(c.title ?: "")
                    if (!c.section.isNullOrBlank()) { if (isNotEmpty()) append(" \u00b7 "); append("Sec ${c.section}") }
                }
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    "${c.room ?: "Room TBA"}${c.instructor?.let { " \u00b7 $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Tag(if (isLab) "LAB" else "LECTURE", badgeContainer, badgeOnContainer)

                // Roadmap #4 — attendance tracking. UP drops a student once
                // absences hit 20% of a class's total meetings; allowedAbsences()
                // estimates that threshold for this specific weekly block.
                Spacer(Modifier.height(10.dp))
                val allowed = c.allowedAbsences()
                val overLimit = c.absences >= allowed
                val nearLimit = !overLimit && c.absences == allowed - 1
                val attendanceColor = when {
                    overLimit -> MaterialTheme.colorScheme.error
                    nearLimit -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Default.EventBusy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = attendanceColor
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (overLimit) "${c.absences}/$allowed absences \u2014 limit reached" else "${c.absences}/$allowed absences used",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = if (overLimit || nearLimit) FontWeight.SemiBold else FontWeight.Normal),
                        color = attendanceColor,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onUndoAbsence(c) }, modifier = Modifier.size(24.dp), enabled = c.absences > 0) {
                        Icon(
                            Icons.Default.Remove,
                            "Undo one absence",
                            modifier = Modifier.size(14.dp),
                            tint = if (c.absences > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { onMarkAbsent(c) }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Add,
                            "Mark absent",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (linked.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.outline)
                    Text("Linked deadlines", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    linked.forEach { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(d.title, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = MaterialTheme.colorScheme.onSurface)
                            Text(d.due, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** web: classForm() — inline card with a conflict warning + the add/edit fields.
 * Roadmap #3: the overlap check runs live off the in-progress day/start/end
 * fields and only ever warns — it never blocks the save. */
@Composable
private fun ClassFormCard(
    editing: ClassSession?,
    allClasses: List<ClassSession>,
    defaultDay: String,
    onCancel: () -> Unit,
    onSubmit: (ClassSession) -> Unit,
    modifier: Modifier = Modifier
) {
    var code by remember(editing?.id) { mutableStateOf(editing?.code ?: "") }
    var section by remember(editing?.id) { mutableStateOf(editing?.section ?: "") }
    var title by remember(editing?.id) { mutableStateOf(editing?.title ?: "") }
    var day by remember(editing?.id) { mutableStateOf(editing?.day ?: defaultDay) }
    var type by remember(editing?.id) { mutableStateOf(editing?.type ?: "lec") }
    var start by remember(editing?.id) { mutableStateOf(editing?.start ?: "07:00") }
    var end by remember(editing?.id) { mutableStateOf(editing?.end ?: "08:00") }
    var room by remember(editing?.id) { mutableStateOf(editing?.room ?: "") }
    var instructor by remember(editing?.id) { mutableStateOf(editing?.instructor ?: "") }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    val conflict = remember(day, start, end, editing?.id, allClasses) {
        allClasses.firstOrNull { existing ->
            existing.id != editing?.id &&
                existing.day == day &&
                start < existing.end && end > existing.start
        }
    }

    // Roadmap #3 follow-up — the time dropdowns let start/end be picked
    // independently, so nothing previously stopped a start time at or after
    // the end time. That's blocked outright (not just warned about, unlike
    // the overlap check above) since a class with zero or negative duration
    // isn't a state that's ever intentional.
    val invalidTimeRange = start >= end

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp)) {
            if (invalidTimeRange) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .padding(bottom = 2.dp)
                ) {
                    Text(
                        "Start time must be before end time.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            if (conflict != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .padding(bottom = 2.dp)
                ) {
                    Text(
                        "This overlaps with ${conflict.code} (${fmtTime(conflict.start)}\u2013${fmtTime(conflict.end)}) on ${DAY_FULL[conflict.day]}. You can still save — just double check it's intentional.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PunlaField("Course code", code, { code = it }, placeholder = "e.g. MATH 20", modifier = Modifier.weight(1f))
                PunlaField("Section", section, { section = it }, placeholder = "e.g. WFY-2L", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            PunlaField("Course title", title, { title = it }, placeholder = "e.g. Analytic Geometry", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PunlaDropdownField(
                    "Day", DAY_FULL[day] ?: day, DAYS.map { DAY_FULL[it] ?: it },
                    onSelect = { day = DAYS[it] }, modifier = Modifier.weight(1f)
                )
                PunlaDropdownField(
                    "Type", TYPE_LABELS.first { it.first == type }.second, TYPE_LABELS.map { it.second },
                    onSelect = { type = TYPE_LABELS[it].first }, modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PunlaDropdownField(
                    "Start time", fmtTime(start), TIME_OPTIONS.map { fmtTime(it) },
                    onSelect = { start = TIME_OPTIONS[it] }, modifier = Modifier.weight(1f)
                )
                PunlaDropdownField(
                    "End time", fmtTime(end), TIME_OPTIONS.map { fmtTime(it) },
                    onSelect = { end = TIME_OPTIONS[it] }, modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PunlaField("Room", room, { room = it }, placeholder = "e.g. AECH 200", modifier = Modifier.weight(1f))
                PunlaField("Instructor", instructor, { instructor = it }, placeholder = "e.g. Dr. Santos", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            // .form-actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (code.isNotBlank() && !invalidTimeRange) {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onSubmit(
                                ClassSession(
                                    id = editing?.id ?: java.util.UUID.randomUUID().toString(),
                                    code = code, section = section.ifBlank { null }, title = title.ifBlank { null },
                                    day = day, type = type, start = start, end = end,
                                    room = room.ifBlank { null }, instructor = instructor.ifBlank { null },
                                    absences = editing?.absences ?: 0
                                )
                            )
                        }
                    },
                    enabled = code.isNotBlank() && !invalidTimeRange,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalPunlaPalette.current.ink, contentColor = LocalPunlaPalette.current.paper)
                ) { Text(if (editing != null) "Save changes" else "Add class", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)) }
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) { Text("Cancel", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)) }
            }
        }
    }
}

/**
 * web: renderScheduleGrid() — a horizontally-scrolling weekly timetable:
 * a fixed time-label column plus one column per day, with class blocks
 * absolutely positioned by minutes-since-GRID_START_HOUR.
 */
@Composable
private fun ScheduleGrid(classes: List<ClassSession>, modifier: Modifier = Modifier) {
    val totalHours = GRID_END_HOUR - GRID_START_HOUR
    val bodyHeight = GRID_HOUR_DP * totalHours

    Box(
        modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            // time column
            Column(Modifier.width(42.dp)) {
                Box(Modifier.fillMaxWidth().height(28.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                Box(Modifier.width(42.dp).height(bodyHeight)) {
                    for (h in GRID_START_HOUR until GRID_END_HOUR) {
                        val top = GRID_HOUR_DP * (h - GRID_START_HOUR)
                        Text(
                            fmtTime("${h.toString().padStart(2, '0')}:00"),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PunlaMono, fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = top - 6.dp)
                                .padding(end = 4.dp)
                        )
                    }
                }
            }
            DAYS.forEach { d ->
                val dayClasses = classes.filter { it.day == d }
                Column(
                    Modifier
                        .width(88.dp)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline))
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(28.dp).background(MaterialTheme.colorScheme.surfaceVariant).border(androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(d, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                    }
                    Box(
                        Modifier
                            .width(88.dp)
                            .height(bodyHeight)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        for (h in GRID_START_HOUR..GRID_END_HOUR) {
                            val top = GRID_HOUR_DP * (h - GRID_START_HOUR)
                            Box(Modifier.fillMaxWidth().offset(y = top).height(1.dp).background(MaterialTheme.colorScheme.outline))
                        }
                        dayClasses.forEach { c ->
                            val (sh, sm) = parseTime(c.start)
                            val (eh, em) = parseTime(c.end)
                            val startMin = ((sh * 60 + sm) - GRID_START_HOUR * 60).coerceAtLeast(0)
                            val endMin = ((eh * 60 + em) - GRID_START_HOUR * 60).coerceAtMost(totalHours * 60)
                            val topDp = GRID_HOUR_DP * (startMin / 60f)
                            val heightDp = (GRID_HOUR_DP * ((endMin - startMin) / 60f)).let { if (it < 18.dp) 18.dp else it }
                            val isLab = c.type == "lab"
                            Box(
                                Modifier
                                    .offset(y = topDp)
                                    .padding(horizontal = 2.dp)
                                    .fillMaxWidth()
                                    .height(heightDp)
                                    .background(
                                        if (isLab) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        BorderStroke(0.dp, Color.Transparent)
                                    )
                            ) {
                                Column(Modifier.padding(horizontal = 5.dp, vertical = 3.dp)) {
                                    Text(
                                        c.code,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        "${fmtTime(c.start)}\u2013${fmtTime(c.end)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = PunlaMono, fontSize = 8.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
