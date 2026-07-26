package com.uplb.punla.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uplb.punla.data.entity.GradeCourse
import com.uplb.punla.data.entity.Semester
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ui.theme.PunlaMono
import com.uplb.punla.ui.theme.LocalPunlaPalette
import kotlinx.coroutines.flow.flowOf

private val GRADE_OPTIONS = listOf(
    "1.00", "1.25", "1.50", "1.75", "2.00", "2.25", "2.50", "2.75", "3.00", "4.00", "5.00", "INC", "DRP", "W"
)

/** sum(units × grade) / sum(units) over only numerically-graded courses —
 * shared by both the per-semester and cumulative (all-semester) summaries
 * so the two stay consistent. */
private fun computeGwa(courses: List<GradeCourse>): Pair<Double?, Double> {
    val gradable = courses.filter { it.grade.toDoubleOrNull() != null && it.units > 0 }
    val totalUnits = gradable.sumOf { it.units }
    val gwa = if (totalUnits > 0) gradable.sumOf { it.units * it.grade.toDouble() } / totalUnits else null
    return gwa to totalUnits
}

/**
 * GWA screen: semester tabs, a weighted-average summary compared against the
 * user's CHED scholarship retention target, and an editable course table.
 * Mirrors the original web app's gwa() computation: sum(units × grade) / sum(units),
 * over only courses with a numeric UPLB grade (1.00 best – 5.00 fail); INC/DRP/W
 * are excluded from the average, matching UPLB's own registrar convention.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(vm: PunlaViewModel, openFormOnStart: Boolean = false) {
    val semesters by vm.semesters.collectAsState()
    val selectedId = vm.selectedSemesterId

    LaunchedEffect(semesters) {
        if (semesters.isNotEmpty() && semesters.none { it.id == selectedId }) {
            vm.selectSemester(semesters.first().id)
        }
    }

    val selectedSemester = semesters.firstOrNull { it.id == vm.selectedSemesterId }
    val courses by remember(selectedSemester?.id) {
        selectedSemester?.let { vm.coursesFlow(it.id) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    var showSemesterDialog by remember { mutableStateOf(false) }
    var showCourseDialog by remember { mutableStateOf(false) }
    var editingCourse by remember { mutableStateOf<GradeCourse?>(null) }

    // Quick-add "grade": mirrors the web app's rule — jump into "new course"
    // if a semester already exists, otherwise prompt to create one first.
    LaunchedEffect(openFormOnStart, semesters) {
        if (openFormOnStart) {
            if (semesters.isEmpty()) showSemesterDialog = true
            else { editingCourse = null; showCourseDialog = true }
        }
    }

    val (gwa, totalUnits) = computeGwa(courses)

    // Roadmap #1: cumulative GWA across every semester, not just the
    // selected tab — this is usually the number a scholarship/CHED target
    // actually tracks.
    val allCourses by vm.allCourses.collectAsState()
    val (cumulativeGwa, cumulativeUnits) = computeGwa(allCourses)

    Scaffold(
        floatingActionButton = {
            if (selectedSemester != null) {
                FloatingActionButton(
                    onClick = { editingCourse = null; showCourseDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Icon(Icons.Default.Add, "Add course") }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (semesters.isEmpty()) {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                EmptyState(
                    icon = Icons.Default.Grade,
                    message = "No semesters yet. Add one to start tracking your GWA.",
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { showSemesterDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                ) { Text("Add first semester") }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SemesterTabs(
                        semesters = semesters,
                        selectedId = vm.selectedSemesterId,
                        onSelect = { vm.selectSemester(it) },
                        onAddNew = { showSemesterDialog = true },
                        onDelete = { vm.deleteSemester(it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    GwaSummaryCard(vm = vm, gwa = gwa, totalUnits = totalUnits)
                    Spacer(Modifier.height(12.dp))
                    CumulativeGwaCard(gwa = cumulativeGwa, totalUnits = cumulativeUnits, target = vm.repo.chedTarget)
                    Spacer(Modifier.height(12.dp))
                    WhatIfCalculatorCard(
                        currentGwa = cumulativeGwa,
                        currentUnits = cumulativeUnits,
                        target = vm.repo.chedTarget
                    )
                }
                if (courses.isNotEmpty()) {
                    item { SectionLabel("Courses") }
                    items(courses.sortedBy { it.code }, key = { it.id }) { c ->
                        CourseCard(
                            course = c,
                            onEdit = { editingCourse = c; showCourseDialog = true },
                            onDelete = { vm.deleteCourse(c) }
                        )
                    }
                } else {
                    item {
                        EmptyState(
                            icon = Icons.Default.Grade,
                            message = "No courses added for this semester yet. Tap + to add one."
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showSemesterDialog) {
        SemesterFormDialog(
            onDismiss = { showSemesterDialog = false },
            onSave = { label -> vm.addSemester(label); showSemesterDialog = false }
        )
    }

    if (showCourseDialog && selectedSemester != null) {
        CourseFormDialog(
            semesterId = selectedSemester.id,
            existing = editingCourse,
            onDismiss = { showCourseDialog = false },
            onSave = { vm.upsertCourse(it); showCourseDialog = false }
        )
    }
}

@Composable
private fun SemesterTabs(
    semesters: List<Semester>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAddNew: () -> Unit,
    onDelete: (Semester) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(semesters, key = { it.id }) { s ->
            FilterChip(
                selected = s.id == selectedId,
                onClick = { onSelect(s.id) },
                label = { Text(s.label) },
                trailingIcon = if (s.id == selectedId) {
                    {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete semester",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onDelete(s) }
                        )
                    }
                } else null
            )
        }
        item {
            AssistChip(onClick = onAddNew, label = { Text("+ New") })
        }
    }
}

@Composable
private fun GwaSummaryCard(vm: PunlaViewModel, gwa: Double?, totalUnits: Double) {
    val target = vm.repo.chedTarget
    var targetInput by remember(target) { mutableStateOf(target?.let { "%.2f".format(it) } ?: "") }
    // gwaStatusClass(gwa, target) in the web app — UPLB scale: lower is better.
    //   gwa > target          -> urgent (maroon warn-box, the .warn-box default)
    //   gwa > target - 0.25   -> soon   (mango warn-box)
    //   else                  -> ok     (leaf warn-box)
    val status = when {
        gwa == null || target == null -> "ok"
        gwa > target -> "urgent"
        gwa > target - 0.25 -> "soon"
        else -> "ok"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                "urgent" -> MaterialTheme.colorScheme.secondaryContainer
                "soon" -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "SEMESTER GWA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            // .seed-amount { font-mono 24px/600 } — not the 30px headlineLarge scale.
            Text(
                gwa?.let { "%.3f".format(it) } ?: "—",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = PunlaMono, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
                color = when (status) {
                    "urgent" -> MaterialTheme.colorScheme.secondary
                    "soon" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            Text(
                "${"%.1f".format(totalUnits)} units counted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (target != null) {
                val (tagBg, tagOn, tagText) = when (status) {
                    "urgent" -> Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.secondary,
                        "Above target of ${"%.2f".format(target)}"
                    )
                    "soon" -> Triple(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.tertiary,
                        "Close to the line — target ${"%.2f".format(target)}"
                    )
                    else -> Triple(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.primary,
                        "On track for ${"%.2f".format(target)}"
                    )
                }
                Tag(tagText, tagBg, tagOn)
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PunlaField(
                    "CHED / scholarship target GWA",
                    targetInput,
                    { targetInput = it },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.updateChedTarget(targetInput.toDoubleOrNull()) }) { Text("Set") }
            }
        }
    }
}

/**
 * Roadmap #1 — running GWA across every semester, shown alongside (not
 * instead of) the per-semester GwaSummaryCard above. This is the number
 * that actually matters for a scholarship/CHED retention target, since
 * those are evaluated cumulatively rather than per term.
 */
@Composable
private fun CumulativeGwaCard(gwa: Double?, totalUnits: Double, target: Double?) {
    val status = when {
        gwa == null || target == null -> "ok"
        gwa > target -> "urgent"
        gwa > target - 0.25 -> "soon"
        else -> "ok"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "CUMULATIVE GWA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${"%.1f".format(totalUnits)} units across all semesters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                gwa?.let { "%.3f".format(it) } ?: "—",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = PunlaMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
                color = when (status) {
                    "urgent" -> MaterialTheme.colorScheme.secondary
                    "soon" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

/**
 * Roadmap #2 — turns the passive CHED/scholarship target into something
 * actionable: given the cumulative units + GWA already locked in, and how
 * many units are still left to be graded, what average grade is needed
 * across those remaining units to land on target?
 *
 * UPLB scale: lower is better (1.00 best – 5.00 fail), so the "needed
 * average" is a minimum quality bar, not a minimum number.
 */
@Composable
private fun WhatIfCalculatorCard(currentGwa: Double?, currentUnits: Double, target: Double?) {
    var remainingUnitsInput by remember { mutableStateOf("") }
    val remainingUnits = remainingUnitsInput.toDoubleOrNull()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "WHAT GRADE DO I NEED?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            if (target == null) {
                Text(
                    "Set a CHED / scholarship target GWA above to use this calculator.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Text(
                "Units still ungraded this term or ahead",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            PunlaField(
                "Remaining units",
                remainingUnitsInput,
                { remainingUnitsInput = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            when {
                remainingUnits == null || remainingUnits <= 0 -> {
                    Text(
                        "Enter the units you still expect to be graded on to see the average you need.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                currentUnits <= 0 -> {
                    Text(
                        "Add some graded courses first — the calculator needs a starting cumulative GWA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    val currentSum = (currentGwa ?: 0.0) * currentUnits
                    val neededTotalUnits = currentUnits + remainingUnits
                    val neededAvg = (target * neededTotalUnits - currentSum) / remainingUnits

                    val (message, color) = when {
                        neededAvg <= 1.00 -> "Target is already secured — even a 1.00 average from here can't push you past it." to MaterialTheme.colorScheme.primary
                        neededAvg > 5.00 -> "Not mathematically reachable over just $remainingUnits remaining units, even with a 1.00 average." to MaterialTheme.colorScheme.secondary
                        else -> "You need about %.2f average over the next %.1f units to reach %.2f.".format(neededAvg, remainingUnits, target) to
                            if (neededAvg <= 2.00) MaterialTheme.colorScheme.primary
                            else if (neededAvg <= 3.00) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.secondary
                    }
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseCard(course: GradeCourse, onEdit: () -> Unit, onDelete: () -> Unit) {
    val isNumeric = course.grade.toDoubleOrNull() != null
    val accent = if (isNumeric) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = onEdit
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            AccentBar(accent)
            Row(
                Modifier.padding(14.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(course.code, style = MaterialTheme.typography.titleSmall)
                    course.title?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${"%.1f".format(course.units)} units",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        course.grade.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = PunlaMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SemesterFormDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add semester") },
        text = {
            PunlaField(
                "Semester label",
                label, { label = it },
                placeholder = "e.g. AY 2026–2027, 1st Sem",
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (label.isNotBlank()) onSave(label) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseFormDialog(
    semesterId: String,
    existing: GradeCourse?,
    onDismiss: () -> Unit,
    onSave: (GradeCourse) -> Unit
) {
    var code by remember(existing?.id) { mutableStateOf(existing?.code ?: "") }
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var units by remember(existing?.id) { mutableStateOf(existing?.units?.let { if (it > 0) it.toString() else "" } ?: "3") }
    var grade by remember(existing?.id) { mutableStateOf(existing?.grade?.ifBlank { GRADE_OPTIONS.first() } ?: GRADE_OPTIONS.first()) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add course" else "Edit course") },
        text = {
            Column {
                PunlaField("Course code", code, { code = it }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PunlaField("Course title", title, { title = it }, placeholder = "Optional", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PunlaField(
                    "Units",
                    units, { units = it },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PunlaDropdownField(
                    "Grade",
                    grade,
                    GRADE_OPTIONS,
                    onSelect = { grade = GRADE_OPTIONS[it] },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (code.isNotBlank()) {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onSave(
                        GradeCourse(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            semesterId = semesterId,
                            code = code,
                            title = title.ifBlank { null },
                            units = units.toDoubleOrNull() ?: 0.0,
                            grade = grade
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
