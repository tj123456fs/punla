package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "flashcard_decks")
data class FlashcardDeck(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val courseCode: String? = null,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

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
    val mastery: Int = 0
)

enum class FlashcardRating { AGAIN, HARD, GOOD }

object FlashcardReviewScheduler {
    private const val MINUTE = 60_000L
    private const val DAY = 24L * 60L * MINUTE

    /**
     * Lightweight offline spaced repetition. It deliberately stays simple and
     * predictable: "Again" returns the card quickly, "Hard" tomorrow, and
     * "Good" grows the interval as the card becomes familiar.
     */
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
