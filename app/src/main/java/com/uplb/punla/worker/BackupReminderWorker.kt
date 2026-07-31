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
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.notification.TrackedNotification
import java.util.concurrent.TimeUnit

/**
 * Roadmap #6 "Backup nudges": a periodic, low-frequency check that nudges
 * (never forces) a backup once it's been a while — mirrors the daily
 * cadence of [DeadlineWorker] but only fires every ~14 days without an
 * export, since backups aren't as time-sensitive as deadlines.
 */
class BackupReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        val NUDGE_THRESHOLD_MILLIS = TimeUnit.DAYS.toMillis(14)
    }

    override suspend fun doWork(): Result {
        val repo = PunlaRepository(context)

        if (!repo.notificationsEnabled) {
            return Result.success()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return Result.success()
            }
        }

        val lastBackup = repo.lastBackupAt
        val staleEnough = lastBackup == null ||
            (System.currentTimeMillis() - lastBackup) >= NUDGE_THRESHOLD_MILLIS

        if (staleEnough) {
            val text = if (lastBackup == null) {
                "You haven't backed up your data yet. Save a copy from Settings."
            } else {
                "It's been a couple weeks since your last backup. Worth saving a fresh copy from Settings."
            }
            showNotification("Backup reminder", text)
        }

        return Result.success()
    }

    private suspend fun showNotification(title: String, content: String) {
        val channelId = "punla_backup_channel"
        val notificationManager = NotificationManagerCompat.from(context)

        val name = "Backup Reminders"
        val descriptionText = "Occasional nudges to back up your data"
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)

        try {
            TrackedNotification.post(
                context = context,
                manager = notificationManager,
                notificationId = 2,
                builder = builder,
                workerName = "BackupReminderWorker",
                notificationType = "backup",
                route = "settings"
            )
        } catch (e: SecurityException) {
            // Permission wasn't granted
        }
    }
}
