package com.uplb.punla.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Help
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uplb.punla.data.QuizJsonExport
import com.uplb.punla.data.QuizJsonImport
import com.uplb.punla.data.QuizJsonPayload
import com.uplb.punla.data.PunlaJsonImportReader
import com.uplb.punla.data.entity.Flashcard
import com.uplb.punla.data.entity.FlashcardDeck
import com.uplb.punla.data.entity.Quiz
import com.uplb.punla.data.entity.QuizAttempt
import com.uplb.punla.data.entity.QuizQuestion
import com.uplb.punla.data.entity.QuizQuestionTypes
import com.uplb.punla.ui.PunlaViewModel
import org.json.JSONArray
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class QuizRunRequest(val questions: List<QuizQuestion>, val label: String, val id: String = UUID.randomUUID().toString())

@Composable
fun QuizScreen(vm: PunlaViewModel) {
    val quizzes by vm.quizzes.collectAsState()
    val allQuestions by vm.quizQuestions.collectAsState()
    val allAttempts by vm.quizAttempts.collectAsState()
    val decks by vm.flashcardDecks.collectAsState()
    val flashcards by vm.flashcards.collectAsState()
    var selectedQuizId by rememberSaveable { mutableStateOf<String?>(null) }
    var runRequest by remember { mutableStateOf<QuizRunRequest?>(null) }

    val selectedQuiz = quizzes.firstOrNull { it.id == selectedQuizId }
    val selectedQuestions = selectedQuiz?.let { quiz -> allQuestions.filter { it.quizId == quiz.id } }.orEmpty()
    val selectedAttempts = selectedQuiz?.let { quiz -> allAttempts.filter { it.quizId == quiz.id }.sortedByDescending { it.completedAt } }.orEmpty()

    BackHandler(enabled = runRequest != null || selectedQuizId != null) {
        if (runRequest != null) runRequest = null else selectedQuizId = null
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
                vm = vm,
                onExit = { runRequest = null },
                onRetryMistakes = { missed -> runRequest = QuizRunRequest(missed, "Retry mistakes") }
            )
            "detail" -> QuizDetailView(
                quiz = selectedQuiz!!,
                questions = selectedQuestions,
                attempts = selectedAttempts,
                vm = vm,
                onBack = { selectedQuizId = null },
                onStart = { questions, label -> runRequest = QuizRunRequest(questions, label) }
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
    onStart: (List<QuizQuestion>, String) -> Unit
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
                    Button(onClick = { onStart(questions, "Full quiz") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.size(6.dp)); Text("Start ${questions.size}-question quiz")
                    }
                }
                if (latestMissed.isNotEmpty()) {
                    item {
                        OutlinedButton(onClick = { onStart(latestMissed, "Retry mistakes") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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
    vm: PunlaViewModel,
    onExit: () -> Unit,
    onRetryMistakes: (List<QuizQuestion>) -> Unit
) {
    val queue = remember(quiz.id, request.id, request.questions.map { it.id }) {
        if (quiz.shuffleQuestions) request.questions.shuffled() else request.questions
    }
    val startedAt = remember(quiz.id, request.id, queue.map { it.id }) { System.currentTimeMillis() }
    var index by rememberSaveable(quiz.id, request.id) { mutableIntStateOf(0) }
    var answer by rememberSaveable(quiz.id, request.id) { mutableStateOf("") }
    var submitted by rememberSaveable(quiz.id, request.id) { mutableStateOf(false) }
    var score by rememberSaveable(quiz.id, request.id) { mutableIntStateOf(0) }
    var missedIds by remember(quiz.id, request.id) { mutableStateOf(listOf<String>()) }
    var attemptSaved by rememberSaveable(quiz.id, request.id) { mutableStateOf(false) }
    var mistakesSavedAsCards by rememberSaveable(quiz.id, request.id) { mutableStateOf(false) }
    val finished = queue.isEmpty() || index >= queue.size
    val horizontalPadding = punlaScreenHorizontalPadding(720.dp)

    if (finished && queue.isNotEmpty() && !attemptSaved) {
        LaunchedEffect(queue, score, missedIds) {
            val completedAt = System.currentTimeMillis()
            vm.recordQuizAttempt(
                QuizAttempt(
                    quizId = quiz.id,
                    startedAt = startedAt,
                    completedAt = completedAt,
                    score = score,
                    total = queue.size,
                    durationMs = completedAt - startedAt,
                    incorrectQuestionIdsJson = JSONArray(missedIds).toString()
                )
            )
            attemptSaved = true
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
                    Text(if (finished) request.label else "${request.label} · Question ${index + 1} of ${queue.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!finished) {
                LinearProgressIndicator(progress = { (index + 1).toFloat() / queue.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(18.dp))
                val question = queue[index]
                val options = remember(question.id) {
                    val base = when (question.type) {
                        QuizQuestionTypes.TRUE_FALSE -> listOf("True", "False")
                        QuizQuestionTypes.MULTIPLE_CHOICE -> question.options()
                        else -> emptyList()
                    }
                    if (quiz.shuffleChoices && question.type == QuizQuestionTypes.MULTIPLE_CHOICE) base.shuffled() else base
                }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(20.dp)) {
                        Tag(questionTypeLabel(question.type), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.size(12.dp))
                        Text(question.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(18.dp))
                        if (question.type == QuizQuestionTypes.IDENTIFICATION) {
                            OutlinedTextField(value = answer, onValueChange = { if (!submitted) answer = it }, label = { Text("Your answer") }, modifier = Modifier.fillMaxWidth(), enabled = !submitted, minLines = 1)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                options.forEach { option ->
                                    FilterChip(
                                        selected = answer == option,
                                        onClick = { if (!submitted) answer = option },
                                        label = { Text(option) },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !submitted
                                    )
                                }
                            }
                        }
                        if (submitted) {
                            Spacer(Modifier.size(16.dp))
                            val correct = question.isCorrect(answer)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (correct) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = if (correct) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(if (correct) "Correct" else "Answer: ${question.correctAnswer}", fontWeight = FontWeight.SemiBold)
                                    question.explanation?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.size(14.dp))
                if (!submitted) {
                    Button(
                        onClick = {
                            submitted = true
                            if (question.isCorrect(answer)) score++ else missedIds = missedIds + question.id
                        },
                        enabled = answer.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("Check answer") }
                } else {
                    Button(
                        onClick = { index++; answer = ""; submitted = false },
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
                        Text(if (passed) "Quiz complete" else "Review and try again", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("$score/${queue.size} · $percent%", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
                        Text("Passing score: ${quiz.passingScore}%", style = MaterialTheme.typography.bodySmall)
                        val missed = queue.filter { it.id in missedIds }
                        if (missed.isNotEmpty()) {
                            Spacer(Modifier.size(16.dp))
                            OutlinedButton(onClick = { onRetryMistakes(missed) }, modifier = Modifier.fillMaxWidth()) { Text("Retry ${missed.size} mistakes") }
                            Spacer(Modifier.size(8.dp))
                            OutlinedButton(
                                onClick = {
                                    vm.createFlashcardsFromQuizMistakes(quiz, missed)
                                    mistakesSavedAsCards = true
                                },
                                enabled = !mistakesSavedAsCards,
                                modifier = Modifier.fillMaxWidth()
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
                onSave(initial?.copy(title = title.trim(), courseCode = course.trim().ifBlank { null }, description = description.trim().ifBlank { null }, passingScore = passingInt ?: 70, shuffleQuestions = shuffleQuestions, shuffleChoices = shuffleChoices, updatedAt = now)
                    ?: Quiz(title = title.trim(), courseCode = course.trim().ifBlank { null }, description = description.trim().ifBlank { null }, passingScore = passingInt ?: 70, shuffleQuestions = shuffleQuestions, shuffleChoices = shuffleChoices, createdAt = now, updatedAt = now))
            }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun QuestionEditorDialog(quizId: String, initial: QuizQuestion?, onDismiss: () -> Unit, onSave: (QuizQuestion) -> Unit) {
    val initialType = when (initial?.type) {
        QuizQuestionTypes.TRUE_FALSE -> 1
        QuizQuestionTypes.IDENTIFICATION -> 2
        else -> 0
    }
    var typeIndex by rememberSaveable(initial?.id) { mutableIntStateOf(initialType) }
    var prompt by rememberSaveable(initial?.id) { mutableStateOf(initial?.prompt.orEmpty()) }
    val initialOptions = initial?.options().orEmpty()
    var a by rememberSaveable(initial?.id) { mutableStateOf(initialOptions.getOrNull(0).orEmpty()) }
    var b by rememberSaveable(initial?.id) { mutableStateOf(initialOptions.getOrNull(1).orEmpty()) }
    var c by rememberSaveable(initial?.id) { mutableStateOf(initialOptions.getOrNull(2).orEmpty()) }
    var d by rememberSaveable(initial?.id) { mutableStateOf(initialOptions.getOrNull(3).orEmpty()) }
    var correctChoice by rememberSaveable(initial?.id) {
        mutableIntStateOf(initialOptions.indexOfFirst { QuizQuestion.normalizeAnswer(it) == QuizQuestion.normalizeAnswer(initial?.correctAnswer.orEmpty()) }.coerceAtLeast(0))
    }
    var trueFalseIndex by rememberSaveable(initial?.id) { mutableIntStateOf(if (initial?.correctAnswer.equals("False", true)) 1 else 0) }
    var identificationAnswer by rememberSaveable(initial?.id) { mutableStateOf(if (initial?.type == QuizQuestionTypes.IDENTIFICATION) initial?.correctAnswer.orEmpty() else "") }
    var explanation by rememberSaveable(initial?.id) { mutableStateOf(initial?.explanation.orEmpty()) }
    var tags by rememberSaveable(initial?.id) { mutableStateOf(initial?.tags.orEmpty()) }
    val type = when (typeIndex) { 1 -> QuizQuestionTypes.TRUE_FALSE; 2 -> QuizQuestionTypes.IDENTIFICATION; else -> QuizQuestionTypes.MULTIPLE_CHOICE }
    val rawChoices = listOf(a, b, c, d).map { it.trim() }
    val choices = rawChoices.filter { it.isNotEmpty() }
    val valid = prompt.trim().isNotEmpty() && when (type) {
        QuizQuestionTypes.MULTIPLE_CHOICE -> choices.size >= 2 && rawChoices.getOrNull(correctChoice).orEmpty().isNotBlank()
        QuizQuestionTypes.IDENTIFICATION -> identificationAnswer.trim().isNotEmpty()
        else -> true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add question" else "Edit question") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SegmentedControl(listOf("Multiple choice", "True / False", "Identification"), typeIndex, { typeIndex = it }, Modifier.fillMaxWidth()) }
                item { OutlinedTextField(prompt, { prompt = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                if (type == QuizQuestionTypes.MULTIPLE_CHOICE) {
                    item { Text("Choices — tap the chip beside the correct one", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    listOf("A" to a, "B" to b, "C" to c, "D" to d).forEachIndexed { idx, pair ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(selected = correctChoice == idx, onClick = { correctChoice = idx }, label = { Text(pair.first) })
                                Spacer(Modifier.size(8.dp))
                                OutlinedTextField(
                                    value = pair.second,
                                    onValueChange = { value -> when (idx) { 0 -> a = value; 1 -> b = value; 2 -> c = value; else -> d = value } },
                                    label = { Text("Choice ${pair.first}") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                        }
                    }
                } else if (type == QuizQuestionTypes.TRUE_FALSE) {
                    item { SegmentedControl(listOf("True", "False"), trueFalseIndex, { trueFalseIndex = it }, Modifier.fillMaxWidth()) }
                } else {
                    item { OutlinedTextField(identificationAnswer, { identificationAnswer = it }, label = { Text("Correct answer") }, modifier = Modifier.fillMaxWidth(), minLines = 1) }
                    item { Text("Identification checking ignores capitalization and repeated spaces, but otherwise expects the same answer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                item { OutlinedTextField(explanation, { explanation = it }, label = { Text("Explanation / feedback (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                item { OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val now = System.currentTimeMillis()
                val normalizedTags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString(", ")
                val options = when (type) { QuizQuestionTypes.TRUE_FALSE -> listOf("True", "False"); QuizQuestionTypes.MULTIPLE_CHOICE -> choices; else -> emptyList() }
                val correct = when (type) {
                    QuizQuestionTypes.TRUE_FALSE -> if (trueFalseIndex == 0) "True" else "False"
                    QuizQuestionTypes.IDENTIFICATION -> identificationAnswer.trim()
                    else -> rawChoices.getOrElse(correctChoice) { choices.firstOrNull().orEmpty() }
                }
                onSave(initial?.copy(type = type, prompt = prompt.trim(), optionsJson = QuizQuestion.encodeOptions(options), correctAnswer = correct, explanation = explanation.trim().ifBlank { null }, tags = normalizedTags, updatedAt = now)
                    ?: QuizQuestion(quizId = quizId, type = type, prompt = prompt.trim(), optionsJson = QuizQuestion.encodeOptions(options), correctAnswer = correct, explanation = explanation.trim().ifBlank { null }, tags = normalizedTags, createdAt = now, updatedAt = now))
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
    else -> "Multiple choice"
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
