package com.uplb.punla.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.uplb.punla.data.dao.ChecklistDao
import com.uplb.punla.data.dao.ClassSessionDao
import com.uplb.punla.data.dao.DeadlineDao
import com.uplb.punla.data.dao.ExpenseDao
import com.uplb.punla.data.dao.GradesDao
import com.uplb.punla.data.dao.StudySessionDao
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
        StudySession::class
    ],
    // v5 -> v6: Expense.isFixed added (Weekly Budgeting feature). No
    // Migration object needed, same as every bump before it — see
    // fallbackToDestructiveMigration() below.
    version = 6,
    exportSchema = false
)
abstract class PunlaDatabase : RoomDatabase() {
    abstract fun classSessionDao(): ClassSessionDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun deadlineDao(): DeadlineDao
    abstract fun gradesDao(): GradesDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun studySessionDao(): StudySessionDao

    companion object {
        @Volatile private var INSTANCE: PunlaDatabase? = null

        fun get(context: Context): PunlaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PunlaDatabase::class.java,
                    "punla.db"
                )
                    // No real migration path is defined for local schema tweaks
                    // like the semesterId index added in v2. Without this, any
                    // version/schema-hash mismatch throws IllegalStateException
                    // at startup instead of just recreating the (local-only,
                    // no-backend) database. Revisit with real Migration objects
                    // before this ever ships with user data worth preserving.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
