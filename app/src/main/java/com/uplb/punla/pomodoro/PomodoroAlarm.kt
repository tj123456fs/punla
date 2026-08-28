package com.uplb.punla.pomodoro

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.uplb.punla.MainActivity
import com.uplb.punla.R
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.data.entity.StudySession
import com.uplb.punla.ui.pomodoro.PomodoroPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * AlarmManager-backed completion signal for the Pomodoro clock.
 *
 * The in-app countdown remains a wall-clock UI, while this alarm wakes Punla
 * when the deadline arrives even if Android has removed the activity/process.
 * Exact delivery is used when the user grants the Android "Alarms & reminders"
 * access; otherwise Android's inexact while-idle fallback still guarantees a
 * background alert, though it may be a little late under heavy battery saving.
 */
object PomodoroAlarmScheduler {
    const val ACTION_PHASE_DEADLINE = "com.uplb.punla.POMODORO_PHASE_DEADLINE"
    const val EXTRA_EXPECTED_DEADLINE = "pomodoro_expected_deadline"
    private const val REQUEST_CODE = 41017

    fun exactAlarmAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun schedule(context: Context, deadline: Long) {
        if (deadline <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val operation = pendingIntent(context, deadline)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, operation)
            }
        } catch (_: SecurityException) {
            // Permission may have changed between canScheduleExactAlarms() and
            // the call. Fall back rather than losing the user's timer alert.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, operation)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, 0L))
    }

    private fun pendingIntent(context: Context, deadline: Long): PendingIntent {
        val intent = Intent(context, PomodoroAlarmReceiver::class.java).apply {
            action = ACTION_PHASE_DEADLINE
            putExtra(EXTRA_EXPECTED_DEADLINE, deadline)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Handles a timer deadline and survives process recreation. */
class PomodoroAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PomodoroAlarmScheduler.ACTION_PHASE_DEADLINE) return
        val expectedDeadline = intent.getLongExtra(PomodoroAlarmScheduler.EXTRA_EXPECTED_DEADLINE, 0L)
        if (expectedDeadline <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                PomodoroCompletionCoordinator.complete(context.applicationContext, expectedDeadline)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Broadcast delivery must not crash the process if local storage or
                // notification services fail. The persisted runtime lets Punla retry.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Restores the alarm after a reboot or an app update, because AlarmManager
 * removes scheduled alarms when the device powers off. */
class PomodoroBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val repo = PunlaRepository(context.applicationContext)
        if (!repo.pomodoroRuntimeRunning) return
        val deadline = repo.pomodoroRuntimeDeadline
        if (deadline <= 0L) return

        if (deadline > System.currentTimeMillis()) {
            PomodoroAlarmScheduler.schedule(context, deadline)
            PomodoroRunningNotification.showFromRepository(context)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                PomodoroCompletionCoordinator.complete(context.applicationContext, deadline)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Leave the persisted runtime intact so the app can recover later.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * One idempotent completion path shared by the visible countdown and the
 * AlarmManager receiver. The mutex prevents the two from logging/posting the
 * same completed interval when they wake within the same few milliseconds.
 */
object PomodoroCompletionCoordinator {
    private val completionMutex = Mutex()

    suspend fun complete(context: Context, expectedDeadline: Long): Boolean {
        completionMutex.lock()
        try {
            val repo = PunlaRepository(context.applicationContext)
            if (!repo.pomodoroRuntimeRunning) return false
            if (repo.pomodoroRuntimeDeadline != expectedDeadline) return false
            if (repo.pomodoroLastHandledDeadline == expectedDeadline) return false

            val storedPhase = repo.pomodoroRuntimePhase ?: return false
            val finishedPhase = runCatching { PomodoroPhase.valueOf(storedPhase) }.getOrNull() ?: return false
            if (finishedPhase == PomodoroPhase.IDLE) return false

            // The mutex serializes in-process completion. Persist the handled marker
            // only after suspendable history writes have succeeded/failed so a cancelled
            // coroutine can safely retry the same deadline instead of stranding the timer.

            val finishedTotalSeconds = repo.pomodoroRuntimeTotalSeconds.coerceAtLeast(0)
            val finishedStartedAt = repo.pomodoroRuntimeStartedAt.takeIf { it > 0L }
                ?: (expectedDeadline - finishedTotalSeconds * 1000L).coerceAtLeast(0L)
            val finishedCourse = repo.pomodoroRuntimeCourseCode
            val previousCycleCount = repo.pomodoroRuntimeCycleCount.coerceAtLeast(0)
            val nextCycleCount = if (finishedPhase == PomodoroPhase.WORK) previousCycleCount + 1 else previousCycleCount

            if (finishedPhase == PomodoroPhase.WORK) {
                val suggestionId = repo.pendingStudySuggestionId
                val session = StudySession(
                    id = "pomodoro:$expectedDeadline",
                    courseCode = finishedCourse,
                    startedAt = finishedStartedAt,
                    endedAt = expectedDeadline,
                    plannedMinutes = (finishedTotalSeconds / 60).coerceAtLeast(1),
                    actualSeconds = finishedTotalSeconds,
                    completed = true,
                    cyclesInSession = nextCycleCount,
                    endReason = "COMPLETED",
                    suggestionId = suggestionId
                )
                try {
                    repo.logStudySession(session)
                    if (suggestionId != null) {
                        repo.latestStudySuggestionEvent(suggestionId)?.let { source ->
                            repo.logStudySuggestionEvent(
                                source.copy(
                                    id = "pomodoro-suggestion:$expectedDeadline:$suggestionId",
                                    occurredAt = expectedDeadline,
                                    outcome = "COMPLETED",
                                    sessionId = session.id
                                )
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The timer must still advance even if optional history or
                    // local learning storage is temporarily unavailable.
                }
                if (suggestionId != null) {
                    repo.pendingStudySuggestionId = null
                    repo.pendingStudySuggestionFeatures = null
                }
            }

            PomodoroRunningNotification.cancel(context)
            try {
                PomodoroNotificationHelper.postCompletion(context, repo, finishedPhase)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A missing/deleted custom ringtone must never strand the timer.
            }

            // From here onward there are no suspend points. Marking handled now is
            // safe: the runtime transition below will also make stale alarm deliveries no-ops.
            repo.pomodoroLastHandledDeadline = expectedDeadline

            val nextPhase = when (finishedPhase) {
                PomodoroPhase.WORK -> {
                    val cycleTarget = repo.pomodoroCyclesBeforeLongBreak.coerceAtLeast(1)
                    if (nextCycleCount % cycleTarget == 0) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
                }
                PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> PomodoroPhase.WORK
                PomodoroPhase.IDLE -> return false
            }

            if (repo.pomodoroAutoStartNext) {
                val seconds = when (nextPhase) {
                    PomodoroPhase.WORK -> repo.pomodoroWorkMinutes
                    PomodoroPhase.SHORT_BREAK -> repo.pomodoroShortBreakMinutes
                    PomodoroPhase.LONG_BREAK -> repo.pomodoroLongBreakMinutes
                    PomodoroPhase.IDLE -> 0
                }.coerceAtLeast(1) * 60
                val startedAt = System.currentTimeMillis()
                val deadline = startedAt + seconds * 1000L
                repo.savePomodoroRuntime(
                    phase = nextPhase.name,
                    deadline = deadline,
                    startedAt = startedAt,
                    remainingSeconds = seconds,
                    totalSeconds = seconds,
                    running = true,
                    cycleCount = nextCycleCount,
                    courseCode = finishedCourse
                )
                PomodoroAlarmScheduler.schedule(context, deadline)
                PomodoroRunningNotification.showFromRepository(context)
            } else {
                repo.savePomodoroRuntime(
                    phase = nextPhase.name,
                    deadline = 0L,
                    startedAt = 0L,
                    remainingSeconds = 0,
                    totalSeconds = 0,
                    running = false,
                    cycleCount = nextCycleCount,
                    courseCode = finishedCourse
                )
                PomodoroAlarmScheduler.cancel(context)
            }
            return true
        } finally {
            completionMutex.unlock()
        }
    }
}

/**
 * Silent ongoing countdown shown while a Pomodoro phase is running.
 * Android's notification chronometer performs the visual countdown, so Punla
 * does not need to wake up and repost a notification every second.
 */
object PomodoroRunningNotification {
    private const val CHANNEL_ID = "punla_pomodoro_running"
    private const val NOTIFICATION_ID = 9000

    fun showFromRepository(context: Context) {
        val appContext = context.applicationContext
        val repo = PunlaRepository(appContext)
        if (!repo.pomodoroTimerNotification || !repo.notificationsEnabled || !repo.pomodoroRuntimeRunning) {
            cancel(appContext)
            return
        }
        val deadline = repo.pomodoroRuntimeDeadline
        if (deadline <= System.currentTimeMillis()) {
            cancel(appContext)
            return
        }
        val phase = runCatching {
            PomodoroPhase.valueOf(repo.pomodoroRuntimePhase ?: "")
        }.getOrNull() ?: run {
            cancel(appContext)
            return
        }
        post(appContext, phase, deadline, repo.pomodoroRuntimeCourseCode)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    private fun post(context: Context, phase: PomodoroPhase, deadline: Long, courseCode: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        ensureChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_START_ROUTE, "pomodoro")
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (phase) {
            PomodoroPhase.WORK -> "Focus timer"
            PomodoroPhase.SHORT_BREAK -> "Short break"
            PomodoroPhase.LONG_BREAK -> "Long break"
            PomodoroPhase.IDLE -> "Pomodoro"
        }
        val body = courseCode?.takeIf { it.isNotBlank() }?.let { "$it • Tap to open Punla" }
            ?: "Tap to open Punla"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(deadline)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Notification permission changed between the check and the post.
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pomodoro live timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silent ongoing countdown for an active Punla Pomodoro"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}

private object PomodoroNotificationHelper {
    private const val NOTIFICATION_ID = 9001

    fun postCompletion(context: Context, repo: PunlaRepository, finishedPhase: PomodoroPhase) {
        if (!repo.notificationsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val focusFinished = finishedPhase == PomodoroPhase.WORK
        val title = if (focusFinished) "Focus block done" else "Break's over"
        val body = if (focusFinished) "Time for a break." else "Ready for another round?"
        val configuredUri = if (focusFinished) repo.pomodoroWorkSoundUri else repo.pomodoroBreakSoundUri
        val soundUri = resolveSoundUri(
            context = context,
            configuredUri = configuredUri,
            soundEnabled = repo.pomodoroAlarmSoundEnabled
        )
        val channelId = channelId(
            focusFinished = focusFinished,
            soundUri = soundUri,
            soundEnabled = repo.pomodoroAlarmSoundEnabled,
            vibrate = repo.pomodoroAlarmVibrationEnabled
        )
        ensureChannel(
            context = context,
            channelId = channelId,
            focusFinished = focusFinished,
            soundUri = soundUri,
            soundEnabled = repo.pomodoroAlarmSoundEnabled,
            vibrate = repo.pomodoroAlarmVibrationEnabled
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_START_ROUTE, "pomodoro")
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Notification permission was revoked between the checks and post.
        }
    }

    private fun resolveSoundUri(
        context: Context,
        configuredUri: String?,
        soundEnabled: Boolean
    ): Uri? {
        if (!soundEnabled) return null
        val custom = configuredUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (custom != null) {
            val readable = runCatching { RingtoneManager.getRingtone(context, custom) != null }.getOrDefault(false)
            if (readable) return custom
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun channelId(
        focusFinished: Boolean,
        soundUri: Uri?,
        soundEnabled: Boolean,
        vibrate: Boolean
    ): String {
        val kind = if (focusFinished) "focus" else "break"
        val signature = "${soundUri ?: "silent"}|$soundEnabled|$vibrate".hashCode().toUInt().toString(16)
        return "punla_pomodoro_${kind}_$signature"
    }

    private fun ensureChannel(
        context: Context,
        channelId: String,
        focusFinished: Boolean,
        soundUri: Uri?,
        soundEnabled: Boolean,
        vibrate: Boolean
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) != null) return

        // A channel's sound is immutable after creation. Sound changes use a
        // new deterministic channel id, and older Punla channels for the same
        // phase are removed so Android Settings does not fill with duplicates.
        val prefix = if (focusFinished) "punla_pomodoro_focus_" else "punla_pomodoro_break_"
        manager.notificationChannels
            .filter { it.id.startsWith(prefix) && it.id != channelId }
            .forEach { manager.deleteNotificationChannel(it.id) }

        val label = if (focusFinished) "Focus complete" else "Break complete"
        val channel = NotificationChannel(channelId, "Pomodoro: $label", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts when a Punla Pomodoro interval finishes"
            enableVibration(vibrate)
            if (vibrate) vibrationPattern = longArrayOf(0, 250, 150, 350)
            if (soundEnabled && soundUri != null) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(soundUri, attributes)
            } else {
                setSound(null, null)
            }
        }
        manager.createNotificationChannel(channel)
    }
}
