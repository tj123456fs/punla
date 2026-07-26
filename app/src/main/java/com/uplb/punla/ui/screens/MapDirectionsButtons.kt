package com.uplb.punla.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.uplb.punla.data.Building

/**
 * "Open in OSM" / "Open in Google Maps" button row for a building, shared by
 * the per-building map sheet (CampusMapScreen) and the full campus map
 * (CampusFullMapScreen) so the intent-building logic only lives in one place.
 */
@Composable
fun DirectionsButtonsRow(building: Building, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = {
                val uri = Uri.parse("https://www.openstreetmap.org/?mlat=${building.lat}&mlon=${building.lon}#map=18/${building.lat}/${building.lon}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("OSM")
        }
        Button(
            onClick = {
                val geoUri = Uri.parse("geo:${building.lat},${building.lon}?q=${building.lat},${building.lon}(${Uri.encode(building.name)})")
                val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                val finalIntent = if (mapIntent.resolveActivity(context.packageManager) != null) {
                    mapIntent
                } else {
                    Intent(Intent.ACTION_VIEW, geoUri)
                }
                context.startActivity(finalIntent)
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Map, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Google Maps")
        }
    }
}
