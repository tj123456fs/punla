package com.uplb.punla.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.diagnostics.PunlaDiagnostics
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for Punla's persistent/recovery background jobs.
 *
 * Keeping these registrations in one place prevents startup, restore, and boot
 * recovery paths from drifting apart as new workers are added.
 */
object CoreReliabilityScheduler {
    const val CLASS_REMINDER_WORK = "class_reminder_work"
    const val STUDY_NUDGE_WORK = "study_nudge_work"
    const val BACKUP_NUDGE_WORK = "backup_nudge_work"

    fun ensureScheduled(context: Context, updateExisting: Boolean = false) {
        val app = context.applicationContext
        val repo = PunlaRepository(app)
        val manager = WorkManager.getInstance(app)
        val policy = if (updateExisting) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP

        fun safe(label: String, block: () -> Unit) {
            runCatching(block).onFailure { error ->
                PunlaDiagnostics.warn(app, "CoreScheduler", "Couldn't schedule $label", error)
            }
        }

        safe("daily reminders") {
            ReminderScheduler.scheduleDaily(app, updateExisting = updateExisting)
        }

        safe("class reminders") {
            val request = PeriodicWorkRequestBuilder<ClassReminderWorker>(15, TimeUnit.MINUTES).build()
            manager.enqueueUniquePeriodicWork(CLASS_REMINDER_WORK, policy, request)
        }

        safe("study nudges") {
            val request = PeriodicWorkRequestBuilder<StudyNudgeWorker>(30, TimeUnit.MINUTES).build()
            manager.enqueueUniquePeriodicWork(STUDY_NUDGE_WORK, policy, request)
        }

        safe("backup nudges") {
            val request = PeriodicWorkRequestBuilder<BackupNudgeWorker>(7, TimeUnit.DAYS).build()
            manager.enqueueUniquePeriodicWork(BACKUP_NUDGE_WORK, policy, request)
        }

        safe("class-day assistant") {
            if (repo.notificationsEnabled && repo.classDayNotificationEnabled) {
                ClassDayNotificationScheduler.ensureScheduled(app)
            } else {
                ClassDayNotificationScheduler.cancel(app)
            }
        }

        safe("attendance auto-log") {
            AttendanceAutoLogScheduler.sync(app, repo.autoAttendanceEnabled)
        }
    }

    fun recordRecoveryEvent(context: Context, action: String?) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_RECOVERY_AT, System.currentTimeMillis())
            .putString(KEY_LAST_RECOVERY_ACTION, action ?: "unknown")
            .apply()
    }

    fun lastRecoveryAt(context: Context): Long =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_RECOVERY_AT, 0L)

    fun lastRecoveryAction(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_RECOVERY_ACTION, null)

    private const val PREFS = "punla_reliability"
    private const val KEY_LAST_RECOVERY_AT = "last_recovery_at"
    private const val KEY_LAST_RECOVERY_ACTION = "last_recovery_action"
}
