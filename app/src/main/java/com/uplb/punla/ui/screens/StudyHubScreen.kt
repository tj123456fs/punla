package com.uplb.punla.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uplb.punla.data.StudyEngine
import com.uplb.punla.data.StudyMathText
import com.uplb.punla.data.StudyJsonImport
import com.uplb.punla.data.StudyJsonBundle
import com.uplb.punla.data.PunlaJsonImportReader
import com.uplb.punla.data.entity.*
import com.uplb.punla.ui.PunlaViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val STUDY_TABS = listOf("Overview", "Queue", "Mistakes", "Notes", "Plan", "Analytics", "Bank")

@Composable
fun StudyHubScreen(
    vm: PunlaViewModel,
    onOpenFlashcards: (String?, String?, Boolean) -> Unit,
    onOpenQuizzes: (String?, String?, Boolean) -> Unit,
    onOpenFocus: (String?) -> Unit
) {
    val topics by vm.studyTopics.collectAsState()
    val notes by vm.studyNotes.collectAsState()
    val formulas by vm.formulaReferences.collectAsState()
    val mistakes by vm.mistakeRecords.collectAsState()
    val goals by vm.studyGoals.collectAsState()
    val planItems by vm.studyPlanItems.collectAsState()
    val reviewProgress by vm.studyReviewProgress.collectAsState()
    val answerResults by vm.quizAnswerResults.collectAsState()
    val flashcardReviews by vm.flashcardReviewEvents.collectAsState()
    val bank by vm.questionBank.collectAsState()
    val decks by vm.flashcardDecks.collectAsState()
    val cards by vm.flashcards.collectAsState()
    val quizzes by vm.quizzes.collectAsState()
    val questions by vm.quizQuestions.collectAsState()
    val attempts by vm.quizAttempts.collectAsState()
    val sessions by vm.studySessions.collectAsState(initial = emptyList())
    val deadlines by vm.deadlines.collectAsState()
    val classes by vm.classes.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedCourse by rememberSaveable { mutableStateOf<String?>(null) }
    var smartSession by remember { mutableStateOf<List<StudyEngine.QueueItem>?>(null) }
    var activeReview by remember { mutableStateOf<StudyReviewTarget?>(null) }
    val courseCodes = remember(decks, quizzes, classes, deadlines, topics, notes, formulas, goals, planItems, bank) {
        buildSet {
            addAll(classes.map { it.code })
            addAll(decks.mapNotNull { it.courseCode })
            addAll(quizzes.mapNotNull { it.courseCode })
            addAll(deadlines.mapNotNull { it.course })
            addAll(topics.map { it.courseCode })
            addAll(notes.mapNotNull { it.courseCode })
            addAll(formulas.mapNotNull { it.courseCode })
            addAll(goals.mapNotNull { it.courseCode })
            addAll(planItems.mapNotNull { it.courseCode })
            addAll(bank.mapNotNull { it.courseCode })
        }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }

    val weak = remember(mistakes, cards, decks, questions, quizzes, answerResults) {
        StudyEngine.weakTopics(mistakes, cards, decks, questions, quizzes, answerResults)
    }
    val examDates = remember(topics, deadlines) {
        buildMap<String, LocalDate> {
            topics.mapNotNull { t ->
                t.examDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { t.courseCode.lowercase() to it }
            }.groupBy({ it.first }, { it.second }).forEach { (course, dates) ->
                dates.minOrNull()?.let { put(course, it) }
            }
            deadlines.filter { !it.done && Regex("(?i)exam|midterm|final|quiz").containsMatchIn(it.title) }.forEach { d ->
                val course = d.course?.lowercase() ?: return@forEach
                val date = runCatching { LocalDate.parse(d.due) }.getOrNull() ?: return@forEach
                val old = get(course)
                if (old == null || date < old) put(course, date)
            }
        }
    }
    val queue = remember(cards, decks, mistakes, planItems, examDates) {
        StudyEngine.smartQueue(cards, decks, mistakes, planItems, examDates)
    }
    val visibleQueue = remember(queue, selectedCourse) {
        if (selectedCourse.isNullOrBlank()) queue
        else queue.filter { it.courseCode.equals(selectedCourse, true) }
    }
    val studyDays = remember(sessions, attempts, flashcardReviews) { StudyEngine.meaningfulStudyDays(sessions, attempts, flashcardReviews) }
    val streak = remember(studyDays) { StudyEngine.currentStreak(studyDays) }

    var showTopicEditor by remember { mutableStateOf(false) }
    var showNoteEditor by remember { mutableStateOf<StudyNote?>(null) }
    var creatingNote by remember { mutableStateOf(false) }
    var showFormulaEditor by remember { mutableStateOf<FormulaReference?>(null) }
    var creatingFormula by remember { mutableStateOf(false) }
    var showGoalEditor by remember { mutableStateOf<StudyGoal?>(null) }
    var creatingGoal by remember { mutableStateOf(false) }
    var showPlanEditor by remember { mutableStateOf<StudyPlanItem?>(null) }
    var creatingPlan by remember { mutableStateOf(false) }
    var practiceMessage by remember { mutableStateOf<String?>(null) }
    var pendingStudyImport by remember { mutableStateOf<StudyJsonBundle?>(null) }
    var duplicateStudyImport by remember { mutableStateOf(false) }
    var studyImporting by remember { mutableStateOf(false) }
    var studyImportError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val importScope = rememberCoroutineScope()
    val studyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !studyImporting) {
            importScope.launch {
                studyImporting = true
                val result = runCatching { withContext(Dispatchers.IO) { StudyJsonImport.parse(PunlaJsonImportReader.readText(context, uri, StudyJsonImport.MAX_FILE_CHARS)) } }
                result.onSuccess { bundle ->
                    vm.checkJsonImport(bundle.fileId, bundle.contentId).onSuccess { already -> duplicateStudyImport = already; pendingStudyImport = bundle }
                        .onFailure { studyImportError = it.message ?: "Punla couldn't check this study pack." }
                }.onFailure { studyImportError = it.message ?: "Punla couldn't read this study JSON." }
                studyImporting = false
            }
        }
    }

    activeReview?.let { target ->
        ReviewReadingScreen(
            target = target,
            notes = notes,
            formulas = formulas,
            topics = topics,
            reviewProgress = reviewProgress,
            vm = vm,
            onExit = { activeReview = null }
        )
        return
    }

    smartSession?.let { activeQueue ->
        SmartStudySession(
            queue = activeQueue,
            cards = cards,
            mistakes = mistakes,
            planItems = planItems,
            vm = vm,
            onOpenFocus = onOpenFocus,
            onExit = { smartSession = null }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            STUDY_TABS.forEachIndexed { index, name ->
                FilterChip(selected = selectedTab == index, onClick = { selectedTab = index }, label = { Text(name) })
            }
        }
        when (selectedTab) {
            1 -> QueueTab(visibleQueue, { onOpenFlashcards(null, null, false) }, { onOpenQuizzes(null, null, false) }, onOpenFocus, vm)
            2 -> MistakesTab(mistakes, vm)
            3 -> NotesTab(notes, formulas, topics, selectedCourse, { creatingNote = true }, { showNoteEditor = it }, { creatingFormula = true }, { showFormulaEditor = it }, vm)
            4 -> PlanTab(planItems, goals, topics, courseCodes, selectedCourse, { creatingPlan = true }, { showPlanEditor = it }, { creatingGoal = true }, { showGoalEditor = it }, vm)
            5 -> AnalyticsTab(weak, attempts, answerResults, studyDays, streak, cards, sessions)
            6 -> QuestionBankTab(bank, quizzes, questions, selectedCourse, vm, { onOpenQuizzes(selectedCourse, null, false) }) { msg -> practiceMessage = msg }
            else -> OverviewTab(
                courseCodes = courseCodes,
                selectedCourse = selectedCourse,
                onSelectCourse = { selectedCourse = it },
                queue = visibleQueue,
                weak = weak,
                decks = decks,
                cards = cards,
                quizzes = quizzes,
                questions = questions,
                attempts = attempts,
                notes = notes,
                formulas = formulas,
                topics = topics,
                mistakes = mistakes,
                planItems = planItems,
                goals = goals,
                reviewProgress = reviewProgress,
                examDates = examDates,
                streak = streak,
                onStudyNow = { if (visibleQueue.isEmpty()) selectedTab = 4 else smartSession = visibleQueue },
                onOpenFlashcards = onOpenFlashcards,
                onOpenQuizzes = onOpenQuizzes,
                onOpenFocus = onOpenFocus,
                onAddTopic = { showTopicEditor = true },
                onImportStudy = { studyPicker.launch(arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")) },
                onOpenReview = { activeReview = it },
                vm = vm
            )
        }
    }

    if (showTopicEditor) TopicDialog(courseCodes, topics, selectedCourse, onDismiss = { showTopicEditor = false }) {
        vm.upsertStudyTopic(it); selectedCourse = it.courseCode; showTopicEditor = false
    }
    if (creatingNote || showNoteEditor != null) NoteDialog(showNoteEditor, courseCodes, topics, selectedCourse, onDismiss = { creatingNote = false; showNoteEditor = null }) {
        vm.upsertStudyNote(it); creatingNote = false; showNoteEditor = null
    }
    if (creatingFormula || showFormulaEditor != null) FormulaDialog(showFormulaEditor, courseCodes, topics, selectedCourse, onDismiss = { creatingFormula = false; showFormulaEditor = null }) {
        vm.upsertFormula(it); creatingFormula = false; showFormulaEditor = null
    }
    if (creatingGoal || showGoalEditor != null) GoalDialog(showGoalEditor, courseCodes, topics, selectedCourse, onDismiss = { creatingGoal = false; showGoalEditor = null }) {
        vm.upsertStudyGoal(it); creatingGoal = false; showGoalEditor = null
    }
    if (creatingPlan || showPlanEditor != null) PlanDialog(showPlanEditor, courseCodes, topics, selectedCourse, onDismiss = { creatingPlan = false; showPlanEditor = null }) {
        vm.upsertStudyPlanItem(it); creatingPlan = false; showPlanEditor = null
    }
    pendingStudyImport?.let { bundle ->
        StudyBundlePreviewDialog(bundle, duplicateStudyImport, studyImporting, onDismiss = { if (!studyImporting) pendingStudyImport = null }) {
            importScope.launch {
                studyImporting = true
                vm.importStudyBundle(bundle).onSuccess { pendingStudyImport = null; selectedCourse = bundle.courseCode ?: selectedCourse; practiceMessage = "Imported ${bundle.title} · ${bundle.itemCount} study items." }
                    .onFailure { studyImportError = it.message ?: "Punla couldn't import this study pack." }
                studyImporting = false
            }
        }
    }
    studyImportError?.let { msg -> AlertDialog(onDismissRequest = { studyImportError = null }, title = { Text("Study import failed") }, text = { Text(msg) }, confirmButton = { TextButton(onClick = { studyImportError = null }) { Text("OK") } }) }
    practiceMessage?.let { AlertDialog(onDismissRequest = { practiceMessage = null }, title = { Text("Study") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { practiceMessage = null }) { Text("OK") } }) }
}

@Composable
private fun OverviewTab(
    courseCodes: List<String>, selectedCourse: String?, onSelectCourse: (String?) -> Unit,
    queue: List<StudyEngine.QueueItem>, weak: List<StudyEngine.WeakTopic>, decks: List<FlashcardDeck>, cards: List<Flashcard>, quizzes: List<Quiz>, questions: List<QuizQuestion>, attempts: List<QuizAttempt>, notes: List<StudyNote>, formulas: List<FormulaReference>, topics: List<StudyTopic>, mistakes: List<MistakeRecord>, planItems: List<StudyPlanItem>, goals: List<StudyGoal>, reviewProgress: List<StudyReviewProgress>, examDates: Map<String, LocalDate>, streak: Int,
    onStudyNow: () -> Unit, onOpenFlashcards: (String?, String?, Boolean) -> Unit, onOpenQuizzes: (String?, String?, Boolean) -> Unit, onOpenFocus: (String?) -> Unit, onAddTopic: () -> Unit, onImportStudy: () -> Unit, onOpenReview: (StudyReviewTarget) -> Unit, vm: PunlaViewModel
) {
    val hp = punlaScreenHorizontalPadding()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = hp, end = hp, top = 8.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.School, null); Spacer(Modifier.width(10.dp)); Text("Study Hub", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(8.dp))
                    Text("One place for your queue, exam prep, mistakes, notes, formulas, practice tests and readiness.")
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onStudyNow, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (queue.isEmpty()) "Plan study" else "Study now · ${queue.size} items") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat(streak.toString(), "day streak", Modifier.weight(1f))
                MiniStat(queue.count { it.kind == StudyEngine.QueueKind.MISTAKE }.toString(), "mistakes due", Modifier.weight(1f))
                MiniStat(cards.count { it.isDue() }.toString(), "cards due", Modifier.weight(1f))
            }
        }
        item { SectionLabel("Course hub", icon = Icons.Default.Hub, actionLabel = "Add topic", onAction = onAddTopic) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedCourse == null, onClick = { onSelectCourse(null) }, label = { Text("All") })
                courseCodes.forEach { course -> FilterChip(selected = selectedCourse == course, onClick = { onSelectCourse(course) }, label = { Text(course) }) }
            }
        }
        if (selectedCourse != null) {
            item {
                val courseDecks = decks.filter { it.courseCode.equals(selectedCourse, true) }
                val courseCards = cards.filter { c -> courseDecks.any { it.id == c.deckId } }
                val courseQuizzes = quizzes.filter { it.courseCode.equals(selectedCourse, true) }
                val due = courseCards.count { it.isDue() }
                val unresolved = mistakes.count { !it.resolved && it.courseCode.equals(selectedCourse, true) }
                val readiness = StudyEngine.readinessForCourse(selectedCourse, weak, due, unresolved, examDates[selectedCourse.lowercase()])
                CourseHubCard(
                    selectedCourse, readiness, due, courseQuizzes.size,
                    notes.count { it.courseCode.equals(selectedCourse, true) }, examDates[selectedCourse.lowercase()],
                    { onOpenFlashcards(selectedCourse, null, false) }, { onOpenQuizzes(selectedCourse, null, false) }, { onOpenFocus(selectedCourse) },
                    onPlanExam = examDates[selectedCourse.lowercase()]?.let { exam -> { vm.generateExamPlan(selectedCourse, exam, 50) } }
                )
            }
            item {
                CourseLearningPath(
                    course = selectedCourse,
                    topics = topics,
                    notes = notes,
                    formulas = formulas,
                    decks = decks,
                    cards = cards,
                    quizzes = quizzes,
                    questions = questions,
                    attempts = attempts,
                    reviewProgress = reviewProgress,
                    onOpenReview = onOpenReview,
                    onOpenFlashcards = onOpenFlashcards,
                    onOpenQuizzes = onOpenQuizzes
                )
            }
        } else {
            item {
                val topWeak = weak.take(4)
                if (topWeak.isEmpty()) Text("Punla will identify weak topics after you review cards or take quizzes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Needs attention", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    topWeak.forEach { WeakTopicRow(it) }
                }
            }
        }
        item { SectionLabel("Quick actions", icon = Icons.Default.Bolt) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpenFlashcards(selectedCourse, null, false) }, modifier = Modifier.weight(1f)) { Text("Flashcards") }
                OutlinedButton(onClick = { onOpenQuizzes(selectedCourse, null, false) }, modifier = Modifier.weight(1f)) { Text("Quizzes") }
            }
        }
        item { OutlinedButton(onClick = onImportStudy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("Import Punla study pack JSON") } }
        item {
            val todayPlan = planItems.count { !it.completed && it.plannedDate <= LocalDate.now().toString() }
            val openGoals = goals.count { !it.completed }
            Text("$todayPlan plan item${if (todayPlan == 1) "" else "s"} ready · $openGoals active goal${if (openGoals == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class StudyReviewTarget(
    val courseCode: String,
    val topicId: String?,
    val title: String,
    val overall: Boolean = false
)

private fun studyTopicScopeIds(rootTopicId: String, topics: List<StudyTopic>): Set<String> {
    val ids = linkedSetOf(rootTopicId)
    var changed = true
    while (changed) {
        changed = false
        topics.forEach { topic ->
            if (topic.parentTopicId?.let { it in ids } == true && ids.add(topic.id)) changed = true
        }
    }
    return ids
}

@Composable
private fun CourseLearningPath(
    course: String,
    topics: List<StudyTopic>,
    notes: List<StudyNote>,
    formulas: List<FormulaReference>,
    decks: List<FlashcardDeck>,
    cards: List<Flashcard>,
    quizzes: List<Quiz>,
    questions: List<QuizQuestion>,
    attempts: List<QuizAttempt>,
    reviewProgress: List<StudyReviewProgress>,
    onOpenReview: (StudyReviewTarget) -> Unit,
    onOpenFlashcards: (String?, String?, Boolean) -> Unit,
    onOpenQuizzes: (String?, String?, Boolean) -> Unit
) {
    val courseTopics = topics.filter { it.courseCode.equals(course, true) }
    val modules = courseTopics.filter { it.parentTopicId == null }.sortedWith(compareBy<StudyTopic> { it.sortOrder }.thenBy { it.name.lowercase() })
    val courseDecks = decks.filter { it.courseCode.equals(course, true) }
    val courseQuizzes = quizzes.filter { it.courseCode.equals(course, true) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Course learning path", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Review → Flashcards → Quiz for each module, then an overall review. Nothing is locked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        if (modules.isEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                Text("Add top-level topics to turn this course into modules. Existing course-level notes, cards and quizzes stay available below as Overall Review material.", modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        modules.forEachIndexed { index, module ->
            val scopeIds = studyTopicScopeIds(module.id, courseTopics)
            val moduleNotes = notes.filter { it.courseCode.equals(course, true) && it.topicId?.let { id -> id in scopeIds } == true }
            val moduleFormulas = formulas.filter { it.courseCode.equals(course, true) && it.topicId?.let { id -> id in scopeIds } == true }
            val moduleDecks = courseDecks.filter { it.topicId?.let { id -> id in scopeIds } == true }
            val deckIds = moduleDecks.mapTo(hashSetOf()) { it.id }
            val moduleCards = cards.filter { it.deckId in deckIds }
            val moduleQuizzes = courseQuizzes.filter { it.topicId?.let { id -> id in scopeIds } == true }
            val moduleQuestionCount = questions.count { q -> moduleQuizzes.any { it.id == q.quizId } }
            val hasReview = moduleNotes.isNotEmpty() || moduleFormulas.isNotEmpty()
            val reviewDone = hasReview && reviewProgress.any { it.id == StudyReviewProgress.key(course, module.id) && it.completed }
            val cardsDone = moduleCards.isNotEmpty() && moduleCards.all { it.reviewCount > 0 }
            val quizDone = moduleQuizzes.isNotEmpty() && moduleQuizzes.all { quiz -> attempts.any { it.quizId == quiz.id && it.percent() >= quiz.passingScore } }
            val hasCards = moduleCards.isNotEmpty()
            val hasQuiz = moduleQuizzes.isNotEmpty()
            val next = when {
                hasReview && !reviewDone -> "Review"
                hasCards && !cardsDone -> "Flashcards"
                hasQuiz && !quizDone -> "Quiz"
                hasReview || hasCards || hasQuiz -> "Module complete"
                else -> "Add study material"
            }

            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("${index + 1}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(module.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${moduleNotes.size} review note${if (moduleNotes.size == 1) "" else "s"} · ${moduleCards.size} cards · $moduleQuestionCount questions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StudyStepPill("Review", reviewDone, hasReview, Modifier.weight(1f))
                        StudyStepPill("Cards", cardsDone, hasCards, Modifier.weight(1f))
                        StudyStepPill("Quiz", quizDone, hasQuiz, Modifier.weight(1f))
                    }
                    Text("Next: $next", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { onOpenReview(StudyReviewTarget(course, module.id, "Module ${index + 1} · ${module.name}")) },
                            enabled = hasReview,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text("Review", maxLines = 1) }
                        OutlinedButton(
                            onClick = { onOpenFlashcards(course, module.id, false) },
                            enabled = hasCards,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text("Cards", maxLines = 1) }
                        Button(
                            onClick = { onOpenQuizzes(course, module.id, false) },
                            enabled = hasQuiz,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text("Quiz", maxLines = 1) }
                    }
                }
            }
        }

        val overallNotes = notes.filter { it.courseCode.equals(course, true) && it.topicId == null }
        val overallFormulas = formulas.filter { it.courseCode.equals(course, true) && it.topicId == null }
        val overallDecks = courseDecks.filter { it.topicId == null }
        val overallDeckIds = overallDecks.mapTo(hashSetOf()) { it.id }
        val overallCards = cards.filter { it.deckId in overallDeckIds }
        val overallQuizzes = courseQuizzes.filter { it.topicId == null }
        val overallQuestions = questions.count { q -> overallQuizzes.any { it.id == q.quizId } }
        val hasOverallReview = overallNotes.isNotEmpty() || overallFormulas.isNotEmpty()
        val overallReviewDone = hasOverallReview && reviewProgress.any { it.id == StudyReviewProgress.key(course, null) && it.completed }
        val overallCardsDone = overallCards.isNotEmpty() && overallCards.all { it.reviewCount > 0 }
        val overallQuizDone = overallQuizzes.isNotEmpty() && overallQuizzes.all { quiz -> attempts.any { it.quizId == quiz.id && it.percent() >= quiz.passingScore } }

        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .72f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Overall Review", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("Course-wide consolidation after the modules — still available anytime for cramming.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = .8f))
                    }
                }
                Text("${overallNotes.size} review note${if (overallNotes.size == 1) "" else "s"} · ${overallCards.size} cards · $overallQuestions questions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StudyStepPill("Review", overallReviewDone, hasOverallReview, Modifier.weight(1f))
                    StudyStepPill("Cards", overallCardsDone, overallCards.isNotEmpty(), Modifier.weight(1f))
                    StudyStepPill("Quiz", overallQuizDone, overallQuizzes.isNotEmpty(), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onOpenReview(StudyReviewTarget(course, null, "$course · Overall Review", overall = true)) }, enabled = hasOverallReview, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Review", maxLines = 1) }
                    OutlinedButton(onClick = { onOpenFlashcards(course, null, true) }, enabled = overallCards.isNotEmpty(), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Cards", maxLines = 1) }
                    Button(onClick = { onOpenQuizzes(course, null, true) }, enabled = overallQuizzes.isNotEmpty(), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Final quiz", maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun StudyStepPill(label: String, done: Boolean, available: Boolean, modifier: Modifier = Modifier) {
    val container = when {
        done -> MaterialTheme.colorScheme.primaryContainer
        available -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)
    }
    val content = when {
        done -> MaterialTheme.colorScheme.onPrimaryContainer
        available -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(999.dp), color = container) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (done) { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = content); Spacer(Modifier.width(4.dp)) }
            Text(if (available) label else "$label —", style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
        }
    }
}

@Composable
private fun ReviewReadingScreen(
    target: StudyReviewTarget,
    notes: List<StudyNote>,
    formulas: List<FormulaReference>,
    topics: List<StudyTopic>,
    reviewProgress: List<StudyReviewProgress>,
    vm: PunlaViewModel,
    onExit: () -> Unit
) {
    BackHandler(onBack = onExit)
    val courseTopics = topics.filter { it.courseCode.equals(target.courseCode, true) }
    val scopeIds = target.topicId?.let { studyTopicScopeIds(it, courseTopics) }
    val shownNotes = notes.filter { note ->
        note.courseCode.equals(target.courseCode, true) && if (target.overall) note.topicId == null else note.topicId?.let { id -> id in scopeIds.orEmpty() } == true
    }.sortedBy { note -> courseTopics.firstOrNull { it.id == note.topicId }?.sortOrder ?: Int.MAX_VALUE }
    val shownFormulas = formulas.filter { formula ->
        formula.courseCode.equals(target.courseCode, true) && if (target.overall) formula.topicId == null else formula.topicId?.let { id -> id in scopeIds.orEmpty() } == true
    }
    val completed = reviewProgress.any { it.id == StudyReviewProgress.key(target.courseCode, target.topicId) && it.completed }
    val hp = punlaScreenHorizontalPadding(820.dp)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hp, end = hp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text(target.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Read first, then use the module flashcards and quiz for active recall.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (shownNotes.isEmpty() && shownFormulas.isEmpty()) {
            item { EmptyState(Icons.Default.MenuBook, "No reviewer has been attached to this section yet.") }
        }
        items(shownNotes, key = { it.id }) { note ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    note.topicId?.let { id -> courseTopics.firstOrNull { it.id == id }?.let { Text(studyTopicPath(it, courseTopics), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } }
                    Text(note.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (shownFormulas.isNotEmpty()) {
            item { SectionLabel("Formula / reference sheet", icon = Icons.Default.Functions) }
            items(shownFormulas, key = { it.id }) { f ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(f.title, fontWeight = FontWeight.SemiBold)
                        Text(StudyMathText.render(f.expression), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        f.variables?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        f.units?.let { Text("Units: $it", style = MaterialTheme.typography.bodySmall) }
                        f.workedExample?.let { Text("Worked: ${StudyMathText.render(it)}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        item {
            Button(
                onClick = { vm.setReviewCompleted(target.courseCode, target.topicId, !completed) },
                enabled = shownNotes.isNotEmpty() || shownFormulas.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Icon(if (completed) Icons.Default.Undo else Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (completed) "Mark review incomplete" else "Mark review complete")
            }
        }
    }
}

@Composable
private fun SmartStudySession(
    queue: List<StudyEngine.QueueItem>,
    cards: List<Flashcard>,
    mistakes: List<MistakeRecord>,
    planItems: List<StudyPlanItem>,
    vm: PunlaViewModel,
    onOpenFocus: (String?) -> Unit,
    onExit: () -> Unit
) {
    var index by rememberSaveable(queue.map { it.id }) { mutableIntStateOf(0) }
    var revealed by rememberSaveable(index) { mutableStateOf(false) }
    val hp = punlaScreenHorizontalPadding(720.dp)
    val finished = index >= queue.size

    fun advance() {
        revealed = false
        index++
    }

    Column(Modifier.fillMaxSize().padding(horizontal = hp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(Icons.Default.Close, "Exit study session") }
            Column(Modifier.weight(1f)) {
                Text("Smart Study", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(if (finished) "Session complete" else "${index + 1} of ${queue.size} · interleaved review", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LinearProgressIndicator(progress = { if (queue.isEmpty()) 1f else (index.coerceAtMost(queue.size) / queue.size.toFloat()) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))

        if (finished) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text("Queue cleared", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("You worked through ${queue.size} prioritized item${if (queue.size == 1) "" else "s"}.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Back to Study Hub") }
                }
            }
            return@Column
        }

        val item = queue[index]
        val card = cards.firstOrNull { it.id == item.id }
        val mistake = mistakes.firstOrNull { it.id == item.id }
        val plan = planItems.firstOrNull { it.id == item.id }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Tag(item.kind.name.replace('_', ' '), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                item.courseCode?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(12.dp))
                when {
                    card != null -> {
                        Text(card.front, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("Say the answer aloud before revealing it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        if (revealed) {
                            HorizontalDivider(Modifier.padding(vertical = 16.dp))
                            Text(card.back, style = MaterialTheme.typography.titleLarge)
                            card.hint?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
                            Spacer(Modifier.height(18.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.rateFlashcard(card, FlashcardRating.AGAIN); advance() }, modifier = Modifier.weight(1f)) { Text("Again") }
                                OutlinedButton(onClick = { vm.rateFlashcard(card, FlashcardRating.HARD); advance() }, modifier = Modifier.weight(1f)) { Text("Hard") }
                                Button(onClick = { vm.rateFlashcard(card, FlashcardRating.GOOD); advance() }, modifier = Modifier.weight(1f)) { Text("Good") }
                            }
                        } else {
                            Spacer(Modifier.height(18.dp)); Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text("Reveal answer") }
                        }
                    }
                    mistake != null -> {
                        Text(mistake.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("Recall it without looking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        if (revealed) {
                            HorizontalDivider(Modifier.padding(vertical = 16.dp)); Text(mistake.correctAnswer, style = MaterialTheme.typography.titleLarge)
                            mistake.explanation?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                            Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.resolveMistake(mistake.copy(retryAt = System.currentTimeMillis() + 12L * 60L * 60L * 1000L), false); advance() }, modifier = Modifier.weight(1f)) { Text("Still learning") }
                                Button(onClick = { vm.resolveMistake(mistake, true); advance() }, modifier = Modifier.weight(1f)) { Text("Got it") }
                            }
                        } else { Spacer(Modifier.height(18.dp)); Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text("Reveal answer") } }
                    }
                    plan != null -> {
                        Text(plan.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("${plan.minutes} min · ${plan.kind.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = { onOpenFocus(plan.courseCode) }, modifier = Modifier.fillMaxWidth()) { Text("Start focus session") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.upsertStudyPlanItem(plan.copy(completed = true)); advance() }, modifier = Modifier.fillMaxWidth()) { Text("Mark done and continue") }
                    }
                    else -> {
                        Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.height(18.dp)); Button(onClick = { advance() }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { advance() }, enabled = !finished) { Text("Skip for now") }
    }
}

@Composable private fun MiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)) { Column(Modifier.padding(12.dp)) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall) } }
}

@Composable private fun CourseHubCard(course: String, readiness: Int, due: Int, quizzes: Int, notes: Int, exam: LocalDate?, onCards: () -> Unit, onQuizzes: () -> Unit, onFocus: () -> Unit, onPlanExam: (() -> Unit)? = null) {
    Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(course, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(exam?.let { "Next exam ${it.format(DateTimeFormatter.ofPattern("MMM d"))}" } ?: "No exam target yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("$readiness%", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { readiness / 100f }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)); Text("$due cards due · $quizzes quizzes · $notes notes", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = onCards) { Text("Cards") }; TextButton(onClick = onQuizzes) { Text("Quizzes") }; TextButton(onClick = onFocus) { Text("Focus") }; if (onPlanExam != null) TextButton(onClick = onPlanExam) { Text("Plan exam") } }
    } }
}

@Composable private fun WeakTopicRow(item: StudyEngine.WeakTopic) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.5f)) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.topic, fontWeight = FontWeight.Medium); Text(listOfNotNull(item.courseCode, "${item.misses} misses").joinToString(" · "), style = MaterialTheme.typography.bodySmall) }; Text("${item.readiness}%", color = if (item.readiness < 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } }
}

@Composable private fun QueueTab(queue: List<StudyEngine.QueueItem>, onCards: () -> Unit, onQuizzes: () -> Unit, onFocus: (String?) -> Unit, vm: PunlaViewModel) {
    val hp = punlaScreenHorizontalPadding()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = hp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionLabel("Smart Study Queue", icon = Icons.Default.AutoAwesome) }
        item { Text("Punla interleaves courses, prioritizes overdue mistakes, and enters exam-cram weighting inside 3 days.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (queue.isEmpty()) item { EmptyState(Icons.Default.CheckCircle, "Nothing urgent is waiting. Add an exam plan or keep studying normally.") }
        items(queue.take(60), key = { "${it.kind}:${it.id}" }) { item ->
            Surface(Modifier.fillMaxWidth().clickable {
                when(item.kind) { StudyEngine.QueueKind.FLASHCARD -> onCards(); StudyEngine.QueueKind.MISTAKE, StudyEngine.QueueKind.QUIZ -> onQuizzes(); StudyEngine.QueueKind.PLAN -> onFocus(item.courseCode); else -> Unit }
            }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when(item.kind){ StudyEngine.QueueKind.FLASHCARD -> Icons.Default.Style; StudyEngine.QueueKind.MISTAKE -> Icons.Default.Replay; StudyEngine.QueueKind.PLAN -> Icons.Default.EventNote; else -> Icons.Default.School }, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(listOfNotNull(item.courseCode, item.subtitle).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(item.priority.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable private fun MistakesTab(mistakes: List<MistakeRecord>, vm: PunlaViewModel) {
    val hp = punlaScreenHorizontalPadding(); var showResolved by rememberSaveable { mutableStateOf(false) }
    val shown = mistakes.filter { showResolved || !it.resolved }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal=hp, vertical=12.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) { SectionLabel("Mistake Notebook", Modifier.weight(1f), Icons.Default.BugReport); Switch(showResolved, {showResolved=it}); Spacer(Modifier.width(4.dp)); Text("Resolved", style=MaterialTheme.typography.labelSmall) } }
        if (shown.isEmpty()) item { EmptyState(Icons.Default.CheckCircle, "No mistakes here yet. Wrong quiz answers and guessed answers will appear automatically.") }
        items(shown, key={it.id}) { m ->
            Card { Column(Modifier.padding(14.dp)) { Row { Column(Modifier.weight(1f)) { Text(m.prompt, fontWeight=FontWeight.SemiBold); Text(listOfNotNull(m.courseCode,m.topicTag,"missed ${m.timesMissed}×").joinToString(" · "), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }; Checkbox(m.resolved,{vm.resolveMistake(m,it)}) }; Text("Answer: ${m.correctAnswer}", style=MaterialTheme.typography.bodyMedium); m.userAnswer?.let { Text("You answered: $it", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.error) }; m.explanation?.let { Text(it, style=MaterialTheme.typography.bodySmall, modifier=Modifier.padding(top=6.dp)) }; Text("Retry ${formatRelativeTime(m.retryAt)} · ${m.confidence.lowercase()}", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.primary) } }
        }
    }
}

@Composable private fun NotesTab(notes: List<StudyNote>, formulas: List<FormulaReference>, topics: List<StudyTopic>, course: String?, onAddNote:()->Unit, onEditNote:(StudyNote)->Unit, onAddFormula:()->Unit, onEditFormula:(FormulaReference)->Unit, vm:PunlaViewModel) {
    val hp=punlaScreenHorizontalPadding(); val filteredNotes=notes.filter{course==null||it.courseCode.equals(course,true)}; val filteredFormulas=formulas.filter{course==null||it.courseCode.equals(course,true)}
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(horizontal=hp,vertical=12.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { SectionLabel("Reviewer notes", icon=Icons.Default.Notes, actionLabel="Add note", onAction=onAddNote) }
        if(filteredNotes.isEmpty()) item{ EmptyState(Icons.Default.Notes,"No reviewer notes yet.",actionLabel="Add note",onAction=onAddNote) }
        items(filteredNotes,key={it.id}) { n ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onEditNote(n) }) {
                            Text(n.title,fontWeight=FontWeight.SemiBold)
                            Text(listOfNotNull(n.courseCode, n.topicId?.let { id -> topics.firstOrNull { it.id == id }?.let { topic -> studyTopicPath(topic, topics) } }, n.tagList().take(3).joinToString(" · ").takeIf{it.isNotBlank()}).joinToString(" · "),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { onEditNote(n) }) { Icon(Icons.Default.Edit, "Edit note") }
                    }
                    Spacer(Modifier.height(5.dp)); Text(n.body,maxLines=5,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Tip: use ‘Question :: Answer’ lines or {{cloze}} text to generate study material.", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            vm.createFlashcardsFromNote(n) { deck ->
                                Toast.makeText(context, if (deck == null) "Add Question :: Answer or {{cloze}} lines first." else "Created ${deck.name}", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Make cards") }
                        TextButton(onClick = {
                            vm.createQuizFromNote(n) { quiz ->
                                Toast.makeText(context, if (quiz == null) "Add Question :: Answer or {{cloze}} lines first." else "Created ${quiz.title}", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Make recall quiz") }
                    }
                }
            }
        }
        item { SectionLabel("Formula sheet", icon=Icons.Default.Functions, actionLabel="Add formula", onAction=onAddFormula) }
        if(filteredFormulas.isEmpty()) item { EmptyState(Icons.Default.Functions, "No formulas yet.", actionLabel="Add formula", onAction=onAddFormula) }
        items(filteredFormulas,key={it.id}){f-> Card(Modifier.fillMaxWidth().clickable{onEditFormula(f)}){Column(Modifier.padding(14.dp)){Text(f.title,fontWeight=FontWeight.SemiBold); Text(listOfNotNull(f.courseCode, f.topicId?.let { id -> topics.firstOrNull { it.id == id }?.let { topic -> studyTopicPath(topic, topics) } }).joinToString(" · "), style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant); Text(StudyMathText.render(f.expression),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary); f.variables?.let{Text(it,style=MaterialTheme.typography.bodySmall)}; f.units?.let{Text("Units: $it",style=MaterialTheme.typography.bodySmall)}; f.workedExample?.let{Text("Worked: ${StudyMathText.render(it)}",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=4.dp))}}}}
    }
}

@Composable private fun PlanTab(items:List<StudyPlanItem>,goals:List<StudyGoal>,topics:List<StudyTopic>,courses:List<String>,course:String?,onAddPlan:()->Unit,onEditPlan:(StudyPlanItem)->Unit,onAddGoal:()->Unit,onEditGoal:(StudyGoal)->Unit,vm:PunlaViewModel){
    val hp=punlaScreenHorizontalPadding(); var examCourse by rememberSaveable{mutableStateOf(course?:courses.firstOrNull().orEmpty())}; var examDate by rememberSaveable{mutableStateOf(LocalDate.now().plusDays(7).toString())}; var minutes by rememberSaveable{mutableStateOf("50")}; val filtered=items.filter{course==null||it.courseCode.equals(course,true)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=hp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{SectionLabel("Exam prep plan",icon=Icons.Default.Event,actionLabel="Add item",onAction=onAddPlan)}
        item{Card{Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Auto-plan an exam",fontWeight=FontWeight.SemiBold); OutlinedTextField(examCourse,{examCourse=it},label={Text("Course")},modifier=Modifier.fillMaxWidth(),singleLine=true); OutlinedTextField(examDate,{examDate=it},label={Text("Exam date (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth(),singleLine=true); OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit)},label={Text("Minutes/day")},modifier=Modifier.fillMaxWidth(),singleLine=true); Button(onClick={runCatching{LocalDate.parse(examDate)}.getOrNull()?.let{vm.generateExamPlan(examCourse.trim(),it,minutes.toIntOrNull()?:50)}},enabled=examCourse.isNotBlank()&&runCatching{LocalDate.parse(examDate)}.isSuccess,modifier=Modifier.fillMaxWidth()){Text("Generate / rebuild plan")}}}}
        items(filtered,key={it.id}){p-> Surface(Modifier.fillMaxWidth().clickable{onEditPlan(p)},shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(p.completed,{vm.upsertStudyPlanItem(p.copy(completed=it))});Column(Modifier.weight(1f)){Text(p.title,fontWeight=FontWeight.Medium);Text(listOfNotNull(p.topicTag, "${p.plannedDate} · ${p.minutes} min · ${p.kind.lowercase().replace('_',' ')}").joinToString(" · "),style=MaterialTheme.typography.bodySmall)}}}}
        item{SectionLabel("Session goals",icon=Icons.Default.TrackChanges,actionLabel="Add goal",onAction=onAddGoal)}
        items(goals.filter{course==null||it.courseCode.equals(course,true)},key={it.id}){g-> Card(Modifier.fillMaxWidth().clickable{onEditGoal(g)}){Column(Modifier.padding(12.dp)){Row{Text(g.title,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Checkbox(g.completed,{vm.upsertStudyGoal(g.copy(completed=it,progressValue=if(it)g.targetValue else g.progressValue))})};LinearProgressIndicator(progress={g.percent()/100f},modifier=Modifier.fillMaxWidth());Text("${studyGoalTypeLabel(g.goalType)} · ${g.progressValue}/${g.targetValue} · ${g.percent()}%${g.topicTag?.let { " · $it" } ?: ""}${g.dueDate?.let{" · due $it"}?:""}",style=MaterialTheme.typography.bodySmall)}}}
    }
}

@Composable private fun AnalyticsTab(weak:List<StudyEngine.WeakTopic>,attempts:List<QuizAttempt>,results:List<QuizAnswerResult>,studyDays:Set<LocalDate>,streak:Int,cards:List<Flashcard>,sessions:List<StudySession>){
    val hp=punlaScreenHorizontalPadding(); val avgQuiz=if(attempts.isEmpty())0 else attempts.map{it.percent()}.average().toInt(); val confidentCorrect=results.count{it.correct&&it.confidence==StudyConfidence.CONFIDENT}; val guessedCorrect=results.count{it.correct&&it.confidence==StudyConfidence.GUESSED}; val minutes=sessions.sumOf{it.actualSeconds}/60
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=hp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{SectionLabel("Study analytics",icon=Icons.Default.Insights)}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){MiniStat("$avgQuiz%","quiz average",Modifier.weight(1f));MiniStat(streak.toString(),"streak",Modifier.weight(1f));MiniStat(minutes.toString(),"focus min",Modifier.weight(1f))}}
        item{Text("Confidence quality",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold);Text("$confidentCorrect confident correct · $guessedCorrect guessed correct",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{Text("28-day study heatmap",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(6.dp));StudyHeatmap(studyDays)}
        item{SectionLabel("Weak topics",icon=Icons.Default.WarningAmber)}
        if(weak.isEmpty()) item{Text("No topic-level evidence yet.",color=MaterialTheme.colorScheme.onSurfaceVariant)} else items(weak.take(20),key={it.key}){WeakTopicRow(it)}
        item{Text("Flashcard mastery",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold);val mastered=cards.count{it.mastery>=4};Text("$mastered/${cards.size} cards at mastery 4–5",style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable
private fun StudyHeatmap(days: Set<LocalDate>) {
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(4) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { col ->
                    val day = today.minusDays((27 - (row * 7 + col)).toLong())
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if (day in days) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (day in days) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun QuestionBankTab(bank:List<QuestionBankItem>,quizzes:List<Quiz>,questions:List<QuizQuestion>,course:String?,vm:PunlaViewModel,onOpenQuizzes:()->Unit,onMessage:(String)->Unit){
    val hp=punlaScreenHorizontalPadding(); val shown=bank.filter{course==null||it.courseCode.equals(course,true)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=hp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{SectionLabel("Question bank",icon=Icons.Default.LibraryBooks)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={vm.syncQuizzesToQuestionBank{count->onMessage("Synced $count current quiz question${if(count==1)"" else "s"} into the reusable bank.")}},modifier=Modifier.weight(1f)){Text("Sync quizzes")};OutlinedButton(onClick={vm.createPracticeTest(course,20,false){onMessage("Practice Test created. Open Quizzes to take it.")}},modifier=Modifier.weight(1f)){Text("Practice test")}}}
        item{OutlinedButton(onClick={vm.createPracticeTest(course,20,true){onMessage("Recall Practice created. Open Quizzes to take it.")}},modifier=Modifier.fillMaxWidth()){Text("Create recall-mode test")}}
        if(shown.isEmpty()) item{EmptyState(Icons.Default.LibraryBooks,"Question bank is empty. Sync your quizzes first.")} else items(shown.take(100),key={it.id}){q->Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(12.dp)){Text(q.prompt,fontWeight=FontWeight.Medium);Text(listOfNotNull(q.courseCode,q.type.lowercase().replace('_',' ')).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("Answer: ${q.correctAnswer}",style=MaterialTheme.typography.bodySmall)}}}
    }
}

@Composable
private fun TopicDialog(
    courses: List<String>,
    topics: List<StudyTopic>,
    selected: String?,
    onDismiss: () -> Unit,
    onSave: (StudyTopic) -> Unit
) {
    var course by rememberSaveable { mutableStateOf(selected ?: courses.firstOrNull().orEmpty()) }
    var name by rememberSaveable { mutableStateOf("") }
    var exam by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableIntStateOf(3) }
    var sortOrder by rememberSaveable { mutableStateOf("0") }
    var parentId by rememberSaveable { mutableStateOf<String?>(null) }
    val parentOptions = topics.filter { it.courseCode.equals(course, true) }
    val labels = listOf("No parent") + parentOptions.map { it.name }
    val selectedParentIndex = parentOptions.indexOfFirst { it.id == parentId }.let { if (it < 0) 0 else it + 1 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add study topic") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(course, { course = it; parentId = null }, label = { Text("Course") }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("Topic / unit") }, singleLine = true)
                PunlaDropdownField(
                    label = "Parent topic (optional)",
                    selectedLabel = labels.getOrElse(selectedParentIndex) { "No parent" },
                    options = labels,
                    onSelect = { index -> parentId = if (index == 0) null else parentOptions.getOrNull(index - 1)?.id },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(exam, { exam = it }, label = { Text("Exam date (optional)") }, singleLine = true)
                OutlinedTextField(sortOrder, { sortOrder = it.filter(Char::isDigit).take(3) }, label = { Text("Module / topic order") }, supportingText = { Text("Lower numbers appear first in the course learning path.") }, singleLine = true)
                Text("Priority")
                SegmentedControl(listOf("1", "2", "3", "4", "5"), priority - 1, { priority = it + 1 })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(StudyTopic(courseCode = course.trim(), name = name.trim(), parentTopicId = parentId, examDate = exam.trim().ifBlank { null }, priority = priority, sortOrder = sortOrder.toIntOrNull() ?: 0)) },
                enabled = course.isNotBlank() && name.isNotBlank() && (exam.isBlank() || runCatching { LocalDate.parse(exam) }.isSuccess)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun studyTopicPath(topic: StudyTopic, topics: List<StudyTopic>): String {
    val byId = topics.associateBy { it.id }
    val seen = mutableSetOf<String>()
    val names = mutableListOf<String>()
    var current: StudyTopic? = topic
    while (current != null && seen.add(current.id) && names.size < 6) {
        names += current.name
        current = current.parentTopicId?.let(byId::get)
    }
    return names.asReversed().joinToString(" › ")
}

private fun topicsForCourse(topics: List<StudyTopic>, course: String): List<StudyTopic> =
    topics.filter { course.isBlank() || it.courseCode.equals(course, true) }
        .sortedWith(compareBy<StudyTopic> { studyTopicPath(it, topics).lowercase() })

private val STUDY_GOAL_TYPE_OPTIONS = listOf(
    StudyGoalTypes.MINUTES to "Focus minutes",
    StudyGoalTypes.FLASHCARDS to "Flashcards reviewed",
    StudyGoalTypes.QUESTIONS to "Quiz questions answered",
    StudyGoalTypes.SCORE to "Best quiz score (%)",
    StudyGoalTypes.CUSTOM to "Manual / custom"
)

private fun studyGoalTypeLabel(type: String): String =
    STUDY_GOAL_TYPE_OPTIONS.firstOrNull { it.first == type }?.second ?: "Manual / custom"

private val STUDY_PLAN_KIND_OPTIONS = listOf(
    StudyPlanKinds.REVIEW to "Review",
    StudyPlanKinds.FLASHCARDS to "Flashcards",
    StudyPlanKinds.QUIZ to "Quiz",
    StudyPlanKinds.NOTES to "Notes / reviewer",
    StudyPlanKinds.FOCUS to "Focus session",
    StudyPlanKinds.PRACTICE_TEST to "Practice test"
)

@Composable
private fun NoteDialog(
    initial: StudyNote?,
    courses: List<String>,
    topics: List<StudyTopic>,
    selected: String?,
    onDismiss: () -> Unit,
    onSave: (StudyNote) -> Unit
) {
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var course by rememberSaveable(initial?.id) { mutableStateOf(initial?.courseCode ?: selected.orEmpty()) }
    var topicId by rememberSaveable(initial?.id) { mutableStateOf(initial?.topicId) }
    var body by rememberSaveable(initial?.id) { mutableStateOf(initial?.body.orEmpty()) }
    var tags by rememberSaveable(initial?.id) { mutableStateOf(initial?.tags.orEmpty()) }
    val availableTopics = topicsForCourse(topics, course)
    val effectiveTopicId = topicId?.takeIf { id -> availableTopics.any { it.id == id } }
    val topicOptions = listOf("No topic") + availableTopics.map { studyTopicPath(it, topics) }
    val selectedTopicIndex = availableTopics.indexOfFirst { it.id == effectiveTopicId }.let { if (it < 0) 0 else it + 1 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New reviewer note" else "Edit note") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(course, { course = it; topicId = null }, label = { Text("Course") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                if (availableTopics.isNotEmpty()) item {
                    PunlaDropdownField(
                        label = "Topic (optional)",
                        selectedLabel = topicOptions.getOrElse(selectedTopicIndex) { "No topic" },
                        options = topicOptions,
                        onSelect = { index -> topicId = if (index == 0) null else availableTopics.getOrNull(index - 1)?.id },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { OutlinedTextField(body, { body = it }, label = { Text("Reviewer / notes") }, modifier = Modifier.fillMaxWidth(), minLines = 6) }
                item { OutlinedTextField(tags, { tags = it }, label = { Text("Tags") }, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    val normalizedTags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString(", ")
                    onSave(
                        initial?.copy(
                            title = title.trim(), courseCode = course.trim().ifBlank { null }, topicId = effectiveTopicId,
                            body = body.trim(), tags = normalizedTags, updatedAt = now
                        ) ?: StudyNote(
                            title = title.trim(), courseCode = course.trim().ifBlank { null }, topicId = effectiveTopicId,
                            body = body.trim(), tags = normalizedTags, createdAt = now, updatedAt = now
                        )
                    )
                },
                enabled = title.isNotBlank() && body.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FormulaDialog(
    initial: FormulaReference?,
    courses: List<String>,
    topics: List<StudyTopic>,
    selected: String?,
    onDismiss: () -> Unit,
    onSave: (FormulaReference) -> Unit
) {
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var course by rememberSaveable(initial?.id) { mutableStateOf(initial?.courseCode ?: selected.orEmpty()) }
    var topicId by rememberSaveable(initial?.id) { mutableStateOf(initial?.topicId) }
    var expression by rememberSaveable(initial?.id) { mutableStateOf(initial?.expression.orEmpty()) }
    var vars by rememberSaveable(initial?.id) { mutableStateOf(initial?.variables.orEmpty()) }
    var units by rememberSaveable(initial?.id) { mutableStateOf(initial?.units.orEmpty()) }
    var worked by rememberSaveable(initial?.id) { mutableStateOf(initial?.workedExample.orEmpty()) }
    val availableTopics = topicsForCourse(topics, course)
    val effectiveTopicId = topicId?.takeIf { id -> availableTopics.any { it.id == id } }
    val topicOptions = listOf("No topic") + availableTopics.map { studyTopicPath(it, topics) }
    val selectedTopicIndex = availableTopics.indexOfFirst { it.id == effectiveTopicId }.let { if (it < 0) 0 else it + 1 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add formula" else "Edit formula") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(course, { course = it; topicId = null }, label = { Text("Course") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                if (availableTopics.isNotEmpty()) item {
                    PunlaDropdownField(
                        label = "Topic (optional)",
                        selectedLabel = topicOptions.getOrElse(selectedTopicIndex) { "No topic" },
                        options = topicOptions,
                        onSelect = { index -> topicId = if (index == 0) null else availableTopics.getOrNull(index - 1)?.id },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { OutlinedTextField(expression, { expression = it }, label = { Text("Formula (supports \\frac, \\sqrt, ^{2})") }, modifier = Modifier.fillMaxWidth()) }
                item { Text("Preview: ${StudyMathText.render(expression)}", color = MaterialTheme.colorScheme.primary) }
                item { OutlinedTextField(vars, { vars = it }, label = { Text("Variables") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(units, { units = it }, label = { Text("Units") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(worked, { worked = it }, label = { Text("Worked example") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(
                        initial?.copy(
                            title = title.trim(), courseCode = course.trim().ifBlank { null }, topicId = effectiveTopicId,
                            expression = expression.trim(), variables = vars.trim().ifBlank { null }, units = units.trim().ifBlank { null },
                            workedExample = worked.trim().ifBlank { null }, updatedAt = now
                        ) ?: FormulaReference(
                            title = title.trim(), courseCode = course.trim().ifBlank { null }, topicId = effectiveTopicId,
                            expression = expression.trim(), variables = vars.trim().ifBlank { null }, units = units.trim().ifBlank { null },
                            workedExample = worked.trim().ifBlank { null }, createdAt = now, updatedAt = now
                        )
                    )
                },
                enabled = title.isNotBlank() && expression.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun GoalDialog(
    initial: StudyGoal?,
    courses: List<String>,
    topics: List<StudyTopic>,
    selected: String?,
    onDismiss: () -> Unit,
    onSave: (StudyGoal) -> Unit
) {
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var course by rememberSaveable(initial?.id) { mutableStateOf(initial?.courseCode ?: selected.orEmpty()) }
    var topicTag by rememberSaveable(initial?.id) { mutableStateOf(initial?.topicTag.orEmpty()) }
    var goalType by rememberSaveable(initial?.id) { mutableStateOf(initial?.goalType ?: StudyGoalTypes.CUSTOM) }
    var target by rememberSaveable(initial?.id) { mutableStateOf((initial?.targetValue ?: 25).toString()) }
    var progress by rememberSaveable(initial?.id) { mutableStateOf((initial?.progressValue ?: 0).toString()) }
    var due by rememberSaveable(initial?.id) { mutableStateOf(initial?.dueDate.orEmpty()) }
    val availableTopics = topicsForCourse(topics, course)
    val topicLabels = listOf("Any topic") + availableTopics.map { studyTopicPath(it, topics) }
    val selectedTopicIndex = availableTopics.indexOfFirst { it.name.equals(topicTag, true) }.let { if (it < 0) 0 else it + 1 }
    val typeIndex = STUDY_GOAL_TYPE_OPTIONS.indexOfFirst { it.first == goalType }.coerceAtLeast(0)
    val targetInt = target.toIntOrNull() ?: 0
    val progressInt = progress.toIntOrNull() ?: 0
    val validDue = due.isBlank() || runCatching { LocalDate.parse(due.trim()) }.isSuccess
    val validTarget = targetInt > 0 && (goalType != StudyGoalTypes.SCORE || targetInt <= 100)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Study goal") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text("Goal") }, modifier = Modifier.fillMaxWidth()) }
                item {
                    PunlaDropdownField(
                        label = "Goal type",
                        selectedLabel = STUDY_GOAL_TYPE_OPTIONS[typeIndex].second,
                        options = STUDY_GOAL_TYPE_OPTIONS.map { it.second },
                        onSelect = { goalType = STUDY_GOAL_TYPE_OPTIONS[it].first },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { OutlinedTextField(course, { course = it; topicTag = "" }, label = { Text("Course (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                if (availableTopics.isNotEmpty() && goalType != StudyGoalTypes.MINUTES) item {
                    PunlaDropdownField(
                        label = "Topic (optional)",
                        selectedLabel = topicLabels.getOrElse(selectedTopicIndex) { "Any topic" },
                        options = topicLabels,
                        onSelect = { index -> topicTag = if (index == 0) "" else availableTopics[index - 1].name },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { OutlinedTextField(target, { target = it.filter(Char::isDigit) }, label = { Text(if (goalType == StudyGoalTypes.SCORE) "Target score (%)" else "Target") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(progress, { progress = it.filter(Char::isDigit) }, label = { Text("Current progress") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(due, { due = it }, label = { Text("Due date (YYYY-MM-DD, optional)") }, modifier = Modifier.fillMaxWidth(), isError = !validDue, singleLine = true) }
                item {
                    Text(
                        when (goalType) {
                            StudyGoalTypes.MINUTES -> "Progress increases automatically from completed Focus minutes."
                            StudyGoalTypes.FLASHCARDS -> "Progress increases automatically when cards are reviewed."
                            StudyGoalTypes.QUESTIONS -> "Progress increases automatically when quiz questions are answered."
                            StudyGoalTypes.SCORE -> "Punla keeps your best quiz score for the matching course/topic."
                            else -> "Manual goals only change when you edit or complete them."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    val t = targetInt.coerceAtLeast(1)
                    val p = progressInt.coerceAtLeast(0).coerceAtMost(t)
                    onSave(
                        initial?.copy(
                            title = title.trim(), courseCode = course.trim().ifBlank { null }, topicTag = if (goalType == StudyGoalTypes.MINUTES) null else topicTag.ifBlank { null },
                            goalType = goalType, targetValue = t, progressValue = p, completed = p >= t,
                            dueDate = due.trim().ifBlank { null }, updatedAt = now
                        ) ?: StudyGoal(
                            title = title.trim(), courseCode = course.trim().ifBlank { null }, topicTag = if (goalType == StudyGoalTypes.MINUTES) null else topicTag.ifBlank { null },
                            goalType = goalType, targetValue = t, progressValue = p, completed = p >= t,
                            dueDate = due.trim().ifBlank { null }, createdAt = now, updatedAt = now
                        )
                    )
                },
                enabled = title.isNotBlank() && validTarget && validDue
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PlanDialog(
    initial: StudyPlanItem?,
    courses: List<String>,
    topics: List<StudyTopic>,
    selected: String?,
    onDismiss: () -> Unit,
    onSave: (StudyPlanItem) -> Unit
) {
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var course by rememberSaveable(initial?.id) { mutableStateOf(initial?.courseCode ?: selected.orEmpty()) }
    var topicTag by rememberSaveable(initial?.id) { mutableStateOf(initial?.topicTag.orEmpty()) }
    var date by rememberSaveable(initial?.id) { mutableStateOf(initial?.plannedDate ?: LocalDate.now().toString()) }
    var minutes by rememberSaveable(initial?.id) { mutableStateOf((initial?.minutes ?: 25).toString()) }
    var kind by rememberSaveable(initial?.id) { mutableStateOf(initial?.kind ?: StudyPlanKinds.REVIEW) }
    val availableTopics = topicsForCourse(topics, course)
    val topicLabels = listOf("No topic") + availableTopics.map { studyTopicPath(it, topics) }
    val selectedTopicIndex = availableTopics.indexOfFirst { it.name.equals(topicTag, true) }.let { if (it < 0) 0 else it + 1 }
    val kindIndex = STUDY_PLAN_KIND_OPTIONS.indexOfFirst { it.first == kind }.coerceAtLeast(0)
    val parsedMinutes = minutes.toIntOrNull() ?: 0
    val validDate = runCatching { LocalDate.parse(date.trim()) }.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Study plan item") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text("Task") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(course, { course = it; topicTag = "" }, label = { Text("Course") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                if (availableTopics.isNotEmpty()) item {
                    PunlaDropdownField(
                        label = "Topic (optional)",
                        selectedLabel = topicLabels.getOrElse(selectedTopicIndex) { "No topic" },
                        options = topicLabels,
                        onSelect = { index -> topicTag = if (index == 0) "" else availableTopics[index - 1].name },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    PunlaDropdownField(
                        label = "Activity",
                        selectedLabel = STUDY_PLAN_KIND_OPTIONS[kindIndex].second,
                        options = STUDY_PLAN_KIND_OPTIONS.map { it.second },
                        onSelect = { kind = STUDY_PLAN_KIND_OPTIONS[it].first },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), isError = !validDate, singleLine = true) }
                item { OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Minutes") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(
                        initial?.copy(
                            title = title.trim(), courseCode = course.trim().ifBlank { null }, topicTag = topicTag.ifBlank { null },
                            plannedDate = date.trim(), minutes = parsedMinutes.coerceIn(1, 720), kind = kind, updatedAt = now
                        ) ?: StudyPlanItem(
                            title = title.trim(), courseCode = course.trim().ifBlank { null }, topicTag = topicTag.ifBlank { null },
                            plannedDate = date.trim(), minutes = parsedMinutes.coerceIn(1, 720), kind = kind, createdAt = now, updatedAt = now
                        )
                    )
                },
                enabled = title.isNotBlank() && validDate && parsedMinutes in 1..720
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable private fun StudyBundlePreviewDialog(bundle:StudyJsonBundle,already:Boolean,importing:Boolean,onDismiss:()->Unit,onImport:()->Unit){
    AlertDialog(onDismissRequest=onDismiss,title={Text("Import ${bundle.title}?")},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){item{Text("${bundle.itemCount} study items${bundle.courseCode?.let{" · $it"}?:""}",fontWeight=FontWeight.SemiBold)};item{Text("Punla file ID: ${bundle.fileId}\nContent ID: ${bundle.contentId}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(already)item{Text("This exact content ID was imported before. Continue only if you intentionally want another copy.",color=MaterialTheme.colorScheme.error)};item{Text("${bundle.topics.size} topics · ${bundle.notes.size} notes · ${bundle.formulas.size} formulas · ${bundle.decks.size} decks · ${bundle.quizzes.size} quizzes",style=MaterialTheme.typography.bodySmall)};if(bundle.warnings.isNotEmpty())item{Text(bundle.warnings.joinToString("\n"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.tertiary)}}},confirmButton={TextButton(onClick=onImport,enabled=!importing){Text(if(importing)"Importing…" else if(already)"Import again" else "Import")}},dismissButton={TextButton(onClick=onDismiss,enabled=!importing){Text("Cancel")}})
}

private fun formatRelativeTime(epoch: Long): String {
    val d = epoch - System.currentTimeMillis()
    return when {
        d <= 0 -> "now"
        d < 60_000L -> "in <1m"
        d < 60L * 60L * 1000L -> "in ${d / (60L * 1000L)}m"
        d < 24L * 60L * 60L * 1000L -> "in ${d / (60L * 60L * 1000L)}h"
        else -> "in ${d / (24L * 60L * 60L * 1000L)}d"
    }
}
