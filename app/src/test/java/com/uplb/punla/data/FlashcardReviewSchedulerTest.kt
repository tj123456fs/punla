package com.uplb.punla.data

import com.uplb.punla.data.entity.Flashcard
import com.uplb.punla.data.entity.FlashcardRating
import com.uplb.punla.data.entity.FlashcardReviewScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashcardReviewSchedulerTest {
    private val base = Flashcard(deckId = "deck", front = "Q", back = "A")
    private val now = 1_000_000L

    @Test fun againResetsMasteryAndReturnsSoon() {
        val reviewed = FlashcardReviewScheduler.reviewed(base.copy(mastery = 4), FlashcardRating.AGAIN, now)
        assertEquals(0, reviewed.mastery)
        assertEquals(1, reviewed.reviewCount)
        assertEquals(0, reviewed.correctCount)
        assertTrue(reviewed.dueAt > now)
        assertTrue(reviewed.dueAt < now + 86_400_000L)
    }

    @Test fun goodBuildsMasteryAndInterval() {
        val first = FlashcardReviewScheduler.reviewed(base, FlashcardRating.GOOD, now)
        val second = FlashcardReviewScheduler.reviewed(first, FlashcardRating.GOOD, now + 1_000L)
        assertEquals(2, second.mastery)
        assertEquals(2, second.reviewCount)
        assertEquals(2, second.correctCount)
        assertTrue(second.dueAt >= now + 3L * 86_400_000L)
    }

    @Test fun hardKeepsCardInLearningState() {
        val reviewed = FlashcardReviewScheduler.reviewed(base.copy(mastery = 3), FlashcardRating.HARD, now)
        assertEquals(3, reviewed.mastery)
        assertEquals(1, reviewed.reviewCount)
        assertEquals(0, reviewed.correctCount)
        assertEquals(now + 86_400_000L, reviewed.dueAt)
    }
}
