package com.uplb.punla.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.uplb.punla.data.Building
import com.uplb.punla.data.CampusDirectory
import com.uplb.punla.data.LocationFailure
import com.uplb.punla.data.OpenFreeMap
import com.uplb.punla.data.RoutePlan
import com.uplb.punla.data.WalkingRoute
import com.uplb.punla.data.fetchOneShotLocation
import com.uplb.punla.data.fetchWalkingRoute
import com.uplb.punla.data.fmtDistance
import com.uplb.punla.data.hasLocationPermission
import com.uplb.punla.data.hasFineLocationPermission
import com.uplb.punla.data.haversineMeters
import com.uplb.punla.data.openAppLocationSettings
import com.uplb.punla.data.rememberLiveLocation
import com.uplb.punla.data.shouldShowLocationRationale
import com.uplb.punla.data.walkingEtaMinutes
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ui.theme.PunlaDisplay
import kotlin.math.roundToInt
import com.uplb.punla.ui.theme.PunlaMono
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polygon
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rough centroid of the UPLB main campus cluster — used as the initial camera
 * position before a GPS fix (or if location permission is denied) and when
 * there's no upcoming class to focus on instead, so the map opens already
 * framed on campus instead of at (0, 0). UPRHS is a genuine outlier out in
 * Bay, Laguna (see CampusDirectory), so it's deliberately left out of this
 * framing — fitting bounds to include it would zoom out past the point where
 * the rest of campus is legible. It's still on the map, just pan.
 */
private val CAMPUS_CENTER = LatLng(14.1630, 121.2420)
private const val CAMPUS_ZOOM = 16.3
private const val FOCUS_ZOOM = 17.3

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

/** Points around [center] approximating a circle of [radiusMeters], used to
 * highlight the next class's building on the map without needing a custom
 * marker icon asset. */
private fun circlePoints(center: LatLng, radiusMeters: Double, steps: Int = 32): List<LatLng> {
    val earthRadius = 6371000.0
    val lat = Math.toRadians(center.latitude)
    val lon = Math.toRadians(center.longitude)
    val angularDistance = radiusMeters / earthRadius
    return (0..steps).map { i ->
        val bearing = 2 * Math.PI * i / steps
        val lat2 = asin(sin(lat) * cos(angularDistance) + cos(lat) * sin(angularDistance) * cos(bearing))
        val lon2 = lon + atan2(
            sin(bearing) * sin(angularDistance) * cos(lat),
            cos(angularDistance) - sin(lat) * sin(lat2)
        )
        LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusFullMapScreen(vm: PunlaViewModel) {
    val context = LocalContext.current
    var selectedBuilding by remember { mutableStateOf<Building?>(null) }
    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var hasFinePermission by remember { mutableStateOf(hasFineLocationPermission(context)) }
    var userLoc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locateFailure by remember { mutableStateOf<LocationFailure?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    val nextClass by vm.nextClassFlow.collectAsState(initial = null)
    val nextClassBuilding = remember(nextClass) {
        nextClass?.let { CampusDirectory.findBuildingForRoom(it.room) }
    }
    val routePlan by vm.routePlan.collectAsState()

    // ---- Real walking route to the next class (single-destination case) ----
    // Resets whenever the destination itself changes (a new `nextClassBuilding`
    // means any previously-fetched route is for the wrong building).
    var nextClassRoute by remember(nextClassBuilding) { mutableStateOf<WalkingRoute?>(null) }
    var lastRouteFetchLoc by remember(nextClassBuilding) { mutableStateOf<Pair<Double, Double>?>(null) }

    // Refetches only once the user has moved a meaningful distance since the
    // last fetch (or the destination changed) — not on every 5-second tick
    // from the new continuous-location tracking, which would otherwise
    // hammer the free OSRM demo server for a route that hasn't meaningfully
    // changed. A multi-stop `routePlan`, if active, takes over the route
    // entirely — see `displayRoutePoints` below — so this skips fetching
    // while one is in effect.
    LaunchedEffect(userLoc, nextClassBuilding, routePlan) {
        if (routePlan != null) return@LaunchedEffect
        val loc = userLoc
        val dest = nextClassBuilding
        if (loc == null || dest == null) {
            nextClassRoute = null
            lastRouteFetchLoc = null
            return@LaunchedEffect
        }
        val movedFarEnough = lastRouteFetchLoc?.let {
            haversineMeters(it.first, it.second, loc.first, loc.second) > 20.0
        } ?: true
        if (movedFarEnough) {
            lastRouteFetchLoc = loc
            // On failure, deliberately leave `nextClassRoute` as whatever it
            // was before — a still-roughly-right real route beats reverting
            // to a straight line over one transient network hiccup.
            fetchWalkingRoute(loc, dest.lat to dest.lon)?.let { nextClassRoute = it }
        }
    }

    // What the map actually draws: a multi-stop plan takes priority over the
    // single-destination case, which itself prefers a real fetched route
    // over the plain straight-line fallback. Every branch degrades
    // gracefully to *something* rather than nothing.
    val displayRoutePoints: List<Pair<Double, Double>>? = when {
        routePlan != null -> routePlan!!.legs.flatMap { leg ->
            leg.route?.points ?: listOf(leg.from, leg.stop.lat to leg.stop.lon)
        }
        nextClassRoute != null -> nextClassRoute!!.points
        userLoc != null && nextClassBuilding != null ->
            listOf(userLoc!!, nextClassBuilding.lat to nextClassBuilding.lon)
        else -> null
    }

    /**
     * [recenterCamera]: when true (an explicit "locate me" FAB tap), snaps
     * the camera to the fix the moment it arrives. This used to be done by
     * reading `userLoc` right after calling the old fire-and-forget
     * `requestFix()` — but that read happened synchronously, before the
     * async fix could possibly have arrived, so the very first tap never
     * recentered anything and every tap after that recentered one fix behind.
     * Doing it inside the result callback fixes that: it always recenters on
     * the fix that was actually just requested.
     */
    fun requestFix(recenterCamera: Boolean = false) {
        fetchOneShotLocation(
            context,
            onResult = { lat, lon, _ ->
                userLoc = lat to lon
                locateFailure = null
                if (recenterCamera) {
                    map?.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(lat, lon))
                        .zoom(17.5)
                        .build()
                }
            },
            onError = { reason -> locateFailure = reason }
        )
    }

    // Continuous updates once permission is granted — this is the piece that
    // keeps `userLoc` (and therefore the distance/ETA card and the route
    // polyline to the next class) live while someone is actually walking,
    // instead of only refreshing on the initial load or a manual FAB tap. `requestFix()` (above) still runs on initial
    // load/permission-grant/FAB-tap for an immediate answer; this just keeps
    // it fresh afterwards. The on-map "you are here" puck itself was always
    // continuous via MapLibre's own LocationComponent — this closes the gap
    // so the numbers next to it stay just as live. See `rememberLiveLocation`
    // in LocationUtils.kt for why this is scoped to this screen only.
    val liveLocation by rememberLiveLocation(enabled = hasPermission)
    LaunchedEffect(liveLocation) {
        liveLocation?.let { userLoc = it }
    }

    val permanentlyDenied = locateFailure == LocationFailure.PERMISSION_DENIED &&
        !shouldShowLocationRationale(context)

    fun enableLocationPuck(target: MapLibreMap) {
        val style = target.style ?: return
        val lc = target.locationComponent
        if (!lc.isLocationComponentActivated) {
            lc.activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
        }
        lc.isLocationComponentEnabled = true
        lc.renderMode = RenderMode.COMPASS
        lc.cameraMode = CameraMode.NONE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = results.values.any { it } || hasLocationPermission(context)
        hasFinePermission = hasFineLocationPermission(context)
        if (hasPermission) {
            map?.let { enableLocationPuck(it) }
            requestFix()
        } else {
            locateFailure = LocationFailure.PERMISSION_DENIED
        }
    }

    // Get an initial fix as soon as the map opens (if permission is already
    // granted) so the "distance to next class" line can be drawn without an
    // extra tap. Re-runs once `map` becomes non-null too, so a fix requested
    // before the map finished loading its style still gets acted on.
    LaunchedEffect(hasPermission, map) {
        if (hasPermission) {
            map?.let { enableLocationPuck(it) }
            requestFix()
        }
    }

    Box(Modifier.fillMaxSize()) {
        CampusFullMapView(
            hasLocationPermission = hasPermission,
            userLoc = userLoc,
            nextClassBuilding = nextClassBuilding,
            routePoints = displayRoutePoints,
            onMapReady = { map = it },
            onBuildingTap = { selectedBuilding = it },
            modifier = Modifier.fillMaxSize()
        )

        if (hasPermission && !hasFinePermission && locateFailure == null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .shadow(2.dp, MaterialTheme.shapes.medium),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Approximate location is active. Precise mode improves the walking route origin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { permissionLauncher.launch(LOCATION_PERMISSIONS) }) {
                        Text("Enable precise")
                    }
                }
            }
        }

        if (locateFailure != null && userLoc == null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .shadow(2.dp, MaterialTheme.shapes.medium),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when {
                            permanentlyDenied -> "Location permission is off for this app."
                            locateFailure == LocationFailure.PERMISSION_DENIED -> "Location permission needed to show you on the map."
                            locateFailure == LocationFailure.TIMEOUT -> "No GPS fix yet — try again outdoors or near a window."
                            else -> "Couldn't get your location."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    if (permanentlyDenied) {
                        TextButton(onClick = { openAppLocationSettings(context) }) {
                            Text("Settings", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                when {
                    hasPermission -> {
                        map?.let { enableLocationPuck(it) }
                        requestFix(recenterCamera = true)
                    }
                    permanentlyDenied -> openAppLocationSettings(context)
                    else -> permissionLauncher.launch(LOCATION_PERMISSIONS)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                if (hasPermission) Icons.Default.MyLocation else Icons.Default.LocationDisabled,
                contentDescription = "My location"
            )
        }

        val plan = routePlan
        if (plan != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(2.dp, MaterialTheme.shapes.medium),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Route · ${plan.legs.size} stop${if (plan.legs.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(onClick = { vm.setRoutePlan(null) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear route",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        "${fmtDistance(plan.totalDistanceMeters)} total · ~${(plan.totalDurationSeconds / 60.0).roundToInt()} min walk",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = PunlaMono),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    plan.legs.forEachIndexed { i, leg ->
                        Text(
                            "${i + 1}. ${leg.stop.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else if (nextClass != null && nextClassBuilding != null && userLoc != null) {
            // Prefer the real fetched route's distance/duration once one's
            // resolved; the straight-line haversine estimate is still the
            // instant fallback shown before that first fetch completes (or
            // if it never does — e.g. offline).
            val meters = nextClassRoute?.distanceMeters ?: remember(userLoc, nextClassBuilding) {
                haversineMeters(
                    userLoc!!.first, userLoc!!.second,
                    nextClassBuilding.lat, nextClassBuilding.lon
                )
            }
            val etaMinutes = nextClassRoute?.let { (it.durationSeconds / 60.0).roundToInt() }
                ?: walkingEtaMinutes(meters)
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(2.dp, MaterialTheme.shapes.medium),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.NearMe,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Next class: ${nextClass!!.code} at ${nextClassBuilding.name}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "${fmtDistance(meters)} away · ~$etaMinutes min walk" +
                                if (nextClassRoute == null) " (straight-line estimate)" else "",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PunlaMono),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }

    val building = selectedBuilding
    if (building != null) {
        ModalBottomSheet(onDismissRequest = { selectedBuilding = null }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        building.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = PunlaDisplay, fontSize = 16.sp),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { selectedBuilding = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    building.directions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                DirectionsButtonsRow(building = building)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * The MapLibre MapView itself: one marker per campus building (tap for
 * details), a highlighted ring + connecting line for the next scheduled
 * class's building when known, and a live "you are here" puck via
 * LocationComponent that keeps updating as the user physically walks
 * around campus.
 */
@Composable
private fun CampusFullMapView(
    hasLocationPermission: Boolean,
    userLoc: Pair<Double, Double>?,
    nextClassBuilding: Building?,
    routePoints: List<Pair<Double, Double>>?,
    onMapReady: (MapLibreMap) -> Unit,
    onBuildingTap: (Building) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewHolder = remember { arrayOfNulls<MapView>(1) }
    var readyMap by remember { mutableStateOf<MapLibreMap?>(null) }
    val highlightHolder = remember { arrayOfNulls<Polygon>(1) }
    val lineHolder = remember { arrayOfNulls<Polyline>(1) }

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            val mapView = MapView(ctx)
            mapViewHolder[0] = mapView
            mapView.onCreate(null)
            mapView.getMapAsync { map ->
                map.setStyle(OpenFreeMap.STYLE_URL) {
                    val focusTarget = nextClassBuilding?.let { LatLng(it.lat, it.lon) } ?: CAMPUS_CENTER
                    val focusZoom = if (nextClassBuilding != null) FOCUS_ZOOM else CAMPUS_ZOOM
                    map.cameraPosition = CameraPosition.Builder()
                        .target(focusTarget)
                        .zoom(focusZoom)
                        .build()

                    CampusDirectory.BUILDINGS.forEach { b ->
                        map.addMarker(
                            MarkerOptions()
                                .position(LatLng(b.lat, b.lon))
                                .title(b.name)
                        )
                    }
                    map.setOnMarkerClickListener { marker ->
                        val match = CampusDirectory.BUILDINGS.firstOrNull { it.name == marker.title }
                        if (match != null) {
                            onBuildingTap(match)
                            true
                        } else {
                            false
                        }
                    }

                    // Setting this (async, after style load) is what lets the
                    // `update` block below react once the map is truly ready
                    // — see the note on readyMap.
                    readyMap = map
                    onMapReady(map)
                }
            }
            mapView
        },
        update = {
            // Reading `readyMap`, `hasLocationPermission`, `userLoc`, and
            // `nextClassBuilding` here means this block re-runs both when the
            // map first becomes ready AND whenever any of those values
            // change afterwards — no manual "did it change" bookkeeping needed.
            val map = readyMap ?: return@AndroidView

            if (hasLocationPermission) {
                val style = map.style
                val lc = map.locationComponent
                if (style != null && !lc.isLocationComponentActivated) {
                    lc.activateLocationComponent(
                        LocationComponentActivationOptions.builder(mapViewHolder[0]!!.context, style).build()
                    )
                }
                lc.isLocationComponentEnabled = true
                lc.renderMode = RenderMode.COMPASS
                lc.cameraMode = CameraMode.NONE
            }

            // Highlight ring around the next class's building.
            highlightHolder[0]?.let { map.removePolygon(it) }
            highlightHolder[0] = nextClassBuilding?.let { b ->
                map.addPolygon(
                    PolygonOptions()
                        .add(*circlePoints(LatLng(b.lat, b.lon), 25.0).toTypedArray())
                        .fillColor(primaryColor)
                        .alpha(0.28f)
                )
            }

            // Route indicator: a real walking-route polyline when one's been
            // resolved (single destination or a multi-stop plan — see
            // `displayRoutePoints` in the parent composable), falling back to
            // a straight "as the crow flies" 2-point line when no real route
            // is available yet or the fetch failed. `routePoints` already
            // encodes which of those two cases applies; this block just
            // draws whatever it's given.
            lineHolder[0]?.let { map.removePolyline(it) }
            lineHolder[0] = if (routePoints != null && routePoints.size >= 2) {
                val options = PolylineOptions()
                routePoints.forEach { (lat, lon) -> options.add(LatLng(lat, lon)) }
                map.addPolyline(options.color(primaryColor).width(4f))
            } else null
        }
    )

    // MapLibre's MapView needs lifecycle events forwarded from the hosting
    // lifecycle (tile loading + GPS listener leaks otherwise — same gotcha
    // osmdroid had).
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
            readyMap?.locationComponent?.isLocationComponentEnabled = false
            mapViewHolder[0]?.onDestroy()
        }
    }
}
