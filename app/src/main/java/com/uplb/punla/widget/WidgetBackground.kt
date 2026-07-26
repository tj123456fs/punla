package com.uplb.punla.widget

import android.content.Context
import androidx.glance.ImageProvider
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.ui.theme.PunlaPalette
import com.uplb.punla.ui.theme.renderBackgroundBitmap
import com.uplb.punla.ui.theme.resolvePalette

/**
 * Call this once per `provideGlance`, sized to the actual widget bounds.
 * Takes an already-resolved [palette] so callers that also need
 * [resolveWidgetColors] (i.e. all three widgets, as of the palette-parity
 * fix) resolve the palette exactly once and share it, rather than each
 * color path calling `resolvePalette()` on its own.
 */
fun widgetBackgroundImageProvider(
    context: Context,
    repo: PunlaRepository,
    palette: PunlaPalette,
    widthPx: Int,
    heightPx: Int,
): ImageProvider? {
    // Same pure function Theme.kt already calls — not a second
    // palette-resolution path.
    val isDark = repo.isDarkModeActive(context)
    val bitmap = renderBackgroundBitmap(repo.backgroundStyle, widthPx, heightPx, palette, isDark)
        ?: return null
    return ImageProvider(bitmap)
}

/** Back-compat overload for any caller that hasn't been updated to share a
 * pre-resolved palette yet — resolves it internally instead. */
fun widgetBackgroundImageProvider(
    context: Context,
    repo: PunlaRepository,
    widthPx: Int,
    heightPx: Int,
): ImageProvider? = widgetBackgroundImageProvider(
    context, repo, resolvePalette(repo.themePreset, repo.customSeedColor), widthPx, heightPx
)
