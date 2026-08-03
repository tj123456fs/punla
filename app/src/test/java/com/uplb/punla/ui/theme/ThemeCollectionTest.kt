package com.uplb.punla.ui.theme

import androidx.compose.ui.graphics.Color
import com.uplb.punla.data.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ThemeCollectionTest {

    private data class ExpectedTheme(
        val preset: ThemePreset,
        val palette: PunlaPalette,
        val designedForDark: Boolean,
        val background: Color,
        val surface: Color,
        val primary: Color,
        val secondary: Color,
        val tertiary: Color,
        val onBackground: Color,
    )

    @Test
    fun catalogContainsClassicAndAllFourteenCollectionThemes() {
        assertEquals(15, PunlaThemeCatalog.size)
        assertEquals(15, PunlaThemeCatalog.map { it.preset }.distinct().size)
        assertEquals(ThemePreset.FIELD_NOTEBOOK, PunlaThemeCatalog.first().preset)
    }

    @Test
    fun curatedPalettesMatchThemeCollectionExactly() {
        val expected = listOf(
            ExpectedTheme(ThemePreset.AURORA_BOREALIS, Palettes.AuroraBorealis, true, Color(0xFF071A2B), Color(0xFF102A3A), Color(0xFF58E0C2), Color(0xFF7CFF9B), Color(0xFF70A9FF), Color(0xFFF2FFFC)),
            ExpectedTheme(ThemePreset.SUNSET_SKY, Palettes.SunsetSky, true, Color(0xFF2B1838), Color(0xFF4A274F), Color(0xFFFF8A5B), Color(0xFFFF5FA2), Color(0xFFB68CFF), Color(0xFFFFF6F1)),
            ExpectedTheme(ThemePreset.OCEAN_DEPTHS, Palettes.OceanDepths, true, Color(0xFF041F33), Color(0xFF0A3A52), Color(0xFF36D6E7), Color(0xFF4BA3FF), Color(0xFF7FFFD4), Color(0xFFEAFBFF)),
            ExpectedTheme(ThemePreset.FOREST_MIST, Palettes.ForestMist, true, Color(0xFF14231C), Color(0xFF243A2E), Color(0xFF8CCF9C), Color(0xFFB7C98C), Color(0xFFD2B48C), Color(0xFFF2F6EF)),
            ExpectedTheme(ThemePreset.LAVENDER_NIGHT, Palettes.LavenderNight, true, Color(0xFF17152C), Color(0xFF292449), Color(0xFFB59CFF), Color(0xFFD6B4FF), Color(0xFF8FB3FF), Color(0xFFF8F5FF)),
            ExpectedTheme(ThemePreset.GOLDEN_DAWN, Palettes.GoldenDawn, false, Color(0xFFFFF4DA), Color(0xFFFFF9EC), Color(0xFFE9A23B), Color(0xFFF2C66D), Color(0xFFF28C5B), Color(0xFF3B2C1E)),
            ExpectedTheme(ThemePreset.COFFEE_SHOP, Palettes.CoffeeShop, true, Color(0xFF2B211C), Color(0xFF49372E), Color(0xFFD6A36B), Color(0xFFB97A56), Color(0xFFF0C987), Color(0xFFFFF7ED)),
            ExpectedTheme(ThemePreset.LOFI_NIGHT, Palettes.LofiNight, true, Color(0xFF15172B), Color(0xFF292A4A), Color(0xFFF08BC2), Color(0xFF8A7CFF), Color(0xFF65C7F7), Color(0xFFF7F3FF)),
            ExpectedTheme(ThemePreset.PAPER_INK, Palettes.PaperInk, false, Color(0xFFF4EFE6), Color(0xFFFFFCF7), Color(0xFF2F5D50), Color(0xFF556B7A), Color(0xFFB07A45), Color(0xFF23201D)),
            ExpectedTheme(ThemePreset.LIBRARY_MODE, Palettes.LibraryMode, true, Color(0xFF211C17), Color(0xFF3B3026), Color(0xFFC9A96E), Color(0xFF6E7A4E), Color(0xFFA45C40), Color(0xFFF5EBD7)),
            ExpectedTheme(ThemePreset.CYBER_NEON, Palettes.CyberNeon, true, Color(0xFF090A12), Color(0xFF171A26), Color(0xFF00E5FF), Color(0xFFFF4FD8), Color(0xFF9DFF00), Color(0xFFF5F7FF)),
            ExpectedTheme(ThemePreset.PASTEL_BLOOM, Palettes.PastelBloom, false, Color(0xFFFFF1F6), Color(0xFFFFFFFF), Color(0xFFE78FB3), Color(0xFF8FD8C7), Color(0xFF91B8F4), Color(0xFF3B3540)),
            ExpectedTheme(ThemePreset.FROST_GLASS, Palettes.FrostGlass, false, Color(0xFFDCEEFF), Color(0xFFF7FBFF), Color(0xFF4C8FD8), Color(0xFF79B7E8), Color(0xFFA7D8FF), Color(0xFF1F3347)),
            ExpectedTheme(ThemePreset.GALAXY, Palettes.Galaxy, true, Color(0xFF0B1026), Color(0xFF1B2250), Color(0xFF8E7CFF), Color(0xFF5AC8FA), Color(0xFFFF7BCB), Color(0xFFF6F5FF)),
        )

        expected.forEach { theme ->
            val actual = resolvePalette(theme.preset, null)
            assertSame(theme.palette, actual)
            if (theme.designedForDark) {
                assertEquals(theme.background, actual.darkBg)
                assertEquals(theme.surface, actual.cardDark)
                assertEquals(theme.primary, actual.leafLight)
                assertEquals(theme.secondary, actual.maroonDark)
                assertEquals(theme.tertiary, actual.mangoDark)
                assertEquals(theme.onBackground, actual.textDark)
            } else {
                assertEquals(theme.background, actual.paper)
                assertEquals(theme.surface, actual.cardLight)
                assertEquals(theme.primary, actual.leaf)
                assertEquals(theme.secondary, actual.maroon)
                assertEquals(theme.tertiary, actual.mango)
                assertEquals(theme.onBackground, actual.ink)
            }
        }
    }
}
