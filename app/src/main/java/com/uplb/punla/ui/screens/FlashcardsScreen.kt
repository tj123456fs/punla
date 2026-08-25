package com.uplb.punla.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uplb.punla.data.FlashcardJsonDeck
import com.uplb.punla.data.FlashcardJsonImport
import com.uplb.punla.data.entity.Flashcard
import com.uplb.punla.data.entity.FlashcardDeck
import com.uplb.punla.data.entity.FlashcardRating
import com.uplb.punla.ui.PunlaViewModel
import java.util.UUID

@Composable
fun FlashcardsScreen(vm: PunlaViewModel) {
    val decks by vm.flashcardDecks.collectAsState()
    val allCards by vm.flashcards.collectAsState()
    var selectedDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var studyRequest by remember { mutableStateOf<List<Flashcard>?>(null) }

    val selectedDeck = decks.firstOrNull { it.id == selectedDeckId }
    val selectedCards = selectedDeck?.let { deck -> allCards.filter { it.deckId == deck.id } }.orEmpty()

    BackHandler(enabled = studyRequest != null || selectedDeckId != null) {
        if (studyRequest != null) studyRequest = null else selectedDeckId = null
    }

    Crossfade(
        targetState = when {
            studyRequest != null && selectedDeck != null -> "study"
            selectedDeck != null -> "deck"
            else -> "library"
        },
        animationSpec = tween(180),
        label = "flashcardScreenMode"
    ) { mode ->
        when (mode) {
            "study" -> StudyDeckView(
                deck = selectedDeck!!,
                startingCards = studyRequest.orEmpty(),
                onRate = vm::rateFlashcard,
                onExit = { studyRequest = null }
            )
            "deck" -> DeckDetailView(
                deck = selectedDeck!!,
                cards = selectedCards,
                vm = vm,
                onBack = { selectedDeckId = null },
                onStudy = { cards -> studyRequest = cards }
            )
            else -> FlashcardLibraryView(
                decks = decks,
                cards = allCards,
                vm = vm,
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
    onOpenDeck: (FlashcardDeck) -> Unit
) {
    var showDeckDialog by rememberSaveable { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<FlashcardJsonDeck?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalArgumentException("Punla couldn't open that file.")
                FlashcardJsonImport.parse(raw)
            }.onSuccess { pendingImport = it }
                .onFailure { importError = it.message ?: "Punla couldn't import that JSON file." }
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
                    title = "Flashcard maker",
                    subtitle = "Build decks, review what is due, and let difficult cards come back sooner."
                )
            }
            item {
                OutlinedButton(
                    onClick = { jsonPicker.launch(FLASHCARD_JSON_MIME_TYPES) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Import JSON deck")
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
                item {
                    SectionLabel(
                        text = "Your decks",
                        icon = Icons.Default.Style
                    )
                }
                items(decks, key = { it.id }) { deck ->
                    val deckCards = cards.filter { it.deckId == deck.id }
                    val due = deckCards.count { it.dueAt == 0L || it.dueAt <= now }
                    val mastered = deckCards.count { it.mastery >= 4 }
                    DeckCard(
                        deck = deck,
                        cardCount = deckCards.size,
                        dueCount = due,
                        masteredCount = mastered,
                        onClick = { onOpenDeck(deck) }
                    )
                }
            }
        }
    }

    if (showDeckDialog) {
        DeckEditorDialog(
            initial = null,
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
            onDismiss = { pendingImport = null },
            onImport = {
                val now = System.currentTimeMillis()
                val deck = FlashcardDeck(
                    name = imported.name,
                    courseCode = imported.courseCode,
                    description = imported.description,
                    createdAt = now,
                    updatedAt = now
                )
                val importedCards = imported.cards.map { card ->
                    Flashcard(
                        deckId = deck.id,
                        front = card.front,
                        back = card.back,
                        hint = card.hint,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                vm.importFlashcardDeck(deck, importedCards)
                pendingImport = null
                onOpenDeck(deck)
            }
        )
    }
    importError?.let { message ->
        FlashcardJsonErrorDialog(message = message, onDismiss = { importError = null })
    }
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
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Style, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(deck.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val meta = listOfNotNull(deck.courseCode?.takeIf { it.isNotBlank() }, "$cardCount cards").joinToString(" · ")
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (dueCount > 0) {
                    AssistChip(onClick = onClick, label = { Text("$dueCount due") })
                }
            }
            deck.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (cardCount > 0) {
                Spacer(Modifier.height(12.dp))
                val progress = masteredCount.toFloat() / cardCount.toFloat()
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(5.dp))
                Text(
                    if (masteredCount == 0) "Start reviewing to build mastery" else "$masteredCount of $cardCount cards well learned",
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
    var importError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalArgumentException("Punla couldn't open that file.")
                FlashcardJsonImport.parse(raw, fallbackDeckName = deck.name)
            }.onSuccess { pendingImport = it }
                .onFailure { importError = it.message ?: "Punla couldn't import that JSON file." }
        }
    }
    val now = System.currentTimeMillis()
    val dueCards = cards.filter { it.dueAt == 0L || it.dueAt <= now }
    val visibleCards = cards.filter {
        query.isBlank() || it.front.contains(query, ignoreCase = true) || it.back.contains(query, ignoreCase = true)
    }
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to decks")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(deck.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(deck.courseCode?.takeIf { it.isNotBlank() }, "${cards.size} cards").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showDeckDialog = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit deck") }
                    IconButton(onClick = { confirmDeleteDeck = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete deck") }
                }
            }

            if (cards.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onStudy(dueCards.shuffled()) },
                            enabled = dueCards.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(if (dueCards.isEmpty()) "Nothing due" else "Study due (${dueCards.size})")
                        }
                        OutlinedButton(
                            onClick = { onStudy(cards.shuffled()) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Study all") }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { jsonPicker.launch(FLASHCARD_JSON_MIME_TYPES) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Import JSON")
                    }
                    FilledTonalButton(onClick = { showBulkDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.MenuBook, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Bulk add")
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
                        placeholder = { Text("Search this deck") },
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            if (cards.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Style,
                        message = "This deck is empty. Add cards one at a time or paste a whole set at once.",
                        actionLabel = "Add first card",
                        onAction = { showCardDialog = true }
                    )
                }
            } else if (visibleCards.isEmpty()) {
                item { EmptyState(icon = Icons.Default.Search, message = "No cards match your search.") }
            } else {
                item { SectionLabel("Cards", icon = Icons.Default.Style) }
                items(visibleCards, key = { it.id }) { card ->
                    FlashcardListRow(
                        card = card,
                        isDue = card.dueAt == 0L || card.dueAt <= now,
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
    pendingImport?.let { imported ->
        FlashcardJsonImportPreviewDialog(
            imported = imported,
            destinationDeckName = deck.name,
            onDismiss = { pendingImport = null },
            onImport = {
                val now = System.currentTimeMillis()
                vm.addFlashcards(
                    imported.cards.map { card ->
                        Flashcard(
                            deckId = deck.id,
                            front = card.front,
                            back = card.back,
                            hint = card.hint,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                )
                pendingImport = null
            }
        )
    }
    importError?.let { message ->
        FlashcardJsonErrorDialog(message = message, onDismiss = { importError = null })
    }
    if (showDeckDialog) {
        DeckEditorDialog(
            initial = deck,
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
            text = { Text("All ${cards.size} cards in this deck will be deleted too.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFlashcardDeck(deck)
                    confirmDeleteDeck = false
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteDeck = false }) { Text("Cancel") } }
        )
    }
    deleteCard?.let { card ->
        AlertDialog(
            onDismissRequest = { deleteCard = null },
            title = { Text("Delete this card?") },
            text = { Text(card.front, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = { vm.deleteFlashcard(card); deleteCard = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteCard = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FlashcardListRow(card: Flashcard, isDue: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(card.front, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(card.back, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit card") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete card") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isDue) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        if (isDue) "Due" else "Scheduled",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text("Mastery ${card.mastery}/5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (card.reviewCount > 0) {
                    Text("${card.reviewCount} reviews", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StudyDeckView(
    deck: FlashcardDeck,
    startingCards: List<Flashcard>,
    onRate: (Flashcard, FlashcardRating) -> Unit,
    onExit: () -> Unit
) {
    val queue = remember(deck.id, startingCards.map { it.id }) { mutableStateListOf<Flashcard>().apply { addAll(startingCards) } }
    var index by rememberSaveable(deck.id) { mutableIntStateOf(0) }
    var revealed by rememberSaveable(deck.id, index) { mutableStateOf(false) }
    var againCount by rememberSaveable(deck.id) { mutableIntStateOf(0) }
    var hardCount by rememberSaveable(deck.id) { mutableIntStateOf(0) }
    var goodCount by rememberSaveable(deck.id) { mutableIntStateOf(0) }
    val horizontalPadding = punlaScreenHorizontalPadding(maxContentWidth = 680.dp)

    BackHandler(onBack = onExit)

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
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 310.dp).clickable { revealed = !revealed },
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 310.dp).padding(26.dp), contentAlignment = Alignment.Center) {
                        AnimatedContent(targetState = revealed, label = "flashcardReveal") { showBack ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (showBack) "ANSWER" else "QUESTION",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(18.dp))
                                Text(
                                    if (showBack) card.back else card.front,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!showBack) {
                                    card.hint?.takeIf { it.isNotBlank() }?.let {
                                        Spacer(Modifier.height(18.dp))
                                        Text("Hint: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (!revealed) {
                    Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Reveal answer")
                    }
                    Spacer(Modifier.height(7.dp))
                    Text("You can also tap the card", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("How well did you know it?", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onRate(card, FlashcardRating.AGAIN)
                                againCount++
                                index++
                                revealed = false
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Again") }
                        FilledTonalButton(
                            onClick = {
                                onRate(card, FlashcardRating.HARD)
                                hardCount++
                                index++
                                revealed = false
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Hard") }
                        Button(
                            onClick = {
                                onRate(card, FlashcardRating.GOOD)
                                goodCount++
                                index++
                                revealed = false
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Good") }
                    }
                }
            } else {
                Spacer(Modifier.height(38.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
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
private fun DeckEditorDialog(initial: FlashcardDeck?, onDismiss: () -> Unit, onSave: (FlashcardDeck) -> Unit) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var course by rememberSaveable(initial?.id) { mutableStateOf(initial?.courseCode.orEmpty()) }
    var description by rememberSaveable(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    val valid = name.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New flashcard deck" else "Edit deck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Deck name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = course, onValueChange = { course = it }, label = { Text("Course code (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(
                        initial?.copy(
                            name = name.trim(),
                            courseCode = course.trim().ifBlank { null },
                            description = description.trim().ifBlank { null },
                            updatedAt = now
                        ) ?: FlashcardDeck(
                            name = name.trim(),
                            courseCode = course.trim().ifBlank { null },
                            description = description.trim().ifBlank { null },
                            createdAt = now,
                            updatedAt = now
                        )
                    )
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
    val valid = front.trim().isNotEmpty() && back.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add flashcard" else "Edit flashcard") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = front, onValueChange = { front = it }, label = { Text("Front / question") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = back, onValueChange = { back = it }, label = { Text("Back / answer") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = hint, onValueChange = { hint = it }, label = { Text("Hint (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(
                        initial?.copy(
                            front = front.trim(), back = back.trim(), hint = hint.trim().ifBlank { null }, updatedAt = now
                        ) ?: Flashcard(
                            deckId = deckId,
                            front = front.trim(), back = back.trim(), hint = hint.trim().ifBlank { null }, createdAt = now, updatedAt = now
                        )
                    )
                },
                enabled = valid
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private val FLASHCARD_JSON_MIME_TYPES = arrayOf(
    "application/json",
    "text/json",
    "text/plain",
    "application/octet-stream"
)

@Composable
private fun FlashcardJsonImportPreviewDialog(
    imported: FlashcardJsonDeck,
    destinationDeckName: String?,
    onDismiss: () -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import ${imported.cards.size} flashcards?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (destinationDeckName == null) {
                    Text(imported.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    imported.courseCode?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    imported.description?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text("Cards will be added to $destinationDeckName.", style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
                imported.cards.take(3).forEach { card ->
                    Column {
                        Text(card.front, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(card.back, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (imported.cards.size > 3) {
                    Text("+${imported.cards.size - 3} more cards", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (imported.warnings.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            imported.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                Text(
                    "Imported cards start as new reviews. Existing mastery or review-history fields in JSON are ignored.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onImport) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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

@Composable
private fun BulkAddDialog(deckId: String, onDismiss: () -> Unit, onSave: (List<Flashcard>) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text, deckId) { parseBulkCards(deckId, text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk add cards") },
        text = {
            Column {
                Text(
                    "Paste one card per line. Separate front and back with a tab or ::",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
