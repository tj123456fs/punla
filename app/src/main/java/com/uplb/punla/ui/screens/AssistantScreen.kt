package com.uplb.punla.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uplb.punla.assistant.AssistantAction
import com.uplb.punla.assistant.AssistantSnapshot
import com.uplb.punla.assistant.LocalAssistant
import com.uplb.punla.data.AssistantApiResult
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ui.pomodoro.StudySuggestion
import com.uplb.punla.ui.theme.PunlaDisplay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

private data class AssistantChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val fromUser: Boolean,
    val text: String,
    val action: AssistantAction? = null,
    val suggestion: StudySuggestion? = null,
    val actionDone: Boolean = false
)

/** Local-first personal assistant. Known planner queries never touch the network. */
@Composable
fun AssistantScreen(
    vm: PunlaViewModel,
    onOpenPomodoro: (String?) -> Unit = {}
) {
    val classes by vm.classes.collectAsState()
    val deadlines by vm.deadlines.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val sessions by vm.studySessions.collectAsState()
    val messages = remember {
        mutableStateListOf(
            AssistantChatMessage(
                fromUser = false,
                text = "Ask me about today's classes, upcoming deadlines, spending, attendance, or a good time to study. Most answers work fully offline."
            )
        )
    }
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun addAssistant(text: String, action: AssistantAction? = null, suggestion: StudySuggestion? = null) {
        suggestion?.let(vm::recordStudySuggestionShown)
        messages += AssistantChatMessage(fromUser = false, text = text, action = action, suggestion = suggestion)
    }

    fun send() {
        val text = query.trim()
        if (text.isBlank() || loading) return
        query = ""
        messages += AssistantChatMessage(fromUser = true, text = text)
        val snapshot = AssistantSnapshot(classes, deadlines, expenses, sessions, vm.repo)
        val local = LocalAssistant.answer(text, snapshot)
        if (local.handled) {
            addAssistant(local.text, local.action, local.suggestion)
        } else if (vm.cloudAssistantEnabled && vm.assistantApiKeyConfigured) {
            loading = true
            scope.launch {
                when (val result = vm.askCloudAssistant(text)) {
                    is AssistantApiResult.Success -> addAssistant(result.text)
                    is AssistantApiResult.Failure -> addAssistant(result.message)
                }
                loading = false
            }
        } else {
            addAssistant(
                "I couldn't resolve that locally. You can enable the optional cloud fallback and add your API key in Settings."
            )
        }
    }

    LaunchedEffect(messages.size, loading) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            "Punla Assistant",
            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = PunlaDisplay),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            if (vm.cloudAssistantEnabled && vm.assistantApiKeyConfigured)
                "Local first · cloud fallback enabled"
            else "Offline planner commands",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 340.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            if (!message.fromUser) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        "PUNLA",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(message.text, style = MaterialTheme.typography.bodyMedium)
                            message.action?.let { action ->
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        when (action) {
                                            is AssistantAction.StartFocus -> {
                                                message.suggestion?.let(vm::acceptStudySuggestion)
                                                vm.updatePomodoroWorkMinutes(action.minutes)
                                                onOpenPomodoro(action.course)
                                            }
                                            is AssistantAction.AddExpense -> {
                                                vm.addExpense(
                                                    Expense(
                                                        amount = action.amount,
                                                        category = action.category,
                                                        date = LocalDate.now().toString(),
                                                        note = action.note
                                                    )
                                                )
                                                val index = messages.indexOfFirst { it.id == message.id }
                                                if (index >= 0) messages[index] = message.copy(actionDone = true)
                                            }
                                        }
                                    },
                                    enabled = !message.actionDone
                                ) {
                                    Text(
                                        when (action) {
                                            is AssistantAction.StartFocus -> "Start ${action.minutes}-minute focus"
                                            is AssistantAction.AddExpense -> if (message.actionDone) "Expense added" else "Add expense"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Thinking with cloud fallback…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("What do I have today?") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = ::send, enabled = query.isNotBlank() && !loading) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}
