package com.uplb.punla.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.uplb.punla.diagnostics.HealthAction
import com.uplb.punla.diagnostics.HealthCheck
import com.uplb.punla.diagnostics.HealthState
import com.uplb.punla.diagnostics.PunlaDiagnostics
import com.uplb.punla.diagnostics.SystemHealthInspector
import com.uplb.punla.diagnostics.SystemHealthSnapshot
import com.uplb.punla.ui.theme.PunlaMono
import com.uplb.punla.worker.CoreReliabilityScheduler
import com.uplb.punla.worker.ReliabilityProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SystemHealthScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var refreshToken by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<SystemHealthSnapshot?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(PunlaDiagnostics.read(context))
                } ?: error("Could not open export destination")
            }
            launch(Dispatchers.Main) {
                exportMessage = if (result.isSuccess) "Diagnostics exported." else "Could not export diagnostics."
            }
        }
    }

    LaunchedEffect(refreshToken) {
        loadError = null
        val result = runCatching { SystemHealthInspector.inspect(context) }
        result.onSuccess { snapshot = it }
            .onFailure {
                loadError = it.message ?: "System Health could not complete its checks."
                PunlaDiagnostics.error(context, "SystemHealthScreen", "Health refresh failed", it)
            }
    }

    // Android permission/battery screens live outside Punla. Refresh whenever
    // the user returns so the status reflects the change without reopening this page.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            val current = snapshot
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "SYSTEM HEALTH",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    when {
                        current == null && loadError == null -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                                Text("Checking Punla's local services…")
                            }
                        }
                        loadError != null -> {
                            Text(
                                loadError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        current != null -> {
                            Text(
                                if (current.attentionCount == 0) {
                                    "Core reliability checks look healthy."
                                } else {
                                    "${current.attentionCount} check${if (current.attentionCount == 1) "" else "s"} need attention."
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "This is a local diagnostic snapshot. Punla does not upload this information.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { refreshToken++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh")
                    }
                }
            }
        }

        snapshot?.checks?.forEach { check ->
            item(key = check.key) {
                HealthCheckCard(
                    check = check,
                    onAction = { action ->
                        when (action) {
                            HealthAction.REPAIR_BACKGROUND_JOBS -> {
                                scope.launch {
                                    CoreReliabilityScheduler.ensureScheduled(context, updateExisting = true)
                                    refreshToken++
                                }
                            }
                            HealthAction.RUN_BACKGROUND_PROBE -> {
                                ReliabilityProbe.scheduleBackgroundProbe(context)
                                refreshToken++
                            }
                            HealthAction.RUN_IDLE_PROBE -> {
                                ReliabilityProbe.scheduleIdleProbe(context)
                                refreshToken++
                            }
                            HealthAction.ARM_REBOOT_PROBE -> {
                                ReliabilityProbe.armRebootProbe(context)
                                refreshToken++
                            }
                            HealthAction.ARM_STABILITY_SOAK -> {
                                ReliabilityProbe.startStabilitySoak(context)
                                refreshToken++
                            }
                            else -> openHealthAction(context, action)
                        }
                    }
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "DIAGNOSTICS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    val current = snapshot
                    Text(
                        when {
                            current == null -> "Reading local diagnostic log…"
                            current.diagnosticErrors == 0 && current.diagnosticWarnings == 0 -> "No warnings or errors are currently recorded."
                            else -> "${current.diagnosticErrors} error entries · ${current.diagnosticWarnings} warning entries"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The log contains Punla component names, short failure messages, and stack traces. It stays on this device unless you export it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showDiagnostics = !showDiagnostics },
                            enabled = !snapshot?.diagnosticTail.isNullOrBlank()
                        ) {
                            Text(if (showDiagnostics) "Hide log" else "View log")
                        }
                        OutlinedButton(
                            onClick = {
                                exportMessage = null
                                exportLauncher.launch("punla-diagnostics-${LocalDate.now()}.txt")
                            }
                        ) {
                            Text("Export")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { showClearConfirm = true },
                        enabled = !snapshot?.diagnosticTail.isNullOrBlank(),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Clear diagnostics")
                    }
                    exportMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (showDiagnostics) {
                        val text = snapshot?.diagnosticTail.orEmpty()
                        if (text.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PunlaMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Tip: Android's battery and background labels vary by phone brand. System Health reports the standard Android signals Punla can read; manufacturer-specific auto-start controls may still need manual review.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear diagnostics?") },
            text = { Text("This clears Punla's local diagnostic log. It does not delete your classes, study data, attendance, grades, or backups.") },
            confirmButton = {
                Button(onClick = {
                    PunlaDiagnostics.clear(context)
                    showClearConfirm = false
                    showDiagnostics = false
                    refreshToken++
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HealthCheckCard(
    check: HealthCheck,
    onAction: (HealthAction) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (check.state) {
                            HealthState.HEALTHY -> Icons.Default.CheckCircle
                            HealthState.ATTENTION -> Icons.Default.Warning
                            HealthState.BLOCKED -> Icons.Default.Error
                            HealthState.INFO -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = when (check.state) {
                            HealthState.HEALTHY -> MaterialTheme.colorScheme.primary
                            HealthState.ATTENTION -> MaterialTheme.colorScheme.secondary
                            HealthState.BLOCKED -> MaterialTheme.colorScheme.error
                            HealthState.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(check.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            check.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = {}, enabled = false, label = { Text(check.status) })
            }
            if (check.action != null && check.actionLabel != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { onAction(check.action) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(check.actionLabel)
                }
            }
        }
    }
}

private fun openHealthAction(context: Context, action: HealthAction) {
    val intent = when (action) {
        HealthAction.NOTIFICATION_SETTINGS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        HealthAction.EXACT_ALARM_SETTINGS -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } else {
                appDetailsIntent(context)
            }
        }
        HealthAction.BATTERY_SETTINGS -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        HealthAction.APP_SETTINGS -> appDetailsIntent(context)
        HealthAction.REPAIR_BACKGROUND_JOBS,
        HealthAction.RUN_BACKGROUND_PROBE,
        HealthAction.RUN_IDLE_PROBE,
        HealthAction.ARM_REBOOT_PROBE,
        HealthAction.ARM_STABILITY_SOAK -> return
    }
    runCatching { context.startActivity(intent) }
        .recoverCatching { context.startActivity(appDetailsIntent(context)) }
}

private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
