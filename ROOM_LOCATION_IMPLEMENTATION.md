# Room Location Corrections — Implementation

**Punla version:** 1.4 (`versionCode 5`)  
**Reviewed:** 2026-08-03  
**Reference:** `uplbtools/room-tba`

## Implemented

- Replaced the legacy 39-marker list with all 52 canonical building markers.
- Updated retained coordinates to the current reviewed building snapshot.
- Retired the fabricated ABC marker and obsolete broad CVM marker.
- Added dedicated AMPED, AMTEC, Animal Husbandry, Basic Veterinary Sciences,
  CVM-IAS, DMST, FPPS, Freedom Park, Fronda Hall, Meat Science, Old Math,
  Social Forestry, Veterinary Paraclinical Sciences, Veterinary Teaching
  Hospital, and Villegas Hall markers.
- Applied reviewed room-level overrides for ABC, PSLH, CHE/ChE, ASR/ASLH,
  Math, Hydraulics, Fronda, veterinary, Forestry, Meat Science, and iLab.
- Removed loose prefix guessing. Unknown and nonphysical room codes now return
  no location rather than a misleading pin.

## Snapshot metadata

- Rooms blob: `0da929878eb408b85163dbab1a7900d7e400e17b`
- Buildings blob: `161d24cb80c5009eb13c8293a650a69ff027e5f4`
- Canonical markers: 52
- Explicit room aliases: 541
- Cross-building folded alias collisions: 0

## Validation

`CampusDirectoryTest.kt` covers the high-risk mappings and coordinates. A
standalone Kotlin regression harness also passes against the bundled source.
