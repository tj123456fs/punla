package com.uplb.punla.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusProfilesTest {
    private val custom = CampusProfile(
        id = "custom-test",
        name = "Test Campus",
        centerLat = 10.0,
        centerLon = 120.0,
        detectionRadiusMeters = 1_500.0
    )

    @Test
    fun automaticModeDetectsUplbNearItsCenter() {
        val resolved = CampusProfiles.resolve(
            profiles = listOf(CampusProfiles.UPLB, custom),
            selectedCampusId = null,
            location = 14.1630 to 121.2420
        )
        assertEquals(CampusProfiles.UPLB_ID, resolved?.id)
    }

    @Test
    fun automaticModeDetectsCustomCampus() {
        val resolved = CampusProfiles.resolve(
            profiles = listOf(CampusProfiles.UPLB, custom),
            selectedCampusId = null,
            location = 10.0005 to 120.0005
        )
        assertEquals(custom.id, resolved?.id)
    }

    @Test
    fun automaticModeUsesCurrentAreaOutsideKnownCampuses() {
        val resolved = CampusProfiles.resolve(
            profiles = listOf(CampusProfiles.UPLB, custom),
            selectedCampusId = null,
            location = 8.0 to 125.0
        )
        assertNull(resolved)
    }

    @Test
    fun manualSelectionWinsEvenWhenGpsIsFarAway() {
        val resolved = CampusProfiles.resolve(
            profiles = listOf(CampusProfiles.UPLB, custom),
            selectedCampusId = custom.id,
            location = 14.1630 to 121.2420
        )
        assertEquals(custom.id, resolved?.id)
    }

    @Test
    fun noGpsKeepsLegacyUplbDefaultInAutomaticMode() {
        val resolved = CampusProfiles.resolve(
            profiles = listOf(CampusProfiles.UPLB, custom),
            selectedCampusId = null,
            location = null
        )
        assertEquals(CampusProfiles.UPLB_ID, resolved?.id)
    }

    @Test
    fun codecDropsMalformedProfilesAndRoundTripsValidOnes() {
        val encoded = CampusProfileStore.encodeProfiles(listOf(custom))
        val decoded = CampusProfileStore.decodeProfiles(encoded.toString())
        assertEquals(listOf(custom), decoded)

        val malformed = """[
            {"id":"bad-lat","name":"Bad","centerLat":999,"centerLon":120,"detectionRadiusMeters":1000},
            {"id":"uplb","name":"Override","centerLat":10,"centerLon":120,"detectionRadiusMeters":1000},
            {"id":"ok","name":"Okay","centerLat":10,"centerLon":120,"detectionRadiusMeters":1000}
        ]"""
        val sanitized = CampusProfileStore.decodeProfiles(malformed)
        assertEquals(1, sanitized.size)
        assertEquals("ok", sanitized.single().id)
        assertTrue(!sanitized.single().builtIn)
    }
}
