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
import androidx.compose.ui.graphics.luminance
import com.uplb.punla.data.FontChoice
import com.uplb.punla.data.ThemePreset

/** Exposes the resolved Punla roles to backgrounds, shadows, and widgets. */
val LocalPunlaPalette = staticCompositionLocalOf { Palettes.FieldNotebook }

private fun Color.lightenedForDark(fraction: Float = 0.45f): Color =
    lerp(this, Color.White, fraction)

/**
 * Accent colors in the three pastel themes are intentionally soft. Choosing
 * the label color from luminance keeps buttons, chips, and icons readable
 * without darkening the collection's actual palette.
 */
private fun readableOn(accent: Color, lightText: Color, darkText: Color): Color {
    fun contrast(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
    return if (contrast(accent, lightText) >= contrast(accent, darkText)) lightText else darkText
}

private fun lightColorsFor(p: PunlaPalette) = lightColorScheme(
    primary = p.leaf,
    onPrimary = readableOn(p.leaf, p.paper, p.ink),
    primaryContainer = p.leafBgLight,
    onPrimaryContainer = p.ink,

    secondary = p.maroon,
    onSecondary = readableOn(p.maroon, p.paper, p.ink),
    secondaryContainer = p.maroonBgLight,
    onSecondaryContainer = p.ink,

    tertiary = p.mango,
    onTertiary = readableOn(p.mango, p.paper, p.ink),
    tertiaryContainer = p.mangoBgLight,
    onTertiaryContainer = p.ink,

    background = p.paper,
    onBackground = p.ink,
    surface = p.paper,
    onSurface = p.ink,
    surfaceVariant = p.cardLight,
    onSurfaceVariant = p.bark,

    outline = p.lineLight,
    outlineVariant = p.lineLight,
    error = p.danger,
    onError = readableOn(p.danger, p.paper, p.ink),
)

private fun darkColorsFor(p: PunlaPalette) = darkColorScheme(
    primary = p.leafLight,
    onPrimary = readableOn(p.leafLight, p.textDark, p.darkBg),
    primaryContainer = p.leafBgDark,
    onPrimaryContainer = readableOn(p.leafBgDark, p.textDark, p.darkBg),

    secondary = p.maroonDark,
    onSecondary = readableOn(p.maroonDark, p.textDark, p.darkBg),
    secondaryContainer = p.maroonBgDark,
    onSecondaryContainer = readableOn(p.maroonBgDark, p.textDark, p.darkBg),

    tertiary = p.mangoDark,
    onTertiary = readableOn(p.mangoDark, p.textDark, p.darkBg),
    tertiaryContainer = p.mangoBgDark,
    onTertiaryContainer = readableOn(p.mangoBgDark, p.textDark, p.darkBg),

    background = p.darkBg,
    onBackground = p.textDark,
    surface = p.darkBg,
    onSurface = p.textDark,
    surfaceVariant = p.cardDark,
    onSurfaceVariant = p.barkDark,

    outline = p.lineDark,
    outlineVariant = p.lineDark,
    error = p.danger.lightenedForDark(),
    onError = readableOn(p.danger.lightenedForDark(), p.textDark, p.darkBg),
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
