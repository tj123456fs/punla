package com.uplb.punla.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "RoutingApi"

/**
 * A real walking route between two points: an ordered list of (lat, lon)
 * points to draw as a polyline (following actual paths/roads, not a straight
 * line), plus the route's real distance and duration.
 */
data class WalkingRoute(
    val points: List<Pair<Double, Double>>,
    val distanceMeters: Double,
    val durationSeconds: Double
)

/**
 * Real walking directions along actual paths/roads, via the public OSRM demo
 * server's "foot" profile (https://router.project-osrm.org) — genuinely free
 * and keyless, no account or dependency needed. It routes against
 * OpenStreetMap's own pedestrian path data, and UPLB's campus has decent
 * footway/road coverage in OSM (per OSM's own wiki page documenting the
 * university's mapping), so this should produce real, sensible routes rather
 * than the straight "as the crow flies" line the map used to draw.
 *
 * This is the app's first outbound network call it makes itself (everything
 * else — Firebase, map tiles — is a library doing its own networking
 * internally), so it's held to a higher bar for graceful failure: this
 * returns `null` on absolutely any problem (no connectivity, demo server
 * down/rate-limited, no route found, malformed response) rather than
 * throwing, so every caller can fall back to the existing straight-line
 * distance/indicator instead of leaving the user with nothing or a crash.
 *
 * Demo server usage note (from its own docs): "restricted to reasonable,
 * non-commercial use... do not exceed 1 request per second... no guarantees
 * wrt. uptime, latency, or data updates." Fine for how this app calls it —
 * on demand, one destination at a time, never in a tight loop — but worth
 * knowing if usage patterns change later. If this server ever becomes
 * unreliable in practice, the natural swap is to self-host the same OSRM
 * `foot.lua` profile, or move to a keyed alternative like OpenRouteService's
 * foot-walking profile — the [WalkingRoute] return shape here wouldn't need
 * to change, just this function's internals.
 */
suspend fun fetchWalkingRoute(from: Pair<Double, Double>, to: Pair<Double, Double>): WalkingRoute? =
    withContext(Dispatchers.IO) {
        try {
            val (fromLat, fromLon) = from
            val (toLat, toLon) = to
            val url = URL(
                "https://router.project-osrm.org/route/v1/foot/" +
                    "$fromLon,$fromLat;$toLon,$toLat" +
                    "?overview=full&geometries=geojson"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
            }

            val body = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                // Every branch below falls back to a straight line on
                // purpose (see the doc comment above), but that made a
                // genuine problem indistinguishable from "hasn't fetched
                // yet" — nothing here or at any call site ever surfaced
                // *why* a route stayed straight. These four log lines don't
                // change behavior, just make the reason checkable in
                // Logcat (filter on tag "RoutingApi") instead of guessing.
                Log.w(TAG, "OSRM request failed: HTTP ${connection.responseCode}")
                null
            }
            connection.disconnect()
            if (body == null) return@withContext null

            val json = JSONObject(body)
            val code = json.optString("code")
            if (code != "Ok") {
                // A 200 response with a non-"Ok" code is OSRM's own way of
                // saying no route exists between these exact points (e.g.
                // "NoRoute" — the coordinates didn't snap to a connected
                // footway in OSM) — a genuinely different case from a
                // network/server failure above, worth telling apart in logs.
                Log.w(TAG, "OSRM returned code=\"$code\" for $from -> $to")
                return@withContext null
            }
            val routes = json.optJSONArray("routes") ?: return@withContext null
            if (routes.length() == 0) return@withContext null

            val route = routes.getJSONObject(0)
            val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
            val points = (0 until coords.length()).map { i ->
                val pair = coords.getJSONArray(i)
                // GeoJSON orders coordinates as [lon, lat] — flipped to this
                // app's (lat, lon) convention (matches Building.lat/lon,
                // haversineMeters, etc.) right here so nothing downstream has
                // to remember which order this one API uses.
                val lon = pair.getDouble(0)
                val lat = pair.getDouble(1)
                lat to lon
            }

            WalkingRoute(
                points = points,
                distanceMeters = route.getDouble("distance"),
                durationSeconds = route.getDouble("duration")
            )
        } catch (e: Exception) {
            Log.w(TAG, "OSRM request threw for $from -> $to", e)
            null
        }
    }
