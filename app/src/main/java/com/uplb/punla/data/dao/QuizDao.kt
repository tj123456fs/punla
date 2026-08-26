package com.uplb.punla.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuiz(quiz: Quiz)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuizzes(quizzes: List<Quiz>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestion(question: QuizQuestion)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestions(questions: List<QuizQuestion>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: QuizAttempt)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempts(attempts: List<QuizAttempt>)

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
