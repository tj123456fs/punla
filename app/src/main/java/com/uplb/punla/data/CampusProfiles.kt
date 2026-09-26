package com.uplb.punla.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CampusProfile(
    val id: String,
    val name: String,
    val centerLat: Double,
    val centerLon: Double,
    val detectionRadiusMeters: Double,
    val builtIn: Boolean = false
)

object CampusProfiles {
    const val UPLB_ID = "uplb"
    const val DEFAULT_CUSTOM_RADIUS_METERS = 1_500.0

    val UPLB = CampusProfile(
        id = UPLB_ID,
        name = "UP Los Baños",
        centerLat = 14.1630,
        centerLon = 121.2420,
        detectionRadiusMeters = 4_000.0,
        builtIn = true
    )

    val builtIns: List<CampusProfile> = listOf(UPLB)

    fun resolve(
        profiles: List<CampusProfile>,
        selectedCampusId: String?,
        location: Pair<Double, Double>?
    ): CampusProfile? {
        if (selectedCampusId != null) {
            return profiles.firstOrNull { it.id == selectedCampusId }
        }

        // Preserve Punla's existing UPLB-first experience until a GPS fix
        // arrives. Once location is known, automatic mode never forces UPLB.
        if (location == null) return profiles.firstOrNull { it.id == UPLB_ID }

        val (lat, lon) = location
        return profiles
            .asSequence()
            .map { profile ->
                profile to haversineMeters(lat, lon, profile.centerLat, profile.centerLon)
            }
            .filter { (profile, distance) -> distance <= profile.detectionRadiusMeters }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    fun buildingsFor(profile: CampusProfile?): List<Building> =
        if (profile?.id == UPLB_ID) CampusDirectory.BUILDINGS else emptyList()
}

/**
 * Lightweight campus profiles deliberately live in preferences rather than
 * Room. They are user settings (name/center/radius), while raw GPS walks and
 * learned path geometry remain in the database.
 */
class CampusProfileStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedCampusId: String?
        get() = prefs.getString(KEY_SELECTED_CAMPUS, AUTO_VALUE)
            ?.takeUnless { it == AUTO_VALUE || it.isBlank() }
        set(value) {
            val valid = value?.takeIf { id -> allProfiles().any { it.id == id } }
            prefs.edit().putString(KEY_SELECTED_CAMPUS, valid ?: AUTO_VALUE).apply()
        }

    fun customProfiles(): List<CampusProfile> =
        decodeProfiles(prefs.getString(KEY_CUSTOM_CAMPUSES, null))

    fun allProfiles(): List<CampusProfile> = CampusProfiles.builtIns + customProfiles()

    fun activeProfile(location: Pair<Double, Double>?): CampusProfile? =
        CampusProfiles.resolve(allProfiles(), selectedCampusId, location)

    fun addCustomCampus(
        name: String,
        centerLat: Double,
        centerLon: Double,
        detectionRadiusMeters: Double = CampusProfiles.DEFAULT_CUSTOM_RADIUS_METERS
    ): CampusProfile? {
        val normalizedName = name.trim().take(MAX_NAME_LENGTH)
        if (normalizedName.isBlank()) return null
        if (!centerLat.isFinite() || centerLat !in -90.0..90.0) return null
        if (!centerLon.isFinite() || centerLon !in -180.0..180.0) return null
        if (!detectionRadiusMeters.isFinite() || detectionRadiusMeters !in MIN_RADIUS_METERS..MAX_RADIUS_METERS) return null

        val profile = CampusProfile(
            id = "custom-${UUID.randomUUID()}",
            name = normalizedName,
            centerLat = centerLat,
            centerLon = centerLon,
            detectionRadiusMeters = detectionRadiusMeters,
            builtIn = false
        )
        writeCustomProfiles(customProfiles() + profile)
        selectedCampusId = profile.id
        return profile
    }

    fun deleteCustomCampus(id: String): Boolean {
        val current = customProfiles()
        val next = current.filterNot { it.id == id }
        if (next.size == current.size) return false
        writeCustomProfiles(next)
        if (selectedCampusId == id) selectedCampusId = null
        return true
    }

    fun exportCustomProfiles(): JSONArray = encodeProfiles(customProfiles())

    fun restore(customProfiles: JSONArray?, selection: String?) {
        val restored = if (customProfiles == null) emptyList() else decodeProfiles(customProfiles.toString())
        writeCustomProfiles(restored)
        selectedCampusId = selection
            ?.takeUnless { it == AUTO_VALUE || it.isBlank() }
            ?.takeIf { id -> allProfiles().any { it.id == id } }
    }

    private fun writeCustomProfiles(profiles: List<CampusProfile>) {
        prefs.edit().putString(KEY_CUSTOM_CAMPUSES, encodeProfiles(profiles).toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "punla_prefs"
        private const val KEY_CUSTOM_CAMPUSES = "campus_custom_profiles"
        private const val KEY_SELECTED_CAMPUS = "campus_selected_id"
        private const val AUTO_VALUE = "auto"
        private const val MAX_NAME_LENGTH = 60
        private const val MIN_RADIUS_METERS = 250.0
        private const val MAX_RADIUS_METERS = 10_000.0

        internal fun encodeProfiles(profiles: List<CampusProfile>): JSONArray =
            JSONArray().apply {
                profiles.filterNot { it.builtIn }.forEach { profile ->
                    put(JSONObject().apply {
                        put("id", profile.id)
                        put("name", profile.name)
                        put("centerLat", profile.centerLat)
                        put("centerLon", profile.centerLon)
                        put("detectionRadiusMeters", profile.detectionRadiusMeters)
                    })
                }
            }

        internal fun decodeProfiles(raw: String?): List<CampusProfile> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    val seenIds = mutableSetOf<String>()
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val id = item.optString("id").trim()
                        val name = item.optString("name").trim().take(MAX_NAME_LENGTH)
                        val lat = item.optDouble("centerLat", Double.NaN)
                        val lon = item.optDouble("centerLon", Double.NaN)
                        val radius = item.optDouble(
                            "detectionRadiusMeters",
                            CampusProfiles.DEFAULT_CUSTOM_RADIUS_METERS
                        )
                        if (id.isBlank() || id == CampusProfiles.UPLB_ID || !seenIds.add(id)) continue
                        if (name.isBlank()) continue
                        if (!lat.isFinite() || lat !in -90.0..90.0) continue
                        if (!lon.isFinite() || lon !in -180.0..180.0) continue
                        if (!radius.isFinite() || radius !in MIN_RADIUS_METERS..MAX_RADIUS_METERS) continue
                        add(
                            CampusProfile(
                                id = id,
                                name = name,
                                centerLat = lat,
                                centerLon = lon,
                                detectionRadiusMeters = radius,
                                builtIn = false
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
