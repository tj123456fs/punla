package com.uplb.punla.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uplb.punla.data.entity.ChecklistItem
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ui.theme.LocalPunlaPalette
import com.uplb.punla.ui.theme.PunlaDisplay

/**
 * "Before classes start" checklist — pre-enrollment documents, requirements,
 * and errands. Ships with a built-in default set (ChecklistDefaults) that's
 * fully editable/deletable, plus a countdown to the term's start date so the
 * urgency is visible without leaving the screen. Notifications for
 * unchecked items as the date approaches are handled by
 * ChecklistReminderWorker, mirroring BudgetWorker's threshold pattern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(vm: PunlaViewModel) {
    val items by vm.checklistItems.collectAsState()
    val dataReady by vm.isDataReady.collectAsState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeleteItem = remember(pendingDeleteItemId, items) { items.firstOrNull { it.id == pendingDeleteItemId } }

    val daysLeft = remember { vm.repo.daysUntilClassesStart() }
    val checkedCount = items.count { it.checked }
    val progress = if (items.isNotEmpty()) checkedCount.toFloat() / items.size else 0f
    val allDone = items.isNotEmpty() && checkedCount == items.size

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Default.Add, "Add item") }
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Before Classes Start",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = PunlaDisplay)
                )
                Spacer(Modifier.height(10.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (allDone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            when {
                                daysLeft > 1 -> "$daysLeft days until classes start"
                                daysLeft == 1L -> "1 day until classes start"
                                daysLeft == 0L -> "Classes start today"
                                else -> "Classes have started"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$checkedCount of ${items.size} done",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            if (items.isNotEmpty()) {
                item { SectionLabel("Requirements") }
                items(items.sortedWith(compareBy({ it.checked }, { it.sortOrder })), key = { it.id }) { checklistItem ->
                    ChecklistItemCard(
                        item = checklistItem,
                        onToggle = { vm.toggleChecklistItem(checklistItem) },
                        onDelete = { pendingDeleteItemId = checklistItem.id }
                    )
                }
                item {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { showResetConfirm = true }) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset to default checklist")
                    }
                }
            } else if (dataReady) {
                item {
                    EmptyState(
                        icon = Icons.Default.CheckCircle,
                        message = "Nothing on your checklist yet. Tap + to add a requirement."
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AddChecklistItemDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, note -> vm.addChecklistItem(title, note); showAddDialog = false }
        )
    }

    if (showResetConfirm) {
        DestructiveActionDialog(
            title = "Reset checklist?",
            message = "This replaces your current list with the default requirements, discarding any edits, checks, or custom items.",
            confirmLabel = "Reset",
            onConfirm = { vm.resetChecklistToDefaults(); showResetConfirm = false },
            onDismiss = { showResetConfirm = false }
        )
    }

    if (pendingDeleteItem != null) {
        DestructiveActionDialog(
            title = "Delete requirement?",
            message = "Remove “${pendingDeleteItem.title}” from the checklist?",
            onConfirm = {
                vm.deleteChecklistItem(pendingDeleteItem)
                pendingDeleteItemId = null
            },
            onDismiss = { pendingDeleteItemId = null }
        )
    }
}

@Composable
private fun ChecklistItemCard(item: ChecklistItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = item.checked, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (!item.note.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddChecklistItemDialog(onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var titleTouched by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    val invalidTitle = titleTouched && title.isBlank()
    val isDirty = title.isNotBlank() || note.isNotBlank()

    fun requestDismiss() {
        if (isDirty) showDiscardConfirm = true else onDismiss()
    }

    AlertDialog(
        onDismissRequest = { requestDismiss() },
        title = { Text(if (showDiscardConfirm) "Discard requirement?" else "Add requirement") },
        text = {
            if (showDiscardConfirm) {
                Text("Your unsaved checklist item will be lost.")
            } else {
                Column {
                    PunlaField(
                        "Title",
                        title,
                        { title = it; titleTouched = true },
                        placeholder = "e.g. Renew scholarship documents",
                        modifier = Modifier.fillMaxWidth(),
                        isError = invalidTitle,
                        supportingText = if (invalidTitle) "A title is required." else null
                    )
                    Spacer(Modifier.height(8.dp))
                    PunlaField("Note", note, { note = it }, placeholder = "Optional", modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (showDiscardConfirm) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Discard") }
            } else {
                TextButton(onClick = {
                    titleTouched = true
                    if (title.isNotBlank()) onSave(title.trim(), note.trim().ifBlank { null })
                }) { Text("Save") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (showDiscardConfirm) showDiscardConfirm = false else requestDismiss()
            }) { Text(if (showDiscardConfirm) "Keep editing" else "Cancel") }
        }
    )
}
