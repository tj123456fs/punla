package com.uplb.punla.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Every color *role* the app uses, gathered into one value so Theme.kt (and,
 * later, the home screen widgets) can build their color scheme from a single
 * [PunlaPalette] instance instead of reaching for hardcoded top-level `val`s.
 *
 * A resolved palette always carries both light- and dark-variant roles at
 * once — which variant is actually used is decided separately by dark-mode
 * state (see [PunlaTheme]), not by which palette/preset is selected.
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
    val leafBgDark: Color, val maroonBgDark: Color, val mangoBgDark: Color,
    // Theme-independent
    val topbarSubtle: Color, val tabInactive: Color, val shadowInk: Color,
    // Category accents
    val catFood: Color, val catTranspo: Color, val catLoad: Color,
    val catSupplies: Color, val catOrg: Color, val catMisc: Color,
    // Explicit theme roles used when a preset needs different light/dark accents
    // or a dark label on a pastel fill for accessibility.
    val secondaryDark: Color = maroon,
    val tertiaryDark: Color = mango,
    val onPrimaryLight: Color = paper,
    val onSecondaryLight: Color = paper,
    val onTertiaryLight: Color = paper,
    val onPrimaryDark: Color = darkBg,
    val onSecondaryDark: Color = darkBg,
    val onTertiaryDark: Color = darkBg,
    val success: Color = leaf,
    val warning: Color = mango,
)

/** Curated, hand-tuned palettes the person can pick from in Settings. */
object Palettes {

    /** The original field-notebook look — unchanged from [Color.kt], just
     * regrouped under the [PunlaPalette] role model. */
    val FieldNotebook = PunlaPalette(
        paper = Paper, ink = Ink, inkSoft = InkSoft,
        cardLight = CardLight, lineLight = LineLight,
        leaf = Leaf, leafLight = LeafLight, leafBgLight = LeafBgLight,
        maroon = Maroon, maroonBgLight = MaroonBgLight,
        mango = Mango, mangoBgLight = MangoBgLight,
        bark = Bark, danger = Danger,
        darkBg = DarkBg, cardDark = CardDark, textDark = TextDark,
        lineDark = LineDark, barkDark = BarkDark,
        leafBgDark = LeafBgDark, maroonBgDark = MaroonBgDark, mangoBgDark = MangoBgDark,
        topbarSubtle = TopbarSubtle, tabInactive = TabInactive, shadowInk = ShadowInk,
        catFood = CatFood, catTranspo = CatTranspo, catLoad = CatLoad,
        catSupplies = CatSupplies, catOrg = CatOrg, catMisc = CatMisc,
    )

    /** Cool blues/teals sibling palette. */
    val Ocean = PunlaPalette(
        paper = Color(0xFFF3F7FA), ink = Color(0xFF16232E), inkSoft = Color(0xFF1E3040),
        cardLight = Color(0xFFFFFFFF), lineLight = Color(0xFFD8E4EC),
        leaf = Color(0xFF1C6E8C), leafLight = Color(0xFF6FB8D4), leafBgLight = Color(0xFFDCEFF5),
        maroon = Color(0xFF3B4C7A), maroonBgLight = Color(0xFFE3E7F5),
        mango = Color(0xFFB98A2E), mangoBgLight = Color(0xFFF3EBD8),
        bark = Color(0xFF52697A), danger = Color(0xFFA33131),
        darkBg = Color(0xFF0D1620), cardDark = Color(0xFF15222E), textDark = Color(0xFFE3ECF2),
        lineDark = Color(0xFF28394A), barkDark = Color(0xFF9AACB8),
        leafBgDark = Color(0xFF17313D), maroonBgDark = Color(0xFF232C47), mangoBgDark = Color(0xFF3A2F18),
        topbarSubtle = Color(0xFFB9CBD6), tabInactive = Color(0xFF7E93A0), shadowInk = Color(0xFF16232E),
        catFood = Color(0xFF1C6E8C), catTranspo = Color(0xFFB98A2E), catLoad = Color(0xFF3B4C7A),
        catSupplies = Color(0xFF3D6B8A), catOrg = Color(0xFF9C6B30), catMisc = Color(0xFF52697A),
    )

    /** Warm oranges/pinks sibling palette. */
    val Sunset = PunlaPalette(
        paper = Color(0xFFFFF6ED), ink = Color(0xFF3A1F1A), inkSoft = Color(0xFF4A2A22),
        cardLight = Color(0xFFFFFFFF), lineLight = Color(0xFFF0DAC8),
        leaf = Color(0xFFD1495B), leafLight = Color(0xFFE88992), leafBgLight = Color(0xFFFBE4E7),
        maroon = Color(0xFF7A3B69), maroonBgLight = Color(0xFFF3E3EF),
        mango = Color(0xFFE08E1D), mangoBgLight = Color(0xFFFCEEDA),
        bark = Color(0xFF8A6650), danger = Color(0xFFA33131),
        darkBg = Color(0xFF201512), cardDark = Color(0xFF2C1D18), textDark = Color(0xFFF5E7DD),
        lineDark = Color(0xFF4A3228), barkDark = Color(0xFFC9A98F),
        leafBgDark = Color(0xFF3B1E23), maroonBgDark = Color(0xFF33202F), mangoBgDark = Color(0xFF3D2A14),
        topbarSubtle = Color(0xFFDCC0AE), tabInactive = Color(0xFFB08E78), shadowInk = Color(0xFF3A1F1A),
        catFood = Color(0xFFD1495B), catTranspo = Color(0xFFE08E1D), catLoad = Color(0xFF7A3B69),
        catSupplies = Color(0xFF3D6B8A), catOrg = Color(0xFF9C6B30), catMisc = Color(0xFF8A6650),
    )

    /** Cool violets/plums sibling palette. */
    val Orchid = PunlaPalette(
        paper = Color(0xFFF8F5FB), ink = Color(0xFF251C33), inkSoft = Color(0xFF332745),
        cardLight = Color(0xFFFFFFFF), lineLight = Color(0xFFE4D9EF),
        leaf = Color(0xFF7C4FA3), leafLight = Color(0xFFB48ED1), leafBgLight = Color(0xFFEEE2F6),
        maroon = Color(0xFF9C3D6E), maroonBgLight = Color(0xFFF7E4EE),
        mango = Color(0xFFB9762E), mangoBgLight = Color(0xFFF7EADA),
        bark = Color(0xFF6F6180), danger = Color(0xFFA33131),
        darkBg = Color(0xFF1A1522), cardDark = Color(0xFF241C30), textDark = Color(0xFFEDE6F4),
        lineDark = Color(0xFF3A3049), barkDark = Color(0xFFC0B4CF),
        leafBgDark = Color(0xFF2F2340), maroonBgDark = Color(0xFF3A2131), mangoBgDark = Color(0xFF3A2A16),
        topbarSubtle = Color(0xFFD0C0DF), tabInactive = Color(0xFF9285A3), shadowInk = Color(0xFF251C33),
        catFood = Color(0xFF7C4FA3), catTranspo = Color(0xFFB9762E), catLoad = Color(0xFF9C3D6E),
        catSupplies = Color(0xFF3D6B8A), catOrg = Color(0xFF9C6B30), catMisc = Color(0xFF6F6180),
    )

    /** Neutral graphite/charcoal sibling palette — the low-key, minimal-color option. */
    val Slate = PunlaPalette(
        paper = Color(0xFFF5F5F3), ink = Color(0xFF1E1F21), inkSoft = Color(0xFF2A2C2F),
        cardLight = Color(0xFFFFFFFF), lineLight = Color(0xFFDDDDD9),
        leaf = Color(0xFF4A5560), leafLight = Color(0xFF8C99A3), leafBgLight = Color(0xFFE4E8EA),
        maroon = Color(0xFF6B4A57), maroonBgLight = Color(0xFFEBE2E5),
        mango = Color(0xFF9C7A3D), mangoBgLight = Color(0xFFF0E8D6),
        bark = Color(0xFF666666), danger = Color(0xFFA33131),
        darkBg = Color(0xFF16171A), cardDark = Color(0xFF212327), textDark = Color(0xFFE7E7E4),
        lineDark = Color(0xFF34363A), barkDark = Color(0xFFAAAAAA),
        leafBgDark = Color(0xFF262B30), maroonBgDark = Color(0xFF302529), mangoBgDark = Color(0xFF332A18),
        topbarSubtle = Color(0xFFC4C6C9), tabInactive = Color(0xFF8D8F92), shadowInk = Color(0xFF1E1F21),
        catFood = Color(0xFF4A5560), catTranspo = Color(0xFF9C7A3D), catLoad = Color(0xFF6B4A57),
        catSupplies = Color(0xFF3D6B8A), catOrg = Color(0xFF9C6B30), catMisc = Color(0xFF666666),
    )

    /** Theme Collection: Aurora Borealis — dark nature preset. */
    val AuroraBorealis = PunlaPalette(
        paper = Color(0xFFF2FFFC), ink = Color(0xFF071A2B), inkSoft = Color(0xFF274654),
        cardLight = Color(0xFFE5F4F1), lineLight = Color(0xFFB7D5D0),
        leaf = Color(0xFF167B6D), leafLight = Color(0xFF58E0C2), leafBgLight = Color(0xFFC7F3E8),
        maroon = Color(0xFF347A42), maroonBgLight = Color(0xFFD8F4DC),
        mango = Color(0xFF3D70B8), mangoBgLight = Color(0xFFDDE9FB),
        bark = Color(0xFF48646C), danger = Color(0xFFFF6B73),
        darkBg = Color(0xFF071A2B), cardDark = Color(0xFF102A3A), textDark = Color(0xFFF2FFFC),
        lineDark = Color(0xFF315064), barkDark = Color(0xFFC2D9D5),
        leafBgDark = Color(0xFF153F42), maroonBgDark = Color(0xFF234A35), mangoBgDark = Color(0xFF1E3A61),
        topbarSubtle = Color(0xFFC2D9D5), tabInactive = Color(0xFF7B9BA1), shadowInk = Color(0xFF020B12),
        catFood = Color(0xFF58E0C2), catTranspo = Color(0xFF70A9FF), catLoad = Color(0xFF7CFF9B),
        catSupplies = Color(0xFF55C2FF), catOrg = Color(0xFFB1FF72), catMisc = Color(0xFF8FB8C2),
        secondaryDark = Color(0xFF7CFF9B), tertiaryDark = Color(0xFF70A9FF),
        onPrimaryLight = Color.White, onSecondaryLight = Color.White, onTertiaryLight = Color.White,
        onPrimaryDark = Color(0xFF071A2B), onSecondaryDark = Color(0xFF071A2B), onTertiaryDark = Color(0xFF071A2B),
        success = Color(0xFF7CFF9B), warning = Color(0xFFFFC857),
    )

    /** Theme Collection: Sunset Sky — dark nature preset. */
    val SunsetSky = PunlaPalette(
        paper = Color(0xFFFFF6F1), ink = Color(0xFF2B1838), inkSoft = Color(0xFF5B405E),
        cardLight = Color(0xFFFFE9E7), lineLight = Color(0xFFE9C4CF),
        leaf = Color(0xFFB94725), leafLight = Color(0xFFFF8A5B), leafBgLight = Color(0xFFFFD8C8),
        maroon = Color(0xFFA92366), maroonBgLight = Color(0xFFFFD7E9),
        mango = Color(0xFF7051B6), mangoBgLight = Color(0xFFE7DDFF),
        bark = Color(0xFF765865), danger = Color(0xFFFF6B73),
        darkBg = Color(0xFF2B1838), cardDark = Color(0xFF4A274F), textDark = Color(0xFFFFF6F1),
        lineDark = Color(0xFF69406C), barkDark = Color(0xFFE7C9D7),
        leafBgDark = Color(0xFF643225), maroonBgDark = Color(0xFF652948), mangoBgDark = Color(0xFF4D3C72),
        topbarSubtle = Color(0xFFE7C9D7), tabInactive = Color(0xFFB58DA1), shadowInk = Color(0xFF170C1E),
        catFood = Color(0xFFFF8A5B), catTranspo = Color(0xFFFFC857), catLoad = Color(0xFFFF5FA2),
        catSupplies = Color(0xFF8EBBFF), catOrg = Color(0xFFB68CFF), catMisc = Color(0xFFD69CB8),
        secondaryDark = Color(0xFFFF5FA2), tertiaryDark = Color(0xFFB68CFF),
        onPrimaryLight = Color.White, onSecondaryLight = Color.White, onTertiaryLight = Color.White,
        onPrimaryDark = Color(0xFF2B1838), onSecondaryDark = Color(0xFF2B1838), onTertiaryDark = Color(0xFF2B1838),
        success = Color(0xFF8ED39A), warning = Color(0xFFFFC857),
    )

    /** Theme Collection: Ocean Depths — dark nature preset. */
    val OceanDepths = PunlaPalette(
        paper = Color(0xFFEAFBFF), ink = Color(0xFF041F33), inkSoft = Color(0xFF345767),
        cardLight = Color(0xFFDDF4FA), lineLight = Color(0xFFADD7E1),
        leaf = Color(0xFF087B89), leafLight = Color(0xFF36D6E7), leafBgLight = Color(0xFFC7F1F5),
        maroon = Color(0xFF286CB2), maroonBgLight = Color(0xFFD6E9FF),
        mango = Color(0xFF20856D), mangoBgLight = Color(0xFFD4F7EA),
        bark = Color(0xFF466A78), danger = Color(0xFFFF6B73),
        darkBg = Color(0xFF041F33), cardDark = Color(0xFF0A3A52), textDark = Color(0xFFEAFBFF),
        lineDark = Color(0xFF1F5870), barkDark = Color(0xFFBADAE3),
        leafBgDark = Color(0xFF0D4C58), maroonBgDark = Color(0xFF173F68), mangoBgDark = Color(0xFF135847),
        topbarSubtle = Color(0xFFBADAE3), tabInactive = Color(0xFF78A7B4), shadowInk = Color(0xFF01111D),
        catFood = Color(0xFF36D6E7), catTranspo = Color(0xFF7FFFD4), catLoad = Color(0xFF4BA3FF),
        catSupplies = Color(0xFF5CC9FF), catOrg = Color(0xFF76E8B4), catMisc = Color(0xFF83B9C8),
        secondaryDark = Color(0xFF4BA3FF), tertiaryDark = Color(0xFF7FFFD4),
        onPrimaryLight = Color.White, onSecondaryLight = Color.White, onTertiaryLight = Color(0xFF041F33),
        onPrimaryDark = Color(0xFF041F33), onSecondaryDark = Color(0xFF041F33), onTertiaryDark = Color(0xFF041F33),
        success = Color(0xFF7FFFD4), warning = Color(0xFFFFC857),
    )

    /** Theme Collection: Coffee Shop — dark cozy preset. */
    val CoffeeShop = PunlaPalette(
        paper = Color(0xFFFFF7ED), ink = Color(0xFF2B211C), inkSoft = Color(0xFF5E493D),
        cardLight = Color(0xFFF7E8D7), lineLight = Color(0xFFDCC4AE),
        leaf = Color(0xFF8A5A2D), leafLight = Color(0xFFD6A36B), leafBgLight = Color(0xFFF2DDC5),
        maroon = Color(0xFF8B4E35), maroonBgLight = Color(0xFFEFD8CD),
        mango = Color(0xFFA66B1F), mangoBgLight = Color(0xFFF7E5BC),
        bark = Color(0xFF6D5548), danger = Color(0xFFFF756D),
        darkBg = Color(0xFF2B211C), cardDark = Color(0xFF49372E), textDark = Color(0xFFFFF7ED),
        lineDark = Color(0xFF654C3F), barkDark = Color(0xFFE1C8B0),
        leafBgDark = Color(0xFF5B422B), maroonBgDark = Color(0xFF60392C), mangoBgDark = Color(0xFF5A421F),
        topbarSubtle = Color(0xFFE1C8B0), tabInactive = Color(0xFFB0927B), shadowInk = Color(0xFF160F0C),
        catFood = Color(0xFFD6A36B), catTranspo = Color(0xFFF0C987), catLoad = Color(0xFFB97A56),
        catSupplies = Color(0xFF8FB5C9), catOrg = Color(0xFFC99A57), catMisc = Color(0xFFB79A86),
        secondaryDark = Color(0xFFB97A56), tertiaryDark = Color(0xFFF0C987),
        onPrimaryLight = Color.White, onSecondaryLight = Color.White, onTertiaryLight = Color(0xFF2B211C),
        onPrimaryDark = Color(0xFF2B211C), onSecondaryDark = Color(0xFFFFF7ED), onTertiaryDark = Color(0xFF2B211C),
        success = Color(0xFF9BC98E), warning = Color(0xFFF0C987),
    )

    /** Theme Collection: Lo-fi Night — dark cozy preset. */
    val LofiNight = PunlaPalette(
        paper = Color(0xFFF7F3FF), ink = Color(0xFF15172B), inkSoft = Color(0xFF4F4E6D),
        cardLight = Color(0xFFECE8FA), lineLight = Color(0xFFCFC8E8),
        leaf = Color(0xFFAF3E7E), leafLight = Color(0xFFF08BC2), leafBgLight = Color(0xFFF9D9EC),
        maroon = Color(0xFF5F52C6), maroonBgLight = Color(0xFFE2DEFF),
        mango = Color(0xFF287FA8), mangoBgLight = Color(0xFFD9F2FD),
        bark = Color(0xFF65627E), danger = Color(0xFFFF6B73),
        darkBg = Color(0xFF15172B), cardDark = Color(0xFF292A4A), textDark = Color(0xFFF7F3FF),
        lineDark = Color(0xFF41436A), barkDark = Color(0xFFC9C4E0),
        leafBgDark = Color(0xFF52324C), maroonBgDark = Color(0xFF3B376D), mangoBgDark = Color(0xFF244E68),
        topbarSubtle = Color(0xFFC9C4E0), tabInactive = Color(0xFF8F8BAC), shadowInk = Color(0xFF090A18),
        catFood = Color(0xFFF08BC2), catTranspo = Color(0xFF65C7F7), catLoad = Color(0xFF8A7CFF),
        catSupplies = Color(0xFF77B7FF), catOrg = Color(0xFFE8A06E), catMisc = Color(0xFFA7A1C8),
        secondaryDark = Color(0xFF8A7CFF), tertiaryDark = Color(0xFF65C7F7),
        onPrimaryLight = Color.White, onSecondaryLight = Color.White, onTertiaryLight = Color.White,
        onPrimaryDark = Color(0xFF15172B), onSecondaryDark = Color(0xFF15172B), onTertiaryDark = Color(0xFF15172B),
        success = Color(0xFF8ED6A4), warning = Color(0xFFFFC857),
    )

    /** Theme Collection: Paper & Ink — light cozy/minimal preset. */
    val PaperInk = PunlaPalette(
        paper = Color(0xFFF4EFE6), ink = Color(0xFF23201D), inkSoft = Color(0xFF393530),
        cardLight = Color(0xFFFFFCF7), lineLight = Color(0xFFD8D0C4),
        leaf = Color(0xFF2F5D50), leafLight = Color(0xFF8DB9AB), leafBgLight = Color(0xFFDDEAE4),
        maroon = Color(0xFF556B7A), maroonBgLight = Color(0xFFE1E8EC),
        mango = Color(0xFFB07A45), mangoBgLight = Color(0xFFF0E1D1),
        bark = Color(0xFF625C55), danger = Color(0xFF9E3535),
        darkBg = Color(0xFF1D1B19), cardDark = Color(0xFF2A2724), textDark = Color(0xFFF4EFE6),
        lineDark = Color(0xFF45403B), barkDark = Color(0xFFC8C0B5),
        leafBgDark = Color(0xFF29443B), maroonBgDark = Color(0xFF33404A), mangoBgDark = Color(0xFF4D3825),
        topbarSubtle = Color(0xFFC8C0B5), tabInactive = Color(0xFF8F8880), shadowInk = Color(0xFF23201D),
        catFood = Color(0xFF2F5D50), catTranspo = Color(0xFFB07A45), catLoad = Color(0xFF556B7A),
        catSupplies = Color(0xFF4E7087), catOrg = Color(0xFF8C6A3D), catMisc = Color(0xFF625C55),
        secondaryDark = Color(0xFF9FB5C3), tertiaryDark = Color(0xFFD7A875),
        onPrimaryLight = Color.White, onSecondaryLight = Color.White, onTertiaryLight = Color.White,
        onPrimaryDark = Color(0xFF1D1B19), onSecondaryDark = Color(0xFF1D1B19), onTertiaryDark = Color(0xFF1D1B19),
        success = Color(0xFF2F5D50), warning = Color(0xFFB07A45),
    )

}
