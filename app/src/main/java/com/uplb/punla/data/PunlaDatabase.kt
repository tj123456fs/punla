package com.uplb.punla.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.uplb.punla.data.dao.AttendanceDao
import com.uplb.punla.data.dao.ChecklistDao
import com.uplb.punla.data.dao.ClassSessionDao
import com.uplb.punla.data.dao.DeadlineDao
import com.uplb.punla.data.dao.ExpenseDao
import com.uplb.punla.data.dao.GradesDao
import com.uplb.punla.data.dao.FlashcardDao
import com.uplb.punla.data.dao.QuizDao
import com.uplb.punla.data.dao.JsonImportDao
import com.uplb.punla.data.dao.StudySessionDao
import com.uplb.punla.data.dao.StudyMaterialDao
import com.uplb.punla.data.dao.IntelligenceDao
import com.uplb.punla.data.entity.Archive
import com.uplb.punla.data.entity.AttendanceRecord
import com.uplb.punla.data.entity.ChecklistItem
import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.data.entity.DeadlineRule
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.data.entity.ExpenseRule
import com.uplb.punla.data.entity.GradeCourse
import com.uplb.punla.data.entity.Flashcard
import com.uplb.punla.data.entity.FlashcardDeck
import com.uplb.punla.data.entity.Quiz
import com.uplb.punla.data.entity.QuizQuestion
import com.uplb.punla.data.entity.QuizAttempt
import com.uplb.punla.data.entity.JsonImportRecord
import com.uplb.punla.data.entity.Semester
import com.uplb.punla.data.entity.StudySession
import com.uplb.punla.data.entity.StudySuggestionEvent
import com.uplb.punla.data.entity.NotificationEvent
import com.uplb.punla.data.entity.StudyTopic
import com.uplb.punla.data.entity.StudyNote
import com.uplb.punla.data.entity.FormulaReference
import com.uplb.punla.data.entity.MistakeRecord
import com.uplb.punla.data.entity.StudyGoal
import com.uplb.punla.data.entity.StudyPlanItem
import com.uplb.punla.data.entity.QuizAnswerResult
import com.uplb.punla.data.entity.FlashcardReviewEvent
import com.uplb.punla.data.entity.QuestionBankItem

@Database(
    entities = [
        ClassSession::class,
        Expense::class,
        ExpenseRule::class,
        Deadline::class,
        DeadlineRule::class,
        Semester::class,
        GradeCourse::class,
        Archive::class,
        ChecklistItem::class,
        StudySession::class,
        StudySuggestionEvent::class,
        NotificationEvent::class,
        AttendanceRecord::class,
        FlashcardDeck::class,
        Flashcard::class,
        Quiz::class,
        QuizQuestion::class,
        QuizAttempt::class,
        JsonImportRecord::class,
        StudyTopic::class,
        StudyNote::class,
        FormulaReference::class,
        MistakeRecord::class,
        StudyGoal::class,
        StudyPlanItem::class,
        FlashcardReviewEvent::class,
        QuizAnswerResult::class,
        QuestionBankItem::class
    ],
    // v7 -> v8: per-occurrence attendance history used by the ongoing
    // class notification and the schedule/dashboard attendance controls.
    version = 11,
    exportSchema = false
)
abstract class PunlaDatabase : RoomDatabase() {
    abstract fun classSessionDao(): ClassSessionDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun deadlineDao(): DeadlineDao
    abstract fun gradesDao(): GradesDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun intelligenceDao(): IntelligenceDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun quizDao(): QuizDao
    abstract fun jsonImportDao(): JsonImportDao
    abstract fun studyMaterialDao(): StudyMaterialDao

    companion object {
        @Volatile private var INSTANCE: PunlaDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `study_sessions` ADD COLUMN `endReason` TEXT NOT NULL DEFAULT 'COMPLETED'")
                db.execSQL("ALTER TABLE `study_sessions` ADD COLUMN `suggestionId` TEXT")
                db.execSQL("UPDATE `study_sessions` SET `endReason` = CASE WHEN `completed` = 1 THEN 'COMPLETED' ELSE 'STOPPED_EARLY' END")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `study_suggestion_events` (
                        `id` TEXT NOT NULL,
                        `suggestionId` TEXT NOT NULL,
                        `occurredAt` INTEGER NOT NULL,
                        `outcome` TEXT NOT NULL,
                        `slotHour` INTEGER NOT NULL,
                        `dayOfWeek` INTEGER NOT NULL,
                        `urgencyDays` INTEGER NOT NULL,
                        `availableMinutes` INTEGER NOT NULL,
                        `deadlineId` TEXT,
                        `courseCode` TEXT,
                        `sessionId` TEXT,
                        PRIMARY KEY(`id`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_suggestion_events_suggestionId` ON `study_suggestion_events` (`suggestionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_suggestion_events_occurredAt` ON `study_suggestion_events` (`occurredAt`)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `notification_events` (
                        `id` TEXT NOT NULL,
                        `notificationKey` TEXT NOT NULL,
                        `workerName` TEXT NOT NULL,
                        `notificationType` TEXT NOT NULL,
                        `occurredAt` INTEGER NOT NULL,
                        `localHour` INTEGER NOT NULL,
                        `outcome` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_events_notificationKey` ON `notification_events` (`notificationKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_events_occurredAt` ON `notification_events` (`occurredAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_events_workerName` ON `notification_events` (`workerName`)")
            }
        }


        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `attendance_records` (
                        `occurrenceKey` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `classCode` TEXT NOT NULL,
                        `occurrenceDate` TEXT NOT NULL,
                        `scheduledStart` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `loggedAt` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        PRIMARY KEY(`occurrenceKey`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_sessionId` ON `attendance_records` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_occurrenceDate` ON `attendance_records` (`occurrenceDate`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_records_sessionId_occurrenceDate_scheduledStart` ON `attendance_records` (`sessionId`, `occurrenceDate`, `scheduledStart`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `flashcard_decks` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `courseCode` TEXT,
                        `description` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `flashcards` (
                        `id` TEXT NOT NULL,
                        `deckId` TEXT NOT NULL,
                        `front` TEXT NOT NULL,
                        `back` TEXT NOT NULL,
                        `hint` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `dueAt` INTEGER NOT NULL,
                        `lastReviewedAt` INTEGER,
                        `reviewCount` INTEGER NOT NULL,
                        `correctCount` INTEGER NOT NULL,
                        `mastery` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`deckId`) REFERENCES `flashcard_decks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_deckId` ON `flashcards` (`deckId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_dueAt` ON `flashcards` (`dueAt`)")
            }
        }


        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `starred` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `reverseEnabled` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `cardType` TEXT NOT NULL DEFAULT 'BASIC'")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `quizzes` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `courseCode` TEXT,
                        `description` TEXT,
                        `passingScore` INTEGER NOT NULL,
                        `shuffleQuestions` INTEGER NOT NULL,
                        `shuffleChoices` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `quiz_questions` (
                        `id` TEXT NOT NULL,
                        `quizId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `prompt` TEXT NOT NULL,
                        `optionsJson` TEXT NOT NULL,
                        `correctAnswer` TEXT NOT NULL,
                        `explanation` TEXT,
                        `tags` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`quizId`) REFERENCES `quizzes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_quizId` ON `quiz_questions` (`quizId`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `quiz_attempts` (
                        `id` TEXT NOT NULL,
                        `quizId` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `completedAt` INTEGER NOT NULL,
                        `score` INTEGER NOT NULL,
                        `total` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `incorrectQuestionIdsJson` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`quizId`) REFERENCES `quizzes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_attempts_quizId` ON `quiz_attempts` (`quizId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_attempts_completedAt` ON `quiz_attempts` (`completedAt`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `json_import_records` (
                        `fileType` TEXT NOT NULL,
                        `contentId` TEXT NOT NULL,
                        `importedAt` INTEGER NOT NULL,
                        `destinationId` TEXT,
                        PRIMARY KEY(`fileType`, `contentId`)
                    )""".trimIndent()
                )
            }
        }


        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `imageUri` TEXT")
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `occlusionJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `quiz_questions` ADD COLUMN `metadataJson` TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE `quiz_questions` ADD COLUMN `imageUri` TEXT")
                db.execSQL("ALTER TABLE `quizzes` ADD COLUMN `timeLimitMinutes` INTEGER")
                db.execSQL("ALTER TABLE `quizzes` ADD COLUMN `feedbackMode` TEXT NOT NULL DEFAULT 'IMMEDIATE'")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `study_topics` (
                    `id` TEXT NOT NULL, `courseCode` TEXT NOT NULL, `name` TEXT NOT NULL, `parentTopicId` TEXT,
                    `examDate` TEXT, `priority` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_topics_courseCode` ON `study_topics` (`courseCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_topics_parentTopicId` ON `study_topics` (`parentTopicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_topics_examDate` ON `study_topics` (`examDate`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `study_notes` (
                    `id` TEXT NOT NULL, `courseCode` TEXT, `topicId` TEXT, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `tags` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`),
                    FOREIGN KEY(`topicId`) REFERENCES `study_topics`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_notes_courseCode` ON `study_notes` (`courseCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_notes_topicId` ON `study_notes` (`topicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_notes_updatedAt` ON `study_notes` (`updatedAt`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `formula_references` (
                    `id` TEXT NOT NULL, `courseCode` TEXT, `topicId` TEXT, `title` TEXT NOT NULL, `expression` TEXT NOT NULL,
                    `variables` TEXT, `units` TEXT, `workedExample` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`),
                    FOREIGN KEY(`topicId`) REFERENCES `study_topics`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_formula_references_courseCode` ON `formula_references` (`courseCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_formula_references_topicId` ON `formula_references` (`topicId`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `mistake_records` (
                    `id` TEXT NOT NULL, `sourceType` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `courseCode` TEXT, `topicTag` TEXT,
                    `prompt` TEXT NOT NULL, `userAnswer` TEXT, `correctAnswer` TEXT NOT NULL, `explanation` TEXT, `confidence` TEXT NOT NULL,
                    `missedAt` INTEGER NOT NULL, `retryAt` INTEGER NOT NULL, `resolved` INTEGER NOT NULL, `timesMissed` INTEGER NOT NULL, PRIMARY KEY(`id`)
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_records_sourceType` ON `mistake_records` (`sourceType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_records_sourceId` ON `mistake_records` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_records_courseCode` ON `mistake_records` (`courseCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_records_topicTag` ON `mistake_records` (`topicTag`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_records_retryAt` ON `mistake_records` (`retryAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mistake_records_resolved` ON `mistake_records` (`resolved`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `study_goals` (
                    `id` TEXT NOT NULL, `courseCode` TEXT, `topicTag` TEXT, `title` TEXT NOT NULL, `goalType` TEXT NOT NULL,
                    `targetValue` INTEGER NOT NULL, `progressValue` INTEGER NOT NULL, `dueDate` TEXT, `completed` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`)
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_goals_courseCode` ON `study_goals` (`courseCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_goals_dueDate` ON `study_goals` (`dueDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_goals_completed` ON `study_goals` (`completed`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `study_plan_items` (
                    `id` TEXT NOT NULL, `courseCode` TEXT, `topicTag` TEXT, `title` TEXT NOT NULL, `plannedDate` TEXT NOT NULL,
                    `minutes` INTEGER NOT NULL, `kind` TEXT NOT NULL, `completed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`)
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_plan_items_plannedDate` ON `study_plan_items` (`plannedDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_plan_items_courseCode` ON `study_plan_items` (`courseCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_plan_items_topicTag` ON `study_plan_items` (`topicTag`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_plan_items_completed` ON `study_plan_items` (`completed`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `flashcard_review_events` (
                    `id` TEXT NOT NULL, `cardId` TEXT NOT NULL, `deckId` TEXT, `courseCode` TEXT, `rating` TEXT NOT NULL,
                    `reviewedAt` INTEGER NOT NULL, PRIMARY KEY(`id`)
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcard_review_events_cardId` ON `flashcard_review_events` (`cardId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcard_review_events_courseCode` ON `flashcard_review_events` (`courseCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcard_review_events_reviewedAt` ON `flashcard_review_events` (`reviewedAt`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `quiz_answer_results` (
                    `id` TEXT NOT NULL, `attemptId` TEXT NOT NULL, `quizId` TEXT NOT NULL, `questionId` TEXT NOT NULL, `userAnswer` TEXT NOT NULL,
                    `correctAnswer` TEXT NOT NULL, `correct` INTEGER NOT NULL, `confidence` TEXT NOT NULL, `answeredAt` INTEGER NOT NULL, PRIMARY KEY(`id`),
                    FOREIGN KEY(`attemptId`) REFERENCES `quiz_attempts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`questionId`) REFERENCES `quiz_questions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_answer_results_attemptId` ON `quiz_answer_results` (`attemptId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_answer_results_questionId` ON `quiz_answer_results` (`questionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_answer_results_answeredAt` ON `quiz_answer_results` (`answeredAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_answer_results_correct` ON `quiz_answer_results` (`correct`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `question_bank` (
                    `id` TEXT NOT NULL, `courseCode` TEXT, `type` TEXT NOT NULL, `prompt` TEXT NOT NULL, `optionsJson` TEXT NOT NULL,
                    `correctAnswer` TEXT NOT NULL, `explanation` TEXT, `tags` TEXT NOT NULL, `metadataJson` TEXT NOT NULL, `imageUri` TEXT,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`)
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_bank_courseCode` ON `question_bank` (`courseCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_bank_tags` ON `question_bank` (`tags`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_bank_updatedAt` ON `question_bank` (`updatedAt`)")
            }
        }

        fun get(context: Context): PunlaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PunlaDatabase::class.java,
                    "punla.db"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    // Very old development installs never had migration specs.
                    // Preserve current v6+ personal data; only pre-v6 schemas
                    // may still be recreated rather than crashing at launch.
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
                    .build().also { INSTANCE = it }
            }
    }
}
