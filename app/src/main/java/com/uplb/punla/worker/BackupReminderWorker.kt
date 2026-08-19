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
import com.uplb.punla.notification.PunlaNotifications
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
        if (PunlaNotifications.isRoutineQuietHours(repo.quietHoursEnabled)) return Result.success()

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
        PunlaNotifications.ensureChannels(context)
        val channelId = PunlaNotifications.CHANNEL_BACKUP
        val notificationManager = NotificationManagerCompat.from(context)

        val builder = PunlaNotifications.routine(NotificationCompat.Builder(context, channelId))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                        .setAutoCancel(true)

        try {
            TrackedNotification.post(
                context = context,
                manager = notificationManager,
                notificationId = PunlaNotifications.ID_BACKUP,
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
