package com.uplb.punla.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoutingAttribution(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Surface(modifier, color = MaterialTheme.colorScheme.surface.copy(alpha = .94f)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { uri.openUri("https://www.openstreetmap.org/copyright") }) { Text("© OpenStreetMap") }
            TextButton(onClick = { uri.openUri("https://routing.openstreetmap.de/about.html") }) { Text("Routing: FOSSGIS") }
            TextButton(onClick = { uri.openUri("https://www.openstreetmap.org/fixthemap") }) { Text("Fix the map") }
        }
    }
}
