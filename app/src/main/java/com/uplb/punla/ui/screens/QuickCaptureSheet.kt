package com.uplb.punla.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.uplb.punla.planning.CaptureParser
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun QuickCaptureSheet(
    text: String, onTextChange: (String) -> Unit, saving: Boolean, error: String?, courses: List<String>,
    onSave: () -> Unit, onDismiss: () -> Unit, onStructuredAdd: (String) -> Unit
) {
    val inputFocus = remember { FocusRequester() }
    val draft = remember(text, courses) { CaptureParser.parse(text, LocalDate.now(), courses) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Get it off your mind", style = MaterialTheme.typography.headlineSmall)
            Text("Save a rough thought now. Review the details in Inbox when you're ready.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(text, { if (it.length <= 20000) onTextChange(it) }, enabled = !saving,
                label = { Text("Task, note or link") }, placeholder = { Text("MATH 27 exercise due Friday") },
                minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth().focusRequester(inputFocus).testTag("capture-input"))
            LaunchedEffect(Unit) { inputFocus.requestFocus() }
            val hints = listOfNotNull(draft.course, draft.date, draft.time)
            if (hints.isNotEmpty()) {
                Text("Suggested details · confirm in Inbox", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    hints.forEach { hint ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                            Text(hint, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("capture-error")) }
            Button(onClick = onSave, enabled = text.isNotBlank() && !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (saving) "Saving…" else "Save to Inbox")
            }
            Text("Or add with details", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Class" to "schedule", "Expense" to "budget", "Deadline" to "deadlines", "Grade" to "grades").forEach { (label, route) ->
                    OutlinedButton(onClick = { onStructuredAdd(route) }, enabled = !saving) { Text(label) }
                }
            }
        }
    }
}
