package com.uplb.punla.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uplb.punla.data.PunlaDatabase
import com.uplb.punla.data.entity.WalkSessionStatus
import com.uplb.punla.data.fmtDistance
import com.uplb.punla.data.hasFineLocationPermission
import com.uplb.punla.data.openAppLocationSettings
import com.uplb.punla.data.shouldShowLocationRationale
import com.uplb.punla.location.WalkRecorder
import com.uplb.punla.ui.theme.PunlaMono

/**
 * Small Campus-tab control for Punla's explicit walk recorder.
 * It deliberately requires precise location because approximate fixes are too
 * coarse to become useful future footpath geometry.
 */
@Composable
fun WalkRecorderCard(
    modifier: Modifier = Modifier,
    onOpenMap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val dao = remember(context.applicationContext) {
        PunlaDatabase.get(context.applicationContext).walkRecordingDao()
    }
    val session by dao.observeActiveSession().collectAsStateWithLifecycle(initialValue = null)

    var permissionDenied by remember { mutableStateOf(false) }
    var startAfterPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val preciseGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            hasFineLocationPermission(context)
        permissionDenied = !preciseGranted
        if (preciseGranted && startAfterPermission) {
            startAfterPermission = false
            WalkRecorder.start(context)
        }
    }

    fun requestStart() {
        if (hasFineLocationPermission(context)) {
            WalkRecorder.start(context)
            return
        }
        startAfterPermission = true
        if (permissionDenied && !shouldShowLocationRationale(context)) {
            openAppLocationSettings(context)
        } else {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val active = session
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (active == null) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.DirectionsWalk,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        if (active == null) "Record a campus walk"
                        else if (active.status == WalkSessionStatus.PAUSED) "Walk recording paused"
                        else "Recording campus walk",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (active == null) {
                            "Punla saves the route directly on this device — no GPX upload needed."
                        } else {
                            "${fmtDistance(active.distanceMeters)} · ${active.pointCount} GPS point${if (active.pointCount == 1) "" else "s"} · ${formatWalkDuration(active.startedAt, active.pausedAt, active.accumulatedPauseMillis)}"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = if (active == null) null else PunlaMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (permissionDenied && !hasFineLocationPermission(context)) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Precise location is required for recording usable walking paths.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(10.dp))
            if (active == null) {
                Button(
                    onClick = { requestStart() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DirectionsWalk, contentDescription = null)
                    Text(" Start walk")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (active.status == WalkSessionStatus.PAUSED) WalkRecorder.resume(context)
                            else WalkRecorder.pause(context)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (active.status == WalkSessionStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null
                        )
                        Text(if (active.status == WalkSessionStatus.PAUSED) " Resume" else " Pause")
                    }
                    Button(
                        onClick = { WalkRecorder.stop(context) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Text(" Stop")
                    }
                }
                if (onOpenMap != null) {
                    OutlinedButton(
                        onClick = onOpenMap,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show recording on map")
                    }
                }
            }
        }
    }
}

private fun formatWalkDuration(startedAt: Long, pausedAt: Long?, accumulatedPauseMillis: Long): String {
    val effectiveEnd = pausedAt ?: System.currentTimeMillis()
    val activeMillis = (effectiveEnd - startedAt - accumulatedPauseMillis).coerceAtLeast(0L)
    val totalSeconds = activeMillis / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
