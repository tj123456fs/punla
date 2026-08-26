package com.uplb.punla.data.entity

import androidx.room.Entity

/**
 * Tracks imported Punla interchange files by both their declared file type and
 * unique content ID. This is intentionally separate from decks/quizzes so the
 * same safety system can be reused by future JSON importers.
 */
@Entity(tableName = "json_import_records", primaryKeys = ["fileType", "contentId"])
data class JsonImportRecord(
    val fileType: String,
    val contentId: String,
    val importedAt: Long = System.currentTimeMillis(),
    val destinationId: String? = null
)
