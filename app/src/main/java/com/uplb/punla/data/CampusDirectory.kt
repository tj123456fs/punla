package com.uplb.punla.data

data class Building(
    val name: String,
    val aka: String?,
    val lat: Double,
    val lon: Double,
    val directions: String,
    val rooms: List<String>
)

/**
 * Reviewed offline snapshot derived from uplbtools/room-tba.
 *
 * Room data contains upstream contradictions, so location-critical aliases are
 * corrected here and loose prefix guessing is deliberately disabled.
 */
object CampusDirectory {
    const val SOURCE_REPOSITORY = "uplbtools/room-tba"
    const val ROOMS_BLOB = "0da929878eb408b85163dbab1a7900d7e400e17b"
    const val BUILDINGS_BLOB = "161d24cb80c5009eb13c8293a650a69ff027e5f4"
    const val REVIEWED_AT = "2026-08-03"

    val BUILDINGS = listOf(
        Building(
            "AMPED Building", "Alejandro B. Catambay Hall", 14.1618544675108, 121.248874965752,
            "Alejandro B. Catambay Hall. Along Pili Drive, to the right of the EE Auditorium when facing the field.",
            listOf("ABC", "ABC 161", "ABC 162a", "ABC 162b", "ABC 163", "ABC 164", "ABC 165", "ABC 167", "ABC 261", "ABC162a", "ABCR")
        ),
        Building(
            "AFBED Building", null, 14.1615075087803, 121.248975755868,
            "Alejandro B. Catambay Hall area along Pili Drive, beside AMPED and near the EE Auditorium.",
            listOf("AFBED LR1", "AFBED LR2", "AFBED LR3", "AFBED PHYSLAB", "AFBED Physlab", "AFBED_Conference Room", "AFBED_LR1", "AFBED_LR2", "AFBED_LR3", "PhysLab")
        ),
        Building(
            "IABE Building", "Institute of Agricultural and Biosystems Engineering", 14.1635810304916, 121.250519445458,
            "Institute of Agricultural and Biosystems Engineering in the eastern CEAT area.",
            listOf("IABE CONFR", "IABE DO Meeting Room", "IABE LH", "IABE Meeting Room")
        ),
        Building(
            "Agricultural Machinery Testing and Evaluation Center", "AMTEC / Roberto C. Bautista Hall", 14.1629346586829, 121.248845917595,
            "Roberto C. Bautista Hall along Pili Drive, beside the Tau Alpha Sports Complex.",
            listOf("AMTEC")
        ),
        Building(
            "CEAT Building", "College of Engineering and Agro-Industrial Technology", 14.1611645381363, 121.245339953157,
            "Main CEAT complex near the Kanan jeep turn from Pili Drive.",
            listOf("CEAT B-100", "CEAT B-100a", "CEAT B-101a", "CEAT B-101b", "CEAT B-103", "CEAT B-105", "CEAT B-108", "CEAT B-200a", "CEAT B-200b", "CEAT B-201", "CEAT B-202", "CEAT B-203", "CEAT B-205", "CEAT B100", "CEAT B100b", "CEAT SHOP", "CEAT SHOP RM")
        ),
        Building(
            "Civil Engineering Building", "CE Building", 14.1610802306548, 121.245919001731,
            "Small CEAT building in front of the Agricultural Systems Institute.",
            listOf("CE 101", "CE 101A", "CE 101B", "CE 102", "CE 103", "CE CONF RM", "CE LAB")
        ),
        Building(
            "Chemical Engineering Building", null, 14.1613943885943, 121.244917833715,
            "Small CEAT complex in front of PHTRC and beside the main CEAT building.",
            listOf("ChE 1", "ChE 2", "ChE 3", "ChE 4", "ChE 5", "ChE SR", "Unit Ops", "CHE 1", "CHE 2", "CHE 4", "CHE 5", "CHE SR", "CHE 3")
        ),
        Building(
            "Electrical Engineering Building", "EE Building", 14.1615806166946, 121.247839677519,
            "White three-storey building along Pili Drive; houses EE rooms and the EE Auditorium.",
            listOf("EE 100", "EE 103", "EE 104", "EE 202", "EE 203", "EE 302", "EE 303", "EE 304", "EE AUDI")
        ),
        Building(
            "Industrial Engineering Building", null, 14.162785386849, 121.249713351694,
            "Part of CEAT; contains Industrial Engineering lecture rooms and laboratories.",
            listOf("IE 100", "IE 101", "IE 102", "IE 103", "IE 104")
        ),
        Building(
            "Hydraulics Laboratory", "HL Building", 14.1612677958838, 121.245639607745,
            "HL Building behind the Civil Engineering Building.",
            listOf("HL CONRM 2", "HL ConfRm2", "HL-100", "HL-201")
        ),
        Building(
            "Agricultural Systems Institute Building", "ASI", 14.1604548553132, 121.245787546245,
            "Along Pili Drive past ICropS, with a curved driveway in front.",
            listOf("ASI 231", "ASI 233", "ASI 235", "ASI 237", "ASI 238A", "ASI 238B", "ASI 240", "ASI 333", "ASI 336", "ASI A-104", "ASI A-119", "ASI B-126", "ASI B-126 / ASI 333", "ASI B-127", "ASI B-138", "ASI Training Room", "ASR 3", "ASILH B125")
        ),
        Building(
            "ICropS Building", null, 14.1600105942164, 121.244457574763,
            "Institute of Crop Science building in the CAFS cluster.",
            listOf("ICROPS 100", "ICROPS 101", "ICROPS 104", "ICROPS 106", "ICROPS 134", "ICROPS 137", "ICROPS 138", "ICROPS 200", "ICROPS 201", "ICROPS 202", "ICROPS 302", "ICROPS 302 MORPH LAB")
        ),
        Building(
            "CAFS Admin Building", "College of Agriculture and Food Science", 14.1603291775193, 121.245087682828,
            "College of Agriculture and Food Science administrative building and nearby central CAFS rooms.",
            listOf("212B", "237", "A-102", "A-115", "AAVLH", "B-100", "B-201", "B101a", "B200", "BAL LAB 1", "BAL LAB 2", "BALH 1", "BALH 2", "BALH1", "BALH2", "Bal lab 2", "Balh1", "D407", "FCN", "Nursery (FCN)", "SOILS LAB", "Soils Lab")
        ),
        Building(
            "PHTRC Building", "Post-Harvest Training and Research Center", 14.1610179873713, 121.244345029575,
            "Post-Harvest Training and Research Center in the CAFS/CEAT area.",
            listOf("PHTRC", "PHTRC 106", "PHTRC ANNEX")
        ),
        Building(
            "PTCF Building", "Plant Tissue Culture Facility", 14.1598111917239, 121.246022003599,
            "Plant Tissue Culture Facility behind ASI and in front of the CEAT complex.",
            listOf("PTCF Conf. Rm", "PTCF LAB", "PTCF Lec 1")
        ),
        Building(
            "IFST Building", "Institute of Food Science and Technology", 14.1602019821866, 121.244154163232,
            "Institute of Food Science and Technology and its annexes in the CAFS cluster.",
            listOf("IFST 201", "IFST 202", "IFST ANNEX A", "IFST ANNEX B", "IFST ANNEX C", "IFST ANNEX D", "IFST L2/L3", "IFST Pilot Plant", "IFST STL")
        ),
        Building(
            "Animal Husbandry Building", "Animal Husbandry", 14.1587435760786, 121.244444474833,
            "Past the Animal Husbandry Arch, beside Fronda Hall; houses Animal Science lecture halls.",
            listOf("ASR 1", "ANSC LH2", "ASLH 1", "ASLH 2", "ASLH1", "ASLH2", "ASLH 3")
        ),
        Building(
            "Fronda Hall", "Francisco M. Fronda Hall", 14.1582756723431, 121.244087688481,
            "Francisco M. Fronda Hall in the Animal Science complex.",
            listOf("Poultry Lab B", "Fronda GSR", "Fronda Hall -  Graduate Student Room (GSR)", "Fronda Hall -  Poultry Lab B", "Fronda Hall - Graduate Student Room (GSR)", "Fronda Hall RM. 24", "Fronda RM. 24", "FRONDA HALL - ROOM 26", "Room 24 - Fronda Hall")
        ),
        Building(
            "Meat Science Building", "Meat Science", 14.1583636390904, 121.244864897433,
            "Past the Animal Husbandry Arch, beside the Animal Husbandry Building.",
            listOf("MSLR 1", "MSLR 2")
        ),
        Building(
            "Villegas Hall", "Villegas", 14.1586658862821, 121.243394567734,
            "Animal and Dairy Sciences Cluster building behind Vraja Cuisine and beside the CVM area.",
            listOf("iLab")
        ),
        Building(
            "CAS Main Building", "CAS Main", 14.1649124885681, 121.241164856492,
            "Main CAS building behind Oblation; basement rooms use B-codes and LITE rooms are upstairs.",
            listOf("CAS 101", "CAS 102", "CAS 103", "CAS 104", "CAS 106", "CAS 107", "CAS 108", "CAS 109", "CAS 110", "CAS B01", "CAS B02", "CAS B03", "CAS B04", "CAS B05", "CAS B06", "CAS B07", "CAS B08", "CAS B09", "CAS B10", "CAS LITE 4", "CAS LITE 5", "CAS 1043")
        ),
        Building(
            "CAS Annex 1", "CAS A1", 14.1656175129359, 121.24111345933,
            "Tall four-storey building left of Oblation, beside OUR.",
            listOf("CAS A1 - GAL 2", "CAS A1 301", "CAS A1 302", "CAS A1 303", "CAS A1 304", "CAS A1 305", "CAS A1 306", "CAS A1 402", "CAS A1 403", "CAS A1 404", "CAS A1 405", "CAS A1 406", "CAS A1 407", "CAS A1 408", "CAS A1 409", "CAS A1 410", "CAS A1 GAL 1", "CAS A1 GAL 2", "CATL", "NCAS Auditorium")
        ),
        Building(
            "CAS Annex 2", "CAS A2", 14.1656699585313, 121.241791407374,
            "Two-storey building beside CAS Annex 1, to the left of Oblation.",
            listOf("CAS A2 101", "CAS A2 102", "CAS A2 103", "CAS A2 200", "CAS A2 201", "CAS A2 202", "CAS A2 203", "CAS A2 MPH1", "CAS A2- 201", "CAS A2-201", "CAS A200", "CAS A2MPH2", "INSTAT RA2")
        ),
        Building(
            "Biological Sciences Building", "BioSci", 14.1663395408121, 121.240295874232,
            "In front of CAS Annex 1 and left of SEARCA; includes the BS wings and IBS rooms.",
            listOf("BS A-109", "BS A-115", "BS A-121", "BS A-137", "BS A-215", "BS A-309", "BS A-315", "BS B-100", "BS B-101", "BS B-105", "BS B-109", "BS B-307", "BS C-105", "BS C-107", "BS C-112", "BS C-113", "BS C-116", "BS C-117", "BS C-125", "BS C-127", "BS C-205", "BS C-227", "BS C-313", "BS C-321", "BS D-229", "BS E-103", "BS E-213", "IBSLH 3", "IBSLH MAIN", "IBSLH Main", "IBSLH2", "IBSLH3", "MBBLH", "IBS Bioinformatics Laboratory", "IBS Bioinformatics Room", "IBS MBB Teaching Laboratory", "IBS Symbiosis-Bioinformatics Laboratory", "Malacology Lab (Snail Room)", "Malacology Laboratory (BS Snail Room)")
        ),
        Building(
            "IWEP Building", "IWEP", 14.1660627800205, 121.240197867595,
            "Institute of Weed Science, Entomology and Plant Pathology, under the same roof complex as BioSci.",
            listOf("IWEP AUDI", "IWEPLH", "WPD Hall LR108")
        ),
        Building(
            "Physical Sciences Building", "Francisco O. Santos Hall", 14.164378759022, 121.241803648353,
            "Francisco O. Santos Hall to the right of Oblation; contains PSLH, PS, ICS, Physics, Chemistry, and Statistics rooms.",
            listOf("PS ANX-300", "PS ANX-306", "PS B-100", "PS B-101", "PS B-103", "PS B-105", "PS B-200", "PS B-201", "PS B-203", "PS B-205", "PS C-101", "PS C-200c", "PS C-203", "PS C-204", "PS C-206", "PS C-214", "PS C-304", "PS C-305", "PS C-306", "PS C-307", "PS C-309", "PS C-312", "PS C-320", "PS C100", "AVR-ANNEX", "EAA LH", "ICS PC1", "ICS PC2", "ICS PC3", "ICS PC4", "ICS PC5", "ICS PC6", "ICS PC7", "ICS PC8", "ICS PC9", "INSTATLH", "MMM LH", "SMA LH", "PSLH A", "PSLH B", "PSLH 1", "PSLH 2", "PSLH 3", "PSLH 4", "MMMLH")
        ),
        Building(
            "New Math Building", "Mathematics Building", 14.164626812368, 121.243664369023,
            "White three-storey New Mathematics Building behind the Old Math building.",
            listOf("MB 100", "MB 101A", "MB 101B", "MB 102", "MB 103A", "MB 104", "MB 301", "MB 302", "MB 303", "MB 304", "MB 305", "MB 307", "MB 308", "MB 309")
        ),
        Building(
            "Old Math/Old Rural Building", "Old Math", 14.165027748684, 121.244109841168,
            "Old building before the bridge to CEAT, across the Diocesan Shrine of St. Therese.",
            listOf("OLD MATH", "OLD RURAL")
        ),
        Building(
            "CHE Building", "College of Human Ecology", 14.1650539223755, 121.242142561802,
            "College of Human Ecology, Gil F. Saguiguit Hall, beside Physical Sciences.",
            listOf("CHE  MPH", "CHE Conference Room", "CHE MPH", "CHE REC", "CHE Rec", "CHE conference room", "CHE-MPH", "Dean's Office Conference Room DECL Hall", "Dean's Office Conference Room, DECL Hall", "CLH", "DHFDS", "DHFDS GRAD", "ALH", "ACL", "CTL", "CERP Conf Room", "CERP Conference Room", "DCERP  AVR", "DCERP AVR")
        ),
        Building(
            "IHNF Building", "Institute of Human Nutrition and Food", 14.1642085758793, 121.242574438014,
            "Institute of Human Nutrition and Food in the CHE complex.",
            listOf("IHNF Conference Room", "RGQ", "NL", "FL")
        ),
        Building(
            "LHKCB Building", "Landscape Horticulture Knowledge Center Building", 14.1641313825217, 121.241385077738,
            "Landscape Horticulture Knowledge Center Building, in front of the University Police Force headquarters.",
            listOf("LHKCB 101B", "LHKCB 204")
        ),
        Building(
            "CDC Building", "College of Development Communication", 14.167, 121.2426,
            "College of Development Communication building at Carabao Park, in front of the Administration Building.",
            listOf("CDC 209A", "CDC 212B", "CDC ANNEX 04", "CDC ANNEX 2", "CDC ANNEX 3", "CDC ANNEX1", "CDC ANNEX2", "CDC ANNEX3", "CDC ANNEX4", "CDC Annex 01", "CDC Annex4", "CDC Conference Rm", "CDC DECIMU", "CDC GRAD", "CDC GRAD ROOM", "CDC IMU", "CDC LR1", "CDC LR2", "CDC RM 201B", "CDC RM 209A", "CDC RM212B", "CDC Rm209A", "CDC Room 200A", "CDC Room 201B", "RM 209A", "Room 200A", "Room 212B", "DEC-IMU", "IMU", "LR1")
        ),
        Building(
            "CEM Building", "College of Economics and Management", 14.1672360835139, 121.241541791738,
            "College of Economics and Management complex, reached through the Kanan road from the main gate.",
            listOf("CEM  202", "CEM 101", "CEM 102", "CEM 104", "CEM 105", "CEM 106", "CEM 107", "CEM 108", "CEM 109", "CEM 110", "CEM 111", "CEM 112", "CEM 113", "CEM 114", "CEM 115", "CEM 116", "CEM 117", "CEM 118", "CEM 118/ONLINE", "CEM 201", "CEM 202", "CEM 203", "CEM 204", "CEM 205", "CEM FH", "CEM Function Hall", "CEM102", "CEMFH")
        ),
        Building(
            "DAAE Building", "DAAE", 14.1669589170645, 121.241006523796,
            "Department of Agricultural and Applied Economics in the main CEM complex.",
            listOf("DAAE Conference Room 1", "DAAE Conference Room 2")
        ),
        Building(
            "ICOPED Building", "ICOPED", 14.1674706449989, 121.242251383207,
            "Institute of Cooperatives and Bio-Enterprise Development in the CEM area.",
            listOf("ICOPED 26", "ICOPED 28", "ICOPED 30")
        ),
        Building(
            "Graduate School Building", "Graduate School", 14.1640193548794, 121.240810800661,
            "Behind International House, at the foot of the ascent toward UHS and Forestry.",
            listOf("GRAD ROOM", "GS 201", "GS Room 202", "Graduate Room", "Graduate Room 120", "Graduate School", "NEW GS RM 2", "Old GS Conference Room")
        ),
        Building(
            "CFNR Admin Building", "College of Forestry and Natural Resources", 14.1549216472564, 121.235260613878,
            "Lansigan Hall in the Forestry campus, facing a large parking area between FPPS and Forest Science.",
            listOf("CFNR Lansigan, Audi", "CFNR Varrons Room", "CFNR, Manza", "ERSG LAB", "Follosco", "GEOMATICS", "GEOMATICS ROOM", "Human Dimensions Lab", "Varrons Room", "Field")
        ),
        Building(
            "Forest Biological Sciences Building", null, 14.1546257185337, 121.236028864192,
            "Building to the left when facing CFNR Admin, marked FOREST SCIENCE.",
            listOf("FBS 051", "FBS 055", "FBS 060", "FBS 151", "FBS 155", "FBS 155/ FBS 051", "FBS 155/FBS 051", "FBS 157", "FBS 161", "FBS 169", "FBS 171", "FBS 175")
        ),
        Building(
            "IRNR Building", null, 14.1544117274588, 121.235918632779,
            "Institute of Renewable Natural Resources in the Forestry campus.",
            listOf("IRNR 252", "IRNR 253", "IRNR 254", "IRNR 271A", "IRNR 273A", "IRNR 273B", "IRNR 290", "IRNR 291", "IRNR 292", "IRNR Conference Room", "IAF")
        ),
        Building(
            "Forest Products and Paper Science", "FPPS", 14.1556309340559, 121.234706086826,
            "WOOD SCIENCE building in the Forestry campus, to the right when facing CFNR Admin.",
            listOf("FPPS", "FPPS ANNEX", "GTOCHLOA", "SHOREA", "SWIETENIA", "VITEX", "WOOD ANATOMY", "WOOD CHEM")
        ),
        Building(
            "Social Forestry and Forestry Governance", "SFFG", 14.1537160569549, 121.235429169105,
            "FORESTRY INFORMATION building beside IRNR.",
            listOf("SFFG", "SFFG 14", "SFFG 16", "SFFG 7", "SFFG 9")
        ),
        Building(
            "CVM-IAS Communal Building", "CVM-IAS", 14.157706836316, 121.243408338305,
            "Beside Copeland Gym; houses the CVM-IAS Library and communal lecture facilities.",
            listOf("CVM-IAS", "ADSC LH3 (IAS-CVM Building)", "Animal Health Lab", "Animal Health Laboratory", "Animal Nutrition Lecture Room", "Animal Physiology Lecture Room", "CVM LR1", "CVM LR2", "DSDS MLH", "DSDS MLH (Main Lecture Hall)", "DSDS Main Lecture Hall", "DSDS Main Lecture Hall (MLH)", "DSDS SDS Lab", "General Physio Lab (Rm. 100)", "General Physiology Lab (Rm. 100)", "IAS Graduate Room (AND)", "IAS RM. 1", "IAS RM. 2", "IAS Room 1", "IAS-LGIL", "MLH", "SDS Lab", "SDS lab")
        ),
        Building(
            "Basic Veterinary Sciences", "DBVS", 14.158431976843, 121.242938480522,
            "In the CVM cluster, beside Veterinary Paraclinical Sciences.",
            listOf("DBVS", "DBVS Graduate Student Lecture Room", "DBVS LR", "DBVS Lecture Room", "EM/HIS LAB", "Gross Anat Lab", "Pharma Lab", "PHYSIO LAB", "ZOTCLAB BC")
        ),
        Building(
            "Veterinary Paraclinical Sciences Building", "VPCS", 14.1581776084402, 121.243146001042,
            "In front of the CVM-IAS building, beside Basic Veterinary Sciences.",
            listOf("VPCS", "Food Hygiene Laboratory Room")
        ),
        Building(
            "Veterinary Teaching Hospital", "VTH", 14.1732785, 121.2644819,
            "Veterinary Teaching Hospital at the UP Open University campus.",
            listOf("VTH", "DVCS L.A. SURGERY RM", "DVCS LR1", "DVCS S.A. SURGERY RM", "DVCS SURGERY THEATRE", "DVCS Small Animal Surgery Room", "DVCS THERIO LAB", "DVCS ZOTC Rm - Gregorio San Agustin Hall", "THERIOGENOLOGY LABORATORY ROOM", "ZOTCLAB-CL")
        ),
        Building(
            "Copeland Gymnasium", "Copeland Gym", 14.1567119704847, 121.242655337659,
            "Main gymnasium near Raymundo Gate; Wing B is toward the rear parking area.",
            listOf("BADMINTON COURT", "BALLET AREA", "BASKETBALL COURT", "BEACH VOLLEYBALL COURT", "BRIDGE ROOM", "DANCE AREA 1", "DANCE AREA 2", "MARTIAL ARTS AREA", "TABLE TENNIS AREA", "UPPERFIELD", "VOLLEYBALL AREA", "WEIGHT TRAINING AREA", "YOGA ROOM", "LR2A", "LR2B", "LR3B", "LR3A")
        ),
        Building(
            "Baker Hall Pool", "Swimming Pool Area", 14.1621441475055, 121.242556021587,
            "Swimming pool behind Baker Hall at Freedom Park.",
            listOf("SWIMMING POOL")
        ),
        Building(
            "Freedom Park", "F-Park", 14.1617660159005, 121.241457649492,
            "Large central field in front of D.L. Umali Hall and the Student Union Building.",
            listOf("F-PARK", "FREEDOM PARK")
        ),
        Building(
            "Department of Military Sciences and Tactics", "DMST / UPLB ROTC", 14.1606091732855, 121.243254526898,
            "Low building beside Baker Hall; headquarters of the UPLB ROTC unit.",
            listOf("DMST", "ROTC")
        ),
        Building(
            "Temporary Common Classrooms", "TCC", 14.1657489322199, 121.243003124553,
            "Temporary Common Classrooms at the Old Maquiling School, beside CHE and behind the Administration Building.",
            listOf("TCC 1", "TCC 11", "TCC 2", "TCC 22", "TCC 4", "TCC 5")
        ),
        Building(
            "Main Library", "Dean Edelwina C. Legaspi Hall", 14.1655002207618, 121.239240341191,
            "Main Library behind the Pegaraw statue.",
            listOf("Main Library")
        ),
        Building(
            "UPRHS Building", "UP Rural High School", 14.1515129065935, 121.2713489164,
            "UP Rural High School in Bay, Laguna, outside the main UPLB campus.",
            listOf("UPRHS")
        )
    )

    /** Room values that are nonphysical, incomplete, or intentionally unresolved. */
    val UNRESOLVED_ROOM_CODES = setOf(
        "ANLR",
        "ANNEX",
        "Annex 04",
        "Annex 3",
        "Annex 4",
        "Online",
        "TBA"
    )

    private fun exactAliasKey(value: String): String =
        value.trim().replace(Regex("[\\s_]+"), " ")

    private fun foldedAliasKey(value: String): String =
        exactAliasKey(value).uppercase().replace(Regex("[^A-Z0-9]"), "")

    private val exactRoomIndex: Map<String, Building> by lazy {
        buildMap {
            BUILDINGS.forEach { building ->
                building.rooms.forEach { alias -> put(exactAliasKey(alias), building) }
            }
        }
    }

    /** Case-insensitive/punctuation-tolerant aliases are accepted only when unambiguous. */
    private val foldedRoomIndex: Map<String, Building> by lazy {
        val candidates = mutableMapOf<String, MutableSet<Building>>()
        BUILDINGS.forEach { building ->
            building.rooms.forEach { alias ->
                candidates.getOrPut(foldedAliasKey(alias)) { linkedSetOf() }.add(building)
            }
        }
        candidates.mapNotNull { (key, buildings) ->
            buildings.singleOrNull()?.let { key to it }
        }.toMap()
    }

    private val buildingIndex: Map<String, Building> by lazy {
        buildMap {
            BUILDINGS.forEach { building ->
                put(foldedAliasKey(building.name), building)
                building.aka?.let { put(foldedAliasKey(it), building) }
            }
        }
    }

    fun findBuildingForRoom(room: String?): Building? {
        if (room.isNullOrBlank()) return null
        val exact = exactAliasKey(room)
        if (UNRESOLVED_ROOM_CODES.any { exactAliasKey(it).equals(exact, ignoreCase = true) }) return null
        exactRoomIndex[exact]?.let { return it }
        foldedRoomIndex[foldedAliasKey(room)]?.let { return it }
        return buildingIndex[foldedAliasKey(room)]
    }

    /** Useful for regression tests and future data imports. */
    fun crossBuildingFoldedAliasCollisions(): Map<String, Set<String>> {
        val candidates = mutableMapOf<String, MutableSet<String>>()
        BUILDINGS.forEach { building ->
            building.rooms.forEach { alias ->
                candidates.getOrPut(foldedAliasKey(alias)) { linkedSetOf() }.add(building.name)
            }
        }
        return candidates.filterValues { it.size > 1 }.mapValues { it.value.toSet() }
    }
}
