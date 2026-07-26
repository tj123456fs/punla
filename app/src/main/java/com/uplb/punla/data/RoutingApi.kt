package com.uplb.punla.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "RoutingApi"

data class WalkingRoute(
    val points: List<Pair<Double, Double>>,
    val distanceMeters: Double,
    val durationSeconds: Double
)

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
                // OSM/OSRM's infra returns 403 for requests without a proper,
                // descriptive User-Agent (their usage policy explicitly
                // requires one) — Java's default was getting blocked.
                setRequestProperty("User-Agent", "Punla-UPLB-App/1.0")
            }

            val body = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.w(TAG, "OSRM request failed: HTTP ${connection.responseCode}")
                null
            }
            connection.disconnect()
            if (body == null) return@withContext null

            val json = JSONObject(body)
            val code = json.optString("code")
            if (code != "Ok") {
                Log.w(TAG, "OSRM returned code=\"$code\" for $from -> $to")
                return@withContext null
            }
            val routes = json.optJSONArray("routes") ?: return@withContext null
            if (routes.length() == 0) return@withContext null

            val route = routes.getJSONObject(0)
            val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
            val points = (0 until coords.length()).map { i ->
                val pair = coords.getJSONArray(i)
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
