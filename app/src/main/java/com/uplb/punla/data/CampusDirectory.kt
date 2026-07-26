package com.uplb.punla.data

data class Building(
    val name: String,
    val aka: String?,
    val lat: Double,
    val lon: Double,
    val directions: String,
    val rooms: List<String>
)

object CampusDirectory {
    val BUILDINGS = listOf(
        Building(
            "Agricultural and Biological Chemistry Building", null, 14.160037897818123, 121.24452978338535,
            "Near the CAFS cluster, behind the Soils Building. Look for the ABC signage.",
            listOf("ABC", "ABC 161", "ABC 162a", "ABC 162b", "ABC 163", "ABC 164", "ABC 165", "ABC 167", "ABC 261", "ABC162a", "ABCR")
        ),
        Building(
            "AFBED Building", null, 14.1625, 121.248,
            "Part of the CEAT complex. Located near the Agricultural and Bio-Resource Engineering area.",
            listOf("AFBED LR1", "AFBED LR2", "AFBED LR3", "AFBED PHYSLAB", "AFBED Physlab", "AFBED_Conference Room", "AFBED_LR1", "AFBED_LR2", "AFBED_LR3")
        ),
        Building(
            "Agricultural Systems Institute Building", null, 14.15908083331246, 121.24371439189271,
            "In the CAFS area, past the crop science buildings. Look for the animal husbandry facilities nearby.",
            listOf("ASI 231", "ASI 233", "ASI 235", "ASI 237", "ASI 238A", "ASI 238B", "ASI 240", "ASI 333", "ASI 336", "ASI A-104", "ASI A-119", "ASI B-126", "ASI B-126 / ASI 333", "ASI B-127", "ASI B-138", "ASI Training Room", "ASR 1", "ASR 3", "ASILH B125")
        ),
        Building(
            "Biological Sciences Building", null, 14.16573858977826, 121.24045282592225,
            "In front of CAS Annex 1, to the left of SEARCA. Has multiple wings (BS A, B, C, D, E) housing the Institute of Biological Sciences.",
            listOf("BS A-109", "BS A-115", "BS A-121", "BS A-137", "BS A-215", "BS A-309", "BS A-315", "BS B-100", "BS B-101", "BS B-105", "BS B-109", "BS B-307", "BS C-105", "BS C-107", "BS C-112", "BS C-113", "BS C-116", "BS C-117", "BS C-125", "BS C-127", "BS C-205", "BS C-227", "BS C-313", "BS C-321", "BS D-229", "BS E-103", "BS E-213", "IBSLH 3", "IBSLH MAIN", "IBSLH Main", "IBSLH2", "IBSLH3", "MBBLH", "MSLR 1", "MSLR 2", "IBS Bioinformatics Laboratory", "IBS Bioinformatics Room", "IBS MBB Teaching Laboratory", "IBS Symbiosis-Bioinformatics Laboratory", "Malacology Lab (Snail Room)", "Malacology Laboratory (BS Snail Room)")
        ),
        Building(
            "CAFS Building", "College of Agriculture and Food Science", 14.160329177519289, 121.24508768282763,
            "College of Agriculture and Food Science cluster. In the upper campus area near the agriculture facilities.",
            listOf("212B", "237", "A-102", "A-115", "AAVLH", "ANSC LH2", "ASLH 1", "ASLH 2", "ASLH1", "ASLH2", "ASLH 3", "B-100", "B-201", "B101a", "B200", "BAL LAB 1", "BAL LAB 2", "BALH 1", "BALH 2", "BALH1", "BALH2", "Bal lab 2", "Balh1", "D407", "FCN", "FPPS ANNEX", "IAF", "Nursery (FCN)", "SOILS LAB", "Soils Lab", "PSLH A", "PSLH B", "Poultry Lab B")
        ),
        Building(
            "CAS Main Building", "CAS Main", 14.164912488568103, 121.24116485649184,
            "The main CAS building is BEHIND the Oblation statue. CAS Bxx rooms are in the basement (stairs in front of OCS kiosks). CAS 1xx rooms are on the first floor. CAS LITE rooms are on the 2nd floor.",
            listOf("CAS 101", "CAS 102", "CAS 103", "CAS 104", "CAS 106", "CAS 107", "CAS 108", "CAS 109", "CAS 110", "CAS B01", "CAS B02", "CAS B03", "CAS B04", "CAS B05", "CAS B06", "CAS B07", "CAS B08", "CAS B09", "CAS B10", "CAS LITE 4", "CAS LITE 5")
        ),
        Building(
            "CAS Annex 1", "CAS A1", 14.165447317040943, 121.24109655582862,
            "The tall 4-floor building to the LEFT of Oble, beside OUR (Office of the University Registrar). Has galleries (GAL) on lower floors and lecture rooms on floors 3-4.",
            listOf("CAS A1 - GAL 2", "CAS A1 301", "CAS A1 302", "CAS A1 303", "CAS A1 304", "CAS A1 305", "CAS A1 306", "CAS A1 402", "CAS A1 403", "CAS A1 404", "CAS A1 405", "CAS A1 406", "CAS A1 407", "CAS A1 408", "CAS A1 409", "CAS A1 410", "CAS A1 GAL 1", "CAS A1 GAL 2", "CATL")
        ),
        Building(
            "CAS Annex 2", "CAS A2", 14.165488927458338, 121.24174028595435,
            "The 2-storey building beside CAS Annex 1, to the left of Oble.",
            listOf("CAS A2 101", "CAS A2 102", "CAS A2 103", "CAS A2 200", "CAS A2 201", "CAS A2 202", "CAS A2 203", "CAS A2 MPH1", "CAS A2- 201", "CAS A2-201", "CAS A200", "CAS A2MPH2", "INSTAT RA2")
        ),
        Building(
            "CDC Building", "College of Development Communication", 14.167, 121.2426,
            "College of Development Communication building. Located at Carabao Park, in front of the Administration Building.",
            listOf("CDC 209A", "CDC 212B", "CDC ANNEX 04", "CDC ANNEX 2", "CDC ANNEX 3", "CDC ANNEX1", "CDC ANNEX2", "CDC ANNEX3", "CDC ANNEX4", "CDC Annex 01", "CDC Annex4", "CDC Conference Rm", "CDC DECIMU", "CDC GRAD", "CDC GRAD ROOM", "CDC IMU", "CDC LR1", "CDC LR2", "CDC RM 201B", "CDC RM 209A", "CDC RM212B", "CDC Rm209A", "CDC Room 200A", "CDC Room 201B", "RM 209A", "Room 200A", "Room 212B", "DEC-IMU", "IMU", "LR1")
        ),
        Building(
            "Civil Engineering Building", null, 14.1618, 121.246,
            "Part of the CEAT complex. Near the main engineering buildings.",
            listOf("CE 101", "CE 101A", "CE 101B", "CE 102", "CE 103", "CE CONF RM", "CE LAB")
        ),
        Building(
            "CEAT Building", "College of Engineering and Agro-Industrial Technology", 14.1613, 121.2454,
            "College of Engineering and Agro-Industrial Technology complex. The CEAT shops and labs are here.",
            listOf("CEAT B-100", "CEAT B-100a", "CEAT B-101a", "CEAT B-101b", "CEAT B-103", "CEAT B-105", "CEAT B-108", "CEAT B-200a", "CEAT B-200b", "CEAT B-201", "CEAT B-202", "CEAT B-203", "CEAT B-205", "CEAT B100", "CEAT B100b", "CEAT SHOP", "CEAT SHOP RM")
        ),
        Building(
            "CEM Building", "College of Economics and Management", 14.166660123648663, 121.24090736444153,
            "College of Economics and Management. The first cluster of buildings if you follow the Kanan road from the main gate. A large building complex with many lecture rooms (CEM 101-118, 201-205).",
            listOf("CEM  202", "CEM 101", "CEM 102", "CEM 104", "CEM 105", "CEM 106", "CEM 107", "CEM 108", "CEM 109", "CEM 110", "CEM 111", "CEM 112", "CEM 113", "CEM 114", "CEM 115", "CEM 116", "CEM 117", "CEM 118", "CEM 118/ONLINE", "CEM 201", "CEM 202", "CEM 203", "CEM 204", "CEM 205", "CEM FH", "CEM Function Hall", "CEM102", "CEMFH")
        ),
        Building(
            "CFNR Building", "College of Forestry and Natural Resources", 14.1543, 121.2353,
            "College of Forestry and Natural Resources. Located in the upper campus. Has multiple buildings and facilities.",
            listOf("CFNR Lansigan, Audi", "CFNR Varrons Room", "CFNR, Manza", "ERSG LAB", "Follosco", "GEOMATICS", "GEOMATICS ROOM", "GTOCHLOA", "Human Dimensions Lab", "SFFG 14", "SFFG 16", "SFFG 7", "SFFG 9", "SHOREA", "SWIETENIA", "VITEX", "Varrons Room", "WOOD ANATOMY", "WOOD CHEM", "Field")
        ),
        Building(
            "CHE Building", "College of Human Ecology", 14.164995709591306, 121.241894417301,
            "College of Human Ecology (Gil F. Saguiguit Hall). Located beside Physical Sciences, on the left side of OPark when facing the Oblation.",
            listOf("CHE  MPH", "CHE 1", "CHE 2", "CHE 4", "CHE 5", "CHE Conference Room", "CHE MPH", "CHE REC", "CHE Rec", "CHE SR", "CHE conference room", "CHE-MPH", "Dean's Office Conference Room DECL Hall", "Dean's Office Conference Room, DECL Hall", "CLH", "DHFDS", "DHFDS GRAD", "ALH", "ACL", "CTL", "CERP Conf Room", "CERP Conference Room", "DCERP  AVR", "DCERP AVR")
        ),
        Building(
            "Chemical Engineering Building", null, 14.1608, 121.245,
            "In the CEAT complex, has specialized labs including Unit Operations. Near the other engineering buildings.",
            listOf("ChE 1", "ChE 2", "ChE 3", "ChE 4", "ChE 5", "ChE SR", "Unit Ops")
        ),
        Building(
            "CVM Building", "College of Veterinary Medicine", 14.15892049473242, 121.24313896221082,
            "College of Veterinary Medicine. Located behind the Carillon Tower. Has multiple facilities including Fronda Hall.",
            listOf("ADSC LH3 (IAS-CVM Building)", "Animal Health Lab", "Animal Health Laboratory", "Animal Nutrition Lecture Room", "Animal Physiology Lecture Room", "CVM LR1", "CVM LR2", "DSDS MLH", "DSDS MLH (Main Lecture Hall)", "DSDS Main Lecture Hall", "DSDS Main Lecture Hall (MLH)", "DSDS SDS Lab", "EM/HIS LAB", "Food Hygiene Laboratory Room", "Fronda GSR", "Fronda Hall -  Graduate Student Room (GSR)", "Fronda Hall -  Poultry Lab B", "Fronda Hall - Graduate Student Room (GSR)", "Fronda Hall RM. 24", "Fronda RM. 24", "FRONDA HALL - ROOM 26", "General Physio Lab (Rm. 100)", "General Physiology Lab (Rm. 100)", "Gross Anat Lab", "IAS Graduate Room (AND)", "IAS RM. 1", "IAS RM. 2", "IAS Room 1", "IAS-LGIL", "MLH", "PHYSIO LAB", "PhysLab", "Pharma Lab", "SDS Lab", "SDS lab", "THERIOGENOLOGY LABORATORY ROOM", "ZOTCLAB BC", "ZOTCLAB-CL", "DBVS Graduate Student Lecture Room", "DBVS LR", "DBVS Lecture Room", "DVCS L.A. SURGERY RM", "DVCS LR1", "DVCS S.A. SURGERY RM", "DVCS SURGERY THEATRE", "DVCS Small Animal Surgery Room", "DVCS THERIO LAB", "DVCS ZOTC Rm - Gregorio San Agustin Hall")
        ),
        Building(
            "DAAE Building", null, 14.167117835374897, 121.24073570307466,
            "Department of Agricultural and Applied Economics. Part of the CEM cluster.",
            listOf("DAAE Conference Room 1", "DAAE Conference Room 2")
        ),
        Building(
            "Electrical Engineering Building", "Dante De Padua Hall (CEAT Administrative Building)", 14.161580616694565, 121.24783967751902,
            "White, three-storey building beside ICrops in Pili Drive. Part of CEAT complex. Houses EE lecture rooms and the EE Auditorium.",
            listOf("EE 100", "EE 103", "EE 104", "EE 202", "EE 203", "EE 302", "EE 303", "EE 304", "EE AUDI")
        ),
        Building(
            "Forestry Biological Sciences Building", null, 14.1548, 121.2358,
            "In the CFNR area in the upper campus. Contains FBS labs and lecture rooms.",
            listOf("FBS 051", "FBS 055", "FBS 060", "FBS 151", "FBS 155", "FBS 155/ FBS 051", "FBS 155/FBS 051", "FBS 157", "FBS 161", "FBS 169", "FBS 171", "FBS 175")
        ),
        Building(
            "Graduate School Building", null, 14.1638, 121.239,
            "Behind the International House dormitory, at the foot of the ascent going to UHS and Forestry.",
            listOf("GRAD ROOM", "GS 201", "GS Room 202", "Graduate Room", "Graduate Room 120", "Graduate School", "NEW GS RM 2", "Old GS Conference Room")
        ),
        Building(
            "Hydrology Building", "HL Building", 14.162790342169593, 121.24949043285048,
            "The Hydrology Laboratory. Located in the CEAT area, near the irrigation and water resources facilities.",
            listOf("HL CONRM 2", "HL ConfRm2", "HL-100", "HL-201")
        ),
        Building(
            "IABE Building", null, 14.162, 121.2475,
            "Institute of Agricultural and Biosystems Engineering. In the CEAT area.",
            listOf("IABE CONFR", "IABE DO Meeting Room", "IABE LH", "IABE Meeting Room")
        ),
        Building(
            "ICOPED Building", null, 14.167409105993643, 121.24232357078375,
            "Institute of Cooperatives and Bio-Enterprise Development. Part of the CEM area.",
            listOf("ICOPED 26", "ICOPED 28", "ICOPED 30")
        ),
        Building(
            "ICropS Building", null, 14.160418508015592, 121.24579971346282,
            "Institute of Crop Science building. In the CAFS cluster.",
            listOf("ICROPS 100", "ICROPS 101", "ICROPS 104", "ICROPS 106", "ICROPS 134", "ICROPS 137", "ICROPS 138", "ICROPS 200", "ICROPS 201", "ICROPS 202", "ICROPS 302", "ICROPS 302 MORPH LAB")
        ),
        Building(
            "Industrial Engineering Building", null, 14.162249399713152, 121.24760215793597,
            "Part of CEAT. Contains IE lecture rooms and labs.",
            listOf("IE 100", "IE 101", "IE 102", "IE 103", "IE 104")
        ),
        Building(
            "IFST Building", null, 14.160501730685995, 121.24442642252787,
            "Institute of Food Science and Technology. In the CAFS cluster, has main building and several annexes (A, B, C, D) including pilot plant facilities.",
            listOf("IFST 201", "IFST 202", "IFST ANNEX A", "IFST ANNEX B", "IFST ANNEX C", "IFST ANNEX D", "IFST L2/L3", "IFST Pilot Plant", "IFST STL")
        ),
        Building(
            "IHNF Building", null, 14.164995709592109, 121.2427098088592,
            "Institute of Human Nutrition and Food. Part of the CHE building complex.",
            listOf("IHNF Conference Room", "RGQ", "NL", "FL")
        ),
        Building(
            "IRNR Building", null, 14.1538, 121.2348,
            "Institute of Renewable Natural Resources. Part of the CFNR complex in the upper campus.",
            listOf("IRNR 252", "IRNR 253", "IRNR 254", "IRNR 271A", "IRNR 273A", "IRNR 273B", "IRNR 290", "IRNR 291", "IRNR 292", "IRNR Conference Room")
        ),
        Building(
            "IWEP Building", null, 14.165827918146277, 121.2406498724568,
            "Institute of Weed Science, Entomology and Plant Pathology. In the CAFS area, includes the WPD Hall.",
            listOf("IWEP AUDI", "IWEPLH", "WPD Hall LR108")
        ),
        Building(
            "LHKCB Building", null, 14.160501730680284, 121.24438350730719,
            "Near the CAFS area. Has lecture halls and classroom facilities.",
            listOf("LHKCB 101B", "LHKCB 204")
        ),
        Building(
            "Math Building", null, 14.165411814244841, 121.24361103115628,
            "The old-looking building on the left before the bridge to CEAT, just across the Diocesan Shrine of St. Therese. Houses IMSP lecture rooms (MB 100-104 on ground floor, MB 301-309 on 3rd floor).",
            listOf("MB 100", "MB 101A", "MB 101B", "MB 102", "MB 103A", "MB 104", "MB 301", "MB 302", "MB 303", "MB 304", "MB 305", "MB 307", "MB 308", "MB 309")
        ),
        Building(
            "Copeland Gymnasium", "Copeland Gym", 14.157297635860596, 121.2427098088592,
            "The main gym facility. From the main gate, it's on the right side before reaching the academic buildings. Has 3 floors - Wing B is towards the parking at the back. Contains various sports facilities.",
            listOf("BADMINTON COURT", "BALLET AREA", "BASKETBALL COURT", "BEACH VOLLEYBALL COURT", "BRIDGE ROOM", "DANCE AREA 1", "DANCE AREA 2", "MARTIAL ARTS AREA", "TABLE TENNIS AREA", "UPPERFIELD", "VOLLEYBALL AREA", "WEIGHT TRAINING AREA", "YOGA ROOM", "LR2A", "LR2B", "LR3B")
        ),
        Building(
            "Baker Hall Pool", "Swimming Pool Area", 14.162253497902821, 121.24266929817223,
            "The swimming pool area, located behind Baker Hall, which is at Freedom Park. Managed by the Department of Human Kinetics (CAS).",
            listOf("SWIMMING POOL")
        ),
        Building(
            "PHTRC Building", null, 14.161542011486866, 121.24429767662376,
            "Post-Harvest Training and Research Center. In the CAFS area.",
            listOf("PHTRC", "PHTRC 106", "PHTRC ANNEX")
        ),
        Building(
            "Physical Sciences Building", "Francisco O. Santos Hall", 14.16449638298952, 121.24167984077903,
            "Francisco O. Santos Hall. A wide building to the RIGHT of Oble. Has PSLH (Physical Science Lecture Halls) A and B, plus many PS rooms.",
            listOf("PS ANX-300", "PS ANX-306", "PS B-100", "PS B-101", "PS B-103", "PS B-105", "PS B-200", "PS B-201", "PS B-203", "PS B-205", "PS C-101", "PS C-200c", "PS C-203", "PS C-204", "PS C-206", "PS C-214", "PS C-304", "PS C-305", "PS C-306", "PS C-307", "PS C-309", "PS C-312", "PS C-320", "PS C100", "AVR-ANNEX", "EAA LH", "ICS PC1", "ICS PC2", "ICS PC3", "ICS PC4", "ICS PC5", "ICS PC6", "ICS PC7", "ICS PC8", "ICS PC9", "INSTATLH", "MMM LH", "SMA LH")
        ),
        Building(
            "PTCF Building", null, 14.156, 121.241,
            "Post-harvest Training and Research Center Facility. In the CAFS area.",
            listOf("PTCF Conf. Rm", "PTCF LAB", "PTCF Lec 1")
        ),
        Building(
            "TCC Building", null, 14.16574893221993, 121.24288733602698,
            "Temporary Common Classroom at the Old Maquiling School. Located beside the CHE building, behind the Administration Building.",
            listOf("TCC 1", "TCC 11", "TCC 2", "TCC 22", "TCC 4", "TCC 5")
        ),
        Building(
            "Main Library", "Dean Edelwina C. Legaspi Hall", 14.165370203813325, 121.23919075095945,
            "The main library building. Located behind the Pegaraw statue (winged tamaraw).",
            listOf("Main Library")
        ),
        Building(
            "UPRHS Building", null, 14.15151290659347, 121.27134891640044,
            "UP Rural High School. Located in Bay, Laguna, OUTSIDE the UPLB main campus.",
            listOf("UPRHS")
        )
    )

    private fun normRoom(s: String): String {
        return s.uppercase().replace(Regex("[^A-Z0-9]"), "")
    }

    private fun roomPrefix(s: String): String {
        val norm = normRoom(s)
        val match = Regex("^[A-Z]+").find(norm)
        return match?.value ?: ""
    }

    private val GENERIC_ROOM_PREFIXES = setOf(
        "ROOM", "RM", "LAB", "LR", "CONF", "CONFERENCE", "HALL", "MPH", "GRAD", "AUDI",
        "MAIN", "ANNEX", "BUILDING", "CENTER", "FH", "SR", "REC", "AREA", "COURT", "FIELD",
        "OFFICE", "CLASSROOM"
    )

    fun findBuildingForRoom(room: String?): Building? {
        if (room.isNullOrBlank()) return null
        val norm = normRoom(room)
        
        // Exact Match
        BUILDINGS.forEach { b ->
            b.rooms.forEach { r ->
                if (normRoom(r) == norm) return b
            }
        }

        // Prefix Match (if prefix maps to exactly one building)
        val pfx = roomPrefix(room)
        if (pfx.isNotBlank() && pfx !in GENERIC_ROOM_PREFIXES) {
            val matchingBuildings = BUILDINGS.filter { b ->
                b.rooms.any { r -> roomPrefix(r) == pfx }
            }
            if (matchingBuildings.size == 1) {
                return matchingBuildings.first()
            }
        }

        // Fallback name contains match
        val matchedByName = BUILDINGS.firstOrNull { b ->
            norm.contains(normRoom(b.name)) || (b.aka != null && norm.contains(normRoom(b.aka)))
        }
        
        return matchedByName
    }
}
