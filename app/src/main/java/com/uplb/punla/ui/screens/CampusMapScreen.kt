package com.uplb.punla.ui.screens

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uplb.punla.data.Building
import com.uplb.punla.data.CampusDirectory
import com.uplb.punla.data.LocationFailure
import com.uplb.punla.data.RoutePlan
import com.uplb.punla.data.RoutePlanLeg
import com.uplb.punla.data.RouteStop
import com.uplb.punla.data.fetchOneShotLocation
import com.uplb.punla.data.fetchWalkingMatrix
import com.uplb.punla.data.fetchWalkingRoute
import com.uplb.punla.data.fmtDistance
import com.uplb.punla.data.hasLocationPermission
import com.uplb.punla.data.hasFineLocationPermission
import com.uplb.punla.data.haversineMeters
import com.uplb.punla.data.openAppLocationSettings
import com.uplb.punla.data.optimizeStopOrder
import com.uplb.punla.data.optimizeStopOrderReal
import com.uplb.punla.data.shouldShowLocationRationale
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ui.theme.PunlaDisplay
import com.uplb.punla.ui.theme.LocalPunlaPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusMapScreen(vm: PunlaViewModel, initialSearch: String = "", onOpenFullMap: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf(initialSearch) }
    var expandedBuildingName by remember { mutableStateOf<String?>(null) }
    var mapBuilding by remember { mutableStateOf<Building?>(null) }

    // ---- Multi-stop route planning ----
    var selectedStops by remember { mutableStateOf<Set<String>>(emptySet()) }
    var planningRoute by remember { mutableStateOf(false) }

    // ---- Locate-me / nearest-building state ----
    // This used to hand-roll its own fused-location-client call instead of
    // the shared `fetchOneShotLocation()` in LocationUtils.kt (which every
    // other GPS-using screen calls) — same behavior, just duplicated, and
    // checking only ACCESS_COARSE_LOCATION rather than either permission.
    // Now uses the shared helper directly, which also brings the timeout,
    // last-known-location fast path, and distinguishable error reasons the
    // old inline version didn't have.
    var userLoc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locating by remember { mutableStateOf(false) }
    var locateFailure by remember { mutableStateOf<LocationFailure?>(null) }

    fun fetchLocation() {
        locating = true
        locateFailure = null
        fetchOneShotLocation(
            context,
            onResult = { lat, lon, _ -> userLoc = lat to lon; locating = false },
            onError = { reason -> locating = false; locateFailure = reason }
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it } || hasLocationPermission(context)) fetchLocation() else {
            locating = false
            locateFailure = LocationFailure.PERMISSION_DENIED
        }
    }

    val permanentlyDenied = locateFailure == LocationFailure.PERMISSION_DENIED &&
        !shouldShowLocationRationale(context)

    fun onLocateMeTapped() {
        when {
            hasLocationPermission(context) -> fetchLocation()
            permanentlyDenied -> openAppLocationSettings(context)
            else -> locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    /**
     * Turns the current [selectedStops] into an ordered [RoutePlan] and hands
     * it to the shared ViewModel state for `CampusFullMapScreen` to render.
     *
     * Ordering prefers real walking *time* (`optimizeStopOrderReal`), fetched
     * as a single OSRM Table-service call covering every pair at once
     * (`fetchWalkingMatrix`) — this is what correctly orders stops on
     * opposite sides of the river, where straight-line distance would ignore
     * that Narra Bridge is the only crossing. If that call fails for any
     * reason (no connectivity, demo server down/rate-limited), this falls
     * back to the cheap straight-line ordering (`optimizeStopOrder`) rather
     * than blocking route planning on it.
     *
     * Either way, only once an order is decided does this fetch a real
     * walking route for each of the (stops.size) sequential legs — not every
     * pair. Fetches run one at a time (not in parallel) as a courtesy to the
     * (free, rate-limited) routing server, so this can take a few seconds
     * for a longer stop list — `planningRoute` drives a loading state in the
     * UI for that reason.
     */
    fun planRoute() {
        val loc = userLoc
        if (loc == null) {
            onLocateMeTapped()
            return
        }
        val stops = CampusDirectory.BUILDINGS
            .filter { it.name in selectedStops }
            .map { RouteStop(it.name, it.lat, it.lon) }
        if (stops.isEmpty()) return

        planningRoute = true
        scope.launch {
            val matrixPoints = listOf(loc) + stops.map { it.lat to it.lon }
            val matrix = fetchWalkingMatrix(matrixPoints)
            val ordered = if (matrix != null && matrix.durationsSeconds.size == matrixPoints.size) {
                optimizeStopOrderReal(stops, matrix.durationsSeconds)
            } else {
                Log.w("CampusMapScreen", "Real walking-time matrix unavailable, falling back to straight-line stop ordering")
                optimizeStopOrder(loc, stops)
            }
            val legs = mutableListOf<RoutePlanLeg>()
            var fromPoint: Pair<Double, Double> = loc
            for (stop in ordered) {
                val route = fetchWalkingRoute(fromPoint, stop.lat to stop.lon)
                legs.add(RoutePlanLeg(fromPoint, stop, route))
                fromPoint = stop.lat to stop.lon
            }
            vm.setRoutePlan(RoutePlan(legs))
            planningRoute = false
            onOpenFullMap()
        }
    }

    val filteredBuildings = remember(searchQuery, userLoc) {
        val base = if (searchQuery.isBlank()) {
            CampusDirectory.BUILDINGS
        } else {
            val query = searchQuery.lowercase().trim()
            CampusDirectory.BUILDINGS.filter { b ->
                b.name.lowercase().contains(query) ||
                        (b.aka != null && b.aka.lowercase().contains(query)) ||
                        b.rooms.any { r -> r.lowercase().contains(query) }
            }
        }
        val loc = userLoc
        if (loc != null) {
            base.sortedBy { haversineMeters(loc.first, loc.second, it.lat, it.lon) }
        } else {
            base
        }
    }

    LaunchedEffect(initialSearch) {
        if (initialSearch.isNotBlank()) {
            searchQuery = initialSearch
            val matches = CampusDirectory.BUILDINGS.filter { b ->
                b.name.lowercase().contains(initialSearch.lowercase()) ||
                        (b.aka != null && b.aka.lowercase().contains(initialSearch.lowercase())) ||
                        b.rooms.any { r -> r.lowercase().contains(initialSearch.lowercase()) }
            }
            if (matches.size == 1) {
                expandedBuildingName = matches.first().name
            }
        }
    }

    val screenGutter = punlaScreenHorizontalPadding(maxContentWidth = 900.dp)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = screenGutter, vertical = 12.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by building name, code, or room...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            if (userLoc == null) {
                FilledTonalIconButton(
                    onClick = { onLocateMeTapped() },
                    enabled = !locating
                ) {
                    if (locating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.MyLocation, contentDescription = "Locate me")
                    }
                }
            } else {
                FilledTonalIconButton(onClick = { userLoc = null; locateFailure = null }) {
                    Icon(Icons.Default.LocationOff, contentDescription = "Clear location")
                }
            }
        }
        if (userLoc != null && !hasFineLocationPermission(context)) {
            Spacer(Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Using approximate location. Precise location improves walking distance and building sorting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Text("Enable precise") }
                }
            }
        }
        if (locateFailure != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        permanentlyDenied -> "Location permission is off for this app."
                        locateFailure == LocationFailure.PERMISSION_DENIED -> "Location permission needed to find nearby buildings."
                        locateFailure == LocationFailure.TIMEOUT -> "No GPS fix yet — try again outdoors or near a window."
                        else -> "Couldn't get your location."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                if (permanentlyDenied) {
                    TextButton(onClick = { openAppLocationSettings(context) }) {
                        Text("Settings", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onOpenFullMap,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Map, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open full campus map")
        }
        Spacer(Modifier.height(10.dp))

        if (selectedStops.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selectedStops.size} stop${if (selectedStops.size == 1) "" else "s"} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { selectedStops = emptySet() }, enabled = !planningRoute) {
                        Text("Clear")
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = { planRoute() },
                        enabled = !planningRoute,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (planningRoute) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(if (userLoc == null) "Locate me first" else "Plan route")
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (filteredBuildings.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Info,
                message = "No matching buildings or rooms found."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredBuildings, key = { it.name }) { b ->
                    val isExpanded = b.name == expandedBuildingName
                    val distanceLabel = userLoc?.let { loc ->
                        fmtDistance(haversineMeters(loc.first, loc.second, b.lat, b.lon))
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f))
                            .clickable {
                                expandedBuildingName = if (isExpanded) null else b.name
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        b.name,
                                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = PunlaDisplay, fontSize = 16.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (b.aka != null) {
                                        Text(
                                            b.aka,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (distanceLabel != null) {
                                    Tag(
                                        "$distanceLabel away",
                                        container = MaterialTheme.colorScheme.secondaryContainer,
                                        onContainer = MaterialTheme.colorScheme.secondary,
                                        mono = true
                                    )
                                }
                                val isStop = b.name in selectedStops
                                IconButton(
                                    onClick = {
                                        selectedStops = if (isStop) selectedStops - b.name else selectedStops + b.name
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isStop) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                        contentDescription = if (isStop) "Remove from route" else "Add to route",
                                        tint = if (isStop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (isExpanded) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(8.dp))

                                Text("DIRECTIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                Text(b.directions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

                                Spacer(Modifier.height(8.dp))
                                Text("ROOM CODES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))

                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    b.rooms.forEach { room ->
                                        Tag(
                                            room,
                                            container = MaterialTheme.colorScheme.primaryContainer,
                                            onContainer = MaterialTheme.colorScheme.primary,
                                            mono = true
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { mapBuilding = b },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("View on map")
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    val activeBuilding = mapBuilding
    if (activeBuilding != null) {
        ModalBottomSheet(onDismissRequest = { mapBuilding = null }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        activeBuilding.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = PunlaDisplay, fontSize = 16.sp),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { mapBuilding = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(8.dp))
                CampusMapView(
                    building = activeBuilding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                )
                Spacer(Modifier.height(12.dp))
                DirectionsButtonsRow(building = activeBuilding)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
