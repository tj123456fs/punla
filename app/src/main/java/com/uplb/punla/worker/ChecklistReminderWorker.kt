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

/**
 * Daily check for whether it's time to nudge about unfinished pre-enrollment
 * checklist items. Fires once per threshold crossing (14/7/3/1/0 days until
 * classes start) rather than once a day, mirroring BudgetWorker's
 * threshold-crossing pattern so it doesn't spam the same warning daily.
 */
class ChecklistReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private val THRESHOLDS = listOf(14L, 7L, 3L, 1L, 0L)
    }

    override suspend fun doWork(): Result {
        val repo = PunlaRepository(context)
        if (!repo.notificationsEnabled) return Result.success()
        if (PunlaNotifications.isRoutineQuietHours(repo.quietHoursEnabled)) return Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return Result.success()
            }
        }

        val daysLeft = repo.daysUntilClassesStart()
        if (daysLeft < 0) return Result.success() // classes already started

        val items = repo.getChecklistItems()
        val pending = items.filter { !it.checked }
        if (pending.isEmpty()) return Result.success()

        // Find the smallest threshold we've reached that we haven't already
        // nudged for (thresholds are checked descending so we land on the
        // most urgent one crossed since the last run).
        val thresholdToNudge = THRESHOLDS
            .filter { daysLeft <= it && it < repo.lastChecklistNudgeDaysOut }
            .minOrNull() ?: return Result.success()

        val text = when {
            thresholdToNudge == 0L -> "Classes start today — ${pending.size} requirement${if (pending.size == 1) "" else "s"} still unchecked."
            thresholdToNudge == 1L -> "Classes start tomorrow — ${pending.size} requirement${if (pending.size == 1) "" else "s"} still unchecked."
            else -> "${pending.size} requirement${if (pending.size == 1) "" else "s"} left with $thresholdToNudge days until classes start."
        }
        showNotification("Before classes start", text)

        repo.lastChecklistNudgeDaysOut = thresholdToNudge
        repo.lastChecklistNudgeAt = System.currentTimeMillis()
        return Result.success()
    }

    private suspend fun showNotification(title: String, content: String) {
        PunlaNotifications.ensureChannels(context)
        val channelId = PunlaNotifications.CHANNEL_ROUTINE
        val notificationManager = NotificationManagerCompat.from(context)

        val builder = PunlaNotifications.routine(NotificationCompat.Builder(context, channelId))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
                        .setAutoCancel(true)

        try {
            TrackedNotification.post(
                context = context,
                manager = notificationManager,
                notificationId = PunlaNotifications.ID_CHECKLIST,
                builder = builder,
                workerName = "ChecklistReminderWorker",
                notificationType = "checklist",
                route = "checklist"
            )
        } catch (e: SecurityException) {
            // Permission wasn't granted
        }
    }
}
