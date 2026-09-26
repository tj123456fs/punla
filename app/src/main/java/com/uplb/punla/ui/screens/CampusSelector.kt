package com.uplb.punla.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.uplb.punla.data.CampusProfile

@Composable
internal fun CampusSelector(
    profiles: List<CampusProfile>,
    activeProfile: CampusProfile?,
    automatic: Boolean,
    onAutomatic: () -> Unit,
    onSelect: (CampusProfile) -> Unit,
    onDelete: (CampusProfile) -> Unit,
    onNewCampus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<CampusProfile?>(null) }
    val label = if (automatic) {
        "Automatic · ${activeProfile?.name ?: "Current area"}"
    } else {
        activeProfile?.name ?: "Campus"
    }

    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Map, contentDescription = null)
            Text("  $label", modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose campus")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Automatic") },
                leadingIcon = {
                    if (automatic) Icon(Icons.Default.Check, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onAutomatic()
                }
            )
            HorizontalDivider()
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name) },
                    leadingIcon = {
                        if (!automatic && activeProfile?.id == profile.id) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                    trailingIcon = {
                        if (!profile.builtIn) {
                            IconButton(
                                onClick = {
                                    expanded = false
                                    deleteCandidate = profile
                                }
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete ${profile.name}"
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(profile)
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("New campus at my location") },
                leadingIcon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null) },
                onClick = {
                    expanded = false
                    onNewCampus()
                }
            )
        }
    }

    val candidate = deleteCandidate
    if (candidate != null) {
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete campus?") },
            text = { Text("Remove ${candidate.name} from your saved campuses? Recorded GPS walks stay on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                        onDelete(candidate)
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
internal fun NewCampusDialog(
    suggestedName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(suggestedName) { mutableStateOf(suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New campus") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Campus name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
