package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import java.time.LocalDate
import java.util.UUID

/** Hierarchical course/unit/topic node used by Study Hub and exam plans. */
@Entity(
    tableName = "study_topics",
    indices = [Index("courseCode"), Index("parentTopicId"), Index("examDate")]
)
data class StudyTopic(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseCode: String,
    val name: String,
    val parentTopicId: String? = null,
    /** ISO yyyy-MM-dd. A topic with an exam date acts as an exam target. */
    val examDate: String? = null,
    /** 1..5; higher values receive more weight in cram/readiness calculations. */
    val priority: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_notes",
    foreignKeys = [ForeignKey(
        entity = StudyTopic::class,
        parentColumns = ["id"],
        childColumns = ["topicId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("courseCode"), Index("topicId"), Index("updatedAt")]
)
data class StudyNote(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseCode: String? = null,
    val topicId: String? = null,
    val title: String,
    /** Markdown-ish reviewer text. Punla keeps source text intact for export. */
    val body: String,
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun tagList(): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

@Entity(
    tableName = "formula_references",
    foreignKeys = [ForeignKey(
        entity = StudyTopic::class,
        parentColumns = ["id"],
        childColumns = ["topicId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("courseCode"), Index("topicId")]
)
data class FormulaReference(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseCode: String? = null,
    val topicId: String? = null,
    val title: String,
    /** Supports plain text plus a small LaTeX-like subset rendered by StudyMathText. */
    val expression: String,
    val variables: String? = null,
    val units: String? = null,
    val workedExample: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object MistakeSourceTypes {
    const val QUIZ = "QUIZ"
    const val FLASHCARD = "FLASHCARD"
    const val PRACTICE = "PRACTICE"
}

object StudyConfidence {
    const val GUESSED = "GUESSED"
    const val UNSURE = "UNSURE"
    const val CONFIDENT = "CONFIDENT"
    const val UNSET = "UNSET"
}

@Entity(
    tableName = "mistake_records",
    indices = [Index("sourceType"), Index("sourceId"), Index("courseCode"), Index("topicTag"), Index("retryAt"), Index("resolved")]
)
data class MistakeRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceType: String,
    val sourceId: String,
    val courseCode: String? = null,
    val topicTag: String? = null,
    val prompt: String,
    val userAnswer: String? = null,
    val correctAnswer: String,
    val explanation: String? = null,
    val confidence: String = StudyConfidence.UNSET,
    val missedAt: Long = System.currentTimeMillis(),
    /** Wrong items re-enter Smart Study after this time. */
    val retryAt: Long = System.currentTimeMillis() + 24L * 60L * 60L * 1000L,
    val resolved: Boolean = false,
    val timesMissed: Int = 1
)

object StudyGoalTypes {
    const val MINUTES = "MINUTES"
    const val FLASHCARDS = "FLASHCARDS"
    const val QUESTIONS = "QUESTIONS"
    const val SCORE = "SCORE"
    const val CUSTOM = "CUSTOM"
}

@Entity(tableName = "study_goals", indices = [Index("courseCode"), Index("dueDate"), Index("completed")])
data class StudyGoal(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseCode: String? = null,
    val topicTag: String? = null,
    val title: String,
    val goalType: String = StudyGoalTypes.CUSTOM,
    val targetValue: Int = 1,
    val progressValue: Int = 0,
    val dueDate: String? = null,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun percent(): Int = if (targetValue <= 0) 100 else ((progressValue * 100.0) / targetValue).toInt().coerceIn(0, 100)
}

object StudyPlanKinds {
    const val FLASHCARDS = "FLASHCARDS"
    const val QUIZ = "QUIZ"
    const val NOTES = "NOTES"
    const val FOCUS = "FOCUS"
    const val PRACTICE_TEST = "PRACTICE_TEST"
    const val REVIEW = "REVIEW"
}

@Entity(tableName = "study_plan_items", indices = [Index("plannedDate"), Index("courseCode"), Index("topicTag"), Index("completed")])
data class StudyPlanItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseCode: String? = null,
    val topicTag: String? = null,
    val title: String,
    val plannedDate: String = LocalDate.now().toString(),
    val minutes: Int = 25,
    val kind: String = StudyPlanKinds.REVIEW,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)


/** Immutable event history keeps study streaks/heatmaps accurate even after a card is reviewed again. */
@Entity(tableName = "flashcard_review_events", indices = [Index("cardId"), Index("courseCode"), Index("reviewedAt")])
data class FlashcardReviewEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val cardId: String,
    val deckId: String? = null,
    val courseCode: String? = null,
    val rating: String,
    val reviewedAt: Long = System.currentTimeMillis()
)

/** Per-question result powers confidence-aware mastery, weak topics and mistake history. */
@Entity(
    tableName = "quiz_answer_results",
    foreignKeys = [
        ForeignKey(entity = QuizAttempt::class, parentColumns = ["id"], childColumns = ["attemptId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = QuizQuestion::class, parentColumns = ["id"], childColumns = ["questionId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("attemptId"), Index("questionId"), Index("answeredAt"), Index("correct")]
)
data class QuizAnswerResult(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val attemptId: String,
    val quizId: String,
    val questionId: String,
    val userAnswer: String,
    val correctAnswer: String,
    val correct: Boolean,
    val confidence: String = StudyConfidence.UNSET,
    val answeredAt: Long = System.currentTimeMillis()
)

/** Reusable question bank item. Can be copied into any quiz/practice test. */
@Entity(tableName = "question_bank", indices = [Index("courseCode"), Index("tags"), Index("updatedAt")])
data class QuestionBankItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseCode: String? = null,
    val type: String,
    val prompt: String,
    val optionsJson: String = "[]",
    val correctAnswer: String,
    val explanation: String? = null,
    val tags: String = "",
    val metadataJson: String = "{}",
    /** Persistable content URI or remote URL for image/diagram bank items. */
    val imageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun options(): List<String> = runCatching {
        val arr = JSONArray(optionsJson)
        List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}
