package com.uplb.punla.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.ColorInt

/**
 * Draws a simple filled-dot-with-white-ring marker bitmap at runtime, used
 * as the `iconImage` for MapLibre's [org.maplibre.android.plugins.annotation.SymbolManager]
 * annotations.
 *
 * The now-deprecated `MapLibreMap#addMarker(MarkerOptions)` API drew a
 * bundled default pin for free; the replacement `SymbolManager` API instead
 * requires the caller to supply an icon image, so this generates one in
 * code instead of adding a new drawable asset.
 */
fun buildMarkerBitmap(@ColorInt color: Int, diameterPx: Int = 72): Bitmap {
    val bitmap = Bitmap.createBitmap(diameterPx, diameterPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = diameterPx / 2f

    // White ring behind the colored dot keeps the marker legible over both
    // light and dark map tiles.
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE }
    canvas.drawCircle(radius, radius, radius, ringPaint)

    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    canvas.drawCircle(radius, radius, radius * 0.72f, dotPaint)

    return bitmap
}
