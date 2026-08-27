package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "flashcard_decks", indices = [Index("courseCode"), Index("topicId")])
data class FlashcardDeck(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val courseCode: String? = null,
    /** Module/topic association used by the course learning path. Null means course-level/overall material. */
    val topicId: String? = null,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object FlashcardTypes {
    const val BASIC = "BASIC"
    const val CLOZE = "CLOZE"
}

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = FlashcardDeck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deckId"), Index("dueAt")]
)
data class Flashcard(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val deckId: String,
    val front: String,
    val back: String,
    val hint: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val dueAt: Long = 0L,
    val lastReviewedAt: Long? = null,
    val reviewCount: Int = 0,
    val correctCount: Int = 0,
    val mastery: Int = 0,
    /** Comma-separated normalized tags. Kept as text so Room stays converter-free. */
    val tags: String = "",
    val starred: Boolean = false,
    /** Alternates front/back direction between reviews when enabled. */
    val reverseEnabled: Boolean = false,
    /** BASIC or CLOZE. Cloze markup uses {{answer}} in [front]. */
    val cardType: String = FlashcardTypes.BASIC,
    /** Persistable content URI for image/diagram cards. */
    val imageUri: String? = null,
    /** JSON array of normalized occlusion rectangles: {x,y,w,h,label}. */
    val occlusionJson: String = "[]"
) {
    fun tagList(): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    fun isNew(): Boolean = reviewCount == 0
    fun isWeak(): Boolean = reviewCount > 0 && mastery <= 1
    fun isDue(now: Long = System.currentTimeMillis()): Boolean = dueAt == 0L || dueAt <= now
}

enum class FlashcardRating { AGAIN, HARD, GOOD }

object FlashcardReviewScheduler {
    private const val MINUTE = 60_000L
    private const val DAY = 24L * 60L * MINUTE

    fun reviewed(card: Flashcard, rating: FlashcardRating, now: Long = System.currentTimeMillis()): Flashcard {
        val nextMastery = when (rating) {
            FlashcardRating.AGAIN -> 0
            FlashcardRating.HARD -> maxOf(1, card.mastery)
            FlashcardRating.GOOD -> (card.mastery + 1).coerceAtMost(5)
        }
        val interval = when (rating) {
            FlashcardRating.AGAIN -> 10L * MINUTE
            FlashcardRating.HARD -> DAY
            FlashcardRating.GOOD -> when (nextMastery) {
                0, 1 -> DAY
                2 -> 3L * DAY
                3 -> 7L * DAY
                4 -> 14L * DAY
                else -> 30L * DAY
            }
        }
        return card.copy(
            updatedAt = now,
            dueAt = now + interval,
            lastReviewedAt = now,
            reviewCount = card.reviewCount + 1,
            correctCount = card.correctCount + if (rating == FlashcardRating.GOOD) 1 else 0,
            mastery = nextMastery
        )
    }
}

object ClozeText {
    private val pattern = Regex("\\{\\{([^{}]+)}}")

    fun hasCloze(text: String): Boolean = pattern.containsMatchIn(text)

    fun question(text: String): String = pattern.replace(text) { match ->
        val answer = match.groupValues[1].trim()
        if (answer.isEmpty()) "[…]" else "[${"•".repeat(answer.length.coerceIn(3, 12))}]"
    }

    fun revealed(text: String): String = pattern.replace(text) { it.groupValues[1].trim() }

    fun answers(text: String): List<String> = pattern.findAll(text)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotEmpty() }
        .toList()
}
