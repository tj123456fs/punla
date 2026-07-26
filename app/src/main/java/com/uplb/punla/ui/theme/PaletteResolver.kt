package com.uplb.punla.ui.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamicColorScheme
import com.uplb.punla.data.ThemePreset

/**
 * Resolves a [ThemePreset] (+ optional custom seed color) down to a concrete
 * [PunlaPalette]. Pure Kotlin, no Context/ViewModel dependency, so it's
 * callable from both `Theme.kt` (Compose) and — once step 5 lands — the
 * three Glance widgets, without either side duplicating this logic.
 */
fun resolvePalette(preset: ThemePreset, customSeedArgb: Int?): PunlaPalette = when (preset) {
    ThemePreset.FIELD_NOTEBOOK -> Palettes.FieldNotebook
    ThemePreset.OCEAN -> Palettes.Ocean
    ThemePreset.SUNSET -> Palettes.Sunset
    ThemePreset.ORCHID -> Palettes.Orchid
    ThemePreset.SLATE -> Palettes.Slate
    ThemePreset.CUSTOM -> customSeedArgb?.let { buildFromSeed(it) } ?: Palettes.FieldNotebook
}

/**
 * Derives a full [PunlaPalette] from a single seed color using materialkolor's
 * HCT-based scheme generator, which produces a contrast-safe light+dark
 * Material 3 [androidx.compose.material3.ColorScheme] pair. Each Punla color
 * *role* is then mapped onto the closest matching Material role.
 *
 * `catSupplies`/`catOrg` are category-only accents that don't correspond to
 * any single Material role (Field Notebook hand-picks a blue and an amber
 * for them independent of the core leaf/maroon/mango hues) — deriving them
 * from a single seed would just be guessing, so they're kept fixed across
 * all custom seed colors rather than invented from nothing.
 */
private fun buildFromSeed(seedArgb: Int): PunlaPalette {
    val seed = Color(seedArgb)
    val light = dynamicColorScheme(seedColor = seed, isDark = false, isAmoled = false)
    val dark = dynamicColorScheme(seedColor = seed, isDark = true, isAmoled = false)

    return PunlaPalette(
        // Light
        paper = light.background,
        ink = light.onBackground,
        inkSoft = light.onSurfaceVariant,
        cardLight = light.surfaceVariant,
        lineLight = light.outlineVariant,
        leaf = light.primary,
        leafLight = dark.primary,
        leafBgLight = light.primaryContainer,
        maroon = light.secondary,
        maroonBgLight = light.secondaryContainer,
        mango = light.tertiary,
        mangoBgLight = light.tertiaryContainer,
        bark = light.onSurfaceVariant,
        danger = light.error,
        // Dark
        darkBg = dark.background,
        cardDark = dark.surfaceVariant,
        textDark = dark.onBackground,
        lineDark = dark.outlineVariant,
        barkDark = dark.onSurfaceVariant,
        leafBgDark = dark.primaryContainer,
        maroonBgDark = dark.secondaryContainer,
        mangoBgDark = dark.tertiaryContainer,
        // Theme-independent
        topbarSubtle = dark.onSurfaceVariant,
        tabInactive = dark.outline,
        shadowInk = light.onBackground,
        // Category accents — core three follow the seed, the other three
        // stay fixed (see kdoc above).
        catFood = light.primary,
        catTranspo = light.tertiary,
        catLoad = light.secondary,
        catSupplies = Palettes.FieldNotebook.catSupplies,
        catOrg = Palettes.FieldNotebook.catOrg,
        catMisc = light.onSurfaceVariant,
    )
}
