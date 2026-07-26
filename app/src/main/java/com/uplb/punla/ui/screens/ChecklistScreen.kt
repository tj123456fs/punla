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
    var showAddDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

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
                        onDelete = { vm.deleteChecklistItem(checklistItem) }
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
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset checklist?") },
            text = { Text("This replaces your current list with the default requirements, discarding any edits, checks, or custom items.") },
            confirmButton = {
                TextButton(onClick = { vm.resetChecklistToDefaults(); showResetConfirm = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
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
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add requirement") },
        text = {
            Column {
                PunlaField("Title", title, { title = it }, placeholder = "e.g. Renew scholarship documents", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PunlaField("Note", note, { note = it }, placeholder = "Optional", modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) onSave(title.trim(), note.ifBlank { null })
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
