package com.uplb.punla.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uplb.punla.data.entity.AttendanceRecord
import com.uplb.punla.data.entity.AttendanceStatus
import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.allowedAbsences
import com.uplb.punla.ml.projectAttendanceRisk
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
    val attendanceRecords by vm.attendanceRecords.collectAsState()
    // Roadmap C — withhold the "No classes scheduled" empty state until
    // Room's first real emission, so it doesn't flash on cold launch.
    val dataReady by vm.isDataReady.collectAsState()

    var scheduleDay by rememberSaveable { mutableStateOf(DAYS[0]) }
    var viewMode by rememberSaveable { mutableStateOf(0) } // 0 = list, 1 = weekly grid
    var showForm by rememberSaveable { mutableStateOf(false) }
    var editingClassId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteClassId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingClass = remember(editingClassId, classes) { classes.firstOrNull { it.id == editingClassId } }
    val pendingDeleteClass = remember(pendingDeleteClassId, classes) { classes.firstOrNull { it.id == pendingDeleteClassId } }

    LaunchedEffect(openFormOnStart) {
        if (openFormOnStart) { editingClassId = null; showForm = true }
    }

    fun openAdd() {
        if (showForm && editingClassId == null) { showForm = false } else {
            editingClassId = null; showForm = true
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
                    onCancel = { showForm = false; editingClassId = null },
                    onSubmit = { candidate ->
                        vm.addOrUpdateClass(candidate); showForm = false; editingClassId = null
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
                    onCancel = { showForm = false; editingClassId = null },
                    onSubmit = { candidate ->
                        vm.addOrUpdateClass(candidate); showForm = false; editingClassId = null
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
                            onEdit = { editingClassId = it.id; showForm = true },
                            onDelete = { pendingDeleteClassId = it.id },
                            attendanceRecords = attendanceRecords.filter { it.sessionId == c.id },
                            onLogAttended = { vm.logAttendance(it, AttendanceStatus.ATTENDED, source = "schedule") },
                            onLogAbsent = { vm.logAttendance(it, AttendanceStatus.ABSENT, source = "schedule") },
                            onClearAttendance = vm::clearAttendance,
                            termStart = vm.termStartDate,
                            termEnd = vm.termEndDate
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (pendingDeleteClass != null) {
        DestructiveActionDialog(
            title = "Delete ${pendingDeleteClass.code}?",
            message = "This removes the class from your schedule and cannot be undone.",
            onConfirm = {
                vm.deleteClass(pendingDeleteClass)
                pendingDeleteClassId = null
            },
            onDismiss = { pendingDeleteClassId = null }
        )
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
    attendanceRecords: List<AttendanceRecord> = emptyList(),
    onLogAttended: (ClassSession) -> Unit = {},
    onLogAbsent: (ClassSession) -> Unit = {},
    onClearAttendance: (AttendanceRecord) -> Unit = {},
    termStart: java.time.LocalDate,
    termEnd: java.time.LocalDate
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
                val projection = remember(c.absences, termStart, termEnd) {
                    projectAttendanceRisk(c, allowed, termStart, termEnd)
                }
                val overLimit = c.absences >= allowed
                val projectedRisk = projection.risk == "HIGH" || projection.risk == "WATCH"
                val nearLimit = !overLimit && (c.absences == allowed - 1 || projectedRisk)
                val attendanceColor = when {
                    overLimit -> MaterialTheme.colorScheme.error
                    projection.risk == "HIGH" -> MaterialTheme.colorScheme.error
                    nearLimit -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val attendedLogged = attendanceRecords.count { it.status == AttendanceStatus.ATTENDED }
                val absentLogged = attendanceRecords.count { it.status == AttendanceStatus.ABSENT }
                val today = java.time.LocalDate.now()
                val todayDay = when (today.dayOfWeek) {
                    java.time.DayOfWeek.MONDAY -> "Mon"
                    java.time.DayOfWeek.TUESDAY -> "Tue"
                    java.time.DayOfWeek.WEDNESDAY -> "Wed"
                    java.time.DayOfWeek.THURSDAY -> "Thu"
                    java.time.DayOfWeek.FRIDAY -> "Fri"
                    java.time.DayOfWeek.SATURDAY -> "Sat"
                    java.time.DayOfWeek.SUNDAY -> "Sun"
                }
                val todayRecord = attendanceRecords.firstOrNull {
                    it.occurrenceDate == today.toString() && it.scheduledStart == c.start
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
                    Text(
                        "$attendedLogged attended · $absentLogged absent logged",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (c.day == todayDay) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Today's attendance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = todayRecord?.status == AttendanceStatus.ATTENDED,
                            onClick = { onLogAttended(c) },
                            label = { Text(if (todayRecord?.status == AttendanceStatus.ATTENDED) "Attended ✓" else "Attended") },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = todayRecord?.status == AttendanceStatus.ABSENT,
                            onClick = { onLogAbsent(c) },
                            label = { Text(if (todayRecord?.status == AttendanceStatus.ABSENT) "Absent ✓" else "Absent") },
                            leadingIcon = { Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        if (todayRecord != null) {
                            TextButton(onClick = { onClearAttendance(todayRecord) }) { Text("Clear") }
                        }
                    }
                }
                if (!overLimit && projection.risk != "LOW") {
                    Text(
                        if (projection.risk == "HIGH")
                            "At your current pace, you may reach the limit before term end. ${projection.explanation}"
                        else
                            "Attendance pace to watch. ${projection.explanation}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = attendanceColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
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
    var code by rememberSaveable(editing?.id) { mutableStateOf(editing?.code ?: "") }
    var section by rememberSaveable(editing?.id) { mutableStateOf(editing?.section ?: "") }
    var title by rememberSaveable(editing?.id) { mutableStateOf(editing?.title ?: "") }
    var day by rememberSaveable(editing?.id) { mutableStateOf(editing?.day ?: defaultDay) }
    var type by rememberSaveable(editing?.id) { mutableStateOf(editing?.type ?: "lec") }
    var start by rememberSaveable(editing?.id) { mutableStateOf(editing?.start ?: "07:00") }
    var end by rememberSaveable(editing?.id) { mutableStateOf(editing?.end ?: "08:00") }
    var room by rememberSaveable(editing?.id) { mutableStateOf(editing?.room ?: "") }
    var instructor by rememberSaveable(editing?.id) { mutableStateOf(editing?.instructor ?: "") }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var codeTouched by rememberSaveable(editing?.id) { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable(editing?.id) { mutableStateOf(false) }

    val initialCode = editing?.code ?: ""
    val initialSection = editing?.section ?: ""
    val initialTitle = editing?.title ?: ""
    val initialDay = editing?.day ?: defaultDay
    val initialType = editing?.type ?: "lec"
    val initialStart = editing?.start ?: "07:00"
    val initialEnd = editing?.end ?: "08:00"
    val initialRoom = editing?.room ?: ""
    val initialInstructor = editing?.instructor ?: ""
    val isDirty = code != initialCode || section != initialSection || title != initialTitle ||
        day != initialDay || type != initialType || start != initialStart || end != initialEnd ||
        room != initialRoom || instructor != initialInstructor

    fun requestCancel() {
        if (isDirty) showDiscardConfirm = true else onCancel()
    }

    BackHandler { requestCancel() }

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
                PunlaField(
                    "Course code",
                    code,
                    { code = it; codeTouched = true },
                    placeholder = "e.g. MATH 20",
                    modifier = Modifier.weight(1f),
                    isError = codeTouched && code.isBlank(),
                    supportingText = if (codeTouched && code.isBlank()) "Course code is required." else null
                )
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
                        codeTouched = true
                        if (code.isNotBlank() && !invalidTimeRange) {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onSubmit(
                                ClassSession(
                                    id = editing?.id ?: java.util.UUID.randomUUID().toString(),
                                    code = code.trim(), section = section.trim().ifBlank { null }, title = title.trim().ifBlank { null },
                                    day = day, type = type, start = start, end = end,
                                    room = room.trim().ifBlank { null }, instructor = instructor.trim().ifBlank { null },
                                    absences = editing?.absences ?: 0
                                )
                            )
                        }
                    },
                    enabled = !invalidTimeRange,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalPunlaPalette.current.ink, contentColor = LocalPunlaPalette.current.paper)
                ) { Text(if (editing != null) "Save changes" else "Add class", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)) }
                OutlinedButton(
                    onClick = { requestCancel() },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) { Text("Cancel", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)) }
            }
        }
    }

    if (showDiscardConfirm) {
        DestructiveActionDialog(
            title = "Discard changes?",
            message = "Your unsaved class details will be lost.",
            confirmLabel = "Discard",
            onConfirm = { showDiscardConfirm = false; onCancel() },
            onDismiss = { showDiscardConfirm = false }
        )
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
