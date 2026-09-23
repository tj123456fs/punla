package com.uplb.punla.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uplb.punla.context.EnergyLevel
import com.uplb.punla.data.entity.AttendanceStatus
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.planning.*
import com.uplb.punla.ui.PunlaViewModel
import java.time.*
import java.time.format.DateTimeFormatter

private data class OsForm(val title: String, val fields: List<Pair<String, String>>, val save: (List<String>) -> Unit)
private fun clock(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EnergyCheckIn(energy: EnergyLevel, onSelect: (EnergyLevel) -> Unit) {
    Column {
        Text("How are you right now?", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(EnergyLevel.DRAINED to "Drained", EnergyLevel.OKAY to "Okay", EnergyLevel.LOCKED_IN to "Locked in").forEach { (level, label) ->
                FilterChip(selected = energy == level, onClick = { onSelect(level) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        Text("Check-in lasts 6 hours. You can change it anytime.", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudentOsScreen(vm: PunlaViewModel, initialTab: Int = 0, onOpen: (String, String?) -> Unit) {
    val os = vm.studentOs
    val snap by os.state.collectAsStateWithLifecycle()
    val message by os.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 5)) }
    var form by remember { mutableStateOf<OsForm?>(null) }
    var convert by remember { mutableStateOf<InboxCapture?>(null) }
    var captureText by rememberSaveable { mutableStateOf("") }
    var week by rememberSaveable { mutableStateOf(false) }
    var assistantInput by rememberSaveable { mutableStateOf("") }
    var assistantAnswer by rememberSaveable { mutableStateOf("Ask about your day, study time, or tonight. You can also draft an expense or move a planned session.") }
    var assistantAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()

    fun editTask(task: WorkItem) {
        val p = snap.profile(task.id)
        form = OsForm(task.title, listOf("Estimated total minutes" to (task.estimateMinutes ?: 25).toString(),
            "Progress (%)" to p.progress.toString(), "Importance (1–5)" to p.importance.toString(),
            "Effort (1 easy, 2 moderate, 3 hard)" to p.effort.toString(), "Due time (HH:mm)" to p.dueTime)) { v ->
            val minutes = v[0].toInt(); val progress = v[1].toInt(); val importance = v[2].toInt(); val effort = v[3].toInt()
            require(minutes in 5..10080 && progress in 0..100 && importance in 1..5 && effort in 1..3) { "Check the field ranges." }
            LocalTime.parse(v[4]); os.savePreferences(p.copy(estimatedMinutes = minutes, progress = progress, importance = importance, effort = effort, dueTime = v[4]))
        }
    }
    fun editBlock(block: DayPlanBlock) {
        val local = Instant.ofEpochMilli(block.startAt).atZone(zone)
        form = OsForm("Move or resize", listOf("Date (YYYY-MM-DD)" to local.toLocalDate().toString(),
            "Start (HH:mm)" to local.toLocalTime().withSecond(0).withNano(0).toString(), "Minutes" to block.window().minutes.toString())) { v ->
            val start = LocalDate.parse(v[0]).atTime(LocalTime.parse(v[1])).atZone(zone).toInstant().toEpochMilli()
            val minutes = v[2].toInt(); require(minutes in 5..240)
            val other = snap.blocks.filter { it.id != block.id && it.status == "PLANNED" }.map { it.window() }
            require(DayPlanner.canPlace(TimeWindow(start, start + minutes * 60000L), snap.busy + other, System.currentTimeMillis())) { "This time overlaps a commitment or is in the past." }
            os.changeBlock(block, start, minutes)
        }
    }
    fun editLife(item: LifeCommitment? = null) {
        form = OsForm(if (item == null) "Add personal commitment" else "Edit personal commitment", listOf(
            "Title" to (item?.title ?: "Lunch"), "Start (HH:mm)" to (item?.startTime ?: "12:00"),
            "End (HH:mm)" to (item?.endTime ?: "13:00"), "Days (1=Mon … 7=Sun, comma separated)" to (item?.days ?: "1,2,3,4,5,6,7"),
            "Category" to (item?.category ?: "Meal"))) { v ->
            require(v[0].isNotBlank()); LocalTime.parse(v[1]); LocalTime.parse(v[2]); require(v[1] != v[2])
            require(v[3].split(',').all { it.trim().toIntOrNull() in 1..7 })
            os.saveLife(LifeCommitment(id = item?.id ?: java.util.UUID.randomUUID().toString(), title = v[0], startTime = v[1], endTime = v[2], days = v[3].replace(" ", ""), category = v[4]))
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
            listOf("Plan", "Inbox", "Pulse", "Timeline", "Life", "Assistant").forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }
        if (!snap.ready) {
            Column(Modifier.padding(24.dp)) { CircularProgressIndicator(); Text("Loading your day…") }
        } else LazyColumn(Modifier.fillMaxWidth().widthIn(max = 840.dp).align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            message?.let { text -> item { OsCard("Update", text) { TextButton(onClick = os::clearMessage) { Text("Dismiss") } } } }
            when (tab) {
                0 -> {
                    item { EnergyCheckIn(snap.context.energy) { os.checkIn(it) } }
                    item { OsCard("Today's plan", "Blocks reserve travel and personal commitments. Regenerating keeps locked blocks.") {
                        Button(onClick = { os.generatePlan() }) { Text("Generate today's plan") }
                    } }
                    val blocks = snap.blocks.filter { Instant.ofEpochMilli(it.startAt).atZone(zone).toLocalDate() >= today.minusDays(1) &&
                        Instant.ofEpochMilli(it.startAt).atZone(zone).toLocalDate() <= today.plusDays(7) }
                    if (blocks.isEmpty()) item { Text("No blocks yet. Add a task in Inbox, then generate a plan.") }
                    items(blocks, key = { "block:${it.id}" }) { block ->
                        OsCard(block.title, "${clock(block.startAt)} · ${block.window().minutes} min · ${block.status.lowercase()}${if (block.locked) " · locked" else ""}") {
                            if (block.status == "PLANNED") OsActions {
                                TextButton(onClick = { onOpen("pomodoro", snap.tasks.firstOrNull { it.id == block.taskId }?.course) }) { Text("Focus") }
                                TextButton(onClick = { os.changeBlock(block, status = "DONE") }) { Text("Done") }
                                TextButton(onClick = { editBlock(block) }) { Text("Move / resize") }
                                TextButton(onClick = { os.changeBlock(block, locked = !block.locked) }) { Text(if (block.locked) "Unlock" else "Lock") }
                                TextButton(onClick = { os.changeBlock(block, status = "SKIPPED") }) { Text("Skip") }
                                if (block.endAt < System.currentTimeMillis()) {
                                    TextButton(onClick = { os.recover(block, false) }) { Text("Next opening") }
                                    TextButton(onClick = { os.recover(block, true) }) { Text("Split & move") }
                                }
                            }
                        }
                    }
                    item { Text("Choose your next task", style = MaterialTheme.typography.titleLarge) }
                    if (snap.tasks.isEmpty()) item { Text("Your task list is clear. Capture something in Inbox when you're ready.") }
                    items(snap.ranked, key = { "task:${it.task.id}" }) { ranked ->
                        val task = ranked.task
                        OsCard(task.title, "${task.course.orEmpty()} · ${task.remainingMinutes} min remaining · ${task.progress}%\n${ranked.reasons.joinToString("\n")}") {
                            OsActions {
                                TextButton(onClick = { onOpen("pomodoro", task.course) }) { Text("Focus") }
                                TextButton(onClick = { os.chooseFirst(task) }) { Text("Choose first") }
                                TextButton(onClick = { editTask(task) }) { Text("Edit effort") }
                                TextButton(onClick = { os.irrelevant(task) }) { Text("Not relevant today") }
                                TextButton(onClick = { os.completeTask(task) }) { Text("Complete task") }
                            }
                        }
                    }
                    val hidden = snap.tasks.filter { it.dismissedUntil > System.currentTimeMillis() }
                    if (hidden.isNotEmpty()) item { TextButton(onClick = { hidden.forEach { os.savePreferences(snap.profile(it.id).copy(dismissedUntil = 0)) } }) { Text("Restore ${hidden.size} hidden suggestions") } }
                    items(snap.workload, key = { "load:${it.task.id}" }) { load ->
                        OsCard(load.task.title, "${load.task.remainingMinutes} min remaining · ${load.availableMinutes} min available before due time (next 8 days)\n" +
                            if (load.overloaded) "Workload exceeds available time across deadlines. Adjust effort or commitments." else "Current estimated workload fits this window.") {
                            TextButton(onClick = { editTask(load.task) }) { Text("Adjust estimate") }
                        }
                    }
                }
                1 -> {
                    item { OsCard("Capture something", "Save a rough task, note, link, or shared material. Confirm details before converting.") {
                        OutlinedTextField(captureText, { captureText = it }, label = { Text("e.g. MATH 27 exercise due Friday 11:59 PM") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { os.capture(captureText); captureText = "" }, enabled = captureText.isNotBlank()) { Text("Save to Inbox") }
                        OsActions {
                            TextButton(onClick = { onOpen("schedule?quickAdd=true&quickAddToken=${System.currentTimeMillis()}", null) }) { Text("Add class") }
                            TextButton(onClick = { onOpen("budget?quickAdd=true&quickAddToken=${System.currentTimeMillis()}", null) }) { Text("Add expense") }
                            TextButton(onClick = { onOpen("study", null) }) { Text("Import study material") }
                        }
                    } }
                    val inbox = snap.captures.filter { !it.processed }
                    if (inbox.isEmpty()) item { Text("Inbox is clear. You can also choose Punla from Android's Share menu.") }
                    items(inbox, key = { it.id }) { capture ->
                        OsCard(capture.text.take(500), capture.attachment?.let { "Attachment: $it" } ?: clock(capture.createdAt)) {
                            OsActions {
                                TextButton(onClick = { convert = capture }) { Text("Review & convert") }
                                TextButton(onClick = { os.discard(capture) }) { Text("Archive") }
                                if (capture.attachment != null) {
                                    val ctx = androidx.compose.ui.platform.LocalContext.current
                                    TextButton(onClick = { com.uplb.punla.planning.CaptureAttachments.open(ctx, capture.attachment) }) { Text("Open attachment") }
                                }
                            }
                        }
                    }
                    val attached = snap.captures.filter { it.processed && it.attachment != null }
                    if (attached.isNotEmpty()) item { Text("Saved attachments", style = MaterialTheme.typography.titleMedium) }
                    items(attached, key = { "attachment:${it.id}" }) { capture ->
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        TextButton(onClick = { com.uplb.punla.planning.CaptureAttachments.open(ctx, capture.attachment!!) }) { Text(capture.text.take(80)) }
                    }
                }
                2 -> {
                    item { Text("Academic Pulse", style = MaterialTheme.typography.titleLarge) }
                    if (snap.attention.isEmpty()) item { Text("No attention signals in your recorded data. Add study and performance records to build a fuller picture.") }
                    items(snap.attention, key = { it.course }) { course ->
                        val stats = snap.context.courses.firstOrNull { it.code == course.course }
                        OsCard(course.course, course.reasons.joinToString("\n")) {
                            OsActions {
                                TextButton(onClick = { onOpen("study", course.course) }) { Text("Study weak concepts") }
                                TextButton(onClick = { onOpen("deadlines", null) }) { Text("Deadlines") }
                                TextButton(onClick = { onOpen("schedule", null) }) { Text("Attendance") }
                            }
                            Text("Suggested 40-minute session", style = MaterialTheme.typography.titleSmall)
                            StudyIntelligence.session(40, stats?.weakFlashcardCount ?: 0, stats?.unresolvedMistakeCount ?: 0).forEach { step ->
                                TextButton(onClick = {
                                    if (step.destination == "pomodoro") vm.updatePomodoroWorkMinutes(step.minutes)
                                    onOpen(step.destination, course.course)
                                }) { Text("${step.minutes} min · ${step.label}") }
                            }
                        }
                    }
                    items(snap.attendance.filter { snap.enabled("attendance") && snap.settings["dismiss:${it.key}"] == null }, key = { it.key }) { item ->
                        OsCard("Confirm ${item.session.code}", "${item.date} · ${item.session.start}–${item.session.end}. Confirm what happened.") {
                            OsActions {
                                TextButton(onClick = { os.confirmAttendance(item, AttendanceStatus.ATTENDED) }) { Text("Attended") }
                                TextButton(onClick = { os.confirmAttendance(item, AttendanceStatus.ABSENT) }) { Text("Absent") }
                                TextButton(onClick = { os.dismiss(item.key) }) { Text("Ignore") }
                            }
                        }
                    }
                    items(snap.suggestions.filter { it.category != "attendance" }, key = { it.id }) { suggestion ->
                        OsCard(suggestion.title, suggestion.reason) {
                            OsActions {
                                TextButton(onClick = { if (suggestion.destination == "student-os") tab = 0 else onOpen(suggestion.destination, null) }) { Text("Review") }
                                TextButton(onClick = { os.dismiss(suggestion.id) }) { Text("Dismiss") }
                            }
                        }
                    }
                }
                3 -> {
                    item { OsActions {
                        FilterChip(!week, { week = false }, label = { Text("Today") })
                        FilterChip(week, { week = true }, label = { Text("Last 7 days") })
                    } }
                    val timeline = snap.timeline.filter {
                        val date = Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate()
                        date <= today && date >= if (week) today.minusDays(6) else today
                    }
                    if (timeline.isEmpty()) item { Text("No activity recorded in this period.") }
                    items(timeline, key = { it.id }) { event ->
                        val recordedAt = if (event.timeKnown) clock(event.at)
                            else Instant.ofEpochMilli(event.at).atZone(zone).toLocalDate().toString()
                        OsCard(event.title, "$recordedAt\n${event.detail}") {
                            TextButton(onClick = { if (event.destination == "student-os") tab = 0 else onOpen(event.destination, null) }) { Text("Open") }
                        }
                    }
                }
                4 -> {
                    item { OsCard("Protect time for daily life", "Meals, errands, organizations, exercise, and sleep reserve time in your plan. Overnight commitments are supported.") {
                        Button(onClick = { editLife() }) { Text("Add commitment") }
                    } }
                    items(snap.life, key = { it.id }) { item ->
                        OsCard(item.title, "${item.startTime}–${item.endTime} · Days ${item.days} · ${item.category}") {
                            OsActions {
                                TextButton(onClick = { editLife(item) }) { Text("Edit") }
                                TextButton(onClick = { os.saveLife(item.copy(enabled = !item.enabled)) }) { Text(if (item.enabled) "Pause" else "Enable") }
                                TextButton(onClick = { os.deleteLife(item) }) { Text("Remove") }
                            }
                        }
                    }
                    item { OsCard("Planning hours", "${snap.settings["dayStart"] ?: "08:00"}–${snap.settings["dayEnd"] ?: "22:00"} · ${snap.settings["travelMinutes"] ?: "10"} min minimum travel buffer") {
                        TextButton(onClick = { form = OsForm("Planning hours", listOf("Start (HH:mm)" to (snap.settings["dayStart"] ?: "08:00"),
                            "End (HH:mm)" to (snap.settings["dayEnd"] ?: "22:00"), "Minimum travel minutes" to (snap.settings["travelMinutes"] ?: "10"))) { v ->
                            require(LocalTime.parse(v[0]) < LocalTime.parse(v[1])) { "End must be later than start." }; require(v[2].toInt() in 0..120)
                            os.planningHours(v[0], v[1], v[2].toInt())
                        } }) { Text("Change hours") }
                    } }
                    item { OsCard("Suggestion controls", "Choose which categories may surface suggestions. Attendance always asks for confirmation.") {
                        listOf("attendance", "study", "workload", "budget").forEach { category ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(category.replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
                                Switch(snap.enabled(category), { os.setting("category:$category", it.toString()) })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("End-of-day recap notification", Modifier.weight(1f))
                            Switch(snap.settings["ambientRecap"] == "true", { os.setting("ambientRecap", it.toString()) })
                        }
                    } }
                }
                5 -> {
                    item { OsCard("Ask Punla", assistantAnswer) {
                        OutlinedTextField(assistantInput, { assistantInput = it }, label = { Text("Ask about your day or draft an action") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            val q = assistantInput.trim(); val lower = q.lowercase(); assistantAction = null
                            val expense = Regex("(?i)(?:add|log)\\s+(?:₱|php\\s*)?(\\d+(?:\\.\\d{1,2})?)\\s+(?:pesos\\s+)?(?:for\\s+)?(.+)").matchEntire(q)
                            when {
                                expense != null -> {
                                    val amount = expense.groupValues[1].toDoubleOrNull()
                                    if (amount == null || !amount.isFinite() || amount <= 0) assistantAnswer = "Enter a positive expense amount."
                                    else {
                                        val note = expense.groupValues[2]
                                        assistantAnswer = "Add ₱$amount for $note on $today in Miscellaneous?"
                                        assistantAction = { vm.addExpense(Expense(amount = amount, category = "Miscellaneous", note = note, date = today.toString())) }
                                    }
                                }
                                lower.startsWith("move ") && lower.endsWith(" tomorrow") -> {
                                    val query = lower.removePrefix("move ").removeSuffix(" tomorrow").removePrefix("tonight's ").removeSuffix(" session").trim()
                                    val candidates = snap.blocks.filter { it.status == "PLANNED" && it.title.contains(query, true) &&
                                        Instant.ofEpochMilli(it.startAt).atZone(zone).toLocalDate() == today }
                                    if (candidates.size != 1) assistantAnswer = "Choose the exact session in Plan, then use Move / resize."
                                    else {
                                        val block = candidates.single(); val newStart = Instant.ofEpochMilli(block.startAt).atZone(zone).plusDays(1).toInstant().toEpochMilli()
                                        assistantAnswer = "Move ${block.title} to ${clock(newStart)} for ${block.window().minutes} minutes?"
                                        assistantAction = { os.changeBlock(block, start = newStart) }
                                    }
                                }
                                "tonight off" in lower -> {
                                    val evening = TimeWindow(today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli(),
                                        today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                                    val withoutTonight = snap.windows.flatMap { DayPlanner.freeWindows(it, listOf(evening)) }
                                    val load = DayPlanner.workload(snap.tasks, withoutTonight, System.currentTimeMillis()).firstOrNull { it.overloaded }
                                    assistantAnswer = if (load != null) "Without tonight, work through ${load.task.title} needs ${load.cumulativeRequired} min but has ${load.availableMinutes} min available. Review Plan before skipping blocks."
                                        else "With tonight removed, your recorded deadline workload still fits the next 8 days of planning windows. Undated work and rough estimates may change this; review Plan before skipping blocks."
                                }
                                "study" in lower -> {
                                    val selected = snap.ranked.firstOrNull { it.task.course?.let { code -> lower.contains(code.lowercase()) } == true } ?: snap.ranked.firstOrNull()
                                    val occupied = snap.blocks.filter { it.status == "PLANNED" }.map { it.window() }
                                    val window = snap.windows.flatMap { DayPlanner.freeWindows(it, occupied) }
                                        .map { TimeWindow(it.start, minOf(it.end, selected?.task?.dueAt ?: it.end)) }
                                        .firstOrNull { it.minutes >= 10 }
                                    assistantAnswer = if (selected == null || window == null) "No suitable task and opening are available. Add tasks or adjust planning hours."
                                        else "${selected.task.title}: a ${minOf(selected.task.remainingMinutes, window.minutes, 50)}-minute start fits at ${clock(window.start)}. ${selected.reasons.first()}. Generate or edit a block in Plan."
                                }
                                "day" in lower || "next" in lower -> {
                                    assistantAnswer = buildString {
                                        append(snap.context.currentClass?.let { "Now: ${it.code}. " } ?: "No class in progress. ")
                                        append(snap.context.nextClass?.let { "Next: ${it.code} at ${it.startTime}. " } ?: "No upcoming class. ")
                                        append("${snap.tasks.size} pending tasks. ")
                                        snap.ranked.firstOrNull()?.let { append("Suggested: ${it.task.title}. ${it.reasons.first()}.") }
                                    }
                                }
                                else -> assistantAnswer = "Try: What's my day like? When should I study MATH 27? Can I take tonight off? Add 85 pesos for lunch. Move Math session tomorrow."
                            }
                        }) { Text("Ask") }
                        assistantAction?.let { action -> OsActions {
                            Button(onClick = { action(); assistantAction = null; assistantAnswer = "Action submitted. Any conflict will appear above." }) { Text("Confirm action") }
                            TextButton(onClick = { assistantAction = null; assistantAnswer = "Draft cancelled." }) { Text("Cancel") }
                        } }
                    } }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    form?.let { data -> OsFormDialog(data) { form = null } }
    convert?.let { capture -> CaptureConvertDialog(capture, snap.context.courses.map { it.code }, { convert = null }) { kind, title, course, date, time, minutes ->
        os.convert(capture, kind, title, course, date, time, minutes); convert = null
    } }
}

@Composable
private fun OsCard(title: String, detail: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OsActions(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)
}
@Composable
private fun OsFormDialog(form: OsForm, dismiss: () -> Unit) {
    val values = remember(form) { form.fields.map { mutableStateOf(it.second) } }
    var error by remember(form) { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(form.title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            form.fields.forEachIndexed { i, field -> OutlinedTextField(values[i].value, { values[i].value = it }, label = { Text(field.first) }, modifier = Modifier.fillMaxWidth()) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching { form.save(values.map { it.value.trim() }) }.onSuccess { dismiss() }.onFailure { error = it.message ?: "Check the fields." }
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureConvertDialog(capture: InboxCapture, courses: List<String>, dismiss: () -> Unit,
    confirm: (String, String, String?, String?, String, Int) -> Unit) {
    val draft = remember(capture.id) { CaptureParser.parse(capture.text, LocalDate.now(), courses) }
    var kind by remember { mutableStateOf(if (draft.date != null) "Deadline" else "Task") }
    var title by remember { mutableStateOf(draft.title) }
    var course by remember { mutableStateOf(draft.course.orEmpty()) }
    var date by remember { mutableStateOf(draft.date.orEmpty()) }
    var time by remember { mutableStateOf(draft.time ?: "23:59") }
    var minutes by remember { mutableStateOf("25") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Confirm captured details") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            draft.warning?.let { Text(it) }
            FlowRow { listOf("Task", "Deadline", "Note").forEach { option -> FilterChip(kind == option, { kind = option }, label = { Text(option) }) } }
            OutlinedTextField(title, { title = it }, label = { Text("Title") })
            OutlinedTextField(course, { course = it }, label = { Text("Course (optional)") })
            if (kind != "Note") {
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") })
                if (kind == "Deadline") OutlinedTextField(time, { time = it }, label = { Text("Due time (HH:mm)") })
                OutlinedTextField(minutes, { minutes = it }, label = { Text("Estimated minutes") })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching {
            require(title.isNotBlank()) { "Enter a title." }
            if (kind == "Deadline") require(date.isNotBlank()) { "Enter a due date." }
            if (date.isNotBlank()) LocalDate.parse(date); LocalTime.parse(time)
            val estimate = minutes.toInt(); require(estimate in 5..10080) { "Use 5–10080 minutes." }
            confirm(kind, title, course.takeIf { it.isNotBlank() }, date.takeIf { it.isNotBlank() }, time, estimate)
        }.onFailure { error = it.message ?: "Check the details." }
    }) { Text("Confirm & save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
