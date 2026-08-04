package com.uplb.punla.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusDirectoryTest {
    private fun building(room: String): Building? = CampusDirectory.findBuildingForRoom(room)

    @Test
    fun canonicalSnapshotContainsAllCurrentBuildings() {
        assertEquals(52, CampusDirectory.BUILDINGS.size)
        assertTrue(CampusDirectory.BUILDINGS.none { it.name == "Agricultural and Biological Chemistry Building" })
        assertTrue(CampusDirectory.BUILDINGS.none { it.name == "CVM Building" })
    }

    @Test
    fun reviewedRoomOverridesResolveCorrectly() {
        assertEquals("AMPED Building", building("ABC 161")?.name)
        assertEquals("Physical Sciences Building", building("PSLH A")?.name)
        assertEquals("Physical Sciences Building", building("PSLH B")?.name)
        assertEquals("Physical Sciences Building", building("PSLH 4")?.name)
        assertEquals("Physical Sciences Building", building("MMMLH")?.name)
        assertEquals("Animal Husbandry Building", building("ASR 1")?.name)
        assertEquals("Animal Husbandry Building", building("ASLH 2")?.name)
        assertEquals("New Math Building", building("MB 301")?.name)
        assertEquals("Hydraulics Laboratory", building("HL-100")?.name)
        assertEquals("Fronda Hall", building("Room 24 - Fronda Hall")?.name)
        assertEquals("Villegas Hall", building("iLab")?.name)
    }

    @Test
    fun humanEcologyAndChemicalEngineeringDoNotCollide() {
        assertEquals("Chemical Engineering Building", building("ChE 1")?.name)
        assertEquals("Chemical Engineering Building", building("CHE 1")?.name)
        assertEquals("CHE Building", building("CHE MPH")?.name)
        assertTrue(CampusDirectory.crossBuildingFoldedAliasCollisions().isEmpty())
    }

    @Test
    fun veterinaryAndForestryRoomsUseSpecificBuildings() {
        assertEquals("Basic Veterinary Sciences", building("DBVS LR")?.name)
        assertEquals("Veterinary Paraclinical Sciences Building", building("Food Hygiene Laboratory Room")?.name)
        assertEquals("Veterinary Teaching Hospital", building("DVCS SURGERY THEATRE")?.name)
        assertEquals("Forest Products and Paper Science", building("WOOD CHEM")?.name)
        assertEquals("Social Forestry and Forestry Governance", building("SFFG 14")?.name)
        assertEquals("Meat Science Building", building("MSLR 1")?.name)
    }

    @Test
    fun canonicalCoordinatesMatchReviewedSnapshot() {
        val iabe = CampusDirectory.BUILDINGS.first { it.name == "IABE Building" }
        assertEquals(14.1635810304916, iabe.lat, 0.000000001)
        assertEquals(121.250519445458, iabe.lon, 0.000000001)

        val ptcf = CampusDirectory.BUILDINGS.first { it.name == "PTCF Building" }
        assertEquals(14.1598111917239, ptcf.lat, 0.000000001)
        assertEquals(121.246022003599, ptcf.lon, 0.000000001)
    }

    @Test
    fun nonphysicalAndUnknownRoomsRemainUnresolved() {
        assertNull(building("TBA"))
        assertNull(building("Online"))
        assertNull(building("ANNEX"))
        assertNull(building("completely unknown room"))
    }
}
