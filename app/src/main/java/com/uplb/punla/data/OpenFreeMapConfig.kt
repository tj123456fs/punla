package com.uplb.punla.data

import kotlin.math.roundToInt

/**
 * OpenFreeMap (https://openfreemap.org) is a free, API-key-less vector tile
 * host built on OpenStreetMap data. Unlike the old osmdroid/MAPNIK setup, it
 * only serves *vector* tiles, so it's rendered through MapLibre Native
 * (see CampusMapView.kt / CampusFullMapScreen.kt) rather than a raster XYZ
 * URL. No account, key, or billing setup is required either way.
 */
object OpenFreeMap {
    /** "Liberty" is OpenFreeMap's full-detail default style — closest match
     * to how much detail the old MAPNIK raster tiles showed. Other options
     * are "positron" (minimal/clean) and "bright" (high-contrast); swap the
     * path segment below to try them. */
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
}

/**
 * Rough walking-pace ETA, in whole minutes, for a given straight-line
 * distance. Uses ~75 m/min (4.5 km/h) — a touch slower than open-road pace
 * to account for campus crossings, stairs, and building entrances. Always
 * rounds up to at least 1 minute so "you're basically there" doesn't
 * confusingly read as "0 min".
 */
fun walkingEtaMinutes(meters: Double): Int = maxOf(1, (meters / 75.0).roundToInt())
