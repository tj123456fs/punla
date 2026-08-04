package com.uplb.punla.ui.theme

import com.uplb.punla.data.BackgroundStyle
import com.uplb.punla.data.ThemePreset
import com.uplb.punla.data.resolveForTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundEngineTest {

    @Test
    fun `catalog covers every background exactly once`() {
        assertEquals(
            BackgroundStyle.entries.toSet(),
            PunlaBackgroundCatalog.map { it.style }.toSet(),
        )
        assertEquals(
            PunlaBackgroundCatalog.size,
            PunlaBackgroundCatalog.map { it.style }.distinct().size,
        )
    }

    @Test
    fun `storage keys round trip and unknown values retain ambient legacy default`() {
        BackgroundStyle.entries.forEach { style ->
            assertEquals(style, BackgroundStyle.fromStorage(style.storageKey))
        }
        assertEquals(BackgroundStyle.AMBIENT, BackgroundStyle.fromStorage(null))
        assertEquals(BackgroundStyle.AMBIENT, BackgroundStyle.fromStorage("unknown"))
    }

    @Test
    fun `theme matched always resolves to a concrete effect`() {
        ThemePreset.entries.forEach { preset ->
            assertNotEquals(
                BackgroundStyle.THEME_MATCHED,
                BackgroundStyle.THEME_MATCHED.resolveForTheme(preset),
            )
        }
    }

    @Test
    fun `signature theme mappings stay intentional`() {
        assertEquals(
            BackgroundStyle.AURORA,
            BackgroundStyle.THEME_MATCHED.resolveForTheme(ThemePreset.AURORA_BOREALIS),
        )
        assertEquals(
            BackgroundStyle.OCEAN_WAVES,
            BackgroundStyle.THEME_MATCHED.resolveForTheme(ThemePreset.OCEAN_DEPTHS),
        )
        assertEquals(
            BackgroundStyle.RAIN,
            BackgroundStyle.THEME_MATCHED.resolveForTheme(ThemePreset.LOFI_NIGHT),
        )
        assertEquals(
            BackgroundStyle.SAKURA,
            BackgroundStyle.THEME_MATCHED.resolveForTheme(ThemePreset.PASTEL_BLOOM),
        )
        assertEquals(
            BackgroundStyle.STARFIELD,
            BackgroundStyle.THEME_MATCHED.resolveForTheme(ThemePreset.GALAXY),
        )
    }

    @Test
    fun `only minimal and paper grain are static`() {
        assertFalse(BackgroundStyle.MINIMAL.isAnimatedBackground())
        assertFalse(BackgroundStyle.PAPER_GRAIN.isAnimatedBackground())
        BackgroundStyle.entries
            .filterNot { it == BackgroundStyle.MINIMAL || it == BackgroundStyle.PAPER_GRAIN }
            .forEach { assertTrue(it.isAnimatedBackground()) }
    }
}
