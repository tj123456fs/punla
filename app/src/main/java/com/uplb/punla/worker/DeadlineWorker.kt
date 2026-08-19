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
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class DeadlineWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = PunlaRepository(context)
        
        if (!repo.notificationsEnabled) {
            return Result.success()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return Result.success() // Can't post without permission
            }
        }

        val deadlines = repo.getDeadlines()
        val pending = deadlines.filter { !it.done }

        val today = LocalDate.now()
        
        val urgentDeadlines = pending.filter { 
            val due = runCatching { LocalDate.parse(it.due) }.getOrNull()
            if (due != null) {
                val days = ChronoUnit.DAYS.between(today, due)
                days in 0..3 // Due today, tomorrow, or in 2-3 days
            } else {
                false
            }
        }

        if (urgentDeadlines.isNotEmpty()) {
            val snapshotKey = urgentDeadlines.sortedBy { it.id }.joinToString("|") { d ->
                val due = runCatching { LocalDate.parse(d.due) }.getOrNull()
                val days = due?.let { ChronoUnit.DAYS.between(today, it) } ?: 99
                "${d.id}:$days"
            }
            val prefs = context.getSharedPreferences("punla_prefs", Context.MODE_PRIVATE)
            if (prefs.getString("deadline_notification_snapshot", null) == snapshotKey) return Result.success()

            val title = "Upcoming Deadlines"
            val text = if (urgentDeadlines.size == 1) {
                val d = urgentDeadlines.first()
                "${d.title} (${d.course ?: d.type}) is due soon!"
            } else {
                "You have ${urgentDeadlines.size} deadlines due within the next 3 days."
            }
            showNotification(title, text)
            prefs.edit().putString("deadline_notification_snapshot", snapshotKey).apply()
        } else {
            context.getSharedPreferences("punla_prefs", Context.MODE_PRIVATE)
                .edit().remove("deadline_notification_snapshot").apply()
        }

        return Result.success()
    }

    private suspend fun showNotification(title: String, content: String) {
        PunlaNotifications.ensureChannels(context)
        val channelId = PunlaNotifications.CHANNEL_DEADLINE
        val notificationManager = NotificationManagerCompat.from(context)

        val builder = PunlaNotifications.academic(NotificationCompat.Builder(context, channelId))
            .setSmallIcon(R.mipmap.ic_launcher) // Defaulting to launcher icon as no custom icon was provided
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)

        try {
            TrackedNotification.post(
                context = context,
                manager = notificationManager,
                notificationId = PunlaNotifications.ID_DEADLINES,
                builder = builder,
                workerName = "DeadlineWorker",
                notificationType = "deadline",
                route = "deadlines"
            )
        } catch (e: SecurityException) {
            // Permission wasn't granted
        }
    }
}
