package com.uplb.punla.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Every color role the app uses, gathered into one value so Compose and the
 * home-screen widgets resolve their colors from the same selected preset.
 *
 * Curated themes carry an exact "designed" light or dark variant from
 * PUNLA_THEME_COLLECTION.md. A companion opposite-mode variant is derived so
 * the existing System/Light/Dark control remains usable without sacrificing
 * readability when a person overrides the theme's recommended mode.
 */
data class PunlaPalette(
    // Light
    val paper: Color, val ink: Color, val inkSoft: Color,
    val cardLight: Color, val lineLight: Color,
    val leaf: Color, val leafLight: Color, val leafBgLight: Color,
    val maroon: Color, val maroonBgLight: Color,
    val mango: Color, val mangoBgLight: Color,
    val bark: Color, val danger: Color,
    // Dark
    val darkBg: Color, val cardDark: Color, val textDark: Color,
    val lineDark: Color, val barkDark: Color,
    val maroonDark: Color, val mangoDark: Color,
    val leafBgDark: Color, val maroonBgDark: Color, val mangoBgDark: Color,
    // Theme-independent
    val topbarSubtle: Color, val tabInactive: Color, val shadowInk: Color,
    // Category accents
    val catFood: Color, val catTranspo: Color, val catLoad: Color,
    val catSupplies: Color, val catOrg: Color, val catMisc: Color,
)

private fun darkCollectionPalette(
    background: Color,
    surface: Color,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    onBackground: Color,
): PunlaPalette {
    // The dark roles below are the exact collection palette. The light roles
    // are intentionally quieter derived companions for manual Light override.
    val paper = lerp(background, Color.White, 0.94f)
    val cardLight = lerp(surface, Color.White, 0.95f)
    val ink = lerp(background, Color.Black, 0.72f)
    val inkSoft = lerp(ink, paper, 0.18f)
    val lightPrimary = lerp(primary, Color.Black, 0.34f)
    val lightSecondary = lerp(secondary, Color.Black, 0.34f)
    val lightTertiary = lerp(tertiary, Color.Black, 0.34f)

    return PunlaPalette(
        paper = paper,
        ink = ink,
        inkSoft = inkSoft,
        cardLight = cardLight,
        lineLight = lerp(paper, ink, 0.13f),
        leaf = lightPrimary,
        leafLight = primary,
        leafBgLight = lerp(paper, primary, 0.17f),
        maroon = lightSecondary,
        maroonBgLight = lerp(paper, secondary, 0.16f),
        mango = lightTertiary,
        mangoBgLight = lerp(paper, tertiary, 0.16f),
        bark = lerp(ink, paper, 0.38f),
        danger = Color(0xFFB3261E),
        darkBg = background,
        cardDark = surface,
        textDark = onBackground,
        lineDark = lerp(background, onBackground, 0.18f),
        barkDark = lerp(onBackground, surface, 0.35f),
        maroonDark = secondary,
        mangoDark = tertiary,
        leafBgDark = lerp(surface, primary, 0.23f),
        maroonBgDark = lerp(surface, secondary, 0.22f),
        mangoBgDark = lerp(surface, tertiary, 0.22f),
        topbarSubtle = lerp(onBackground, surface, 0.32f),
        tabInactive = lerp(onBackground, surface, 0.52f),
        shadowInk = background,
        catFood = primary,
        catTranspo = tertiary,
        catLoad = secondary,
        catSupplies = lerp(primary, tertiary, 0.52f),
        catOrg = lerp(secondary, tertiary, 0.45f),
        catMisc = lerp(onBackground, surface, 0.42f),
    )
}

private fun lightCollectionPalette(
    background: Color,
    surface: Color,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    onBackground: Color,
): PunlaPalette {
    // The light roles below are the exact collection palette. The dark roles
    // are derived companions for System/Dark override.
    val darkBg = lerp(background, Color.Black, 0.86f)
    val cardDark = lerp(surface, Color.Black, 0.80f)
    val textDark = lerp(onBackground, Color.White, 0.86f)
    val darkPrimary = lerp(primary, Color.White, 0.23f)
    val darkSecondary = lerp(secondary, Color.White, 0.23f)
    val darkTertiary = lerp(tertiary, Color.White, 0.23f)

    return PunlaPalette(
        paper = background,
        ink = onBackground,
        inkSoft = lerp(onBackground, background, 0.20f),
        cardLight = surface,
        lineLight = lerp(background, onBackground, 0.14f),
        leaf = primary,
        leafLight = darkPrimary,
        leafBgLight = lerp(background, primary, 0.16f),
        maroon = secondary,
        maroonBgLight = lerp(background, secondary, 0.16f),
        mango = tertiary,
        mangoBgLight = lerp(background, tertiary, 0.16f),
        bark = lerp(onBackground, background, 0.36f),
        danger = Color(0xFFB3261E),
        darkBg = darkBg,
        cardDark = cardDark,
        textDark = textDark,
        lineDark = lerp(darkBg, textDark, 0.18f),
        barkDark = lerp(textDark, cardDark, 0.34f),
        maroonDark = darkSecondary,
        mangoDark = darkTertiary,
        leafBgDark = lerp(cardDark, darkPrimary, 0.23f),
        maroonBgDark = lerp(cardDark, darkSecondary, 0.22f),
        mangoBgDark = lerp(cardDark, darkTertiary, 0.22f),
        topbarSubtle = lerp(textDark, cardDark, 0.32f),
        tabInactive = lerp(textDark, cardDark, 0.52f),
        shadowInk = onBackground,
        catFood = primary,
        catTranspo = tertiary,
        catLoad = secondary,
        catSupplies = lerp(primary, tertiary, 0.52f),
        catOrg = lerp(secondary, tertiary, 0.45f),
        catMisc = lerp(onBackground, background, 0.42f),
    )
}

/** Curated, hand-tuned palettes selectable in Settings. */
object Palettes {

    /** Punla's original field-notebook identity. */
    val FieldNotebook = PunlaPalette(
        paper = Paper, ink = Ink, inkSoft = InkSoft,
        cardLight = CardLight, lineLight = LineLight,
        leaf = Leaf, leafLight = LeafLight, leafBgLight = LeafBgLight,
        maroon = Maroon, maroonBgLight = MaroonBgLight,
        mango = Mango, mangoBgLight = MangoBgLight,
        bark = Bark, danger = Danger,
        darkBg = DarkBg, cardDark = CardDark, textDark = TextDark,
        lineDark = LineDark, barkDark = BarkDark,
        maroonDark = Color(0xFFB68997), mangoDark = Mango,
        leafBgDark = LeafBgDark, maroonBgDark = MaroonBgDark, mangoBgDark = MangoBgDark,
        topbarSubtle = TopbarSubtle, tabInactive = TabInactive, shadowInk = ShadowInk,
        catFood = CatFood, catTranspo = CatTranspo, catLoad = CatLoad,
        catSupplies = CatSupplies, catOrg = CatOrg, catMisc = CatMisc,
    )

    val AuroraBorealis = darkCollectionPalette(
        background = Color(0xFF071A2B), surface = Color(0xFF102A3A),
        primary = Color(0xFF58E0C2), secondary = Color(0xFF7CFF9B),
        tertiary = Color(0xFF70A9FF), onBackground = Color(0xFFF2FFFC),
    )

    val SunsetSky = darkCollectionPalette(
        background = Color(0xFF2B1838), surface = Color(0xFF4A274F),
        primary = Color(0xFFFF8A5B), secondary = Color(0xFFFF5FA2),
        tertiary = Color(0xFFB68CFF), onBackground = Color(0xFFFFF6F1),
    )

    val OceanDepths = darkCollectionPalette(
        background = Color(0xFF041F33), surface = Color(0xFF0A3A52),
        primary = Color(0xFF36D6E7), secondary = Color(0xFF4BA3FF),
        tertiary = Color(0xFF7FFFD4), onBackground = Color(0xFFEAFBFF),
    )

    val ForestMist = darkCollectionPalette(
        background = Color(0xFF14231C), surface = Color(0xFF243A2E),
        primary = Color(0xFF8CCF9C), secondary = Color(0xFFB7C98C),
        tertiary = Color(0xFFD2B48C), onBackground = Color(0xFFF2F6EF),
    )

    val LavenderNight = darkCollectionPalette(
        background = Color(0xFF17152C), surface = Color(0xFF292449),
        primary = Color(0xFFB59CFF), secondary = Color(0xFFD6B4FF),
        tertiary = Color(0xFF8FB3FF), onBackground = Color(0xFFF8F5FF),
    )

    val GoldenDawn = lightCollectionPalette(
        background = Color(0xFFFFF4DA), surface = Color(0xFFFFF9EC),
        primary = Color(0xFFE9A23B), secondary = Color(0xFFF2C66D),
        tertiary = Color(0xFFF28C5B), onBackground = Color(0xFF3B2C1E),
    )

    val CoffeeShop = darkCollectionPalette(
        background = Color(0xFF2B211C), surface = Color(0xFF49372E),
        primary = Color(0xFFD6A36B), secondary = Color(0xFFB97A56),
        tertiary = Color(0xFFF0C987), onBackground = Color(0xFFFFF7ED),
    )

    val LofiNight = darkCollectionPalette(
        background = Color(0xFF15172B), surface = Color(0xFF292A4A),
        primary = Color(0xFFF08BC2), secondary = Color(0xFF8A7CFF),
        tertiary = Color(0xFF65C7F7), onBackground = Color(0xFFF7F3FF),
    )

    val PaperInk = lightCollectionPalette(
        background = Color(0xFFF4EFE6), surface = Color(0xFFFFFCF7),
        primary = Color(0xFF2F5D50), secondary = Color(0xFF556B7A),
        tertiary = Color(0xFFB07A45), onBackground = Color(0xFF23201D),
    )

    val LibraryMode = darkCollectionPalette(
        background = Color(0xFF211C17), surface = Color(0xFF3B3026),
        primary = Color(0xFFC9A96E), secondary = Color(0xFF6E7A4E),
        tertiary = Color(0xFFA45C40), onBackground = Color(0xFFF5EBD7),
    )

    val CyberNeon = darkCollectionPalette(
        background = Color(0xFF090A12), surface = Color(0xFF171A26),
        primary = Color(0xFF00E5FF), secondary = Color(0xFFFF4FD8),
        tertiary = Color(0xFF9DFF00), onBackground = Color(0xFFF5F7FF),
    )

    val PastelBloom = lightCollectionPalette(
        background = Color(0xFFFFF1F6), surface = Color(0xFFFFFFFF),
        primary = Color(0xFFE78FB3), secondary = Color(0xFF8FD8C7),
        tertiary = Color(0xFF91B8F4), onBackground = Color(0xFF3B3540),
    )

    val FrostGlass = lightCollectionPalette(
        background = Color(0xFFDCEEFF), surface = Color(0xFFF7FBFF),
        primary = Color(0xFF4C8FD8), secondary = Color(0xFF79B7E8),
        tertiary = Color(0xFFA7D8FF), onBackground = Color(0xFF1F3347),
    )

    val Galaxy = darkCollectionPalette(
        background = Color(0xFF0B1026), surface = Color(0xFF1B2250),
        primary = Color(0xFF8E7CFF), secondary = Color(0xFF5AC8FA),
        tertiary = Color(0xFFFF7BCB), onBackground = Color(0xFFF6F5FF),
    )
}
