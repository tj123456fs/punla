package com.uplb.punla.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uplb.punla.MainActivity
import com.uplb.punla.R
import com.uplb.punla.data.PunlaRepository
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

    private fun showNotification(title: String, content: String) {
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

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainActivity.EXTRA_START_ROUTE, "settings")
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            notificationManager.notify(2, builder.build())
        } catch (e: SecurityException) {
            // Permission wasn't granted
        }
    }
}
