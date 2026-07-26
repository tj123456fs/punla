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
}
