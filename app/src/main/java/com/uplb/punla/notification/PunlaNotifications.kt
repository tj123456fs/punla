package com.uplb.punla.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import java.time.LocalTime

/** Shared notification policy so channels, IDs, grouping, and quiet behavior stay consistent. */
object PunlaNotifications {
    const val CHANNEL_CLASS = "punla_class_channel"
    const val CHANNEL_CLASS_DAY = "punla_class_day"
    const val CHANNEL_DEADLINE = "punla_deadline_channel"
    const val CHANNEL_DAILY_BRIEF = "punla_daily_brief"
    const val CHANNEL_ROUTINE = "punla_routine_channel"
    const val CHANNEL_BUDGET = "punla_budget_channel"
    const val CHANNEL_BACKUP = "punla_backup_channel"
    const val CHANNEL_PUSH = "punla_push_channel"

    const val GROUP_ACADEMIC = "punla.academic"
    const val GROUP_ROUTINE = "punla.routine"
    const val GROUP_FINANCE = "punla.finance"

    const val ID_DEADLINES = 1101
    const val ID_CHECKLIST = 1201
    const val ID_BUDGET_MONTH = 1301
    const val ID_BUDGET_WEEK = 1302
    const val ID_BACKUP = 1401
    const val ID_MORNING_AGENDA = 1501
    const val ID_PUSH_BASE = 1600

    const val QUIET_START_HOUR = 22
    const val QUIET_END_HOUR = 7

    fun classReminderId(code: String): Int = 20000 + Math.floorMod(code.hashCode(), 9000)

    /** Quiet hours intentionally apply to routine nudges, not class/timer/deadline alerts. */
    fun isRoutineQuietHours(enabled: Boolean, time: LocalTime = LocalTime.now()): Boolean {
        if (!enabled) return false
        val hour = time.hour
        return hour >= QUIET_START_HOUR || hour < QUIET_END_HOUR
    }

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(CHANNEL_CLASS, "Class reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Time-sensitive alerts before a class starts"
            },
            NotificationChannel(CHANNEL_CLASS_DAY, "Current class", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Silent ongoing card for the current class, next class, and breaks"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_DEADLINE, "Deadlines", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Academic deadlines that need attention"
            },
            NotificationChannel(CHANNEL_DAILY_BRIEF, "Daily brief", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A quiet morning summary of today's classes and due work"
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_ROUTINE, "Routine reminders", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Checklists and other low-priority planning nudges"
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_BUDGET, "Budget alerts", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Budget threshold check-ins"
            },
            NotificationChannel(CHANNEL_BACKUP, "Backup reminders", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Occasional reminders to back up Punla data"
                setShowBadge(false)
            },
            NotificationChannel(CHANNEL_PUSH, "Punla updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Important updates delivered to Punla"
            }
        )
        channels.forEach(manager::createNotificationChannel)

        // Session 22 used a temporary channel id before notification policy was centralized.
        // Remove that orphaned channel so Android settings do not show two "Current class" entries.
        if (manager.getNotificationChannel("punla_class_day_channel") != null) {
            manager.deleteNotificationChannel("punla_class_day_channel")
        }
    }

    fun routine(builder: NotificationCompat.Builder): NotificationCompat.Builder = builder
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setGroup(GROUP_ROUTINE)

    fun academic(builder: NotificationCompat.Builder): NotificationCompat.Builder = builder
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setOnlyAlertOnce(true)
        .setGroup(GROUP_ACADEMIC)
}
