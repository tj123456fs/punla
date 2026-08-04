# Punla Room and Building Location Audit

**Audit date:** August 3, 2026  
**Punla build checked:** Session 19 – Full Theme Collection  
**Punla file checked:** `app/src/main/java/com/uplb/punla/data/CampusDirectory.kt`  
**Reference repository:** `uplbtools/room-tba` (`main`)  
**Reference files:**

- `exports/deep-research/rooms.json` — blob `0da929878eb408b85163dbab1a7900d7e400e17b`
- `exports/deep-research/buildings.json` — blob `161d24cb80c5009eb13c8293a650a69ff027e5f4`
- `public/room_info.json`

## 1. Executive summary

Punla Session 19 currently contains:

- **39 building markers**
- **517 room aliases**
- **5 cross-building normalized-name collisions**

The current `room-tba` building export contains **52 building markers**. Relative to it, Punla has:

- **15 current buildings completely omitted**
- **2 obsolete broad/fabricated building markers** that no longer exist as current canonical buildings
- **32 retained markers displaced by at least 10 meters**
- **20 markers displaced by at least 50 meters**
- **10 markers displaced by at least 100 meters**
- **5 guaranteed CHE/ChE collisions** caused by normalization
- Multiple room assignments copied from contradictory or outdated upstream records

A direct import from `rooms.json` is also unsafe because the current repository has internal contradictions. Punla needs a canonical import plus a reviewed override table.

---

## 2. Confirmed Punla room-to-building errors

### 2.1 ABC rooms are assigned to a non-current building

Punla places these 11 aliases at an `Agricultural and Biological Chemistry Building` marker near CAFS:

- `ABC`
- `ABC 161`
- `ABC 162a`
- `ABC 162b`
- `ABC 163`
- `ABC 164`
- `ABC 165`
- `ABC 167`
- `ABC 261`
- `ABC162a`
- `ABCR`

The current repository assigns all of them to **AMPED Building** at:

- **14.1618544675108, 121.248874965752**

**Required action:** Remove the fabricated ABC marker and move all 11 aliases to AMPED.

### 2.2 CVM rooms use an obsolete broad marker

Punla places **49 room aliases** under `CVM Building` at:

- **14.15892049473242, 121.24313896221082**

`CVM Building` is absent from the current canonical building export. The current room export assigns these aliases mainly to **CVM-IAS Communal Building** at:

- **14.157706836316, 121.243408338305**

This is about **138 m** from Punla's marker.

Affected groups include:

- `CVM LR1`, `CVM LR2`
- `DBVS ...`
- `DVCS ...`
- `DSDS ...`
- `Animal Health ...`
- `Animal Nutrition ...`
- `Animal Physiology ...`
- `PHYSIO LAB`, `PhysLab`
- `Fronda ...`
- `SDS Lab`
- `ZOTCLAB ...`
- surgery, anatomy, physiology, pharmacology, and theriogenology rooms

**Required action:** Replace the obsolete broad marker. Initially follow current canonical mappings, then split named rooms into the newer veterinary buildings through reviewed overrides.

### 2.3 `ASR 1` is assigned to the wrong building

- **Punla:** Agricultural Systems Institute Building
- **Current repository:** Animal Husbandry Building

Animal Husbandry Building coordinates:

- **14.1587435760786, 121.244444474833**

### 2.4 Physics lecture halls appear in CAFS

Punla assigns:

- `PSLH A`
- `PSLH B`

to CAFS Admin Building.

The upstream room records are themselves contradictory: their `building_name` says CAFS Admin Building, while their directions explicitly say they are inside **Physical Sciences Building**. Physical Sciences Building is at:

- **14.164378759022, 121.241803648353**

**Required override:** Force `PSLH A` and `PSLH B` to Physical Sciences Building.

Also add reviewed aliases where encountered, such as:

- `PSLH 1`
- `PSLH 2`
- `PSLH 3`
- `PSLH 4`
- `MMMLH`
- `MMM LH`

Do not use a generic `PHYS` rule because `AFBED PHYSLAB`, `PHYSIO LAB`, and `PhysLab` refer to other facilities.

### 2.5 CHE and ChE collide

Punla normalization uppercases and removes punctuation:

```text
CHE 1 -> CHE1
ChE 1 -> CHE1
```

The following five pairs collide:

| Normalized key | Human Ecology alias | Chemical Engineering alias |
|---|---|---|
| `CHE1` | `CHE 1` | `ChE 1` |
| `CHE2` | `CHE 2` | `ChE 2` |
| `CHE4` | `CHE 4` | `ChE 4` |
| `CHE5` | `CHE 5` | `ChE 5` |
| `CHESR` | `CHE SR` | `ChE SR` |

Because CHE Building appears first, the Chemical Engineering aliases can resolve to Human Ecology.

The upstream source is also contradictory: `CHE 1`, `CHE 2`, `CHE 4`, `CHE 5`, and `CHE SR` have `building_name: CHE Building` while their college/division fields identify Chemical Engineering.

**Required action:** Use explicit case-preserving aliases and reviewed overrides. Do not normalize `CHE` and `ChE` into one identifier.

### 2.6 Math rooms use an outdated/generalized marker

Punla puts all `MB ...` rooms under `Math Building`. The current repository names the canonical destination **New Math Building** at:

- **14.164626812368, 121.243664369023**

Punla's point is about **87 m** away and is closer to the old-math area.

Affected aliases include `MB 100`, `MB 101A`, `MB 101B`, `MB 102`, `MB 103A`, `MB 104`, and `MB 301–309`.

### 2.7 Hydrology/Hydraulics rooms use the wrong marker

Punla uses `Hydrology Building` at:

- **14.162790342169593, 121.24949043285048**

The current canonical building is **Hydraulics Laboratory** at:

- **14.1612677958838, 121.245639607745**

The error is about **448 m**.

Affected aliases:

- `HL CONRM 2`
- `HL ConfRm2`
- `HL-100`
- `HL-201`

---

## 3. Highest-priority coordinate corrections

| Punla marker | Current canonical marker | Approx. error |
|---|---|---:|
| PTCF Building | PTCF Building | **688 m** |
| LHKCB Building | LHKCB Building | **517 m** |
| Hydrology Building | Hydraulics Laboratory | **448 m** |
| IABE Building | IABE Building | **370 m** |
| Agricultural Systems Institute Building | Agricultural Systems Institute Building | **271 m** |
| Industrial Engineering Building | Industrial Engineering Building | **235 m** |
| Graduate School Building | Graduate School Building | **197 m** |
| AFBED Building | AFBED Building | **152 m** |
| ICropS Building | ICropS Building | **152 m** |
| IRNR Building | IRNR Building | **138 m** |
| CEM Building | CEM Building | **94 m** |
| IHNF Building | IHNF Building | **89 m** |
| Math Building | New Math Building | **87 m** |
| Civil Engineering Building | Civil Engineering Building | **81 m** |
| CFNR Building | CFNR Admin Building | **69 m** |
| Biological Sciences Building | Biological Sciences Building | **69 m** |
| Chemical Engineering Building | Chemical Engineering Building | **67 m** |
| Copeland Gymnasium | Copeland Gymnasium | **65 m** |
| PHTRC Building | PHTRC Building | **58 m** |
| IWEP Building | IWEP Building | **55 m** |

Additional corrections below 50 m still exist for IFST, DAAE, Forest Biological Sciences, CHE, CAS Annexes 1 and 2, Physical Sciences, Baker Hall Pool, CEAT, Main Library, Temporary Common Classrooms, and ICOPED.

### Exact coordinates for the largest errors

| Building | Correct latitude | Correct longitude |
|---|---:|---:|
| PTCF Building | 14.1598111917239 | 121.246022003599 |
| LHKCB Building | 14.1641313825217 | 121.241385077738 |
| Hydraulics Laboratory | 14.1612677958838 | 121.245639607745 |
| IABE Building | 14.1635810304916 | 121.250519445458 |
| Agricultural Systems Institute Building | 14.1604548553132 | 121.245787546245 |
| Industrial Engineering Building | 14.162785386849 | 121.249713351694 |
| Graduate School Building | 14.1640193548794 | 121.240810800661 |
| AFBED Building | 14.1615075087803 | 121.248975755868 |
| ICropS Building | 14.1600105942164 | 121.244457574763 |
| IRNR Building | 14.1544117274588 | 121.235918632779 |

---

## 4. Current canonical buildings missing from Punla

Punla has no dedicated marker for these 15 current buildings:

1. AMPED Building
2. Agricultural Machinery Testing and Evaluation Center
3. Animal Husbandry Building
4. Basic Veterinary Sciences
5. CVM-IAS Communal Building
6. Department of Military Sciences and Tactics
7. Forest Products and Paper Science
8. Freedom Park
9. Fronda Hall
10. Meat Science Building
11. Old Math/Old Rural Building
12. Social Forestry and Forestry Governance
13. Veterinary Paraclinical Sciences Building
14. Veterinary Teaching Hospital
15. Villegas Hall

Two Punla markers should be retired or replaced:

- `Agricultural and Biological Chemistry Building`
- `CVM Building`

Several names also need canonical renaming:

- `CAFS Building` -> `CAFS Admin Building`
- `CFNR Building` -> `CFNR Admin Building`
- `Forestry Biological Sciences Building` -> `Forest Biological Sciences Building`
- `Hydrology Building` -> `Hydraulics Laboratory`
- `Math Building` -> `New Math Building` for MB rooms
- `TCC Building` -> `Temporary Common Classrooms`

---

## 5. Current repository inconsistencies that require overrides

These problems exist in `room-tba` itself. Punla should not blindly copy them.

### 5.1 PSLH contradiction

`PSLH A` and `PSLH B` say `CAFS Admin Building`, but their directions explicitly place them in Physical Sciences Building.

**Override:** Physical Sciences Building.

### 5.2 CHE/ChE contradiction

`CHE 1`, `CHE 2`, `CHE 4`, `CHE 5`, and `CHE SR` say CHE Building while their division says Department of Chemical Engineering.

**Override:** Review against actual schedules/signage and preserve the capitalization distinction between `CHE` and `ChE`.

### 5.3 ASLH contradiction and duplicates

The file contains older `ASLH 1` and `ASLH 2` entries assigned to CAFS Admin Building and newer duplicate records with no building. Their directions describe rooms near/inside the Animal Husbandry area.

**Override candidate:** Animal Husbandry Building, pending campus verification.

`ASR 1` is already explicitly assigned to Animal Husbandry Building and should be moved there in Punla.

### 5.4 Fronda rooms do not use the Fronda Hall marker

The building export contains Fronda Hall, but room records such as these still point to CVM-IAS Communal Building:

- `Fronda GSR`
- `Fronda Hall - Graduate Student Room (GSR)`
- `Fronda Hall RM. 24`
- `Fronda RM. 24`
- `FRONDA HALL - ROOM 26`
- `Room 24 - Fronda Hall`
- `Fronda Hall - Poultry Lab B`

**Override candidate:** Fronda Hall.

### 5.5 Veterinary buildings exist but rooms remain grouped under CVM-IAS

The building export contains:

- Basic Veterinary Sciences
- Veterinary Paraclinical Sciences Building
- CVM-IAS Communal Building
- Veterinary Teaching Hospital

However, many `DBVS`, paraclinical, anatomy, pharmacology, and clinical aliases remain under CVM-IAS Communal Building.

**Action:** Keep the current canonical mapping as a temporary fallback, then create verified room-level overrides.

### 5.6 Forestry buildings exist but rooms remain under CFNR Admin

The export includes dedicated markers for:

- Social Forestry and Forestry Governance
- Forest Products and Paper Science
- Forest Biological Sciences
- IRNR

Yet several `SFFG`, `FPPS`, `WOOD`, `SHOREA`, `SWIETENIA`, and related aliases remain grouped under CFNR Admin or CAFS Admin.

**Action:** Verify and split these aliases instead of preserving one broad CFNR marker.

### 5.7 `iLab` contradiction

`iLab` is assigned to AMPED Building, but its directions say it is in Villegas Hall.

**Override candidate:** Villegas Hall, pending verification.

### 5.8 Source-only oddities to avoid treating as trusted locations

- `TBA` is explicitly not a physical room.
- `Online` has no building.
- `ANLR`, `ANNEX`, `Annex 04`, `Annex 3`, `Annex 4`, and `LR3A` have no canonical building.
- `CAS 1043` appears to be a likely data-entry typo replacing or conflicting with `CAS 104`.

These should remain unresolved rather than being guessed through prefixes.

---

## 6. Room codes present upstream but absent from Punla

Observed current records missing from the explicit Punla alias list include:

- `ANLR`
- `ANNEX`
- `Annex 04`
- `Annex 3`
- `Annex 4`
- `CDC RM201B`
- `LR3A`
- `Online`
- `TBA`
- `Room 24 - Fronda Hall`
- `iLab`
- `NCAS Auditorium`
- `CAS 1043`

Some are intentionally unmapped; others need aliases or correction. Punla currently includes `CAS 104`, while the current export contains `CAS 1043`, which should be reviewed as a likely typo rather than copied automatically.

---

## 7. Resolver defects in `CampusDirectory.kt`

### 7.1 First-match behavior silently decides collisions

Exact matching loops through buildings in list order and immediately returns the first normalized match. This is why CHE wins over ChE.

### 7.2 Normalization destroys meaningful capitalization

The resolver uses:

```kotlin
s.uppercase().replace(Regex("[^A-Z0-9]"), "")
```

This removes the only distinction between Human Ecology `CHE` and Chemical Engineering `ChE`.

### 7.3 Prefix extraction happens after separators are removed

For example:

- `PSLH A` becomes `PSLHA`
- `PSLH B` becomes `PSLHB`
- `PSLH 1` becomes prefix `PSLH`

This means the two letter-suffix aliases do not establish a usable `PSLH` prefix for numbered variants. Prefix matching is inconsistent across room formats.

### 7.4 Unique-prefix guessing can amplify stale data

Once a prefix appears under only one outdated broad marker, any unknown room with that prefix may be sent there. Examples of risky families include:

- `ABC...`
- `ASLH...`
- `DBVS...`
- `DVCS...`
- `FRONDA...`
- `SFFG...`
- `FPPS...`
- `HL...`

### 7.5 Unknown and source-null rooms should not be guessed

Rooms with no upstream building should return an unresolved state with directions or a warning. They should not be routed based on college, division, or loose prefix similarity.

---

## 8. Recommended replacement design

### 8.1 Use canonical IDs

Represent buildings with a stable ID rather than using display names as keys.

```kotlin
data class CampusBuilding(
    val id: String,
    val canonicalName: String,
    val lat: Double,
    val lon: Double,
    val aliases: Set<String>
)
```

### 8.2 Separate three data layers

1. **Canonical building export** — coordinates and official/current building names
2. **Raw room export** — source room-to-building assignments
3. **Punla override table** — reviewed corrections for upstream contradictions

### 8.3 Use exact aliases first and conservative matching second

Recommended order:

1. Exact case-preserving alias
2. Explicit normalized alias that has no collision
3. Reviewed override
4. Canonical building name/alias match
5. Unresolved result

Avoid prefix guessing for location-critical navigation.

### 8.4 Add regression tests

Minimum required tests:

- `ABC 161` -> AMPED Building
- `PSLH A` -> Physical Sciences Building
- `PSLH B` -> Physical Sciences Building
- `ChE 1` -> Chemical Engineering Building
- `CHE MPH` -> CHE Building
- `ASR 1` -> Animal Husbandry Building
- `MB 301` -> New Math Building
- `HL-100` -> Hydraulics Laboratory
- `TBA` -> unresolved/nonphysical
- Unknown room -> unresolved, never guessed
- No two explicit aliases may normalize to the same key across different buildings unless an intentional disambiguation rule exists

### 8.5 Version the snapshot

Store source metadata with the bundled map data:

```text
source_repo: uplbtools/room-tba
rooms_blob: 0da929878eb408b85163dbab1a7900d7e400e17b
buildings_blob: 161d24cb80c5009eb13c8293a650a69ff027e5f4
reviewed_at: 2026-08-03
```

---

## 9. Recommended implementation order

1. Fix the 5 CHE/ChE collisions.
2. Override PSLH A/B to Physical Sciences.
3. Replace ABC with AMPED.
4. Replace obsolete CVM marker with CVM-IAS and prepare veterinary overrides.
5. Correct all markers displaced by more than 100 m.
6. Add the 15 missing current buildings.
7. Move `ASR 1` to Animal Husbandry.
8. Rename and relocate Math and Hydraulics.
9. Add unresolved handling for null/TBA/Online records.
10. Add automated mapping and coordinate regression tests.
11. Review ambiguous upstream assignments with UPLB student verification before publishing them as navigation truth.

