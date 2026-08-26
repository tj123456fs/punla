package com.uplb.punla.data

import com.uplb.punla.data.entity.ClozeText
import com.uplb.punla.data.entity.QuizQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyToolsTest {
    @Test fun clozeMasksAndRevealsMarkedAnswer() {
        val raw = "Photosynthesis occurs in the {{chloroplast}}."
        assertTrue(ClozeText.hasCloze(raw))
        assertFalse(ClozeText.question(raw).contains("chloroplast"))
        assertEquals("Photosynthesis occurs in the chloroplast.", ClozeText.revealed(raw))
        assertEquals(listOf("chloroplast"), ClozeText.answers(raw))
    }

    @Test fun identificationMatchingIgnoresCaseAndRepeatedWhitespace() {
        val q = QuizQuestion(quizId = "quiz", prompt = "Site?", correctAnswer = "Thylakoid membrane")
        assertTrue(q.isCorrect("  THYLAKOID   membrane "))
        assertFalse(q.isCorrect("stroma"))
    }

    @Test fun punlaJsonFileIdsAreDistinct() {
        assertTrue(PunlaJsonFileIds.FLASHCARD_DECK != PunlaJsonFileIds.QUIZ)
        assertTrue(PunlaJsonFileIds.FLASHCARD_DECK != PunlaJsonFileIds.BACKUP)
        assertTrue(PunlaJsonFileIds.QUIZ != PunlaJsonFileIds.BACKUP)
    }
}
