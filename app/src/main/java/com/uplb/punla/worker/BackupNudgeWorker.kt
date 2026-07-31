package com.uplb.punla.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uplb.punla.R
import com.uplb.punla.data.PunlaDatabase
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.notification.TrackedNotification
import java.util.concurrent.TimeUnit

/**
 * Roadmap #6 — nudges (never forces) a backup export when it's been a
 * while since the last one. Shape mirrors DeadlineWorker/ClassReminderWorker.
 */
class BackupNudgeWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        /** Don't nag more than once a week, and don't post the same nudge
         * again just because the backup is still stale the next time this
         * worker runs. */
        private val NUDGE_INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(7)
    }

    override suspend fun doWork(): Result {
        val repo = PunlaRepository(context)
        if (!repo.notificationsEnabled) return Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return Result.success()
            }
        }

        val now = System.currentTimeMillis()
        val lastBackup = repo.lastBackupAt
        val backupIsStale = lastBackup == null || (now - lastBackup) >= NUDGE_INTERVAL_MILLIS
        if (!backupIsStale) return Result.success()

        val lastNudged = repo.lastBackupNudgeAt
        if (lastNudged != null && (now - lastNudged) < NUDGE_INTERVAL_MILLIS) return Result.success()

        // Nothing worth backing up yet on a freshly-installed app — don't
        // nudge an empty planner.
        val db = PunlaDatabase.get(context)
        val hasData = db.classSessionDao().getAll().isNotEmpty() ||
            db.expenseDao().getAll().isNotEmpty() ||
            db.deadlineDao().getAll().isNotEmpty()
        if (!hasData) return Result.success()

        val text = if (lastBackup == null) {
            "You haven't backed up your data yet. Save a copy from Settings."
        } else {
            val days = TimeUnit.MILLISECONDS.toDays(now - lastBackup)
            "It's been $days days since your last backup. Save a fresh copy from Settings."
        }
        showNotification("Back up your data", text)
        repo.lastBackupNudgeAt = now
        return Result.success()
    }

    private suspend fun showNotification(title: String, content: String) {
        val channelId = "punla_backup_channel"
        val notificationManager = NotificationManagerCompat.from(context)

        val name = "Backup Reminders"
        val descriptionText = "Occasional nudges to back up your Punla data"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val sysManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        sysManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)

        try {
            TrackedNotification.post(
                context = context,
                manager = notificationManager,
                notificationId = 2,
                builder = builder,
                workerName = "BackupNudgeWorker",
                notificationType = "backup",
                route = "settings"
            )
        } catch (e: SecurityException) {
            // Permission wasn't granted
        }
    }
}
