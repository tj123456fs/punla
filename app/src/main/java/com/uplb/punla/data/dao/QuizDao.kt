package com.uplb.punla.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.uplb.punla.data.entity.Quiz
import com.uplb.punla.data.entity.QuizAttempt
import com.uplb.punla.data.entity.QuizQuestion
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {
    @Query("SELECT * FROM quizzes ORDER BY updatedAt DESC, title COLLATE NOCASE ASC")
    fun observeQuizzes(): Flow<List<Quiz>>

    @Query("SELECT * FROM quizzes ORDER BY updatedAt DESC, title COLLATE NOCASE ASC")
    suspend fun getQuizzes(): List<Quiz>

    @Query("SELECT * FROM quiz_questions ORDER BY createdAt ASC")
    fun observeAllQuestions(): Flow<List<QuizQuestion>>

    @Query("SELECT * FROM quiz_questions ORDER BY createdAt ASC")
    suspend fun getAllQuestions(): List<QuizQuestion>

    @Query("SELECT * FROM quiz_questions WHERE quizId = :quizId ORDER BY createdAt ASC")
    fun observeQuestions(quizId: String): Flow<List<QuizQuestion>>

    @Query("SELECT * FROM quiz_questions WHERE quizId = :quizId ORDER BY createdAt ASC")
    suspend fun getQuestions(quizId: String): List<QuizQuestion>

    @Query("SELECT * FROM quiz_attempts ORDER BY completedAt DESC")
    fun observeAllAttempts(): Flow<List<QuizAttempt>>

    @Query("SELECT * FROM quiz_attempts ORDER BY completedAt DESC")
    suspend fun getAllAttempts(): List<QuizAttempt>

    @Query("SELECT * FROM quiz_attempts WHERE quizId = :quizId ORDER BY completedAt DESC")
    fun observeAttempts(quizId: String): Flow<List<QuizAttempt>>

    @Query("SELECT * FROM quiz_attempts WHERE id = :attemptId LIMIT 1")
    suspend fun getAttempt(attemptId: String): QuizAttempt?

    @Upsert
    suspend fun upsertQuiz(quiz: Quiz)

    @Upsert
    suspend fun upsertQuizzes(quizzes: List<Quiz>)

    @Upsert
    suspend fun upsertQuestion(question: QuizQuestion)

    @Upsert
    suspend fun upsertQuestions(questions: List<QuizQuestion>)

    @Upsert
    suspend fun insertAttempt(attempt: QuizAttempt)

    @Upsert
    suspend fun insertAttempts(attempts: List<QuizAttempt>)

    @Query("UPDATE quizzes SET topicId = NULL WHERE topicId = :topicId")
    suspend fun clearTopicAssociation(topicId: String)

    @Delete
    suspend fun deleteQuiz(quiz: Quiz)

    @Delete
    suspend fun deleteQuestion(question: QuizQuestion)

    @Query("DELETE FROM quiz_attempts")
    suspend fun clearAttempts()

    @Query("DELETE FROM quiz_questions")
    suspend fun clearQuestions()

    @Query("DELETE FROM quizzes")
    suspend fun clearQuizzes()

    @Transaction
    suspend fun clearAll() {
        clearAttempts()
        clearQuestions()
        clearQuizzes()
    }
}
