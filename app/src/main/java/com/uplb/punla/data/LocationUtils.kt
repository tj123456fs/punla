package com.uplb.punla.data

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Port of the web app's haversineMeters(lat1, lon1, lat2, lon2). */
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0 // Earth radius, meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

/** Port of the web app's fmtDistance(): "180 m" below 1km, "1.2 km" at/above. */
fun fmtDistance(m: Double): String =
    if (m < 1000) "${m.roundToInt()} m" else "%.1f km".format(m / 1000)

/**
 * True if either coarse or fine location permission has been granted.
 *
 * Single canonical implementation — `CampusMapScreen.kt` used to hand-roll
 * its own inline check (COARSE only) and `CampusFullMapScreen.kt` kept a
 * private duplicate of this exact function (shadowing this one under the
 * same name). Both now call this one directly; the two copies are gone.
 */
fun hasFineLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun hasCoarseLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun hasLocationPermission(context: Context): Boolean =
    hasFineLocationPermission(context) || hasCoarseLocationPermission(context)

/**
 * Unwraps a Compose `Context` (which may be a `ContextThemeWrapper` etc.)
 * down to the underlying `Activity`, if there is one. Needed for the
 * rationale check below, which is an `Activity`-scoped API.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * True if the system would still show its own "why do you need this"
 * permission rationale on the next request — i.e. permission has been
 * denied at most once so far. False once it's been denied a second time (or
 * denied with "Don't ask again"), at which point `ActivityResultContracts.
 * RequestPermission()` silently no-ops instead of showing a dialog, and the
 * only way forward is [openAppLocationSettings]. Returns false (treat as
 * "permanently denied," the safer default for UI purposes) if a real
 * `Activity` can't be found.
 */
fun shouldShowLocationRationale(context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) ||
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
}

/**
 * Deep-links into this app's page in system Settings. For when location
 * permission has been permanently denied ("Don't ask again" / denied twice)
 * — at that point `ActivityResultContracts.RequestPermission()` won't even
 * show the system dialog again, so a "Locate me" button that only ever
 * re-launches the permission request becomes a silent dead end. Screens that
 * show a [LocationFailure.PERMISSION_DENIED] message can offer this as the
 * button's action instead.
 */
fun openAppLocationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/** Why a location request came back empty — lets each screen show a message
 * that actually matches what went wrong, instead of one generic
 * "Couldn't get your location" for every case. */
enum class LocationFailure {
    /** Permission was never granted (or was revoked since). */
    PERMISSION_DENIED,
    /** Nothing arrived within the requested timeout — e.g. indoors with no
     * GPS visibility and no network location fallback available. */
    TIMEOUT,
    /** Play Services returned, but with no usable fix (null location, or a
     * failure from the underlying location provider). */
    NO_FIX
}

/** How old a cached "last known location" is allowed to be before it's
 * treated as fresh enough to show immediately. Anything older than this is
 * ignored rather than shown, since e.g. a week-old fix from a different city
 * would actively mislead a "how far is it" reading. */
private const val LAST_KNOWN_MAX_AGE_MILLIS = 5 * 60 * 1000L // 5 minutes

/**
 * One-shot GPS fix via Play Services' fused provider, shared by the Campus
 * Map "locate me" flow, the full campus map, and the Dashboard's "distance
 * to next class" card. Caller is responsible for having already requested
 * permission if [hasLocationPermission] was false — this just reports
 * [LocationFailure.PERMISSION_DENIED] via [onError] if it's still missing.
 *
 * Two-phase result, both delivered through [onResult]:
 * 1. If Play Services already has a recent cached fix (< 5 minutes old), it's
 *    delivered immediately with `isLastKnown = true` so the UI has something
 *    to show right away instead of sitting on "Locating…".
 * 2. A fresh fix follows with `isLastKnown = false` once one arrives, or
 *    [onError] fires with [LocationFailure.TIMEOUT] if [timeoutMillis]
 *    passes with nothing back (this used to be able to spin forever with no
 *    feedback — e.g. inside a lecture hall with no GPS visibility).
 */
fun fetchOneShotLocation(
    context: Context,
    onResult: (lat: Double, lon: Double, isLastKnown: Boolean) -> Unit,
    onError: (reason: LocationFailure) -> Unit,
    timeoutMillis: Long = 15_000L
) {
    if (!hasLocationPermission(context)) {
        onError(LocationFailure.PERMISSION_DENIED)
        return
    }

    val client = LocationServices.getFusedLocationProviderClient(context)

    // Fast path: whatever's already cached, shown immediately (if recent
    // enough) while a fresh fix is still in flight below.
    client.lastLocation.addOnSuccessListener { last ->
        if (last != null) {
            val ageMillis = (SystemClock.elapsedRealtimeNanos() - last.elapsedRealtimeNanos) / 1_000_000
            if (ageMillis in 0..LAST_KNOWN_MAX_AGE_MILLIS) {
                onResult(last.latitude, last.longitude, true)
            }
        }
    }

    var resolved = false
    val cancelSource = CancellationTokenSource()
    val timeoutHandler = Handler(Looper.getMainLooper())
    val timeoutRunnable = Runnable {
        if (!resolved) {
            resolved = true
            cancelSource.cancel() // stop burning battery on a fix nothing's waiting for anymore
            onError(LocationFailure.TIMEOUT)
        }
    }
    timeoutHandler.postDelayed(timeoutRunnable, timeoutMillis)

    val priority = if (hasFineLocationPermission(context)) {
        Priority.PRIORITY_HIGH_ACCURACY
    } else {
        Priority.PRIORITY_BALANCED_POWER_ACCURACY
    }
    client.getCurrentLocation(priority, cancelSource.token)
        .addOnSuccessListener { location ->
            if (resolved) return@addOnSuccessListener
            resolved = true
            timeoutHandler.removeCallbacks(timeoutRunnable)
            if (location != null) {
                onResult(location.latitude, location.longitude, false)
            } else {
                onError(LocationFailure.NO_FIX)
            }
        }
        .addOnFailureListener {
            if (resolved) return@addOnFailureListener
            resolved = true
            timeoutHandler.removeCallbacks(timeoutRunnable)
            onError(LocationFailure.NO_FIX)
        }
}

/**
 * Continuous location updates for as long as [enabled] is true. Used by the
 * full campus map so the "distance/ETA to next class" numbers and the
 * straight-line indicator stay live while someone is actually walking,
 * instead of only refreshing on first load or a manual FAB tap.
 *
 * (MapLibre's on-map "you are here" puck was already continuous via its own
 * LocationComponent — this closes the gap so the *numbers* derived from
 * location match that same liveness, instead of going stale next to a puck
 * that's still visibly moving.)
 *
 * Deliberately not used on the compact Campus list or the Dashboard card —
 * both are glanced at rather than walked around with open, so a one-shot fix
 * refreshed on demand is the right tradeoff there; continuous GPS is
 * meaningfully more battery-hungry and isn't worth it for a screen someone
 * has open for a few seconds.
 *
 * Cleans up after itself: `removeLocationUpdates` runs in `onDispose`, so
 * updates stop the moment [enabled] flips false or the composable leaves
 * composition — no leaked GPS listener draining battery in the background.
 */
@Composable
fun rememberLiveLocation(
    enabled: Boolean,
    intervalMillis: Long = 5_000L
): State<Pair<Double, Double>?> {
    val context = LocalContext.current
    val locationState = remember { mutableStateOf<Pair<Double, Double>?>(null) }

    DisposableEffect(enabled) {
        if (!enabled || !hasLocationPermission(context)) {
            return@DisposableEffect onDispose {}
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(intervalMillis)
            .setPriority(
                if (hasFineLocationPermission(context)) Priority.PRIORITY_HIGH_ACCURACY
                else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            )
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                locationState.value = loc.latitude to loc.longitude
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        onDispose {
            client.removeLocationUpdates(callback)
        }
    }

    return locationState
}
