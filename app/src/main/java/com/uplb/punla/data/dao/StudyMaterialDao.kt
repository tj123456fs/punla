package com.uplb.punla.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.uplb.punla.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyMaterialDao {
    @Query("SELECT * FROM study_topics ORDER BY courseCode COLLATE NOCASE, name COLLATE NOCASE")
    fun observeTopics(): Flow<List<StudyTopic>>
    @Query("SELECT * FROM study_topics") suspend fun getTopics(): List<StudyTopic>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTopic(item: StudyTopic)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTopics(items: List<StudyTopic>)
    @Query("UPDATE study_topics SET parentTopicId = NULL, updatedAt = :updatedAt WHERE parentTopicId = :topicId")
    suspend fun clearChildParentReferences(topicId: String, updatedAt: Long)
    @Delete suspend fun deleteTopic(item: StudyTopic)

    @Query("SELECT * FROM study_notes ORDER BY updatedAt DESC") fun observeNotes(): Flow<List<StudyNote>>
    @Query("SELECT * FROM study_notes ORDER BY updatedAt DESC") suspend fun getNotes(): List<StudyNote>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertNote(item: StudyNote)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertNotes(items: List<StudyNote>)
    @Delete suspend fun deleteNote(item: StudyNote)

    @Query("SELECT * FROM formula_references ORDER BY courseCode COLLATE NOCASE, title COLLATE NOCASE") fun observeFormulas(): Flow<List<FormulaReference>>
    @Query("SELECT * FROM formula_references") suspend fun getFormulas(): List<FormulaReference>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertFormula(item: FormulaReference)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertFormulas(items: List<FormulaReference>)
    @Delete suspend fun deleteFormula(item: FormulaReference)

    @Query("SELECT * FROM mistake_records ORDER BY resolved ASC, retryAt ASC, missedAt DESC") fun observeMistakes(): Flow<List<MistakeRecord>>
    @Query("SELECT * FROM mistake_records") suspend fun getMistakes(): List<MistakeRecord>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMistake(item: MistakeRecord)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMistakes(items: List<MistakeRecord>)
    @Query("DELETE FROM mistake_records WHERE id = :id") suspend fun deleteMistake(id: String)

    @Query("SELECT * FROM study_goals ORDER BY completed ASC, dueDate ASC, updatedAt DESC") fun observeGoals(): Flow<List<StudyGoal>>
    @Query("SELECT * FROM study_goals") suspend fun getGoals(): List<StudyGoal>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGoal(item: StudyGoal)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGoals(items: List<StudyGoal>)
    @Delete suspend fun deleteGoal(item: StudyGoal)

    @Query("SELECT * FROM study_plan_items ORDER BY completed ASC, plannedDate ASC, createdAt ASC") fun observePlanItems(): Flow<List<StudyPlanItem>>
    @Query("SELECT * FROM study_plan_items") suspend fun getPlanItems(): List<StudyPlanItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlanItem(item: StudyPlanItem)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlanItems(items: List<StudyPlanItem>)
    @Delete suspend fun deletePlanItem(item: StudyPlanItem)
    @Query("DELETE FROM study_plan_items WHERE courseCode = :courseCode COLLATE NOCASE AND title LIKE 'Exam prep:%'") suspend fun clearGeneratedExamPlan(courseCode: String)

    @Query("SELECT * FROM flashcard_review_events ORDER BY reviewedAt DESC") fun observeFlashcardReviewEvents(): Flow<List<FlashcardReviewEvent>>
    @Query("SELECT * FROM flashcard_review_events ORDER BY reviewedAt DESC") suspend fun getFlashcardReviewEvents(): List<FlashcardReviewEvent>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertFlashcardReviewEvent(item: FlashcardReviewEvent)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertFlashcardReviewEvents(items: List<FlashcardReviewEvent>)

    @Query("SELECT * FROM quiz_answer_results ORDER BY answeredAt DESC") fun observeAnswerResults(): Flow<List<QuizAnswerResult>>
    @Query("SELECT * FROM quiz_answer_results") suspend fun getAnswerResults(): List<QuizAnswerResult>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAnswerResult(item: QuizAnswerResult)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAnswerResults(items: List<QuizAnswerResult>)

    @Query("SELECT * FROM question_bank ORDER BY updatedAt DESC") fun observeQuestionBank(): Flow<List<QuestionBankItem>>
    @Query("SELECT * FROM question_bank") suspend fun getQuestionBank(): List<QuestionBankItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBankItem(item: QuestionBankItem)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBankItems(items: List<QuestionBankItem>)
    @Delete suspend fun deleteBankItem(item: QuestionBankItem)

    @Query("DELETE FROM study_topics") suspend fun clearTopics()
    @Query("DELETE FROM study_notes") suspend fun clearNotes()
    @Query("DELETE FROM formula_references") suspend fun clearFormulas()
    @Query("DELETE FROM mistake_records") suspend fun clearMistakes()
    @Query("DELETE FROM study_goals") suspend fun clearGoals()
    @Query("DELETE FROM study_plan_items") suspend fun clearPlanItems()
    @Query("DELETE FROM flashcard_review_events") suspend fun clearFlashcardReviewEvents()
    @Query("DELETE FROM quiz_answer_results") suspend fun clearAnswerResults()
    @Query("DELETE FROM question_bank") suspend fun clearQuestionBank()
}
