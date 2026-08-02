package com.uplb.punla.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.uplb.punla.data.FontChoice
import com.uplb.punla.data.ThemePreset

/**
 * Exposes the currently-resolved [PunlaPalette] to any composable under
 * [PunlaTheme] — not just the derived [androidx.compose.material3.ColorScheme]
 * roles. A handful of spots (the topbar's fixed "notebook cover" ink
 * gradient, card shadow tint) intentionally use raw palette roles rather
 * than roles that flip with light/dark mode, and previously did so by
 * importing hardcoded `Color.kt` constants directly — which meant they never
 * reacted to a preset or custom accent color change. Reading from this
 * CompositionLocal instead keeps them theme-aware without swapping with
 * light/dark mode.
 */
val LocalPunlaPalette = staticCompositionLocalOf { Palettes.FieldNotebook }

/** Lightens a color toward white — used to derive dark-mode-appropriate
 * variants of roles that only have a single (light-mode) value in
 * [PunlaPalette], the same way `leafLight` is a pre-picked lighter sibling
 * of `leaf`. */
private fun Color.lightenedForDark(fraction: Float = 0.45f): Color = lerp(this, Color.White, fraction)

private fun lightColorsFor(p: PunlaPalette) = lightColorScheme(
    primary = p.leaf,
    onPrimary = p.onPrimaryLight,
    primaryContainer = p.leafBgLight,
    onPrimaryContainer = p.inkSoft,

    secondary = p.maroon,
    onSecondary = p.onSecondaryLight,
    secondaryContainer = p.maroonBgLight,
    onSecondaryContainer = p.maroon,

    tertiary = p.mango,
    onTertiary = p.onTertiaryLight,
    tertiaryContainer = p.mangoBgLight,
    onTertiaryContainer = p.bark,

    background = p.paper,
    onBackground = p.ink,
    surface = p.cardLight,
    onSurface = p.ink,
    surfaceVariant = p.cardLight,
    onSurfaceVariant = p.bark,

    outline = p.lineLight,
    outlineVariant = p.lineLight,
    error = p.danger,
    onError = p.paper,
)

private fun darkColorsFor(p: PunlaPalette) = darkColorScheme(
    primary = p.leafLight,
    onPrimary = p.onPrimaryDark,
    primaryContainer = p.leafBgDark,
    onPrimaryContainer = p.textDark,

    secondary = p.secondaryDark,
    onSecondary = p.onSecondaryDark,
    secondaryContainer = p.maroonBgDark,
    onSecondaryContainer = p.textDark,

    tertiary = p.tertiaryDark,
    onTertiary = p.onTertiaryDark,
    tertiaryContainer = p.mangoBgDark,
    onTertiaryContainer = p.textDark,

    background = p.darkBg,
    onBackground = p.textDark,
    surface = p.cardDark,
    onSurface = p.textDark,
    surfaceVariant = p.cardDark,
    onSurfaceVariant = p.barkDark,

    outline = p.lineDark,
    outlineVariant = p.lineDark,
    error = p.danger.lightenedForDark(),
    onError = p.darkBg,
)

@Composable
fun PunlaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    preset: ThemePreset = ThemePreset.FIELD_NOTEBOOK,
    customSeedArgb: Int? = null,
    fontChoice: FontChoice = FontChoice.DEFAULT,
    content: @Composable () -> Unit
) {
    val palette = resolvePalette(preset, customSeedArgb)
    CompositionLocalProvider(LocalPunlaPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColorsFor(palette) else lightColorsFor(palette),
            typography = punlaTypography(fontChoice),
            shapes = PunlaShapes,
            content = content
        )
    }
}
