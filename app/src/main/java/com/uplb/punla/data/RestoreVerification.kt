package com.uplb.punla.data

import android.content.Context

/**
 * Small local receipt proving that a backup restore reached the verified commit path.
 *
 * This intentionally stores only counts/backup metadata, never study content.
 */
data class RestoreReceipt(
    val verifiedAt: Long,
    val backupVersion: Int,
    val contentId: String?,
    val exportedAt: String?,
    val classes: Int,
    val deadlines: Int,
    val attendance: Int,
    val flashcards: Int,
    val quizzes: Int,
    val studyItems: Int
)

object RestoreVerification {
    private const val PREFS = "punla_restore_verification"
    private const val KEY_VERIFIED_AT = "verified_at"
    private const val KEY_VERSION = "backup_version"
    private const val KEY_CONTENT_ID = "content_id"
    private const val KEY_EXPORTED_AT = "exported_at"
    private const val KEY_CLASSES = "classes"
    private const val KEY_DEADLINES = "deadlines"
    private const val KEY_ATTENDANCE = "attendance"
    private const val KEY_FLASHCARDS = "flashcards"
    private const val KEY_QUIZZES = "quizzes"
    private const val KEY_STUDY_ITEMS = "study_items"

    fun record(
        context: Context,
        backupVersion: Int,
        contentId: String?,
        exportedAt: String?,
        classes: Int,
        deadlines: Int,
        attendance: Int,
        flashcards: Int,
        quizzes: Int,
        studyItems: Int
    ) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_VERIFIED_AT, System.currentTimeMillis())
            .putInt(KEY_VERSION, backupVersion)
            .putString(KEY_CONTENT_ID, contentId?.takeIf { it.isNotBlank() })
            .putString(KEY_EXPORTED_AT, exportedAt?.takeIf { it.isNotBlank() })
            .putInt(KEY_CLASSES, classes)
            .putInt(KEY_DEADLINES, deadlines)
            .putInt(KEY_ATTENDANCE, attendance)
            .putInt(KEY_FLASHCARDS, flashcards)
            .putInt(KEY_QUIZZES, quizzes)
            .putInt(KEY_STUDY_ITEMS, studyItems)
            .apply()
    }

    fun last(context: Context): RestoreReceipt? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val verifiedAt = prefs.getLong(KEY_VERIFIED_AT, 0L)
        if (verifiedAt <= 0L) return null
        return RestoreReceipt(
            verifiedAt = verifiedAt,
            backupVersion = prefs.getInt(KEY_VERSION, 0),
            contentId = prefs.getString(KEY_CONTENT_ID, null),
            exportedAt = prefs.getString(KEY_EXPORTED_AT, null),
            classes = prefs.getInt(KEY_CLASSES, 0),
            deadlines = prefs.getInt(KEY_DEADLINES, 0),
            attendance = prefs.getInt(KEY_ATTENDANCE, 0),
            flashcards = prefs.getInt(KEY_FLASHCARDS, 0),
            quizzes = prefs.getInt(KEY_QUIZZES, 0),
            studyItems = prefs.getInt(KEY_STUDY_ITEMS, 0)
        )
    }
}
