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
            val title = "Upcoming Deadlines"
            val text = if (urgentDeadlines.size == 1) {
                val d = urgentDeadlines.first()
                "${d.title} (${d.course ?: d.type}) is due soon!"
            } else {
                "You have ${urgentDeadlines.size} deadlines due within the next 3 days."
            }
            showNotification(title, text)
        }

        return Result.success()
    }

    private fun showNotification(title: String, content: String) {
        val channelId = "punla_deadline_channel"
        val notificationManager = NotificationManagerCompat.from(context)

        // minSdk is 26 (O), so NotificationChannel is always available here — no SDK_INT guard needed.
        val name = "Deadline Reminders"
        val descriptionText = "Reminders for upcoming academic deadlines"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val sysManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        sysManager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Defaulting to launcher icon as no custom icon was provided
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            notificationManager.notify(1, builder.build())
        } catch (e: SecurityException) {
            // Permission wasn't granted
        }
    }
}
