package com.uplb.punla.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import com.uplb.punla.data.BackgroundStyle
import com.uplb.punla.data.ThemePreset
import com.uplb.punla.data.resolveForTheme
import kotlin.math.PI

/**
 * Single rendering dispatcher shared by the live Compose surface, Settings
 * previews, and frozen home-screen widget frames.
 */
fun paintBackgroundFrame(
    canvas: Canvas,
    style: BackgroundStyle,
    widthPx: Float,
    heightPx: Float,
    palette: PunlaPalette,
    isDark: Boolean,
    themePreset: ThemePreset = ThemePreset.FIELD_NOTEBOOK,
    tSeconds: Float = FIXED_WIDGET_PHASE_SECONDS,
) {
    val resolved = style.resolveForTheme(themePreset)
    when (resolved) {
        BackgroundStyle.MINIMAL -> canvas.drawColor(
            (if (isDark) palette.darkBg else palette.paper).toArgb()
        )

        BackgroundStyle.AMBIENT -> paintAmbientWash(
            canvas = canvas,
            widthPx = widthPx,
            heightPx = heightPx,
            palette = palette,
            isDark = isDark,
            t = tSeconds * ((2f * PI.toFloat()) / AMBIENT_LOOP_SECONDS),
        )

        BackgroundStyle.STARFIELD -> paintStarfield(
            canvas = canvas,
            widthPx = widthPx,
            heightPx = heightPx,
            t = tSeconds,
            starColorArgb = (if (isDark) palette.textDark else palette.ink).toArgb(),
            accentColorArgb = (if (isDark) palette.mangoDark else palette.mango).toArgb(),
            baseColorArgb = (if (isDark) palette.darkBg else palette.paper).toArgb(),
        )

        BackgroundStyle.PAPER_GRAIN -> paintPaperGrain(
            canvas, widthPx, heightPx, palette, isDark
        )

        BackgroundStyle.RAIN -> paintRain(
            canvas, widthPx, heightPx, tSeconds, palette, isDark
        )

        BackgroundStyle.AURORA -> paintAurora(
            canvas, widthPx, heightPx, tSeconds, palette, isDark
        )

        BackgroundStyle.OCEAN_WAVES -> paintOceanWaves(
            canvas, widthPx, heightPx, tSeconds, palette, isDark
        )

        BackgroundStyle.FIREFLIES -> paintFireflies(
            canvas, widthPx, heightPx, tSeconds, palette, isDark
        )

        BackgroundStyle.SAKURA -> paintSakura(
            canvas, widthPx, heightPx, tSeconds, palette, isDark
        )

        BackgroundStyle.SNOW -> paintSnow(
            canvas, widthPx, heightPx, tSeconds, palette, isDark
        )

        BackgroundStyle.BUBBLES -> paintBubbles(
            canvas, widthPx, heightPx, tSeconds, palette, isDark
        )

        BackgroundStyle.THEME_MATCHED -> error("THEME_MATCHED must resolve before rendering")
    }
}

/**
 * Widget-side entry point. Glance cannot host a live Compose Canvas, so each
 * animated style is represented by one deliberately populated frozen frame.
 */
fun renderBackgroundBitmap(
    style: BackgroundStyle,
    widthPx: Int,
    heightPx: Int,
    palette: PunlaPalette,
    isDark: Boolean,
    themePreset: ThemePreset = ThemePreset.FIELD_NOTEBOOK,
): Bitmap? {
    val resolved = style.resolveForTheme(themePreset)
    if (resolved == BackgroundStyle.MINIMAL || widthPx <= 0 || heightPx <= 0) {
        return null
    }

    return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also { bitmap ->
        paintBackgroundFrame(
            canvas = Canvas(bitmap),
            style = resolved,
            widthPx = widthPx.toFloat(),
            heightPx = heightPx.toFloat(),
            palette = palette,
            isDark = isDark,
            themePreset = themePreset,
            tSeconds = FIXED_WIDGET_PHASE_SECONDS,
        )
    }
}

private const val AMBIENT_LOOP_SECONDS = 34f
private const val FIXED_WIDGET_PHASE_SECONDS = 7.3f
