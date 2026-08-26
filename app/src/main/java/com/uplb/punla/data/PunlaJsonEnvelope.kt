package com.uplb.punla.data

import java.util.UUID

/** Stable type IDs used to keep Punla JSON files from being opened in the wrong importer. */
object PunlaJsonFileIds {
    const val FLASHCARD_DECK = "punla.flashcards.deck"
    const val QUIZ = "punla.quiz"
    const val BACKUP = "punla.backup"

    fun label(fileId: String): String = when (fileId) {
        FLASHCARD_DECK -> "flashcard deck"
        QUIZ -> "quiz"
        BACKUP -> "backup"
        else -> "unknown Punla JSON"
    }

    fun requireUuid(contentId: String): String {
        require(contentId.isNotBlank()) { "This Punla JSON is missing its unique contentId." }
        runCatching { UUID.fromString(contentId) }.getOrElse {
            throw IllegalArgumentException("This Punla JSON has an invalid contentId. Ask for the file to be regenerated.")
        }
        return contentId
    }

    fun wrongImporter(actual: String, expected: String): IllegalArgumentException =
        IllegalArgumentException(
            "This is a Punla ${label(actual)} JSON, not a ${label(expected)} JSON. Open it from the ${destinationName(actual)} screen instead."
        )

    private fun destinationName(fileId: String): String = when (fileId) {
        FLASHCARD_DECK -> "Flashcards"
        QUIZ -> "Quizzes"
        BACKUP -> "Settings → Backup"
        else -> "matching importer"
    }
}
