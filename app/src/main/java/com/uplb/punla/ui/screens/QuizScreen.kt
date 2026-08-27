package com.uplb.punla.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uplb.punla.data.QuizJsonExport
import com.uplb.punla.data.QuizJsonImport
import com.uplb.punla.data.QuizJsonPayload
import com.uplb.punla.data.PunlaJsonImportReader
import com.uplb.punla.data.StudyEngine
import com.uplb.punla.data.entity.Flashcard
import com.uplb.punla.data.entity.FlashcardDeck
import com.uplb.punla.data.entity.Quiz
import com.uplb.punla.data.entity.QuizAttempt
import com.uplb.punla.data.entity.QuizAnswerResult
import com.uplb.punla.data.entity.StudyConfidence
import com.uplb.punla.data.entity.QuizQuestion
import com.uplb.punla.data.entity.QuizQuestionTypes
import com.uplb.punla.ui.PunlaViewModel
import org.json.JSONArray
import org.json.JSONObject
import coil.compose.AsyncImage
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class QuizRunRequest(val questionIds: List<String>, val label: String, val examMode: Boolean = false, val id: String = UUID.randomUUID().toString())

private val QUIZ_TYPE_OPTIONS = listOf("Multiple choice", "True / False", "Identification", "Multi-select", "Numeric", "Ordering", "Matching", "Image ID")
private fun quizTypeFromIndex(index: Int): String = when (index) {
    1 -> QuizQuestionTypes.TRUE_FALSE
    2 -> QuizQuestionTypes.IDENTIFICATION
    3 -> QuizQuestionTypes.MULTI_SELECT
    4 -> QuizQuestionTypes.NUMERIC
    5 -> QuizQuestionTypes.ORDERING
    6 -> QuizQuestionTypes.MATCHING
    7 -> QuizQuestionTypes.IMAGE_IDENTIFICATION
    else -> QuizQuestionTypes.MULTIPLE_CHOICE
}
private fun quizTypeIndex(type: String): Int = when (type) {
    QuizQuestionTypes.TRUE_FALSE -> 1
    QuizQuestionTypes.IDENTIFICATION -> 2
    QuizQuestionTypes.MULTI_SELECT -> 3
    QuizQuestionTypes.NUMERIC -> 4
    QuizQuestionTypes.ORDERING -> 5
    QuizQuestionTypes.MATCHING -> 6
    QuizQuestionTypes.IMAGE_IDENTIFICATION -> 7
    else -> 0
}

@Composable
fun QuizScreen(vm: PunlaViewModel) {
    val quizzes by vm.quizzes.collectAsState()
    val allQuestions by vm.quizQuestions.collectAsState()
    val allAttempts by vm.quizAttempts.collectAsState()
    val decks by vm.flashcardDecks.collectAsState()
    val flashcards by vm.flashcards.collectAsState()
    var selectedQuizId by rememberSaveable { mutableStateOf<String?>(null) }
    // Store the active run as primitives so an in-progress quiz survives
    // rotation/process recreation instead of falling back to the detail screen.
    var runQuestionIds by rememberSaveable { mutableStateOf<String?>(null) }
    var runLabel by rememberSaveable { mutableStateOf("") }
    var runExamMode by rememberSaveable { mutableStateOf(false) }
    var runId by rememberSaveable { mutableStateOf<String?>(null) }
    val runRequest = runQuestionIds?.let { encoded ->
        QuizRunRequest(
            questionIds = encoded.split(',').filter { it.isNotBlank() },
            label = runLabel,
            examMode = runExamMode,
            id = runId ?: "restored-run"
        )
    }

    fun startRun(questions: List<QuizQuestion>, label: String, examMode: Boolean = false) {
        runQuestionIds = questions.joinToString(",") { it.id }
        runLabel = label
        runExamMode = examMode
        runId = UUID.randomUUID().toString()
    }
    fun clearRun() {
        runQuestionIds = null
        runLabel = ""
        runExamMode = false
        runId = null
    }

    val selectedQuiz = quizzes.firstOrNull { it.id == selectedQuizId }
    val selectedQuestions = selectedQuiz?.let { quiz -> allQuestions.filter { it.quizId == quiz.id } }.orEmpty()
    val selectedAttempts = selectedQuiz?.let { quiz -> allAttempts.filter { it.quizId == quiz.id }.sortedByDescending { it.completedAt } }.orEmpty()
    val runQuestions = runRequest?.let { request ->
        val byId = selectedQuestions.associateBy { it.id }
        request.questionIds.mapNotNull(byId::get)
    }.orEmpty()

    BackHandler(enabled = runRequest != null || selectedQuizId != null) {
        if (runRequest != null) clearRun() else selectedQuizId = null
    }

    Crossfade(
        targetState = when {
            runRequest != null && selectedQuiz != null -> "take"
            selectedQuiz != null -> "detail"
            else -> "library"
        },
        animationSpec = tween(180),
        label = "quizMode"
    ) { mode ->
        when (mode) {
            "take" -> TakeQuizView(
                quiz = selectedQuiz!!,
                request = runRequest!!,
                questions = runQuestions,
                vm = vm,
                onExit = { clearRun() },
                onRetryMistakes = { missed -> startRun(missed, "Retry mistakes") }
            )
            "detail" -> QuizDetailView(
                quiz = selectedQuiz!!,
                questions = selectedQuestions,
                attempts = selectedAttempts,
                vm = vm,
                onBack = { selectedQuizId = null },
                onStart = { questions, label, examMode -> startRun(questions, label, examMode) }
            )
            else -> QuizLibraryView(
                quizzes = quizzes,
                questions = allQuestions,
                attempts = allAttempts,
                decks = decks,
                flashcards = flashcards,
                vm = vm,
                onOpenQuiz = { selectedQuizId = it.id }
            )
        }
    }
}

@Composable
private fun QuizLibraryView(
    quizzes: List<Quiz>,
    questions: List<QuizQuestion>,
    attempts: List<QuizAttempt>,
    decks: List<FlashcardDeck>,
    flashcards: List<Flashcard>,
    vm: PunlaViewModel,
    onOpenQuiz: (Quiz) -> Unit
) {
    var showQuizDialog by rememberSaveable { mutableStateOf(false) }
    var showFromFlashcards by rememberSaveable { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<QuizJsonPayload?>(null) }
    var duplicateImport by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importInProgress by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val importScope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importInProgress) {
            importScope.launch {
                importInProgress = true
                val parsed = runCatching {
                    withContext(Dispatchers.IO) {
                        QuizJsonImport.parse(
                            PunlaJsonImportReader.readText(context, uri, QuizJsonImport.MAX_FILE_CHARS)
                        )
                    }
                }
                parsed.onSuccess { imported ->
                    vm.checkJsonImport(QuizJsonImport.FILE_ID, imported.contentId)
                        .onSuccess { already ->
                            duplicateImport = already
                            pendingImport = imported
                        }
                        .onFailure { error ->
                            importError = quizImportFailureMessage("check this file", error)
                        }
                }.onFailure { error ->
                    importError = error.message ?: "Punla couldn't import that quiz JSON."
                }
                importInProgress = false
            }
        }
    }
    val horizontalPadding = punlaScreenHorizontalPadding()

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showQuizDialog = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Quiz") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Help, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.size(10.dp))
                            Text("Quiz maker", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.size(10.dp))
                        Text("Create your own tests, turn flashcards into identification quizzes, or import a type-safe Punla quiz JSON from ChatGPT.")
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch(JSON_MIME_TYPES) }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) {
                        Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Import JSON")
                    }
                    OutlinedButton(onClick = { showFromFlashcards = true }, enabled = decks.any { deck -> flashcards.any { it.deckId == deck.id } }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) {
                        Icon(Icons.Default.Style, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("From cards")
                    }
                }
            }
            if (quizzes.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Help,
                        message = "No quizzes yet. Create one manually, import one from ChatGPT, or turn a flashcard deck into a quiz.",
                        actionLabel = "Create your first quiz",
                        onAction = { showQuizDialog = true }
                    )
                }
            } else {
                item { SectionLabel("Your quizzes", icon = Icons.Default.Help) }
                items(quizzes, key = { it.id }) { quiz ->
                    val quizQuestions = questions.filter { it.quizId == quiz.id }
                    val quizAttempts = attempts.filter { it.quizId == quiz.id }
                    QuizListCard(quiz, quizQuestions.size, quizAttempts, onClick = { onOpenQuiz(quiz) })
                }
            }
        }
    }

    if (showQuizDialog) {
        QuizEditorDialog(null, onDismiss = { showQuizDialog = false }) {
            vm.upsertQuiz(it)
            showQuizDialog = false
            onOpenQuiz(it)
        }
    }
    if (showFromFlashcards) {
        FlashcardDeckPickerDialog(
            decks = decks,
            cards = flashcards,
            onDismiss = { showFromFlashcards = false },
            onSelect = { deck, deckCards ->
                vm.createQuizFromFlashcards(deck, deckCards) { created -> onOpenQuiz(created) }
                showFromFlashcards = false
            }
        )
    }
    pendingImport?.let { imported ->
        QuizJsonPreviewDialog(
            imported = imported,
            alreadyImported = duplicateImport,
            importing = importInProgress,
            onDismiss = { if (!importInProgress) pendingImport = null }
        ) {
            if (!importInProgress) {
                val now = System.currentTimeMillis()
                val quiz = Quiz(
                    title = imported.title,
                    courseCode = imported.courseCode,
                    description = imported.description,
                    passingScore = imported.passingScore,
                    shuffleQuestions = imported.shuffleQuestions,
                    shuffleChoices = imported.shuffleChoices,
                    timeLimitMinutes = imported.timeLimitMinutes,
                    feedbackMode = imported.feedbackMode,
                    createdAt = now,
                    updatedAt = now
                )
                val importedQuestions = imported.questions.map { q ->
                    QuizQuestion(
                        quizId = quiz.id,
                        type = q.type,
                        prompt = q.prompt,
                        optionsJson = QuizQuestion.encodeOptions(q.options),
                        correctAnswer = q.correctAnswer,
                        explanation = q.explanation,
                        tags = q.tags,
                        metadataJson = q.metadataJson,
                        imageUri = q.imageUri,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                importScope.launch {
                    importInProgress = true
                    val result = vm.importQuiz(quiz, importedQuestions, imported.contentId)
                    importInProgress = false
                    result.onSuccess {
                        pendingImport = null
                        onOpenQuiz(quiz)
                    }.onFailure { error ->
                        importError = quizImportFailureMessage("save the imported quiz", error)
                    }
                }
            }
        }
    }
    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Couldn't import quiz") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun QuizListCard(quiz: Quiz, questionCount: Int, attempts: List<QuizAttempt>, onClick: () -> Unit) {
    val best = attempts.maxOfOrNull { it.percent() }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Help, null, tint = MaterialTheme.colorScheme.tertiary) }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(quiz.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(quiz.courseCode?.takeIf { it.isNotBlank() }, "$questionCount questions").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (best != null) Tag("Best $best%", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun QuizDetailView(
    quiz: Quiz,
    questions: List<QuizQuestion>,
    attempts: List<QuizAttempt>,
    vm: PunlaViewModel,
    onBack: () -> Unit,
    onStart: (List<QuizQuestion>, String, Boolean) -> Unit
) {
    var showQuizDialog by rememberSaveable(quiz.id) { mutableStateOf(false) }
    var showQuestionDialog by rememberSaveable(quiz.id) { mutableStateOf(false) }
    var editingQuestion by remember { mutableStateOf<QuizQuestion?>(null) }
    var deleteQuestion by remember { mutableStateOf<QuizQuestion?>(null) }
    var confirmDelete by rememberSaveable(quiz.id) { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val payload = QuizJsonExport.build(quiz, questions)
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    ?: throw IllegalStateException("Punla couldn't write that file.")
            }.onSuccess { exportMessage = "Quiz JSON exported. You can send it to ChatGPT for editing or import it into Punla elsewhere." }
                .onFailure { exportMessage = it.message ?: "Couldn't export this quiz." }
        }
    }
    val latest = attempts.firstOrNull()
    val latestMissed = latest?.incorrectQuestionIds()?.let { ids -> questions.filter { it.id in ids } }.orEmpty()
    val horizontalPadding = punlaScreenHorizontalPadding()

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editingQuestion = null; showQuestionDialog = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Question") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to quizzes") }
                    Column(Modifier.weight(1f)) {
                        Text(quiz.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(quiz.courseCode, "${questions.size} questions", "Pass ${quiz.passingScore}%").filter { !it.isNullOrBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { exportLauncher.launch(safeQuizFileName(quiz.title)) }) { Icon(Icons.Default.Save, "Export quiz JSON") }
                    IconButton(onClick = { showQuizDialog = true }) { Icon(Icons.Default.Edit, "Edit quiz") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete quiz") }
                }
            }
            quiz.description?.takeIf { it.isNotBlank() }?.let { description ->
                item { Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (questions.isNotEmpty()) {
                item {
                    Button(onClick = { onStart(questions, "Full quiz", false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.size(6.dp)); Text("Start ${questions.size}-question quiz")
                    }
                }
                item {
                    OutlinedButton(onClick = { onStart(questions, "Practice test", true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Default.Assignment, null); Spacer(Modifier.size(6.dp)); Text("Practice test mode · feedback at end")
                    }
                }
                if (latestMissed.isNotEmpty()) {
                    item {
                        OutlinedButton(onClick = { onStart(latestMissed, "Retry mistakes", false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Retry ${latestMissed.size} mistake${if (latestMissed.size == 1) "" else "s"} from last attempt")
                        }
                    }
                }
            }
            if (attempts.isNotEmpty()) {
                item { SectionLabel("Recent attempts", icon = Icons.Default.History) }
                items(attempts.take(5), key = { it.id }) { attempt -> AttemptRow(attempt, quiz.passingScore) }
            }
            item { SectionLabel("Questions", icon = Icons.Default.Help) }
            if (questions.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Help,
                        message = "No questions yet. Add one manually or create a new quiz from a flashcard deck.",
                        actionLabel = "Add first question",
                        onAction = { editingQuestion = null; showQuestionDialog = true }
                    )
                }
            } else {
                items(questions, key = { it.id }) { question ->
                    QuestionListCard(question, onEdit = { editingQuestion = question; showQuestionDialog = true }, onDelete = { deleteQuestion = question })
                }
            }
        }
    }

    if (showQuizDialog) {
        QuizEditorDialog(quiz, onDismiss = { showQuizDialog = false }) { vm.upsertQuiz(it); showQuizDialog = false }
    }
    if (showQuestionDialog) {
        QuestionEditorDialog(quiz.id, editingQuestion, onDismiss = { showQuestionDialog = false; editingQuestion = null }) {
            vm.upsertQuizQuestion(it)
            showQuestionDialog = false
            editingQuestion = null
        }
    }
    deleteQuestion?.let { q ->
        AlertDialog(
            onDismissRequest = { deleteQuestion = null },
            title = { Text("Delete question?") },
            text = { Text(q.prompt, maxLines = 5, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton(onClick = { vm.deleteQuizQuestion(q); deleteQuestion = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteQuestion = null }) { Text("Cancel") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${quiz.title}?") },
            text = { Text("This deletes its questions and attempt history. Flashcards used to create it are not affected.") },
            confirmButton = { TextButton(onClick = { vm.deleteQuiz(quiz); confirmDelete = false; onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    exportMessage?.let { msg ->
        AlertDialog(onDismissRequest = { exportMessage = null }, title = { Text("Quiz") }, text = { Text(msg) }, confirmButton = { TextButton(onClick = { exportMessage = null }) { Text("OK") } })
    }
}

@Composable
private fun AttemptRow(attempt: QuizAttempt, passingScore: Int) {
    val percent = attempt.percent()
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${attempt.score}/${attempt.total} · $percent%", fontWeight = FontWeight.SemiBold)
                Text("${attempt.incorrectQuestionIds().size} missed · ${formatDuration(attempt.durationMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Tag(if (percent >= passingScore) "Passed" else "Review", if (percent >= passingScore) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer, if (percent >= passingScore) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun QuestionListCard(question: QuizQuestion, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Tag(questionTypeLabel(question.type), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.size(7.dp))
                    Text(question.prompt, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit question") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete question") }
            }
            Spacer(Modifier.size(8.dp))
            Text("Answer: ${question.correctAnswer}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (question.tags.isNotBlank()) Text(question.tagList().joinToString(" · ") { "#$it" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TakeQuizView(
    quiz: Quiz,
    request: QuizRunRequest,
    questions: List<QuizQuestion>,
    vm: PunlaViewModel,
    onExit: () -> Unit,
    onRetryMistakes: (List<QuizQuestion>) -> Unit
) {
    if (questions.isEmpty() && request.questionIds.isNotEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading quiz…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val queueIds = rememberSaveable(quiz.id, request.id) {
        if (quiz.shuffleQuestions) request.questionIds.shuffled(Random(request.id.hashCode())) else request.questionIds
    }
    val requestQuestionsById = remember(questions.map { it.id }) { questions.associateBy { it.id } }
    val queue = remember(queueIds, requestQuestionsById) { queueIds.mapNotNull(requestQuestionsById::get) }
    val startedAt = rememberSaveable(quiz.id, request.id) { System.currentTimeMillis() }
    val attemptId = rememberSaveable(quiz.id, request.id) { UUID.randomUUID().toString() }
    var index by rememberSaveable(quiz.id, request.id) { mutableIntStateOf(0) }
    var answer by rememberSaveable(quiz.id, request.id) { mutableStateOf("") }
    var submitted by rememberSaveable(quiz.id, request.id) { mutableStateOf(false) }
    var answerQuestionId by rememberSaveable(quiz.id, request.id) { mutableStateOf<String?>(null) }
    var score by rememberSaveable(quiz.id, request.id) { mutableIntStateOf(0) }
    var missedIds by rememberSaveable(quiz.id, request.id) { mutableStateOf(listOf<String>()) }
    val answerResultsSaver = remember {
        listSaver<List<QuizAnswerResult>, Any>(
            save = { results ->
                results.flatMap { result ->
                    listOf(
                        result.id, result.attemptId, result.quizId, result.questionId,
                        result.userAnswer, result.correctAnswer, result.correct,
                        result.confidence, result.answeredAt
                    )
                }
            },
            restore = { saved ->
                saved.chunked(9).mapNotNull { values ->
                    if (values.size != 9) return@mapNotNull null
                    QuizAnswerResult(
                        id = values[0] as String,
                        attemptId = values[1] as String,
                        quizId = values[2] as String,
                        questionId = values[3] as String,
                        userAnswer = values[4] as String,
                        correctAnswer = values[5] as String,
                        correct = values[6] as Boolean,
                        confidence = values[7] as String,
                        answeredAt = values[8] as Long
                    )
                }
            }
        )
    }
    var answerResults by rememberSaveable(quiz.id, request.id, stateSaver = answerResultsSaver) {
        mutableStateOf(listOf<QuizAnswerResult>())
    }
    var confidence by rememberSaveable(quiz.id, request.id) { mutableStateOf(StudyConfidence.UNSURE) }
    var attemptSaved by rememberSaveable(quiz.id, request.id) { mutableStateOf(false) }
    var attemptSaving by remember(quiz.id, request.id) { mutableStateOf(false) }
    var attemptSaveError by remember(quiz.id, request.id) { mutableStateOf<String?>(null) }
    var mistakesSavedAsCards by rememberSaveable(quiz.id, request.id) { mutableStateOf(false) }
    var elapsedSeconds by rememberSaveable(quiz.id, request.id) { mutableIntStateOf(0) }
    var timedOut by rememberSaveable(quiz.id, request.id) { mutableStateOf(false) }
    val finished = queue.isEmpty() || index >= queue.size
    val horizontalPadding = punlaScreenHorizontalPadding(720.dp)
    val effectiveTimeLimitMinutes = quiz.timeLimitMinutes?.takeIf { it > 0 }
        ?: if (request.examMode) queue.size.coerceAtLeast(1) else null
    val remainingSeconds = effectiveTimeLimitMinutes?.let { (it * 60 - elapsedSeconds).coerceAtLeast(0) }
    val timeExpired = timedOut || (remainingSeconds == 0 && effectiveTimeLimitMinutes != null && !finished)
    val deferredFeedback = request.examMode || quiz.feedbackMode == "AFTER"

    LaunchedEffect(startedAt, effectiveTimeLimitMinutes, finished) {
        while (!finished) {
            kotlinx.coroutines.delay(1_000L)
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(0)
            if (effectiveTimeLimitMinutes != null && elapsedSeconds >= effectiveTimeLimitMinutes * 60) {
                timedOut = true
                val alreadyAnswered = answerResults.mapTo(hashSetOf()) { it.questionId }
                val unanswered = queue.drop(index).filter { it.id !in alreadyAnswered }
                if (unanswered.isNotEmpty()) {
                    missedIds = (missedIds + unanswered.map { it.id }).distinct()
                    val answeredAt = System.currentTimeMillis()
                    answerResults = answerResults + unanswered.map { q ->
                        QuizAnswerResult(
                            attemptId = attemptId,
                            quizId = quiz.id,
                            questionId = q.id,
                            userAnswer = "",
                            correctAnswer = q.correctAnswer,
                            correct = false,
                            confidence = StudyConfidence.UNSET,
                            answeredAt = answeredAt
                        )
                    }
                }
                index = queue.size
                break
            }
        }
    }

    if (finished && queue.isNotEmpty() && !attemptSaved && !attemptSaving && attemptSaveError == null) {
        LaunchedEffect(queue, score, missedIds, answerResults) {
            attemptSaving = true
            val completedAt = System.currentTimeMillis()
            val attempt = QuizAttempt(
                id = attemptId,
                quizId = quiz.id,
                startedAt = startedAt,
                completedAt = completedAt,
                score = score,
                total = queue.size,
                durationMs = completedAt - startedAt,
                incorrectQuestionIdsJson = JSONArray(missedIds.distinct()).toString()
            )
            vm.recordQuizAttemptWithResults(attempt, answerResults, queue.associateBy { it.id }, quiz)
                .onSuccess { attemptSaved = true }
                .onFailure { error ->
                    attemptSaveError = error.message ?: "Punla couldn't save this quiz attempt."
                }
            attemptSaving = false
        }
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = horizontalPadding, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Exit quiz") }
                Column(Modifier.weight(1f)) {
                    Text(quiz.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (finished) request.label else "${request.label} · Question ${index + 1} of ${queue.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                remainingSeconds?.let {
                    Text("%02d:%02d".format(it / 60, it % 60), color = if (it <= 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            if (!finished) {
                LinearProgressIndicator(progress = { (index + 1).toFloat() / queue.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(18.dp))
                val question = queue[index]
                // Choice/order shuffles are deterministic for this attempt so
                // rotation/recomposition cannot silently reshuffle the active question.
                val baseOptions = remember(question.id, request.id, attemptId) {
                    val seed = "$attemptId|${question.id}|options".hashCode()
                    when (question.type) {
                        QuizQuestionTypes.TRUE_FALSE -> listOf("True", "False")
                        QuizQuestionTypes.MULTIPLE_CHOICE, QuizQuestionTypes.MULTI_SELECT -> {
                            val base = question.options()
                            if (quiz.shuffleChoices) base.shuffled(Random(seed)) else base
                        }
                        QuizQuestionTypes.ORDERING -> question.options().shuffled(Random(seed))
                        else -> question.options()
                    }
                }
                LaunchedEffect(question.id, answerQuestionId) {
                    // LaunchedEffect runs again after a configuration change. The
                    // saved question id prevents it from wiping an in-progress answer.
                    if (answerQuestionId != question.id) {
                        answer = when (question.type) {
                            QuizQuestionTypes.ORDERING -> JSONArray(baseOptions).toString()
                            QuizQuestionTypes.MATCHING -> "{}"
                            else -> ""
                        }
                        submitted = false
                        confidence = StudyConfidence.UNSURE
                        answerQuestionId = question.id
                    }
                }
                val currentCorrect = StudyEngine.evaluate(question, answer)

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(20.dp)) {
                        Tag(questionTypeLabel(question.type), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.size(12.dp))
                        if (!question.imageUri.isNullOrBlank()) {
                            AsyncImage(model = question.imageUri, contentDescription = "Question diagram", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 260.dp).clip(RoundedCornerShape(14.dp)))
                            Spacer(Modifier.size(12.dp))
                        }
                        Text(question.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(18.dp))

                        when (question.type) {
                            QuizQuestionTypes.IDENTIFICATION, QuizQuestionTypes.IMAGE_IDENTIFICATION, QuizQuestionTypes.NUMERIC -> {
                                OutlinedTextField(
                                    value = answer,
                                    onValueChange = { if (!submitted) answer = it },
                                    label = { Text(if (question.type == QuizQuestionTypes.NUMERIC) "Numeric answer" else "Your answer") },
                                    modifier = Modifier.fillMaxWidth(), enabled = !submitted, minLines = 1
                                )
                                if (question.type == QuizQuestionTypes.NUMERIC) {
                                    val tolerance = runCatching { JSONObject(question.metadataJson).optDouble("tolerance", 0.0) }.getOrDefault(0.0)
                                    if (tolerance > 0) Text("Accepted tolerance: ±$tolerance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            QuizQuestionTypes.MULTI_SELECT -> {
                                val selected = decodeJsonStringList(answer).toMutableSet()
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    baseOptions.forEach { option ->
                                        FilterChip(
                                            selected = selected.any { QuizQuestion.normalizeAnswer(it) == QuizQuestion.normalizeAnswer(option) },
                                            onClick = {
                                                if (!submitted) {
                                                    val match = selected.firstOrNull { QuizQuestion.normalizeAnswer(it) == QuizQuestion.normalizeAnswer(option) }
                                                    if (match != null) selected.remove(match) else selected.add(option)
                                                    answer = JSONArray(selected.toList()).toString()
                                                }
                                            }, label = { Text(option) }, modifier = Modifier.fillMaxWidth(), enabled = !submitted
                                        )
                                    }
                                }
                            }
                            QuizQuestionTypes.ORDERING -> {
                                val ordered = decodeJsonStringList(answer).ifEmpty { baseOptions }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ordered.forEachIndexed { pos, item ->
                                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text("${pos + 1}.", modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Bold)
                                                Text(item, modifier = Modifier.weight(1f))
                                                IconButton(enabled = !submitted && pos > 0, onClick = { answer = JSONArray(ordered.toMutableList().apply { val v = removeAt(pos); add(pos - 1, v) }).toString() }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                                                IconButton(enabled = !submitted && pos < ordered.lastIndex, onClick = { answer = JSONArray(ordered.toMutableList().apply { val v = removeAt(pos); add(pos + 1, v) }).toString() }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                                            }
                                        }
                                    }
                                }
                            }
                            QuizQuestionTypes.MATCHING -> {
                                val correctMap = decodeMatchingPairs(question.correctAnswer)
                                val current = decodeMatchingPairs(answer).toMutableMap()
                                val rights = remember(question.id, request.id, attemptId) {
                                    correctMap.values.distinct().shuffled(Random("$attemptId|${question.id}|matching".hashCode()))
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                    correctMap.keys.forEach { left ->
                                        PunlaDropdownField(
                                            label = left,
                                            selectedLabel = current[left] ?: "Choose match",
                                            options = rights,
                                            onSelect = { selected ->
                                                if (!submitted) {
                                                    current[left] = rights[selected]
                                                    answer = JSONObject(current as Map<*, *>).toString()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                            else -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    baseOptions.forEach { option ->
                                        FilterChip(selected = answer == option, onClick = { if (!submitted) answer = option }, label = { Text(option) }, modifier = Modifier.fillMaxWidth(), enabled = !submitted)
                                    }
                                }
                            }
                        }

                        if (!submitted) {
                            Spacer(Modifier.size(16.dp))
                            Text("How sure are you?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.size(6.dp))
                            SegmentedControl(
                                listOf("Guessed", "Unsure", "Confident"),
                                when (confidence) { StudyConfidence.GUESSED -> 0; StudyConfidence.CONFIDENT -> 2; else -> 1 },
                                { confidence = when (it) { 0 -> StudyConfidence.GUESSED; 2 -> StudyConfidence.CONFIDENT; else -> StudyConfidence.UNSURE } },
                                Modifier.fillMaxWidth()
                            )
                        }

                        if (submitted && !deferredFeedback) {
                            Spacer(Modifier.size(16.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (currentCorrect) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = if (currentCorrect) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(if (currentCorrect) "Correct" else "Answer: ${displayCorrectAnswer(question)}", fontWeight = FontWeight.SemiBold)
                                    question.explanation?.takeIf { it.isNotBlank() }?.let {
                                        Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        } else if (submitted && deferredFeedback) {
                            Spacer(Modifier.size(12.dp)); Text("Answer locked. Feedback will appear after the test.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.size(14.dp))
                if (!submitted) {
                    Button(
                        onClick = {
                            val correct = StudyEngine.evaluate(question, answer)
                            submitted = true
                            if (correct) score++ else missedIds = missedIds + question.id
                            answerResults = answerResults + QuizAnswerResult(
                                attemptId = attemptId, quizId = quiz.id, questionId = question.id,
                                userAnswer = answer, correctAnswer = question.correctAnswer, correct = correct,
                                confidence = confidence, answeredAt = System.currentTimeMillis()
                            )
                        },
                        enabled = answerReady(question, answer) && !timeExpired,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text(if (request.examMode) "Lock answer" else "Check answer") }
                } else {
                    Button(
                        onClick = { index++; answer = ""; submitted = false; confidence = StudyConfidence.UNSURE },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text(if (index == queue.lastIndex) "See results" else "Next question") }
                }
            } else {
                Spacer(Modifier.size(28.dp))
                val percent = if (queue.isEmpty()) 0 else ((score * 100.0) / queue.size).toInt()
                val passed = percent >= quiz.passingScore
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = if (passed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(10.dp))
                        Text(if (timeExpired) "Time is up" else if (passed) "Quiz complete" else "Review and try again", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("$score/${queue.size} · $percent%", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
                        Text("${formatDuration((elapsedSeconds * 1000L))} · passing ${quiz.passingScore}%", style = MaterialTheme.typography.bodySmall)
                        when {
                            attemptSaving -> Text("Saving attempt…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                            attemptSaveError != null -> {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text(attemptSaveError ?: "Couldn't save attempt.", style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = { attemptSaveError = null }) { Text("Retry save") }
                                    }
                                }
                            }
                        }
                        val guessedCorrect = answerResults.count { it.correct && it.confidence == StudyConfidence.GUESSED }
                        if (guessedCorrect > 0) Text("$guessedCorrect correct answer${if (guessedCorrect == 1) " was" else "s were"} guessed and added to weak-review signals.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        val missed = queue.filter { it.id in missedIds }
                        if (deferredFeedback && missed.isNotEmpty()) {
                            Spacer(Modifier.size(16.dp)); Text("Mistake review", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            missed.take(6).forEach { q ->
                                Text("• ${q.prompt}\n  ${displayCorrectAnswer(q)}${q.explanation?.let { " — $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                            }
                        }
                        if (missed.isNotEmpty()) {
                            Spacer(Modifier.size(16.dp))
                            OutlinedButton(onClick = { onRetryMistakes(missed) }, modifier = Modifier.fillMaxWidth()) { Text("Retry ${missed.size} mistakes") }
                            Spacer(Modifier.size(8.dp))
                            OutlinedButton(
                                onClick = { vm.createFlashcardsFromQuizMistakes(quiz, missed); mistakesSavedAsCards = true },
                                enabled = !mistakesSavedAsCards, modifier = Modifier.fillMaxWidth()
                            ) { Text(if (mistakesSavedAsCards) "Mistakes saved to Flashcards" else "Make flashcards from mistakes") }
                        }
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Back to quiz") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizEditorDialog(initial: Quiz?, onDismiss: () -> Unit, onSave: (Quiz) -> Unit) {
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var course by rememberSaveable(initial?.id) { mutableStateOf(initial?.courseCode.orEmpty()) }
    var description by rememberSaveable(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var passing by rememberSaveable(initial?.id) { mutableStateOf((initial?.passingScore ?: 70).toString()) }
    var shuffleQuestions by rememberSaveable(initial?.id) { mutableStateOf(initial?.shuffleQuestions ?: true) }
    var shuffleChoices by rememberSaveable(initial?.id) { mutableStateOf(initial?.shuffleChoices ?: true) }
    var timeLimit by rememberSaveable(initial?.id) { mutableStateOf(initial?.timeLimitMinutes?.toString().orEmpty()) }
    var feedbackAfter by rememberSaveable(initial?.id) { mutableStateOf(initial?.feedbackMode == "AFTER") }
    val passingInt = passing.toIntOrNull()
    val valid = title.trim().isNotBlank() && passingInt != null && passingInt in 1..100
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New quiz" else "Edit quiz") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text("Quiz title") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(course, { course = it }, label = { Text("Course code (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                item { OutlinedTextField(passing, { passing = it.filter(Char::isDigit).take(3) }, label = { Text("Passing score %") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = passing.isNotBlank() && (passingInt == null || passingInt !in 1..100)) }
                item { OutlinedTextField(timeLimit, { timeLimit = it.filter(Char::isDigit).take(3) }, label = { Text("Time limit minutes (optional)") }, supportingText = { Text("Leave blank for untimed. Practice-test mode defaults to roughly 1 minute/question if blank.") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Feedback after quiz"); Text("Hide answers/explanations until the end", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(feedbackAfter, { feedbackAfter = it })
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Shuffle questions"); Text("Randomize question order on each attempt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(shuffleQuestions, { shuffleQuestions = it })
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Shuffle choices"); Text("Randomize multiple-choice options", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(shuffleChoices, { shuffleChoices = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val now = System.currentTimeMillis()
                onSave(initial?.copy(title = title.trim(), courseCode = course.trim().ifBlank { null }, description = description.trim().ifBlank { null }, passingScore = passingInt ?: 70, shuffleQuestions = shuffleQuestions, shuffleChoices = shuffleChoices, timeLimitMinutes = timeLimit.toIntOrNull()?.takeIf { it > 0 }, feedbackMode = if (feedbackAfter) "AFTER" else "IMMEDIATE", updatedAt = now)
                    ?: Quiz(title = title.trim(), courseCode = course.trim().ifBlank { null }, description = description.trim().ifBlank { null }, passingScore = passingInt ?: 70, shuffleQuestions = shuffleQuestions, shuffleChoices = shuffleChoices, timeLimitMinutes = timeLimit.toIntOrNull()?.takeIf { it > 0 }, feedbackMode = if (feedbackAfter) "AFTER" else "IMMEDIATE", createdAt = now, updatedAt = now))
            }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun QuestionEditorDialog(quizId: String, initial: QuizQuestion?, onDismiss: () -> Unit, onSave: (QuizQuestion) -> Unit) {
    var typeIndex by rememberSaveable(initial?.id) { mutableIntStateOf(quizTypeIndex(initial?.type ?: QuizQuestionTypes.MULTIPLE_CHOICE)) }
    var prompt by rememberSaveable(initial?.id) { mutableStateOf(initial?.prompt.orEmpty()) }
    val initialOptions = initial?.options().orEmpty()
    var a by rememberSaveable(initial?.id) { mutableStateOf(initialOptions.getOrNull(0).orEmpty()) }
    var b by rememberSaveable(initial?.id) { mutableStateOf(initialOptions.getOrNull(1).orEmpty()) }
    var c by rememberSaveable(initial?.id) { mutableStateOf(initialOptions.getOrNull(2).orEmpty()) }
    var d by rememberSaveable(initial?.id) { mutableStateOf(initialOptions.getOrNull(3).orEmpty()) }
    var correctChoice by rememberSaveable(initial?.id) {
        mutableIntStateOf(initialOptions.indexOfFirst { QuizQuestion.normalizeAnswer(it) == QuizQuestion.normalizeAnswer(initial?.correctAnswer.orEmpty()) }.coerceAtLeast(0))
    }
    var multiCorrectCsv by rememberSaveable(initial?.id) {
        mutableStateOf(if (initial?.type == QuizQuestionTypes.MULTI_SELECT) decodeJsonStringList(initial?.correctAnswer.orEmpty()).mapNotNull { ans -> initialOptions.indexOfFirst { QuizQuestion.normalizeAnswer(it) == QuizQuestion.normalizeAnswer(ans) }.takeIf { it >= 0 } }.joinToString(",") else "")
    }
    var trueFalseIndex by rememberSaveable(initial?.id) { mutableIntStateOf(if (initial?.correctAnswer.equals("False", true)) 1 else 0) }
    var typedAnswer by rememberSaveable(initial?.id) {
        mutableStateOf(if (initial?.type in setOf(QuizQuestionTypes.IDENTIFICATION, QuizQuestionTypes.NUMERIC, QuizQuestionTypes.IMAGE_IDENTIFICATION)) initial?.correctAnswer.orEmpty() else "")
    }
    var numericTolerance by rememberSaveable(initial?.id) {
        mutableStateOf(runCatching { JSONObject(initial?.metadataJson ?: "{}").optDouble("tolerance", 0.0).toString() }.getOrDefault("0.0"))
    }
    var orderingText by rememberSaveable(initial?.id) {
        mutableStateOf(if (initial?.type == QuizQuestionTypes.ORDERING) decodeJsonStringList(initial?.correctAnswer.orEmpty()).joinToString("\n") else initialOptions.joinToString("\n"))
    }
    var matchingText by rememberSaveable(initial?.id) {
        mutableStateOf(if (initial?.type == QuizQuestionTypes.MATCHING) decodeMatchingPairs(initial?.correctAnswer.orEmpty()).entries.joinToString("\n") { "${it.key} :: ${it.value}" } else "")
    }
    var explanation by rememberSaveable(initial?.id) { mutableStateOf(initial?.explanation.orEmpty()) }
    var tags by rememberSaveable(initial?.id) { mutableStateOf(initial?.tags.orEmpty()) }
    var imageUri by rememberSaveable(initial?.id) { mutableStateOf(initial?.imageUri) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            imageUri = uri.toString()
            typeIndex = 7
        }
    }

    val type = quizTypeFromIndex(typeIndex)
    val rawChoices = listOf(a, b, c, d).map { it.trim() }
    val choices = rawChoices.filter { it.isNotEmpty() }
    val choicesAreUnique = choices.map(QuizQuestion::normalizeAnswer).distinct().size == choices.size
    val multiSelected = multiCorrectCsv.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    val orderItems = orderingText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val orderItemsAreUnique = orderItems.map(QuizQuestion::normalizeAnswer).distinct().size == orderItems.size
    val matchPairs = parseMatchingEditor(matchingText)
    val matchKeysAreUnique = matchPairs.keys.map(QuizQuestion::normalizeAnswer).distinct().size == matchPairs.size
    val matchValuesAreUnique = matchPairs.values.map(QuizQuestion::normalizeAnswer).distinct().size == matchPairs.size
    val valid = prompt.trim().isNotEmpty() && when (type) {
        QuizQuestionTypes.MULTIPLE_CHOICE -> choices.size >= 2 && choicesAreUnique && rawChoices.getOrNull(correctChoice).orEmpty().isNotBlank()
        QuizQuestionTypes.MULTI_SELECT -> choices.size >= 2 && choicesAreUnique && multiSelected.isNotEmpty() && multiSelected.all { it in rawChoices.indices && rawChoices[it].isNotBlank() }
        QuizQuestionTypes.TRUE_FALSE -> true
        QuizQuestionTypes.IDENTIFICATION, QuizQuestionTypes.IMAGE_IDENTIFICATION -> typedAnswer.trim().isNotEmpty() && (type != QuizQuestionTypes.IMAGE_IDENTIFICATION || !imageUri.isNullOrBlank())
        QuizQuestionTypes.NUMERIC -> typedAnswer.toDoubleOrNull() != null && (numericTolerance.toDoubleOrNull()?.let { it >= 0.0 } == true)
        QuizQuestionTypes.ORDERING -> orderItems.size >= 2 && orderItemsAreUnique
        QuizQuestionTypes.MATCHING -> matchPairs.size >= 2 && matchKeysAreUnique && matchValuesAreUnique
        else -> false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add question" else "Edit question") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    PunlaDropdownField(
                        label = "Question type",
                        selectedLabel = QUIZ_TYPE_OPTIONS[typeIndex],
                        options = QUIZ_TYPE_OPTIONS,
                        onSelect = { typeIndex = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { OutlinedTextField(prompt, { prompt = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }

                if (type in setOf(QuizQuestionTypes.MULTIPLE_CHOICE, QuizQuestionTypes.MULTI_SELECT)) {
                    item { Text(if (type == QuizQuestionTypes.MULTI_SELECT) "Choices — select every correct answer" else "Choices — select the correct answer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    listOf("A" to a, "B" to b, "C" to c, "D" to d).forEachIndexed { idx, pair ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = if (type == QuizQuestionTypes.MULTI_SELECT) idx in multiSelected else correctChoice == idx,
                                    onClick = {
                                        if (type == QuizQuestionTypes.MULTI_SELECT) {
                                            val next = multiSelected.toMutableSet().apply { if (!add(idx)) remove(idx) }
                                            multiCorrectCsv = next.sorted().joinToString(",")
                                        } else correctChoice = idx
                                    },
                                    label = { Text(pair.first) }
                                )
                                Spacer(Modifier.size(8.dp))
                                OutlinedTextField(
                                    value = pair.second,
                                    onValueChange = { value -> when (idx) { 0 -> a = value; 1 -> b = value; 2 -> c = value; else -> d = value } },
                                    label = { Text("Choice ${pair.first}") }, modifier = Modifier.weight(1f), singleLine = true
                                )
                            }
                        }
                    }
                } else if (type == QuizQuestionTypes.TRUE_FALSE) {
                    item { SegmentedControl(listOf("True", "False"), trueFalseIndex, { trueFalseIndex = it }, Modifier.fillMaxWidth()) }
                } else if (type in setOf(QuizQuestionTypes.IDENTIFICATION, QuizQuestionTypes.IMAGE_IDENTIFICATION)) {
                    if (type == QuizQuestionTypes.IMAGE_IDENTIFICATION) {
                        item {
                            OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text(if (imageUri == null) "Choose diagram / image" else "Change image")
                            }
                        }
                        if (imageUri != null) item { AsyncImage(model = imageUri, contentDescription = "Question image", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 220.dp).clip(RoundedCornerShape(12.dp))) }
                    }
                    item { OutlinedTextField(typedAnswer, { typedAnswer = it }, label = { Text("Correct answer") }, modifier = Modifier.fillMaxWidth()) }
                    item { Text("Typed checking ignores capitalization and repeated spaces.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else if (type == QuizQuestionTypes.NUMERIC) {
                    item { OutlinedTextField(typedAnswer, { typedAnswer = it }, label = { Text("Correct numeric answer") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(numericTolerance, { numericTolerance = it }, label = { Text("Accepted ± tolerance") }, supportingText = { Text("0 means exact. Example: 0.01 accepts 9.80–9.82 for 9.81.") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                } else if (type == QuizQuestionTypes.ORDERING) {
                    item { OutlinedTextField(orderingText, { orderingText = it }, label = { Text("Correct order — one item per line") }, supportingText = { Text("Punla shuffles these during the quiz; the learner moves them back into order.") }, modifier = Modifier.fillMaxWidth(), minLines = 5) }
                } else if (type == QuizQuestionTypes.MATCHING) {
                    item { OutlinedTextField(matchingText, { matchingText = it }, label = { Text("Pairs — one per line") }, supportingText = { Text("Use: term :: matching answer") }, modifier = Modifier.fillMaxWidth(), minLines = 5) }
                }

                if ((type in setOf(QuizQuestionTypes.MULTIPLE_CHOICE, QuizQuestionTypes.MULTI_SELECT) && !choicesAreUnique) ||
                    (type == QuizQuestionTypes.ORDERING && !orderItemsAreUnique) ||
                    (type == QuizQuestionTypes.MATCHING && (!matchKeysAreUnique || !matchValuesAreUnique))) {
                    item {
                        Text(
                            "Choices/items must be unique (capitalization and extra spaces do not make a duplicate different).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                item { OutlinedTextField(explanation, { explanation = it }, label = { Text("Explanation / worked solution (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                item { OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val now = System.currentTimeMillis()
                val normalizedTags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString(", ")
                val options = when (type) {
                    QuizQuestionTypes.TRUE_FALSE -> listOf("True", "False")
                    QuizQuestionTypes.MULTIPLE_CHOICE, QuizQuestionTypes.MULTI_SELECT -> choices
                    QuizQuestionTypes.ORDERING -> orderItems
                    QuizQuestionTypes.MATCHING -> matchPairs.keys.toList() + matchPairs.values.toList()
                    else -> emptyList()
                }
                val correct = when (type) {
                    QuizQuestionTypes.TRUE_FALSE -> if (trueFalseIndex == 0) "True" else "False"
                    QuizQuestionTypes.IDENTIFICATION, QuizQuestionTypes.IMAGE_IDENTIFICATION, QuizQuestionTypes.NUMERIC -> typedAnswer.trim()
                    QuizQuestionTypes.MULTI_SELECT -> JSONArray(multiSelected.sorted().mapNotNull { rawChoices.getOrNull(it)?.takeIf { choice -> choice.isNotBlank() } }).toString()
                    QuizQuestionTypes.ORDERING -> JSONArray(orderItems).toString()
                    QuizQuestionTypes.MATCHING -> JSONObject(matchPairs as Map<*, *>).toString()
                    else -> rawChoices.getOrElse(correctChoice) { choices.firstOrNull().orEmpty() }
                }
                val metadata = JSONObject().apply {
                    if (type == QuizQuestionTypes.NUMERIC) put("tolerance", numericTolerance.toDoubleOrNull() ?: 0.0)
                    if (type == QuizQuestionTypes.MATCHING) put("rightOptions", JSONArray(matchPairs.values.toList()))
                }.toString()
                onSave(initial?.copy(
                    type = type, prompt = prompt.trim(), optionsJson = QuizQuestion.encodeOptions(options), correctAnswer = correct,
                    explanation = explanation.trim().ifBlank { null }, tags = normalizedTags, metadataJson = metadata,
                    imageUri = if (type == QuizQuestionTypes.IMAGE_IDENTIFICATION) imageUri else null, updatedAt = now
                ) ?: QuizQuestion(
                    quizId = quizId, type = type, prompt = prompt.trim(), optionsJson = QuizQuestion.encodeOptions(options), correctAnswer = correct,
                    explanation = explanation.trim().ifBlank { null }, tags = normalizedTags, metadataJson = metadata,
                    imageUri = if (type == QuizQuestionTypes.IMAGE_IDENTIFICATION) imageUri else null, createdAt = now, updatedAt = now
                ))
            }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FlashcardDeckPickerDialog(decks: List<FlashcardDeck>, cards: List<Flashcard>, onDismiss: () -> Unit, onSelect: (FlashcardDeck, List<Flashcard>) -> Unit) {
    val available = decks.map { it to cards.filter { card -> card.deckId == it.id } }.filter { it.second.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make quiz from flashcards") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Punla will create identification questions from the selected deck. The flashcards stay unchanged.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(available, key = { it.first.id }) { (deck, deckCards) ->
                    Surface(modifier = Modifier.fillMaxWidth().clickable { onSelect(deck, deckCards) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(deck.name, fontWeight = FontWeight.SemiBold); Text("${deckCards.size} cards", style = MaterialTheme.typography.bodySmall) }
                            Icon(Icons.Default.PlayArrow, null)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun quizImportFailureMessage(action: String, error: Throwable): String {
    val detail = error.message?.trim()?.takeIf { it.isNotEmpty() }?.take(240)
    return buildString {
        append("Punla couldn't $action. No partial quiz data was kept.")
        if (detail != null) append("\n\nDetails: ").append(detail)
    }
}

@Composable
private fun QuizJsonPreviewDialog(
    imported: QuizJsonPayload,
    alreadyImported: Boolean,
    importing: Boolean,
    onDismiss: () -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import ${imported.questions.size}-question quiz?") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(imported.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                item { Text("Punla file ID: ${QuizJsonImport.FILE_ID}\nContent ID: ${imported.contentId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (alreadyImported) item { QuizWarning("This exact quiz content ID was imported before. Continue only if you intentionally want another copy.") }
                if (imported.warnings.isNotEmpty()) item { QuizWarning(imported.warnings.joinToString("\n")) }
                item { HorizontalDivider() }
                items(imported.questions.take(3)) { q ->
                    Column { Text(q.prompt, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("Answer: ${q.correctAnswer}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                }
                if (imported.questions.size > 3) item { Text("+${imported.questions.size - 3} more questions", style = MaterialTheme.typography.labelMedium) }
                item { Text("Attempt history and scores are never accepted from imported quiz JSON.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = {
            TextButton(onClick = onImport, enabled = !importing) {
                Text(if (importing) "Importing…" else if (alreadyImported) "Import again" else "Import")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !importing) { Text("Cancel") } }
    )
}

@Composable
private fun QuizWarning(message: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
    }
}

private fun questionTypeLabel(type: String): String = when (type) {
    QuizQuestionTypes.TRUE_FALSE -> "True / False"
    QuizQuestionTypes.IDENTIFICATION -> "Identification"
    QuizQuestionTypes.MULTI_SELECT -> "Multi-select"
    QuizQuestionTypes.NUMERIC -> "Numeric"
    QuizQuestionTypes.ORDERING -> "Ordering"
    QuizQuestionTypes.MATCHING -> "Matching"
    QuizQuestionTypes.IMAGE_IDENTIFICATION -> "Image ID"
    else -> "Multiple choice"
}

private fun decodeJsonStringList(raw: String): List<String> = runCatching {
    val arr = JSONArray(raw)
    List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
}.getOrElse { raw.split('|').map { it.trim() }.filter { it.isNotEmpty() } }

private fun decodeMatchingPairs(raw: String): Map<String, String> = runCatching {
    val obj = JSONObject(raw)
    obj.keys().asSequence().associateWith { obj.optString(it) }
}.getOrElse { emptyMap() }

private fun parseMatchingEditor(raw: String): LinkedHashMap<String, String> {
    val out = linkedMapOf<String, String>()
    raw.lines().forEach { line ->
        val parts = line.split("::", limit = 2).map { it.trim() }
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) out[parts[0]] = parts[1]
    }
    return out
}

private fun answerReady(question: QuizQuestion, answer: String): Boolean = when (question.type) {
    QuizQuestionTypes.MULTI_SELECT, QuizQuestionTypes.ORDERING -> decodeJsonStringList(answer).isNotEmpty()
    QuizQuestionTypes.MATCHING -> {
        val expected = decodeMatchingPairs(question.correctAnswer)
        val actual = decodeMatchingPairs(answer)
        expected.isNotEmpty() && expected.keys.all { !actual[it].isNullOrBlank() }
    }
    else -> answer.trim().isNotEmpty()
}

private fun displayCorrectAnswer(question: QuizQuestion): String = when (question.type) {
    QuizQuestionTypes.MULTI_SELECT, QuizQuestionTypes.ORDERING -> decodeJsonStringList(question.correctAnswer).joinToString(" · ")
    QuizQuestionTypes.MATCHING -> decodeMatchingPairs(question.correctAnswer).entries.joinToString(" · ") { "${it.key} → ${it.value}" }
    else -> question.correctAnswer
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = seconds / 60
    val remaining = seconds % 60
    return if (minutes > 0) "${minutes}m ${remaining}s" else "${remaining}s"
}

private fun safeQuizFileName(title: String): String {
    val base = title.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "punla-quiz" }.take(50)
    return "$base-quiz.json"
}

private val JSON_MIME_TYPES = arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
