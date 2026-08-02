package com.uplb.punla.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import com.uplb.punla.data.BackgroundStyle

/**
 * Widget-side entry point: given a style, a size, and a resolved palette,
 * produce a single frozen-frame [Bitmap] — Glance's `provideGlance` has no
 * live `DrawScope` to animate into, so this renders one frame of whatever
 * [paintStarfield]/[paintAmbientWash] would otherwise draw continuously.
 */
fun renderBackgroundBitmap(
    style: BackgroundStyle,
    widthPx: Int,
    heightPx: Int,
    palette: PunlaPalette,
    isDark: Boolean,
): Bitmap? {
    if (style == BackgroundStyle.MINIMAL || widthPx <= 0 || heightPx <= 0) {
        return null // flat color — no bitmap needed, widget falls back to a ColorProvider fill
    }
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val base = (if (isDark) palette.darkBg else palette.paper).toArgb()
    when (style) {
        BackgroundStyle.STARFIELD -> paintStarfield(
            canvas, widthPx.toFloat(), heightPx.toFloat(),
            t = FIXED_WIDGET_PHASE,
            starColorArgb = (if (isDark) palette.textDark else palette.ink).toArgb(),
            accentColorArgb = (if (isDark) palette.tertiaryDark else palette.mango).toArgb(),
            baseColorArgb = base,
        )
        BackgroundStyle.AMBIENT -> paintAmbientWash(canvas, widthPx.toFloat(), heightPx.toFloat(), palette, isDark)
        BackgroundStyle.PAPER_GRAIN -> paintPaperGrain(canvas, widthPx.toFloat(), heightPx.toFloat(), palette, isDark)
        BackgroundStyle.RAIN -> paintRain(
            canvas, widthPx.toFloat(), heightPx.toFloat(),
            tSeconds = FIXED_RAIN_PHASE, palette = palette, isDark = isDark,
        )
        BackgroundStyle.MINIMAL -> Unit // unreachable, guarded above
    }
    return bitmap
}

/** Freezes the starfield twinkle at one arbitrary phase — an actual
 * mid-cycle value, not 0, so no star reads as fully "off" in the frozen
 * frame. Doesn't need to mean anything beyond "a phase where the field
 * looks populated." [paintAmbientWash] has its own equivalent default. */
private const val FIXED_WIDGET_PHASE = 2.1f

/** Same idea as [FIXED_WIDGET_PHASE], for [paintRain]'s `tSeconds` — an
 * arbitrary "seconds elapsed" value chosen so the frozen frame shows drops
 * spread across the full fall path rather than all bunched near the top. */
private const val FIXED_RAIN_PHASE = 3.7f
