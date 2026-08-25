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
import com.uplb.punla.data.dao.StudySessionDao
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
import com.uplb.punla.data.entity.Semester
import com.uplb.punla.data.entity.StudySession
import com.uplb.punla.data.entity.StudySuggestionEvent
import com.uplb.punla.data.entity.NotificationEvent

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
        Flashcard::class
    ],
    // v7 -> v8: per-occurrence attendance history used by the ongoing
    // class notification and the schedule/dashboard attendance controls.
    version = 9,
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

        fun get(context: Context): PunlaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PunlaDatabase::class.java,
                    "punla.db"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    // Very old development installs never had migration specs.
                    // Preserve current v6+ personal data; only pre-v6 schemas
                    // may still be recreated rather than crashing at launch.
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
                    .build().also { INSTANCE = it }
            }
    }
}
