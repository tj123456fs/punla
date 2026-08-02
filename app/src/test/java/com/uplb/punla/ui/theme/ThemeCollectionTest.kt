package com.uplb.punla.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.uplb.punla.data.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCollectionTest {

    @Test
    fun recommendedThemesResolveToTheirCollectionPalettes() {
        assertEquals(Palettes.AuroraBorealis, resolvePalette(ThemePreset.AURORA_BOREALIS, null))
        assertEquals(Palettes.SunsetSky, resolvePalette(ThemePreset.SUNSET_SKY, null))
        assertEquals(Palettes.OceanDepths, resolvePalette(ThemePreset.OCEAN_DEPTHS, null))
        assertEquals(Palettes.CoffeeShop, resolvePalette(ThemePreset.COFFEE_SHOP, null))
        assertEquals(Palettes.LofiNight, resolvePalette(ThemePreset.LOFI_NIGHT, null))
        assertEquals(Palettes.PaperInk, resolvePalette(ThemePreset.PAPER_INK, null))
    }

    @Test
    fun collectionCoreColorsMatchTheSpecification() {
        assertArgb("#071A2B", Palettes.AuroraBorealis.darkBg)
        assertArgb("#58E0C2", Palettes.AuroraBorealis.leafLight)
        assertArgb("#FF8A5B", Palettes.SunsetSky.leafLight)
        assertArgb("#36D6E7", Palettes.OceanDepths.leafLight)
        assertArgb("#D6A36B", Palettes.CoffeeShop.leafLight)
        assertArgb("#F08BC2", Palettes.LofiNight.leafLight)
        assertArgb("#F4EFE6", Palettes.PaperInk.paper)
        assertArgb("#2F5D50", Palettes.PaperInk.leaf)
    }

    @Test
    fun bodyTextContrastExceedsWcagAaInIntendedMode() {
        val darkThemes = listOf(
            Palettes.AuroraBorealis,
            Palettes.SunsetSky,
            Palettes.OceanDepths,
            Palettes.CoffeeShop,
            Palettes.LofiNight,
        )
        darkThemes.forEach { palette ->
            assertTrue(contrastRatio(palette.textDark, palette.darkBg) >= 4.5)
            assertTrue(contrastRatio(palette.textDark, palette.cardDark) >= 4.5)
        }
        assertTrue(contrastRatio(Palettes.PaperInk.ink, Palettes.PaperInk.paper) >= 4.5)
        assertTrue(contrastRatio(Palettes.PaperInk.ink, Palettes.PaperInk.cardLight) >= 4.5)
    }

    private fun assertArgb(hex: String, actual: Color) {
        val rgb = hex.removePrefix("#").toLong(16)
        val expected = (0xFF000000L or rgb).toInt()
        assertEquals(expected, actual.toArgb())
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val lighter = maxOf(relativeLuminance(a), relativeLuminance(b))
        val darker = minOf(relativeLuminance(a), relativeLuminance(b))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
