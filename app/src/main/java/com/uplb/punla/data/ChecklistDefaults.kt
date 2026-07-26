package com.uplb.punla.data

import com.uplb.punla.data.entity.ChecklistItem

/**
 * Built-in "before classes start" checklist, seeded once into the
 * `checklist_items` table on first launch (see PunlaViewModel.init).
 * Intentionally UPLB-specific for now — same pattern as CampusDirectory's
 * BUILDINGS list before the multi-university work lands; if/when a second
 * school is added, this should move under a per-school default the same
 * way CampusDirectory's data would.
 *
 * These are starting points, not gospel — every item is a normal editable
 * ChecklistItem row afterward, and the person can rename, delete, or add
 * their own alongside them.
 */
object ChecklistDefaults {
    val ITEMS: List<ChecklistItem> = listOf(
        ChecklistItem(
            title = "Check Certificate of Registration (COR)",
            note = "Confirm your enrolled units and section assignments are correct in CRS/SAIS before the add/drop period.",
            sortOrder = 0
        ),
        ChecklistItem(
            title = "Settle tuition & miscellaneous fees",
            note = "Full or first-installment payment, depending on your plan.",
            sortOrder = 1
        ),
        ChecklistItem(
            title = "Secure UPLB student insurance",
            note = "Usually bundled with miscellaneous fees, but confirm it's active.",
            sortOrder = 2
        ),
        ChecklistItem(
            title = "Get medical/dental clearance",
            note = "Required by UPLB Health Service for incoming and continuing students, especially after a leave or transfer.",
            sortOrder = 3
        ),
        ChecklistItem(
            title = "Prepare or renew student ID",
            note = "Bring valid ID + COR to the Office of Student Affairs if yours is lost, expired, or you're new.",
            sortOrder = 4
        ),
        ChecklistItem(
            title = "Confirm dorm/housing arrangements",
            note = "Dorm application, contract signing, or off-campus lease — whichever applies to you.",
            sortOrder = 5
        ),
        ChecklistItem(
            title = "Buy books & course materials",
            note = "Check your syllabi or course FB groups for required textbooks/readers.",
            sortOrder = 6
        ),
        ChecklistItem(
            title = "Set your class schedule & find your rooms",
            note = "Use the Schedule tab, and check Campus map for unfamiliar buildings ahead of the first day.",
            sortOrder = 7
        )
    )
}
