package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single "before classes start" item — enrollment requirements, documents
 * to prepare, clearances, etc. Ships with a built-in default set (see
 * [com.uplb.punla.data.ChecklistDefaults]) that's seeded once on first
 * launch, but every row is a normal editable/deletable entity afterward —
 * there's no hardcoded distinction at the DB level between a built-in item
 * and one the person added themselves, aside from [isCustom] which is purely
 * informational (used to hide a "default" hint once they've edited it).
 */
@Entity(tableName = "checklist_items")
data class ChecklistItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String? = null,
    val checked: Boolean = false,
    val isCustom: Boolean = false,
    val sortOrder: Int = 0
)
