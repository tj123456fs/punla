package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import java.util.UUID

object QuizQuestionTypes {
    const val MULTIPLE_CHOICE = "MULTIPLE_CHOICE"
    const val TRUE_FALSE = "TRUE_FALSE"
    const val IDENTIFICATION = "IDENTIFICATION"
    const val MULTI_SELECT = "MULTI_SELECT"
    const val NUMERIC = "NUMERIC"
    const val ORDERING = "ORDERING"
    const val MATCHING = "MATCHING"
    const val IMAGE_IDENTIFICATION = "IMAGE_IDENTIFICATION"
}

@Entity(tableName = "quizzes")
data class Quiz(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val courseCode: String? = null,
    val description: String? = null,
    val passingScore: Int = 70,
    val shuffleQuestions: Boolean = true,
    val shuffleChoices: Boolean = true,
    /** Null/0 means untimed. */
    val timeLimitMinutes: Int? = null,
    /** IMMEDIATE shows explanations per question; AFTER keeps a mock-exam feel. */
    val feedbackMode: String = "IMMEDIATE",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "quiz_questions",
    foreignKeys = [
        ForeignKey(
            entity = Quiz::class,
            parentColumns = ["id"],
            childColumns = ["quizId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("quizId")]
)
data class QuizQuestion(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val quizId: String,
    val type: String = QuizQuestionTypes.MULTIPLE_CHOICE,
    val prompt: String,
    /** JSON string array. Empty for identification. */
    val optionsJson: String = "[]",
    /** Stored as the actual answer text, never the choice index. */
    val correctAnswer: String,
    val explanation: String? = null,
    val tags: String = "",
    /** Optional JSON for numeric tolerance, matching pairs, ordering rules, etc. */
    val metadataJson: String = "{}",
    /** Persistable content URI for image/diagram questions. */
    val imageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun options(): List<String> = runCatching {
        val array = JSONArray(optionsJson)
        List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    fun tagList(): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun isCorrect(answer: String): Boolean = normalizeAnswer(answer) == normalizeAnswer(correctAnswer)

    companion object {
        fun encodeOptions(options: List<String>): String = JSONArray(options).toString()
        fun normalizeAnswer(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}

@Entity(
    tableName = "quiz_attempts",
    foreignKeys = [
        ForeignKey(
            entity = Quiz::class,
            parentColumns = ["id"],
            childColumns = ["quizId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("quizId"), Index("completedAt")]
)
data class QuizAttempt(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val quizId: String,
    val startedAt: Long,
    val completedAt: Long,
    val score: Int,
    val total: Int,
    val durationMs: Long,
    /** IDs of missed questions, allowing Retry mistakes later. */
    val incorrectQuestionIdsJson: String = "[]"
) {
    fun percent(): Int = if (total <= 0) 0 else ((score * 100.0) / total).toInt()
    fun incorrectQuestionIds(): List<String> = runCatching {
        val arr = JSONArray(incorrectQuestionIdsJson)
        List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}
