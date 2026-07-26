package com.uplb.punla.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.uplb.punla.data.Building
import com.uplb.punla.data.OpenFreeMap
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

/**
 * Wraps a MapLibre Native MapView for Compose, centered on [building] with a
 * single marker. Uses OpenFreeMap's free "liberty" vector style — no API
 * key/billing account needed, same as the osmdroid setup this replaced.
 */
@Composable
fun CampusMapView(building: Building, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewHolder = remember { arrayOfNulls<MapView>(1) }

    AndroidView(
        modifier = modifier,
        factory = {
            MapLibre.getInstance(context)
            MapView(it).apply {
                mapViewHolder[0] = this
                onCreate(null)
                getMapAsync { map ->
                    map.setStyle(OpenFreeMap.STYLE_URL) {
                        val point = LatLng(building.lat, building.lon)
                        map.cameraPosition = CameraPosition.Builder()
                            .target(point)
                            .zoom(17.0)
                            .build()
                        map.clear()
                        map.addMarker(
                            MarkerOptions()
                                .position(point)
                                .title(building.name)
                        )
                    }
                }
            }
        }
    )

    // MapLibre's MapView, like osmdroid's, needs lifecycle events forwarded
    // from the hosting lifecycle or tile loading leaks.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapViewHolder[0]?.onStart()
                Lifecycle.Event.ON_RESUME -> mapViewHolder[0]?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewHolder[0]?.onPause()
                Lifecycle.Event.ON_STOP -> mapViewHolder[0]?.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewHolder[0]?.onDestroy()
        }
    }
}
