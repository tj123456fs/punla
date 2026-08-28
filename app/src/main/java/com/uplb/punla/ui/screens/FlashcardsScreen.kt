package com.uplb.punla.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.uplb.punla.data.FlashcardJsonDeck
import com.uplb.punla.data.FlashcardJsonExport
import com.uplb.punla.data.FlashcardJsonImport
import com.uplb.punla.data.PunlaJsonImportReader
import com.uplb.punla.data.entity.ClozeText
import com.uplb.punla.data.entity.Flashcard
import com.uplb.punla.data.entity.FlashcardDeck
import com.uplb.punla.data.entity.FlashcardRating
import com.uplb.punla.data.entity.FlashcardTypes
import com.uplb.punla.data.entity.StudyTopic
import com.uplb.punla.ui.PunlaViewModel
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FlashcardsScreen(vm: PunlaViewModel, initialCourse: String? = null, initialTopicId: String? = null, overallOnly: Boolean = false) {
    val decks by vm.flashcardDecks.collectAsState()
    val allCards by vm.flashcards.collectAsState()
    val studyTopics by vm.studyTopics.collectAsState()
    val scopedTopicIds = remember(studyTopics, initialTopicId) {
        if (initialTopicId == null) emptySet<String>() else {
            val ids = linkedSetOf(initialTopicId)
            var changed = true
            while (changed) {
                changed = false
                studyTopics.forEach { topic -> if (topic.parentTopicId?.let { it in ids } == true && ids.add(topic.id)) changed = true }
            }
            ids
        }
    }
    val scopedDecks = remember(decks, initialCourse, initialTopicId, overallOnly, scopedTopicIds) {
        decks.filter { deck ->
            (initialCourse.isNullOrBlank() || deck.courseCode.equals(initialCourse, true)) &&
                when {
                    overallOnly -> deck.topicId == null
                    initialTopicId != null -> deck.topicId?.let { it in scopedTopicIds } == true
                    else -> true
                }
        }
    }
    val scopedDeckIds = remember(scopedDecks) { scopedDecks.mapTo(hashSetOf()) { it.id } }
    val scopedCards = remember(allCards, scopedDeckIds) { allCards.filter { it.deckId in scopedDeckIds } }
    var selectedDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialScopeApplied by rememberSaveable(initialCourse, initialTopicId, overallOnly) { mutableStateOf(false) }
    var studyCardIds by rememberSaveable { mutableStateOf<String?>(null) }
    var studyRunId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(scopedDecks, initialScopeApplied) {
        if (!initialScopeApplied && (!initialCourse.isNullOrBlank() || initialTopicId != null || overallOnly)) {
            if (scopedDecks.size == 1) selectedDeckId = scopedDecks.first().id
            initialScopeApplied = true
        }
    }

    val selectedDeck = decks.firstOrNull { it.id == selectedDeckId }
    val selectedCards = selectedDeck?.let { deck -> allCards.filter { it.deckId == deck.id } }.orEmpty()
    val studyRequest = studyCardIds?.let { encoded ->
        val byId = selectedCards.associateBy { it.id }
        encoded.split(',').filter { it.isNotBlank() }.mapNotNull(byId::get)
    }
    fun startStudy(cards: List<Flashcard>) {
        studyCardIds = cards.joinToString(",") { it.id }
        studyRunId = UUID.randomUUID().toString()
    }
    fun clearStudy() {
        studyCardIds = null
        studyRunId = null
    }

    BackHandler(enabled = studyCardIds != null || selectedDeckId != null) {
        if (studyCardIds != null) clearStudy() else selectedDeckId = null
    }

    Crossfade(
        targetState = when {
            studyCardIds != null && selectedDeck != null -> "study"
            selectedDeck != null -> "deck"
            else -> "library"
        },
        animationSpec = tween(180),
        label = "flashcardScreenMode"
    ) { mode ->
        when (mode) {
            "study" -> {
                // Crossfade keeps outgoing content composed briefly after navigation
                // state is cleared. Avoid force-unwrapping selectedDeck during that
                // transition or Back to deck/library can crash on recomposition.
                val activeDeck = selectedDeck
                if (activeDeck != null) {
                    StudyDeckView(
                        deck = activeDeck,
                        startingCards = studyRequest.orEmpty(),
                        runId = studyRunId ?: "restored-study",
                        onRate = vm::rateFlashcard,
                        onExit = { clearStudy() }
                    )
                }
            }
            "deck" -> {
                val activeDeck = selectedDeck
                if (activeDeck != null) {
                    DeckDetailView(
                        deck = activeDeck,
                        cards = selectedCards,
                        vm = vm,
                        topics = studyTopics,
                        onBack = { selectedDeckId = null },
                        onStudy = { startStudy(it) }
                    )
                }
            }
            else -> FlashcardLibraryView(
                decks = scopedDecks,
                cards = scopedCards,
                vm = vm,
                topics = studyTopics,
                scopeCourse = initialCourse,
                scopeTopicId = initialTopicId,
                overallOnly = overallOnly,
                onOpenDeck = { selectedDeckId = it.id }
            )
        }
    }
}

@Composable
private fun FlashcardLibraryView(
    decks: List<FlashcardDeck>,
    cards: List<Flashcard>,
    vm: PunlaViewModel,
    topics: List<StudyTopic>,
    scopeCourse: String? = null,
    scopeTopicId: String? = null,
    overallOnly: Boolean = false,
    onOpenDeck: (FlashcardDeck) -> Unit
) {
    var showDeckDialog by rememberSaveable { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<FlashcardJsonDeck?>(null) }
    var duplicateImport by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importInProgress by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val importScope = rememberCoroutineScope()
    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importInProgress) {
            importScope.launch {
                importInProgress = true
                try {
                    val imported = withContext(Dispatchers.IO) {
                        FlashcardJsonImport.parse(
                            PunlaJsonImportReader.readText(context, uri, FlashcardJsonImport.MAX_FILE_CHARS)
                        )
                    }
                    vm.checkJsonImport(FlashcardJsonImport.FILE_ID, imported.contentId)
                        .onSuccess { already ->
                            duplicateImport = already
                            pendingImport = imported
                        }
                        .onFailure { error ->
                            importError = importFailureMessage("check this file", error)
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    importError = error.message ?: "Punla couldn't import that JSON file."
                } finally {
                    importInProgress = false
                }
            }
        }
    }
    val now = System.currentTimeMillis()
    val horizontalPadding = punlaScreenHorizontalPadding()

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showDeckDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New deck") }
            )
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
                FlashcardHero(
                    title = "Flashcards",
                    subtitle = "Cloze, reverse cards, tags, starred cards, smart study, and safe Punla JSON import/export."
                )
            }
            item {
                OutlinedButton(
                    onClick = { jsonPicker.launch(JSON_MIME_TYPES) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Import Punla flashcard JSON")
                }
            }
            if (decks.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Style,
                        message = "No flashcard decks yet. Start with one subject, lecture, or exam topic.",
                        actionLabel = "Create your first deck",
                        onAction = { showDeckDialog = true }
                    )
                }
            } else {
                item { SectionLabel(text = "Your decks", icon = Icons.Default.Style) }
                items(decks, key = { it.id }) { deck ->
                    val deckCards = cards.filter { it.deckId == deck.id }
                    DeckCard(
                        deck = deck,
                        cardCount = deckCards.size,
                        dueCount = deckCards.count { it.isDue(now) },
                        masteredCount = deckCards.count { it.mastery >= 4 },
                        starredCount = deckCards.count { it.starred },
                        onClick = { onOpenDeck(deck) }
                    )
                }
            }
        }
    }

    if (showDeckDialog) {
        DeckEditorDialog(
            initial = null,
            topics = topics,
            defaultCourse = scopeCourse,
            defaultTopicId = if (overallOnly) null else scopeTopicId,
            onDismiss = { showDeckDialog = false },
            onSave = {
                vm.upsertFlashcardDeck(it)
                showDeckDialog = false
            }
        )
    }
    pendingImport?.let { imported ->
        FlashcardJsonImportPreviewDialog(
            imported = imported,
            destinationDeckName = null,
            alreadyImported = duplicateImport,
            exactDuplicateCards = 0,
            importing = importInProgress,
            onDismiss = { if (!importInProgress) pendingImport = null },
            onImport = {
                if (!importInProgress) {
                    val timestamp = System.currentTimeMillis()
                    val deck = FlashcardDeck(
                        name = imported.name,
                        courseCode = scopeCourse ?: imported.courseCode,
                        topicId = if (overallOnly) null else scopeTopicId,
                        description = imported.description,
                        createdAt = timestamp,
                        updatedAt = timestamp
                    )
                    val importedCards = imported.cards.map { it.toEntity(deck.id, timestamp) }
                    importScope.launch {
                        importInProgress = true
                        val result = vm.importFlashcardDeck(deck, importedCards, imported.contentId)
                        importInProgress = false
                        result.onSuccess {
                            pendingImport = null
                            onOpenDeck(deck)
                        }.onFailure { error ->
                            importError = importFailureMessage("save the imported flashcards", error)
                        }
                    }
                }
            }
        )
    }
    importError?.let { FlashcardJsonErrorDialog(it) { importError = null } }
}

@Composable
private fun FlashcardHero(title: String, subtitle: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp).size(22.dp)
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DeckCard(
    deck: FlashcardDeck,
    cardCount: Int,
    dueCount: Int,
    masteredCount: Int,
    starredCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Style, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(deck.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(deck.courseCode?.takeIf { it.isNotBlank() }, "$cardCount cards").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (dueCount > 0) AssistChip(onClick = onClick, label = { Text("$dueCount due") })
            }
            deck.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (cardCount > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { masteredCount.toFloat() / cardCount.toFloat() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(5.dp))
                Text(
                    "$masteredCount learned · $starredCount starred",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeckDetailView(
    deck: FlashcardDeck,
    cards: List<Flashcard>,
    vm: PunlaViewModel,
    topics: List<StudyTopic>,
    onBack: () -> Unit,
    onStudy: (List<Flashcard>) -> Unit
) {
    var query by rememberSaveable(deck.id) { mutableStateOf("") }
    var showCardDialog by rememberSaveable(deck.id) { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }
    var showBulkDialog by rememberSaveable(deck.id) { mutableStateOf(false) }
    var showDeckDialog by rememberSaveable(deck.id) { mutableStateOf(false) }
    var confirmDeleteDeck by rememberSaveable(deck.id) { mutableStateOf(false) }
    var deleteCard by remember { mutableStateOf<Flashcard?>(null) }
    var pendingImport by remember { mutableStateOf<FlashcardJsonDeck?>(null) }
    var duplicateImport by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var importInProgress by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val importScope = rememberCoroutineScope()

    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importInProgress) {
            importScope.launch {
                importInProgress = true
                try {
                    val imported = withContext(Dispatchers.IO) {
                        FlashcardJsonImport.parse(
                            PunlaJsonImportReader.readText(context, uri, FlashcardJsonImport.MAX_FILE_CHARS)
                        )
                    }
                    vm.checkJsonImport(FlashcardJsonImport.FILE_ID, imported.contentId)
                        .onSuccess { already ->
                            duplicateImport = already
                            pendingImport = imported
                        }
                        .onFailure { error ->
                            importError = importFailureMessage("check this file", error)
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    importError = error.message ?: "Punla couldn't import that JSON file."
                } finally {
                    importInProgress = false
                }
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val payload = FlashcardJsonExport.build(deck, cards)
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    ?: throw IllegalStateException("Punla couldn't write that file.")
            }.onSuccess { exportMessage = "Flashcard JSON exported. You can send this file back to ChatGPT or import it into Punla on another device." }
                .onFailure { exportMessage = it.message ?: "Couldn't export this deck." }
        }
    }

    val now = System.currentTimeMillis()
    val dueCards = cards.filter { it.isDue(now) }
    val weakCards = cards.filter { it.isWeak() }
    val newCards = cards.filter { it.isNew() }
    val starredCards = cards.filter { it.starred }
    val visibleCards = cards.filter {
        query.isBlank() || it.front.contains(query, true) || it.back.contains(query, true) || it.tags.contains(query, true)
    }
    val exactImportDuplicates = pendingImport?.cards?.count { incoming ->
        cards.any { existing -> existing.front.trim().equals(incoming.front.trim(), true) && existing.back.trim().equals(incoming.back.trim(), true) }
    } ?: 0
    val horizontalPadding = punlaScreenHorizontalPadding()

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingCard = null; showCardDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Card") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to decks") }
                    Column(Modifier.weight(1f)) {
                        Text(deck.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(deck.courseCode?.takeIf { it.isNotBlank() }, "${cards.size} cards").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { exportLauncher.launch(safeJsonFileName(deck.name, "flashcards")) }) { Icon(Icons.Default.Save, contentDescription = "Export deck JSON") }
                    IconButton(onClick = { showDeckDialog = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit deck") }
                    IconButton(onClick = { confirmDeleteDeck = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete deck") }
                }
            }

            if (cards.isNotEmpty()) {
                item { SectionLabel(text = "Smart study", icon = Icons.Default.AutoAwesome) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudyModeButton("Due", dueCards.size, dueCards.isNotEmpty(), Modifier.weight(1f)) { onStudy(dueCards) }
                            StudyModeButton("Weak", weakCards.size, weakCards.isNotEmpty(), Modifier.weight(1f)) { onStudy(weakCards) }
                            StudyModeButton("New", newCards.size, newCards.isNotEmpty(), Modifier.weight(1f)) { onStudy(newCards) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudyModeButton("Starred", starredCards.size, starredCards.isNotEmpty(), Modifier.weight(1f)) { onStudy(starredCards) }
                            Button(onClick = { onStudy(cards) }, modifier = Modifier.weight(2f).heightIn(min = 48.dp)) { Text("Study all ${cards.size}") }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showBulkDialog = true }, modifier = Modifier.weight(1f)) { Text("Bulk add") }
                    OutlinedButton(onClick = { jsonPicker.launch(JSON_MIME_TYPES) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Import JSON")
                    }
                }
            }

            if (cards.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Search cards or tags") }
                    )
                }
            }

            if (cards.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.MenuBook,
                        message = "This deck is empty. Add a card manually, paste several at once, or import a Punla JSON deck.",
                        actionLabel = "Add first card",
                        onAction = { editingCard = null; showCardDialog = true }
                    )
                }
            } else if (visibleCards.isEmpty()) {
                item { EmptyState(icon = Icons.Default.Search, message = "No cards match “$query”.") }
            } else {
                item { SectionLabel(text = if (query.isBlank()) "Cards" else "Search results", icon = Icons.Default.MenuBook) }
                items(visibleCards, key = { it.id }) { card ->
                    FlashcardListItem(
                        card = card,
                        onToggleStar = { vm.toggleFlashcardStar(card) },
                        onEdit = { editingCard = card; showCardDialog = true },
                        onDelete = { deleteCard = card }
                    )
                }
            }
        }
    }

    if (showCardDialog) {
        CardEditorDialog(
            deckId = deck.id,
            initial = editingCard,
            onDismiss = { showCardDialog = false; editingCard = null },
            onSave = {
                vm.upsertFlashcard(it)
                showCardDialog = false
                editingCard = null
            }
        )
    }
    if (showBulkDialog) {
        BulkAddDialog(
            deckId = deck.id,
            onDismiss = { showBulkDialog = false },
            onSave = {
                vm.addFlashcards(it)
                showBulkDialog = false
            }
        )
    }
    if (showDeckDialog) {
        DeckEditorDialog(
            initial = deck,
            topics = topics,
            onDismiss = { showDeckDialog = false },
            onSave = {
                vm.upsertFlashcardDeck(it)
                showDeckDialog = false
            }
        )
    }
    if (confirmDeleteDeck) {
        AlertDialog(
            onDismissRequest = { confirmDeleteDeck = false },
            title = { Text("Delete ${deck.name}?") },
            text = { Text("This deletes the deck and all ${cards.size} cards. Quiz copies made earlier are not affected.") },
            confirmButton = { TextButton(onClick = { vm.deleteFlashcardDeck(deck); confirmDeleteDeck = false; onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDeleteDeck = false }) { Text("Cancel") } }
        )
    }
    deleteCard?.let { card ->
        AlertDialog(
            onDismissRequest = { deleteCard = null },
            title = { Text("Delete flashcard?") },
            text = { Text(card.front, maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton(onClick = { vm.deleteFlashcard(card); deleteCard = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteCard = null }) { Text("Cancel") } }
        )
    }
    pendingImport?.let { imported ->
        FlashcardJsonImportPreviewDialog(
            imported = imported,
            destinationDeckName = deck.name,
            alreadyImported = duplicateImport,
            exactDuplicateCards = exactImportDuplicates,
            importing = importInProgress,
            onDismiss = { if (!importInProgress) pendingImport = null },
            onImport = {
                if (!importInProgress) {
                    val timestamp = System.currentTimeMillis()
                    val freshCards = imported.cards.filterNot { incoming ->
                        cards.any { existing -> existing.front.trim().equals(incoming.front.trim(), true) && existing.back.trim().equals(incoming.back.trim(), true) }
                    }.map { it.toEntity(deck.id, timestamp) }
                    importScope.launch {
                        importInProgress = true
                        val result = vm.importFlashcardsIntoDeck(deck, freshCards, imported.contentId)
                        importInProgress = false
                        result.onSuccess {
                            pendingImport = null
                            exportMessage = if (freshCards.isEmpty()) "Nothing new was imported; all cards were exact duplicates." else "Imported ${freshCards.size} new cards${if (exactImportDuplicates > 0) " and skipped $exactImportDuplicates duplicates" else ""}."
                        }.onFailure { error ->
                            importError = importFailureMessage("save the imported flashcards", error)
                        }
                    }
                }
            }
        )
    }
    importError?.let { FlashcardJsonErrorDialog(it) { importError = null } }
    exportMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text("Flashcards") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { exportMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun StudyModeButton(label: String, count: Int, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) {
        Text("$label $count", maxLines = 1)
    }
}

@Composable
private fun FlashcardListItem(card: Flashcard, onToggleStar: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (card.cardType == FlashcardTypes.CLOZE) {
                            Tag("Cloze", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.size(6.dp))
                        }
                        if (card.reverseEnabled) Tag("Reverse", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    if (card.cardType == FlashcardTypes.CLOZE || card.reverseEnabled) Spacer(Modifier.height(7.dp))
                    Text(card.front, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(card.back, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onToggleStar) {
                    Icon(if (card.starred) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = if (card.starred) "Unstar card" else "Star card", tint = if (card.starred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit card") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete card") }
            }
            if (card.tags.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(card.tagList().joinToString(" · ") { "#$it" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    card.isNew() -> "New"
                    card.isWeak() -> "Weak · ${card.reviewCount} reviews"
                    else -> "Mastery ${card.mastery}/5 · ${card.reviewCount} reviews"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StudyDeckView(
    deck: FlashcardDeck,
    startingCards: List<Flashcard>,
    runId: String,
    onRate: (Flashcard, FlashcardRating) -> Unit,
    onExit: () -> Unit
) {
    val queue = remember(deck.id, runId, startingCards.map { it.id }) {
        startingCards.shuffled(kotlin.random.Random("$runId|${deck.id}".hashCode()))
    }
    var index by rememberSaveable(deck.id, runId) { mutableIntStateOf(0) }
    var revealed by rememberSaveable(deck.id, runId) { mutableStateOf(false) }
    var goodCount by rememberSaveable(deck.id, runId) { mutableIntStateOf(0) }
    var hardCount by rememberSaveable(deck.id, runId) { mutableIntStateOf(0) }
    var againCount by rememberSaveable(deck.id, runId) { mutableIntStateOf(0) }
    val horizontalPadding = punlaScreenHorizontalPadding(680.dp)
    val finished = queue.isEmpty() || index >= queue.size

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = horizontalPadding, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit study") }
                Column(Modifier.weight(1f)) {
                    Text(deck.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (!finished) Text("Card ${index + 1} of ${queue.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!finished) {
                LinearProgressIndicator(progress = { (index + 1).toFloat() / queue.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(22.dp))
                val card = queue[index]
                val isCloze = card.cardType == FlashcardTypes.CLOZE && ClozeText.hasCloze(card.front)
                val reversed = !isCloze && card.reverseEnabled && card.reviewCount % 2 == 1
                val questionText = when {
                    isCloze -> ClozeText.question(card.front)
                    reversed -> card.back
                    else -> card.front
                }
                val answerText = when {
                    isCloze -> listOf(ClozeText.revealed(card.front), card.back).filter { it.isNotBlank() }.joinToString("\n\n")
                    reversed -> card.front
                    else -> card.back
                }
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 310.dp).clickable { revealed = !revealed },
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 310.dp).padding(26.dp), contentAlignment = Alignment.Center) {
                        AnimatedContent(targetState = revealed, label = "flashcardReveal") { showBack ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (!card.imageUri.isNullOrBlank()) {
                                    FlashcardStudyImage(card = card, revealed = showBack)
                                    Spacer(Modifier.height(14.dp))
                                }
                                Text(if (showBack) "ANSWER" else if (isCloze) "FILL THE BLANK" else if (reversed) "REVERSE" else "QUESTION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(18.dp))
                                Text(if (showBack) answerText else questionText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                if (!showBack) card.hint?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(18.dp))
                                    Text("Hint: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (!revealed) {
                    Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Reveal answer") }
                    Spacer(Modifier.height(7.dp))
                    Text("Tap the card to flip it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("How well did you know it?", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onRate(card, FlashcardRating.AGAIN); againCount++; index++; revealed = false },
                            modifier = Modifier.weight(1f)
                        ) { Text("Again") }
                        FilledTonalButton(
                            onClick = { onRate(card, FlashcardRating.HARD); hardCount++; index++; revealed = false },
                            modifier = Modifier.weight(1f)
                        ) { Text("Hard") }
                        Button(
                            onClick = { onRate(card, FlashcardRating.GOOD); goodCount++; index++; revealed = false },
                            modifier = Modifier.weight(1f)
                        ) { Text("Good") }
                    }
                }
            } else {
                Spacer(Modifier.height(38.dp))
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Study round complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("$goodCount good · $hardCount hard · $againCount again", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = onExit) { Text("Back to deck") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckEditorDialog(
    initial: FlashcardDeck?,
    topics: List<StudyTopic>,
    defaultCourse: String? = null,
    defaultTopicId: String? = null,
    onDismiss: () -> Unit,
    onSave: (FlashcardDeck) -> Unit
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var course by rememberSaveable(initial?.id, defaultCourse) { mutableStateOf(initial?.courseCode ?: defaultCourse.orEmpty()) }
    var topicId by rememberSaveable(initial?.id, defaultTopicId) { mutableStateOf(initial?.topicId ?: defaultTopicId) }
    var description by rememberSaveable(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    val valid = name.trim().isNotEmpty()
    val modules = topics.filter { it.courseCode.equals(course.trim(), true) && it.parentTopicId == null }.sortedWith(compareBy<StudyTopic> { it.sortOrder }.thenBy { it.name.lowercase() })
    val moduleLabels = listOf("Overall / course-wide") + modules.map { it.name }
    val moduleIndex = modules.indexOfFirst { it.id == topicId }.let { if (it < 0) 0 else it + 1 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New flashcard deck" else "Edit deck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Deck name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = course, onValueChange = { course = it; topicId = null }, label = { Text("Course code (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PunlaDropdownField(
                    label = "Module",
                    selectedLabel = moduleLabels.getOrElse(moduleIndex) { "Overall / course-wide" },
                    options = moduleLabels,
                    onSelect = { index -> topicId = if (index == 0) null else modules.getOrNull(index - 1)?.id },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(initial?.copy(name = name.trim(), courseCode = course.trim().ifBlank { null }, topicId = topicId, description = description.trim().ifBlank { null }, updatedAt = now)
                        ?: FlashcardDeck(name = name.trim(), courseCode = course.trim().ifBlank { null }, topicId = topicId, description = description.trim().ifBlank { null }, createdAt = now, updatedAt = now))
                },
                enabled = valid
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CardEditorDialog(deckId: String, initial: Flashcard?, onDismiss: () -> Unit, onSave: (Flashcard) -> Unit) {
    var front by rememberSaveable(initial?.id) { mutableStateOf(initial?.front.orEmpty()) }
    var back by rememberSaveable(initial?.id) { mutableStateOf(initial?.back.orEmpty()) }
    var hint by rememberSaveable(initial?.id) { mutableStateOf(initial?.hint.orEmpty()) }
    var tags by rememberSaveable(initial?.id) { mutableStateOf(initial?.tags.orEmpty()) }
    var starred by rememberSaveable(initial?.id) { mutableStateOf(initial?.starred ?: false) }
    var reverse by rememberSaveable(initial?.id) { mutableStateOf(initial?.reverseEnabled ?: false) }
    var imageUri by rememberSaveable(initial?.id) { mutableStateOf(initial?.imageUri) }
    var occlusionSpec by rememberSaveable(initial?.id) { mutableStateOf(occlusionSpecFromJson(initial?.occlusionJson.orEmpty())) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            imageUri = uri.toString()
        }
    }
    var typeIndex by rememberSaveable(initial?.id) { mutableIntStateOf(if (initial?.cardType == FlashcardTypes.CLOZE) 1 else 0) }
    val isCloze = typeIndex == 1
    val valid = front.trim().isNotEmpty() && back.trim().isNotEmpty() && (!isCloze || ClozeText.hasCloze(front))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add flashcard" else "Edit flashcard") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SegmentedControl(listOf("Basic", "Cloze"), typeIndex, { typeIndex = it }, Modifier.fillMaxWidth()) }
                if (isCloze) {
                    item {
                        Text("Wrap the hidden answer in double braces, e.g. Photosynthesis occurs in the {{chloroplast}}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { OutlinedTextField(value = front, onValueChange = { front = it }, label = { Text(if (isCloze) "Cloze sentence" else "Front / question") }, modifier = Modifier.fillMaxWidth(), minLines = 2, isError = isCloze && front.isNotBlank() && !ClozeText.hasCloze(front)) }
                item { OutlinedTextField(value = back, onValueChange = { back = it }, label = { Text(if (isCloze) "Explanation / back" else "Back / answer") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                item { OutlinedTextField(value = hint, onValueChange = { hint = it }, label = { Text("Hint (optional)") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags (comma separated)") }, placeholder = { Text("Lecture 3, Midterm, Plant anatomy") }, modifier = Modifier.fillMaxWidth()) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Image, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (imageUri == null) "Add image / diagram" else "Change image")
                        }
                        if (imageUri != null) {
                            AsyncImage(model = imageUri, contentDescription = "Card image preview", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)))
                            TextButton(onClick = { imageUri = null; occlusionSpec = "" }) { Text("Remove image") }
                            OutlinedTextField(
                                value = occlusionSpec, onValueChange = { occlusionSpec = it },
                                label = { Text("Image occlusion (optional)") },
                                supportingText = { Text("x,y,width,height in %, e.g. 20,30,35,15. Hidden until reveal.") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Starred")
                            Text("Include in quick starred review", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = starred, onCheckedChange = { starred = it })
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Study both directions")
                            Text(if (isCloze) "Reverse mode is disabled for cloze cards" else "Alternates front→back and back→front between reviews", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = reverse && !isCloze, onCheckedChange = { reverse = it }, enabled = !isCloze)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    val normalizedTags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString(", ")
                    onSave(initial?.copy(
                        front = front.trim(), back = back.trim(), hint = hint.trim().ifBlank { null }, tags = normalizedTags,
                        starred = starred, reverseEnabled = reverse && !isCloze, cardType = if (isCloze) FlashcardTypes.CLOZE else FlashcardTypes.BASIC,
                        imageUri = imageUri, occlusionJson = occlusionJsonFromSpec(occlusionSpec), updatedAt = now
                    ) ?: Flashcard(
                        deckId = deckId, front = front.trim(), back = back.trim(), hint = hint.trim().ifBlank { null }, tags = normalizedTags,
                        starred = starred, reverseEnabled = reverse && !isCloze, cardType = if (isCloze) FlashcardTypes.CLOZE else FlashcardTypes.BASIC,
                        imageUri = imageUri, occlusionJson = occlusionJsonFromSpec(occlusionSpec), createdAt = now, updatedAt = now
                    ))
                },
                enabled = valid
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FlashcardJsonImportPreviewDialog(
    imported: FlashcardJsonDeck,
    destinationDeckName: String?,
    alreadyImported: Boolean,
    exactDuplicateCards: Int,
    importing: Boolean,
    onDismiss: () -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import ${imported.cards.size} flashcards?") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    if (destinationDeckName == null) {
                        Text(imported.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        imported.courseCode?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                        imported.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    } else Text("Cards will be added to $destinationDeckName.", style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    Text("Punla file ID: ${FlashcardJsonImport.FILE_ID}\nContent ID: ${imported.contentId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (alreadyImported) {
                    item { WarningSurface("This exact content ID was imported before. Import again only if you intentionally want another copy.") }
                }
                if (exactDuplicateCards > 0) {
                    item { WarningSurface("$exactDuplicateCards exact front/back duplicate${if (exactDuplicateCards == 1) "" else "s"} already exist in this deck. Punla will skip them.") }
                }
                item { HorizontalDivider() }
                items(imported.cards.take(3)) { card ->
                    Column {
                        Text(card.front, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(card.back, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (imported.cards.size > 3) item { Text("+${imported.cards.size - 3} more cards", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (imported.warnings.isNotEmpty()) item { WarningSurface(imported.warnings.joinToString("\n")) }
                item { Text("Review history, mastery, due dates, and IDs from outside files are never imported.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun WarningSurface(message: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
    }
}

@Composable
private fun FlashcardJsonErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Couldn't import flashcards") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

private data class OcclusionRect(val x: Float, val y: Float, val w: Float, val h: Float)

private fun occlusionRects(json: String): List<OcclusionRect> = runCatching {
    val arr = JSONArray(json.ifBlank { "[]" })
    buildList {
        for (i in 0 until minOf(arr.length(), 64)) {
            val o = arr.optJSONObject(i) ?: continue
            val rawX = o.optDouble("x", 0.0).toFloat()
            val rawY = o.optDouble("y", 0.0).toFloat()
            val rawW = o.optDouble("w", 0.0).toFloat()
            val rawH = o.optDouble("h", 0.0).toFloat()
            if (!rawX.isFinite() || !rawY.isFinite() || !rawW.isFinite() || !rawH.isFinite() || rawW <= 0f || rawH <= 0f) continue
            val x = rawX.coerceIn(0f, 1f)
            val y = rawY.coerceIn(0f, 1f)
            val w = rawW.coerceIn(0f, 1f - x)
            val h = rawH.coerceIn(0f, 1f - y)
            if (w > 0f && h > 0f) add(OcclusionRect(x, y, w, h))
        }
    }
}.getOrDefault(emptyList())

private fun occlusionJsonFromSpec(spec: String): String {
    val nums = spec.split(',').mapNotNull { it.trim().toFloatOrNull() }
    if (nums.size != 4) return "[]"
    val (x,y,w,h) = nums.map { it.coerceIn(0f,100f) }
    return JSONArray().put(JSONObject().put("x",x/100f).put("y",y/100f).put("w",w/100f).put("h",h/100f)).toString()
}

private fun occlusionSpecFromJson(json: String): String {
    val r = occlusionRects(json).firstOrNull() ?: return ""
    return listOf(r.x,r.y,r.w,r.h).joinToString(",") { (it*100f).toInt().toString() }
}

@Composable
private fun FlashcardStudyImage(card: Flashcard, revealed: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp))) {
        AsyncImage(model = card.imageUri, contentDescription = "Study diagram", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (!revealed) {
            occlusionRects(card.occlusionJson).forEach { r ->
                Surface(
                    modifier = Modifier.offset(x = maxWidth * r.x, y = maxHeight * r.y).size(width = maxWidth * r.w, height = maxHeight * r.h),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .92f),
                    shape = RoundedCornerShape(5.dp)
                ) {}
            }
        }
    }
}

private fun importFailureMessage(action: String, error: Throwable): String {
    val detail = error.message?.trim()?.takeIf { it.isNotEmpty() }?.take(240)
    return buildString {
        append("Punla couldn't $action. No partial cards were kept.")
        if (detail != null) append("\n\nDetails: ").append(detail)
    }
}

@Composable
private fun BulkAddDialog(deckId: String, onDismiss: () -> Unit, onSave: (List<Flashcard>) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text, deckId) { parseBulkCards(deckId, text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk add cards") },
        text = {
            Column {
                Text("Paste one card per line. Separate front and back with a tab or ::", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp),
                    placeholder = { Text("Photosynthesis :: Converts light energy into chemical energy\nSalinity<TAB>Dissolved salt concentration") }
                )
                Spacer(Modifier.height(8.dp))
                Text("${parsed.size} valid cards detected", style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(parsed) }, enabled = parsed.isNotEmpty()) { Text("Add ${parsed.size}") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun parseBulkCards(deckId: String, raw: String): List<Flashcard> {
    val now = System.currentTimeMillis()
    return raw.lineSequence().mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@mapNotNull null
        val pair = when {
            '\t' in trimmed -> trimmed.split('\t', limit = 2)
            "::" in trimmed -> trimmed.split("::", limit = 2)
            else -> return@mapNotNull null
        }
        val front = pair.getOrNull(0)?.trim().orEmpty()
        val back = pair.getOrNull(1)?.trim().orEmpty()
        if (front.isBlank() || back.isBlank()) null
        else Flashcard(id = UUID.randomUUID().toString(), deckId = deckId, front = front, back = back, createdAt = now, updatedAt = now)
    }.toList()
}

private fun com.uplb.punla.data.FlashcardJsonCard.toEntity(deckId: String, timestamp: Long): Flashcard = Flashcard(
    deckId = deckId,
    front = front,
    back = back,
    hint = hint,
    tags = tags,
    starred = starred,
    reverseEnabled = reverseEnabled,
    cardType = cardType,
    imageUri = imageUri,
    occlusionJson = occlusionJson,
    createdAt = timestamp,
    updatedAt = timestamp
)

private fun safeJsonFileName(name: String, suffix: String): String {
    val base = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "punla" }.take(50)
    return "$base-$suffix.json"
}

private val JSON_MIME_TYPES = arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
