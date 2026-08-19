package com.uplb.punla.worker

import android.Manifest
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
import com.uplb.punla.notification.TrackedNotification
import com.uplb.punla.notification.PunlaNotifications
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

        if (!repo.notificationsEnabled) return Result.success()

        // The ongoing class-day card is intentionally silent. This worker is
        // the single attention-grabbing alert before class, so it remains
        // enabled even when the class-day card is visible.

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

            showNotification(c.id, c.code, formatBody(c), c.room)
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

    private suspend fun showNotification(sessionId: String, code: String, body: String, room: String?) {
        PunlaNotifications.ensureChannels(context)
        val channelId = PunlaNotifications.CHANNEL_CLASS
        val notificationManager = NotificationManagerCompat.from(context)

        // minSdk is 26 (O), so notification channels always exist here — no SDK_INT guard needed.
        val builder = PunlaNotifications.academic(NotificationCompat.Builder(context, channelId))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$code starts in 15 min")
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(20L * 60L * 1000L)
            .addAction(R.mipmap.ic_launcher, "Schedule", activityIntent("schedule", null, "$code:schedule"))

        room?.takeIf { it.isNotBlank() }?.let {
            builder.addAction(R.mipmap.ic_launcher, "Navigate", activityIntent("campus", it, "$code:navigate"))
        }

        try {
            TrackedNotification.post(
                context = context,
                manager = notificationManager,
                notificationId = PunlaNotifications.classReminderId(sessionId),
                builder = builder,
                workerName = "ClassReminderWorker",
                notificationType = "class",
                route = "schedule"
            )
        } catch (e: SecurityException) {
            // Permission wasn't granted
        }
    }
    private fun activityIntent(route: String, mapQuery: String?, key: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.uplb.punla.CLASS_REMINDER_ACTION:$key"
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_START_ROUTE, route)
            mapQuery?.let { putExtra(MainActivity.EXTRA_MAP_QUERY, it) }
        }
        return PendingIntent.getActivity(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

}
