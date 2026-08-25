package com.uplb.punla.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Stable interchange format for flashcard decks generated outside Punla.
 *
 * Canonical shape:
 * {
 *   "format": "punla-flashcards",
 *   "version": 1,
 *   "deck": { "name": "...", "courseCode": "...", "description": "..." },
 *   "cards": [ { "front": "...", "back": "...", "hint": "..." } ]
 * }
 *
 * For convenience the parser also accepts a bare card array and common aliases
 * such as question/answer, prompt/response, and term/definition.
 */
data class FlashcardJsonCard(
    val front: String,
    val back: String,
    val hint: String? = null
)

data class FlashcardJsonDeck(
    val name: String,
    val courseCode: String? = null,
    val description: String? = null,
    val cards: List<FlashcardJsonCard>,
    val warnings: List<String> = emptyList()
)

object FlashcardJsonImport {
    const val FORMAT = "punla-flashcards"
    const val VERSION = 1
    const val MAX_FILE_CHARS = 4_000_000
    const val MAX_CARDS = 5_000

    fun parse(raw: String, fallbackDeckName: String = "Imported deck"): FlashcardJsonDeck {
        require(raw.length <= MAX_FILE_CHARS) { "This JSON file is too large. Keep imports under about 4 MB." }
        val text = raw.trim().removePrefix("\uFEFF").trim()
        require(text.isNotEmpty()) { "The selected file is empty." }

        return try {
            when (text.first()) {
                '[' -> parseArrayRoot(JSONArray(text), fallbackDeckName)
                '{' -> parseObjectRoot(JSONObject(text), fallbackDeckName)
                else -> throw IllegalArgumentException("This file is not valid JSON.")
            }
        } catch (e: JSONException) {
            throw IllegalArgumentException("Punla couldn't read this JSON file. Check that it is valid JSON.", e)
        }
    }

    private fun parseArrayRoot(array: JSONArray, fallbackDeckName: String): FlashcardJsonDeck {
        val warnings = mutableListOf<String>()
        val cards = parseCards(array, warnings)
        require(cards.isNotEmpty()) { "No valid flashcards were found in this JSON file." }
        return FlashcardJsonDeck(
            name = fallbackDeckName.ifBlank { "Imported deck" },
            cards = cards,
            warnings = warnings
        )
    }

    private fun parseObjectRoot(root: JSONObject, fallbackDeckName: String): FlashcardJsonDeck {
        val warnings = mutableListOf<String>()
        val format = root.optString("format").trim()
        if (format.isNotEmpty() && format != FORMAT) {
            warnings += "Unknown format '$format'; imported using Punla's compatible fields."
        }
        val version = if (root.has("version")) root.optInt("version", VERSION) else VERSION
        if (version > VERSION) {
            warnings += "This file uses format version $version; unsupported fields were ignored."
        }

        val deck = root.optJSONObject("deck")
        val name = firstText(deck, "name", "title")
            ?: firstText(root, "deckName", "name", "title")
            ?: fallbackDeckName.ifBlank { "Imported deck" }
        val courseCode = firstText(deck, "courseCode", "course", "subject")
            ?: firstText(root, "courseCode", "course", "subject")
        val description = firstText(deck, "description", "notes")
            ?: firstText(root, "description", "notes")

        val array = root.optJSONArray("cards")
            ?: root.optJSONArray("flashcards")
            ?: throw IllegalArgumentException("No 'cards' array was found in this JSON file.")
        val cards = parseCards(array, warnings)
        require(cards.isNotEmpty()) { "No valid flashcards were found in this JSON file." }
        return FlashcardJsonDeck(
            name = name.take(120),
            courseCode = courseCode?.take(60),
            description = description?.take(1_000),
            cards = cards,
            warnings = warnings
        )
    }

    private fun parseCards(array: JSONArray, warnings: MutableList<String>): List<FlashcardJsonCard> {
        val limit = minOf(array.length(), MAX_CARDS)
        if (array.length() > MAX_CARDS) {
            warnings += "Only the first $MAX_CARDS cards were imported."
        }
        val cards = ArrayList<FlashcardJsonCard>(limit)
        var skipped = 0
        for (index in 0 until limit) {
            val obj = array.optJSONObject(index)
            if (obj == null) {
                skipped++
                continue
            }
            val front = firstText(obj, "front", "question", "prompt", "term")
            val back = firstText(obj, "back", "answer", "response", "definition")
            if (front.isNullOrBlank() || back.isNullOrBlank()) {
                skipped++
                continue
            }
            cards += FlashcardJsonCard(
                front = front.take(10_000),
                back = back.take(20_000),
                hint = firstText(obj, "hint", "clue")?.take(5_000)
            )
        }
        if (skipped > 0) warnings += "$skipped invalid card${if (skipped == 1) " was" else "s were"} skipped."
        return cards
    }

    private fun firstText(obj: JSONObject?, vararg keys: String): String? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = obj.optString(key).trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }
}
