package com.uplb.punla.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal val MORE_DESTINATIONS = listOf(
    "Your semester" to listOf("Schedule" to "schedule", "Deadlines" to "deadlines", "Grades" to "grades", "Academic Pulse" to "student-os?tab=2"),
    "Campus & daily life" to listOf("Budget" to "budget", "Campus map" to "campus", "Life & planning hours" to "student-os?tab=4", "Before classes start" to "checklist"),
    "Study tools" to listOf("Focus timer" to "pomodoro", "Flashcards" to "flashcards", "Quizzes" to "quizzes", "Assistant" to "assistant", "Planning assistant" to "student-os?tab=5"),
    "Preferences" to listOf("Settings" to "settings")
)

@Composable
internal fun MoreScreen(onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Everything else, close at hand.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        MORE_DESTINATIONS.forEach { (section, links) ->
            item { Text(section, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
            items(links, key = { it.second }) { (label, route) ->
                Surface(onClick = { onOpen(route) }, modifier = Modifier.testTag("more:$route"), shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
