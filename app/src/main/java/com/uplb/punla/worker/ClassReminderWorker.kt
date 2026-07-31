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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Sibling to [DeadlineWorker]: checks for classes starting within the next
 * 15 minutes, mirrors the web app's checkReminders() class-start branch
 * (index.html ~line 783), including its notifiedKeys-style dedupe so the
 * same class doesn't re-notify twice in one day.
 */
class ClassReminderWorker(
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
                return Result.success()
            }
        }

        val classes = repo.allClasses()
        val soon = repo.classesStartingSoon(classes)
        if (soon.isEmpty()) return Result.success()

        val today = LocalDate.now().toString()
        val prefs = context.getSharedPreferences("punla_prefs", Context.MODE_PRIVATE)
        val notifiedKeys = (prefs.getStringSet("class_notified_keys", null) ?: emptySet()).toMutableSet()

        var didNotify = false
        for (c in soon) {
            val key = "class:${c.id}:$today:${c.start}"
            if (key in notifiedKeys) continue

            showNotification(c.code, formatBody(c))
            notifiedKeys += key
            didNotify = true
        }

        if (didNotify) {
            // Cap at ~200 entries, same as the web app, so this doesn't grow forever.
            val trimmed = if (notifiedKeys.size > 200) {
                notifiedKeys.toList().takeLast(200).toMutableSet()
            } else notifiedKeys
            prefs.edit().putStringSet("class_notified_keys", trimmed).apply()
        }

        return Result.success()
    }

    private fun formatBody(c: com.uplb.punla.data.entity.ClassSession): String {
        val time = runCatching {
            LocalTime.parse(c.start, DateTimeFormatter.ofPattern("HH:mm"))
                .format(DateTimeFormatter.ofPattern("h:mm a"))
        }.getOrDefault(c.start)
        return "$time \u00B7 ${c.room ?: "TBA"}"
    }

    private suspend fun showNotification(code: String, body: String) {
        val channelId = "punla_class_channel"
        val notificationManager = NotificationManagerCompat.from(context)

        // minSdk is 26 (O), so notification channels always exist here — no SDK_INT guard needed.
        val channel = NotificationChannel(
            channelId,
            "Class Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminders when a class is about to start"
        }
        val sysManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        sysManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$code starts soon")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)

        try {
            TrackedNotification.post(
                context = context,
                manager = notificationManager,
                notificationId = code.hashCode(),
                builder = builder,
                workerName = "ClassReminderWorker",
                notificationType = "class",
                route = "schedule"
            )
        } catch (e: SecurityException) {
            // Permission wasn't granted
        }
    }
}
