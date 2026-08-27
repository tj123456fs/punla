package com.uplb.punla.data

import com.uplb.punla.data.entity.Flashcard
import com.uplb.punla.data.entity.FlashcardDeck
import com.uplb.punla.data.entity.FlashcardTypes
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Strict, type-safe Punla flashcard interchange format. */
data class FlashcardJsonCard(
    val front: String,
    val back: String,
    val hint: String? = null,
    val tags: String = "",
    val starred: Boolean = false,
    val reverseEnabled: Boolean = false,
    val cardType: String = FlashcardTypes.BASIC,
    val imageUri: String? = null,
    val occlusionJson: String = "[]"
)

data class FlashcardJsonDeck(
    val contentId: String,
    val name: String,
    val courseCode: String? = null,
    val description: String? = null,
    val cards: List<FlashcardJsonCard>,
    val warnings: List<String> = emptyList()
)

object FlashcardJsonImport {
    const val FILE_ID = PunlaJsonFileIds.FLASHCARD_DECK
    const val VERSION = 3
    const val MAX_FILE_CHARS = 4_000_000
    const val MAX_CARDS = 5_000

    fun parse(raw: String): FlashcardJsonDeck {
        require(raw.length <= MAX_FILE_CHARS) { "This JSON file is too large. Keep flashcard imports under about 4 MB." }
        val text = raw.trim().removePrefix("\uFEFF").trim()
        require(text.isNotEmpty()) { "The selected file is empty." }

        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Punla couldn't read this JSON file. Check that it is valid JSON.", e)
        }

        val actualFileId = root.optString("punlaFileId").trim()
        require(actualFileId.isNotEmpty()) {
            "This JSON has no Punla file ID. For safety, Flashcards now accepts only files generated for Punla Flashcards v2 or newer."
        }
        if (actualFileId != FILE_ID) throw PunlaJsonFileIds.wrongImporter(actualFileId, FILE_ID)

        val version = root.optInt("schemaVersion", -1)
        require(version in 1..VERSION) {
            if (version > VERSION) "This flashcard JSON uses schema v$version, but this Punla build supports up to v$VERSION."
            else "This flashcard JSON is missing a supported schemaVersion."
        }
        val contentId = PunlaJsonFileIds.requireUuid(root.optString("contentId").trim())

        val warnings = mutableListOf<String>()
        val deck = root.optJSONObject("deck")
            ?: throw IllegalArgumentException("This flashcard JSON is missing its 'deck' object.")
        val name = firstText(deck, "name", "title")
            ?: throw IllegalArgumentException("The flashcard deck has no name.")
        val courseCode = firstText(deck, "courseCode", "course", "subject")
        val description = firstText(deck, "description", "notes")
        val array = root.optJSONArray("cards")
            ?: throw IllegalArgumentException("This flashcard JSON has no 'cards' array.")
        val cards = parseCards(array, warnings)
        require(cards.isNotEmpty()) { "No valid flashcards were found in this JSON file." }

        return FlashcardJsonDeck(
            contentId = contentId,
            name = name.take(120),
            courseCode = courseCode?.take(60),
            description = description?.take(1_000),
            cards = cards,
            warnings = warnings
        )
    }

    private fun parseCards(array: JSONArray, warnings: MutableList<String>): List<FlashcardJsonCard> {
        val limit = minOf(array.length(), MAX_CARDS)
        if (array.length() > MAX_CARDS) warnings += "Only the first $MAX_CARDS cards were imported."
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
            val requestedType = firstText(obj, "cardType", "type")?.uppercase()
            val cardType = when (requestedType) {
                "CLOZE" -> FlashcardTypes.CLOZE
                else -> FlashcardTypes.BASIC
            }
            if (cardType == FlashcardTypes.CLOZE && !com.uplb.punla.data.entity.ClozeText.hasCloze(front)) {
                warnings += "Card ${index + 1} requested CLOZE but had no {{answer}} marker; imported as Basic."
            }
            cards += FlashcardJsonCard(
                front = front.take(10_000),
                back = back.take(20_000),
                hint = firstText(obj, "hint", "clue")?.take(5_000),
                tags = parseTags(obj.opt("tags")).take(1_000),
                starred = obj.optBoolean("starred", false),
                reverseEnabled = obj.optBoolean("reverseEnabled", obj.optBoolean("reverse", false)),
                cardType = if (cardType == FlashcardTypes.CLOZE && com.uplb.punla.data.entity.ClozeText.hasCloze(front)) cardType else FlashcardTypes.BASIC,
                imageUri = firstText(obj, "imageUri", "image", "imageUrl")?.take(5_000),
                occlusionJson = parseOcclusion(obj.opt("occlusion"))
            )
        }
        if (skipped > 0) warnings += "$skipped invalid card${if (skipped == 1) " was" else "s were"} skipped."
        return cards
    }

    private fun parseOcclusion(value: Any?): String = when (value) {
        is JSONArray -> value.toString()
        is JSONObject -> JSONArray().put(value).toString()
        is String -> runCatching { JSONArray(value).toString() }.getOrDefault("[]")
        else -> "[]"
    }

    private fun parseTags(value: Any?): String = when (value) {
        is JSONArray -> buildList {
            for (i in 0 until value.length()) value.optString(i).trim().takeIf { it.isNotEmpty() }?.let(::add)
        }.distinct().joinToString(", ")
        is String -> value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
        else -> ""
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

object FlashcardJsonExport {
    fun build(deck: FlashcardDeck, cards: List<Flashcard>): String {
        val root = JSONObject().apply {
            put("punlaFileId", FlashcardJsonImport.FILE_ID)
            put("schemaVersion", FlashcardJsonImport.VERSION)
            // Deck IDs are UUIDs already, making the content identity stable across re-exports.
            put("contentId", deck.id)
            put("deck", JSONObject().apply {
                put("name", deck.name)
                deck.courseCode?.let { put("courseCode", it) }
                deck.description?.let { put("description", it) }
            })
            put("cards", JSONArray().apply {
                cards.forEach { card ->
                    put(JSONObject().apply {
                        put("front", card.front)
                        put("back", card.back)
                        card.hint?.let { put("hint", it) }
                        if (card.tags.isNotBlank()) put("tags", JSONArray(card.tagList()))
                        if (card.starred) put("starred", true)
                        if (card.reverseEnabled) put("reverseEnabled", true)
                        if (card.cardType != FlashcardTypes.BASIC) put("cardType", card.cardType)
                        card.imageUri?.let { put("imageUri", it) }
                        if (card.occlusionJson != "[]") put("occlusion", runCatching { JSONArray(card.occlusionJson) }.getOrDefault(JSONArray()))
                    })
                }
            })
        }
        return root.toString(2)
    }
}
