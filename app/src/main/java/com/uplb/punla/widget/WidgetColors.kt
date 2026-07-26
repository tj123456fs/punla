package com.uplb.punla.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.ui.theme.PunlaPalette
import com.uplb.punla.ui.theme.resolvePalette

/**
 * Every color role the three home-screen widgets need, resolved from the
 * person's actual [PunlaPalette] instead of the old hardcoded Field
 * Notebook literals (0xFF8FB35C, 0xFFB9C9BB, etc.) that used to be sprinkled
 * across each widget file.
 *
 * Widgets always render as a "dark" surface regardless of the phone's
 * day/night setting or in-app theme (same as before this fix — the old code
 * passed the same literal to both `day` and `night` in every ColorProvider).
 * So this intentionally mirrors the *dark* role mapping from
 * `Theme.kt#darkColorsFor`, just widget-flavored, rather than reacting to
 * light/dark mode itself.
 */
data class WidgetColors(
    val accent: Color,          // section labels, "+" chip, ongoing indicators
    val onAccent: Color,        // glyph/text drawn on top of an accent-filled chip
    val textPrimary: Color,     // headline text (deadline title, amount, class code)
    val textSecondary: Color,   // supporting text (due date, "of ₱X", room/time)
    val textMuted: Color,       // smallest/quietest text (weekday initials under bars)
    val backgroundFallback: Color, // Minimal-mode / bgImage == null fallback fill
    val urgent: Color,          // overdue/urgent red
    val barActive: Color,       // today's bar in the spending chart
    val barInactive: Color,     // other days' bars
    val indicatorInactive: Color, // non-ongoing class row indicator
)

private fun Color.lightenedForDark(fraction: Float = 0.45f): Color = lerp(this, Color.White, fraction)

fun resolveWidgetColors(palette: PunlaPalette): WidgetColors = WidgetColors(
    accent = palette.leafLight,
    onAccent = palette.darkBg,
    textPrimary = palette.textDark,
    textSecondary = palette.barkDark,
    textMuted = palette.barkDark.copy(alpha = 0.7f),
    backgroundFallback = palette.darkBg,
    urgent = palette.danger.lightenedForDark(),
    barActive = palette.leafLight,
    barInactive = palette.leafBgDark,
    indicatorInactive = palette.lineDark,
)

/** Convenience overload — resolves the palette from [repo] first. */
fun resolveWidgetColors(repo: PunlaRepository): WidgetColors =
    resolveWidgetColors(resolvePalette(repo.themePreset, repo.customSeedColor))
