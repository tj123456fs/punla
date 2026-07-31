package com.uplb.punla.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.uplb.punla.data.dao.ChecklistDao
import com.uplb.punla.data.dao.ClassSessionDao
import com.uplb.punla.data.dao.DeadlineDao
import com.uplb.punla.data.dao.ExpenseDao
import com.uplb.punla.data.dao.GradesDao
import com.uplb.punla.data.dao.StudySessionDao
import com.uplb.punla.data.dao.IntelligenceDao
import com.uplb.punla.data.entity.Archive
import com.uplb.punla.data.entity.ChecklistItem
import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.data.entity.DeadlineRule
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.data.entity.ExpenseRule
import com.uplb.punla.data.entity.GradeCourse
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
        NotificationEvent::class
    ],
    // v6 -> v7: local-intelligence event tables plus StudySession end reason
    // and suggestion linkage. This is the first data-preserving migration.
    version = 7,
    exportSchema = false
)
abstract class PunlaDatabase : RoomDatabase() {
    abstract fun classSessionDao(): ClassSessionDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun deadlineDao(): DeadlineDao
    abstract fun gradesDao(): GradesDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun intelligenceDao(): IntelligenceDao

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

        fun get(context: Context): PunlaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PunlaDatabase::class.java,
                    "punla.db"
                )
                    .addMigrations(MIGRATION_6_7)
                    // Very old development installs never had migration specs.
                    // Preserve current v6+ personal data; only pre-v6 schemas
                    // may still be recreated rather than crashing at launch.
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
                    .build().also { INSTANCE = it }
            }
    }
}
