package com.uplb.punla.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.uplb.punla.data.PunlaDatabase
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.notification.PunlaNotifications
import com.uplb.punla.pomodoro.PomodoroAlarmScheduler
import com.uplb.punla.pomodoro.PomodoroBootReceiver
import com.uplb.punla.worker.CoreReliabilityScheduler
import com.uplb.punla.worker.ReliabilityProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Coarse health state intended for a human-readable reliability screen. */
enum class HealthState {
    HEALTHY,
    ATTENTION,
    BLOCKED,
    INFO
}

enum class HealthAction {
    NOTIFICATION_SETTINGS,
    EXACT_ALARM_SETTINGS,
    BATTERY_SETTINGS,
    APP_SETTINGS,
    REPAIR_BACKGROUND_JOBS,
    RUN_BACKGROUND_PROBE,
    ARM_REBOOT_PROBE
}

data class HealthCheck(
    val key: String,
    val title: String,
    val status: String,
    val detail: String,
    val state: HealthState,
    val action: HealthAction? = null,
    val actionLabel: String? = null
)

data class SystemHealthSnapshot(
    val generatedAt: Long,
    val checks: List<HealthCheck>,
    val diagnosticErrors: Int,
    val diagnosticWarnings: Int,
    val diagnosticTail: String
) {
    val attentionCount: Int
        get() = checks.count { it.state == HealthState.ATTENTION || it.state == HealthState.BLOCKED }
}

/**
 * Phase 0B local-only health inspector.
 *
 * It deliberately avoids network calls and does not upload anything. Potentially
 * blocking checks (Room / WorkManager / diagnostic-file reads) run on Dispatchers.IO.
 */
object SystemHealthInspector {
    private val staticNotificationChannels = setOf(
        PunlaNotifications.CHANNEL_CLASS,
        PunlaNotifications.CHANNEL_CLASS_DAY,
        PunlaNotifications.CHANNEL_DEADLINE,
        PunlaNotifications.CHANNEL_DAILY_BRIEF,
        PunlaNotifications.CHANNEL_ROUTINE,
        PunlaNotifications.CHANNEL_BUDGET,
        PunlaNotifications.CHANNEL_BACKUP,
        PunlaNotifications.CHANNEL_PUSH
    )

    suspend fun inspect(context: Context): SystemHealthSnapshot = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val repo = PunlaRepository(app)
        val checks = mutableListOf<HealthCheck>()

        checks += notificationHealth(app, repo)
        checks += exactAlarmHealth(app)
        checks += backgroundRestrictionHealth(app)
        checks += batteryHealth(app)
        checks += recoveryHealth(app)
        checks += backgroundProbeHealth(app)
        checks += rebootProbeHealth(app)
        checks += workerHealth(app, repo)
        checks += databaseHealth(app)
        checks += backupHealth(repo.lastBackupAt)

        val diagnostics = PunlaDiagnostics.read(app)
        val lines = diagnostics.lineSequence().filter { it.isNotBlank() }.toList()
        val errorCount = lines.count { " ERROR [" in it }
        val warningCount = lines.count { " WARN [" in it }
        val tail = lines.takeLast(80).joinToString("\n")

        SystemHealthSnapshot(
            generatedAt = System.currentTimeMillis(),
            checks = checks,
            diagnosticErrors = errorCount,
            diagnosticWarnings = warningCount,
            diagnosticTail = tail
        )
    }

    private fun notificationHealth(context: Context, repo: PunlaRepository): HealthCheck {
        if (!repo.notificationsEnabled) {
            return HealthCheck(
                key = "notifications",
                title = "Notifications",
                status = "Off in Punla",
                detail = "All Punla reminders are disabled in app settings.",
                state = HealthState.INFO,
                action = HealthAction.NOTIFICATION_SETTINGS,
                actionLabel = "Android settings"
            )
        }

        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!runtimeGranted || !systemEnabled) {
            return HealthCheck(
                key = "notifications",
                title = "Notifications",
                status = "Blocked",
                detail = "Android is preventing Punla from posting notifications.",
                state = HealthState.BLOCKED,
                action = HealthAction.NOTIFICATION_SETTINGS,
                actionLabel = "Fix"
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val blocked = manager.notificationChannels.count {
                it.id in staticNotificationChannels && it.importance == NotificationManager.IMPORTANCE_NONE
            }
            if (blocked > 0) {
                return HealthCheck(
                    key = "notifications",
                    title = "Notifications",
                    status = "$blocked category blocked",
                    detail = "The app can notify, but one or more Punla notification categories are disabled in Android.",
                    state = HealthState.ATTENTION,
                    action = HealthAction.NOTIFICATION_SETTINGS,
                    actionLabel = "Review"
                )
            }
        }

        return HealthCheck(
            key = "notifications",
            title = "Notifications",
            status = "Working",
            detail = "Punla has permission to post notifications.",
            state = HealthState.HEALTHY,
            action = HealthAction.NOTIFICATION_SETTINGS,
            actionLabel = "Manage"
        )
    }

    private fun exactAlarmHealth(context: Context): HealthCheck {
        val allowed = PomodoroAlarmScheduler.exactAlarmAvailable(context)
        return if (allowed) {
            HealthCheck(
                key = "exact_alarm",
                title = "Exact alarms",
                status = "Allowed",
                detail = "Pomodoro deadlines can use Android's punctual alarm path.",
                state = HealthState.HEALTHY
            )
        } else {
            HealthCheck(
                key = "exact_alarm",
                title = "Exact alarms",
                status = "Not allowed",
                detail = "Pomodoro still has WorkManager and inexact-alarm recovery, but completion may arrive late under heavy idle/battery restrictions.",
                state = HealthState.ATTENTION,
                action = HealthAction.EXACT_ALARM_SETTINGS,
                actionLabel = "Allow"
            )
        }
    }

    private fun backgroundRestrictionHealth(context: Context): HealthCheck {
        val restricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted
        } else {
            false
        }
        return if (restricted) {
            HealthCheck(
                key = "background",
                title = "Background activity",
                status = "Restricted",
                detail = "Android reports that this app is background-restricted. Reminders and timer recovery can be delayed.",
                state = HealthState.BLOCKED,
                action = HealthAction.APP_SETTINGS,
                actionLabel = "App settings"
            )
        } else {
            HealthCheck(
                key = "background",
                title = "Background activity",
                status = "Allowed",
                detail = "Android is not reporting an explicit background restriction for Punla.",
                state = HealthState.HEALTHY
            )
        }
    }

    private fun batteryHealth(context: Context): HealthCheck {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val unrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
        return if (unrestricted) {
            HealthCheck(
                key = "battery",
                title = "Battery optimization",
                status = "Unrestricted",
                detail = "Punla is exempt from Doze battery optimization.",
                state = HealthState.HEALTHY
            )
        } else {
            HealthCheck(
                key = "battery",
                title = "Battery optimization",
                status = "Optimized",
                detail = "Android may defer non-urgent background jobs while the phone is idle. Punla uses recovery paths, but aggressive OEM battery managers can still delay work.",
                state = HealthState.ATTENTION,
                action = HealthAction.BATTERY_SETTINGS,
                actionLabel = "Review"
            )
        }
    }

    private fun recoveryHealth(context: Context): HealthCheck {
        val receiverEnabled = runCatching {
            context.packageManager.getReceiverInfo(
                ComponentName(context, PomodoroBootReceiver::class.java),
                0
            ).enabled
        }.getOrDefault(false)

        if (!receiverEnabled) {
            return HealthCheck(
                key = "recovery",
                title = "Recovery hooks",
                status = "Unavailable",
                detail = "Punla's reboot/update recovery receiver is unavailable, so background schedules may not self-repair.",
                state = HealthState.BLOCKED
            )
        }

        val lastAction = CoreReliabilityScheduler.lastRecoveryAction(context)
        return HealthCheck(
            key = "recovery",
            title = "Recovery hooks",
            status = "Armed",
            detail = if (lastAction.isNullOrBlank()) {
                "Boot, app-update, clock, and time-zone changes can re-register Punla background jobs and restore an active timer."
            } else {
                "Recovery receiver is active. Last system recovery event: ${lastAction.substringAfterLast('.')}"
            },
            state = HealthState.HEALTHY
        )
    }

    private fun backgroundProbeHealth(context: Context): HealthCheck {
        val scheduledAt = ReliabilityProbe.backgroundScheduledAt(context)
        val completedAt = ReliabilityProbe.backgroundCompletedAt(context)
        if (scheduledAt <= 0L) {
            return HealthCheck(
                key = "background_probe",
                title = "Background execution test",
                status = "Not tested",
                detail = "Run a 2-minute probe, swipe Punla away, and reopen it after a few minutes to verify WorkManager can execute while the app UI is closed.",
                state = HealthState.INFO,
                action = HealthAction.RUN_BACKGROUND_PROBE,
                actionLabel = "Run 2-min test"
            )
        }
        if (completedAt >= scheduledAt) {
            return HealthCheck(
                key = "background_probe",
                title = "Background execution test",
                status = "Passed",
                detail = "The most recent delayed probe completed after it was scheduled.",
                state = HealthState.HEALTHY,
                action = HealthAction.RUN_BACKGROUND_PROBE,
                actionLabel = "Run again"
            )
        }
        val ageMinutes = TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - scheduledAt).coerceAtLeast(0L))
        return if (ageMinutes <= ReliabilityProbe.BACKGROUND_TIMEOUT_MINUTES) {
            HealthCheck(
                key = "background_probe",
                title = "Background execution test",
                status = "Pending",
                detail = "Probe scheduled. Swipe Punla away and leave it closed; reopen after about ${ReliabilityProbe.BACKGROUND_DELAY_MINUTES + 1} minutes.",
                state = HealthState.INFO
            )
        } else {
            HealthCheck(
                key = "background_probe",
                title = "Background execution test",
                status = "Delayed / blocked",
                detail = "The probe has not completed after $ageMinutes minutes. Battery restrictions or force-stop behavior may be blocking background work.",
                state = HealthState.ATTENTION,
                action = HealthAction.RUN_BACKGROUND_PROBE,
                actionLabel = "Retry"
            )
        }
    }

    private fun rebootProbeHealth(context: Context): HealthCheck {
        val armedAt = ReliabilityProbe.rebootArmedAt(context)
        val passedAt = ReliabilityProbe.rebootPassedAt(context)
        if (armedAt <= 0L) {
            return HealthCheck(
                key = "reboot_probe",
                title = "Reboot recovery test",
                status = "Not tested",
                detail = "Arm this test, restart the phone normally, then reopen Punla. The boot receiver will record whether recovery ran.",
                state = HealthState.INFO,
                action = HealthAction.ARM_REBOOT_PROBE,
                actionLabel = "Arm test"
            )
        }
        if (passedAt >= armedAt) {
            return HealthCheck(
                key = "reboot_probe",
                title = "Reboot recovery test",
                status = "Passed",
                detail = "Punla received BOOT_COMPLETED after the most recently armed reboot test and re-registered its recovery schedules.",
                state = HealthState.HEALTHY,
                action = HealthAction.ARM_REBOOT_PROBE,
                actionLabel = "Test again"
            )
        }
        return HealthCheck(
            key = "reboot_probe",
            title = "Reboot recovery test",
            status = "Armed",
            detail = "Restart the phone normally, then open System Health again. Do not use Android's Force stop for this test.",
            state = HealthState.ATTENTION
        )
    }

    private fun workerHealth(context: Context, repo: PunlaRepository): HealthCheck {
        val expected = buildList {
            add("DeadlineWorker")
            add("budget_nudge_work")
            add("checklist_reminder_work")
            add("morning_agenda_work")
            add(CoreReliabilityScheduler.CLASS_REMINDER_WORK)
            add(CoreReliabilityScheduler.STUDY_NUDGE_WORK)
            add(CoreReliabilityScheduler.BACKUP_NUDGE_WORK)
            if (repo.notificationsEnabled && repo.classDayNotificationEnabled) add("class_day_notification_recovery")
            if (repo.autoAttendanceEnabled) add("attendance_auto_log_periodic")
        }

        return try {
            val manager = WorkManager.getInstance(context)
            val missing = expected.filter { name ->
                val infos = manager.getWorkInfosForUniqueWork(name).get(3, TimeUnit.SECONDS)
                infos.none { info ->
                    info.state == WorkInfo.State.ENQUEUED ||
                        info.state == WorkInfo.State.RUNNING ||
                        info.state == WorkInfo.State.BLOCKED
                }
            }
            if (missing.isEmpty()) {
                HealthCheck(
                    key = "workers",
                    title = "Background jobs",
                    status = "Scheduled",
                    detail = "${expected.size} expected persistent jobs are registered with WorkManager.",
                    state = HealthState.HEALTHY
                )
            } else {
                HealthCheck(
                    key = "workers",
                    title = "Background jobs",
                    status = "${missing.size} missing",
                    detail = "Missing: ${missing.joinToString()}. Reopening Punla normally reschedules core periodic work.",
                    state = HealthState.ATTENTION,
                    action = HealthAction.REPAIR_BACKGROUND_JOBS,
                    actionLabel = "Repair jobs"
                )
            }
        } catch (error: Exception) {
            PunlaDiagnostics.warn(context, "SystemHealth", "Could not inspect WorkManager jobs", error)
            HealthCheck(
                key = "workers",
                title = "Background jobs",
                status = "Check unavailable",
                detail = "Punla could not read WorkManager state during this health check.",
                state = HealthState.ATTENTION
            )
        }
    }

    private fun databaseHealth(context: Context): HealthCheck {
        return try {
            val db = PunlaDatabase.get(context).openHelper.readableDatabase
            val result = db.query("PRAGMA quick_check(1)").use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            if (result.equals("ok", ignoreCase = true)) {
                HealthCheck(
                    key = "database",
                    title = "Database",
                    status = "Healthy",
                    detail = "SQLite quick_check completed successfully.",
                    state = HealthState.HEALTHY
                )
            } else {
                HealthCheck(
                    key = "database",
                    title = "Database",
                    status = "Needs attention",
                    detail = "SQLite returned: ${result ?: "no result"}.",
                    state = HealthState.BLOCKED
                )
            }
        } catch (error: Exception) {
            PunlaDiagnostics.error(context, "SystemHealth", "Database health check failed", error)
            HealthCheck(
                key = "database",
                title = "Database",
                status = "Check failed",
                detail = "Punla could not complete a local database integrity check. The error was added to Diagnostics.",
                state = HealthState.BLOCKED
            )
        }
    }

    private fun backupHealth(lastBackupAt: Long?): HealthCheck {
        if (lastBackupAt == null) {
            return HealthCheck(
                key = "backup",
                title = "Last backup",
                status = "Never",
                detail = "No Punla backup has been exported from this installation yet.",
                state = HealthState.ATTENTION
            )
        }
        val days = TimeUnit.MILLISECONDS.toDays((System.currentTimeMillis() - lastBackupAt).coerceAtLeast(0L))
        return when {
            days == 0L -> HealthCheck("backup", "Last backup", "Today", "A backup was exported today.", HealthState.HEALTHY)
            days <= 7L -> HealthCheck("backup", "Last backup", "$days days ago", "Backup freshness is within one week.", HealthState.HEALTHY)
            days <= 14L -> HealthCheck("backup", "Last backup", "$days days ago", "Consider exporting a fresh backup soon.", HealthState.INFO)
            else -> HealthCheck("backup", "Last backup", "$days days ago", "Your most recent exported backup is over two weeks old.", HealthState.ATTENTION)
        }
    }
}
