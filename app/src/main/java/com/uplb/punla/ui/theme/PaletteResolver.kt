package com.uplb.punla.ui.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamicColorScheme
import com.uplb.punla.data.ThemePreset

/** Resolves a saved preset (+ optional custom seed) to one concrete palette. */
fun resolvePalette(preset: ThemePreset, customSeedArgb: Int?): PunlaPalette = when (preset) {
    ThemePreset.FIELD_NOTEBOOK -> Palettes.FieldNotebook
    ThemePreset.AURORA_BOREALIS -> Palettes.AuroraBorealis
    ThemePreset.SUNSET_SKY -> Palettes.SunsetSky
    ThemePreset.OCEAN_DEPTHS -> Palettes.OceanDepths
    ThemePreset.FOREST_MIST -> Palettes.ForestMist
    ThemePreset.LAVENDER_NIGHT -> Palettes.LavenderNight
    ThemePreset.GOLDEN_DAWN -> Palettes.GoldenDawn
    ThemePreset.COFFEE_SHOP -> Palettes.CoffeeShop
    ThemePreset.LOFI_NIGHT -> Palettes.LofiNight
    ThemePreset.PAPER_INK -> Palettes.PaperInk
    ThemePreset.LIBRARY_MODE -> Palettes.LibraryMode
    ThemePreset.CYBER_NEON -> Palettes.CyberNeon
    ThemePreset.PASTEL_BLOOM -> Palettes.PastelBloom
    ThemePreset.FROST_GLASS -> Palettes.FrostGlass
    ThemePreset.GALAXY -> Palettes.Galaxy
    ThemePreset.CUSTOM -> customSeedArgb?.let { buildFromSeed(it) } ?: Palettes.FieldNotebook
}

/**
 * Derives a full light + dark palette from a single seed with materialkolor.
 * The extra explicit dark secondary/tertiary roles keep custom themes aligned
 * with the curated collection and avoid altering those colors in Theme.kt.
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
        maroonDark = dark.secondary,
        mangoDark = dark.tertiary,
        leafBgDark = dark.primaryContainer,
        maroonBgDark = dark.secondaryContainer,
        mangoBgDark = dark.tertiaryContainer,
        // Theme-independent
        topbarSubtle = dark.onSurfaceVariant,
        tabInactive = dark.outline,
        shadowInk = light.onBackground,
        // Category accents
        catFood = light.primary,
        catTranspo = light.tertiary,
        catLoad = light.secondary,
        catSupplies = Palettes.FieldNotebook.catSupplies,
        catOrg = Palettes.FieldNotebook.catOrg,
        catMisc = light.onSurfaceVariant,
    )
}
