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

    @Test fun studyPackKeepsModuleOrderAndDeckQuizLinks() {
        val json = """
            {
              "punlaFileId":"punla.study.bundle",
              "schemaVersion":1,
              "contentId":"1e8104e5-b6db-45cf-9073-e91b80f79ddb",
              "bundle":{"title":"Course Pack","courseCode":"TEST 1"},
              "topics":[{"key":"module-a","name":"Module A","sortOrder":2}],
              "notes":[{"title":"Review","body":"Key idea","topicKey":"module-a"}],
              "flashcardDecks":[{"name":"Cards","topicKey":"module-a","cards":[{"front":"Q","back":"A"}]}],
              "quizzes":[{"title":"Quiz","topicKey":"module-a","questions":[{"type":"identification","question":"Q","correctAnswer":"A"}]}]
            }
        """.trimIndent()
        val parsed = StudyJsonImport.parse(json)
        assertEquals(2, parsed.topics.single().sortOrder)
        assertEquals("module-a", parsed.decks.single().topicKey)
        assertEquals("module-a", parsed.quizzes.single().topicKey)
    }
}
