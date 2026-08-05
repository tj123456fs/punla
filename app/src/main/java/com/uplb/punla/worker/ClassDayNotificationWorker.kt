package com.uplb.punla.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uplb.punla.MainActivity
import com.uplb.punla.R
import com.uplb.punla.data.CampusDirectory
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.data.entity.AttendanceLog
import com.uplb.punla.data.entity.AttendanceRecord
import com.uplb.punla.data.entity.AttendanceStatus
import com.uplb.punla.data.entity.NotificationEvent
import com.uplb.punla.notification.ClassDayTimeline
import com.uplb.punla.notification.TrackedNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Maintains one silent notification that evolves through the class day:
 * pre-class -> ongoing -> break -> done. Android's own chronometer renders
 * the live countdown, so Punla does not need battery-heavy minute updates.
 */
class ClassDayNotificationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = PunlaRepository(context)
        val manager = NotificationManagerCompat.from(context)

        if (!repo.notificationsEnabled || !repo.classDayNotificationEnabled || !canPostNotifications()) {
            manager.cancel(ClassDayNotification.NOTIFICATION_ID)
            ClassDayNotificationScheduler.cancelTransitions(context)
            return Result.success()
        }

        val now = LocalDateTime.now()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_HIDDEN_DATE, null) == now.toLocalDate().toString()) {
            manager.cancel(ClassDayNotification.NOTIFICATION_ID)
            scheduleAfterHiddenDay(repo.allClasses(), now)
            return Result.success()
        }

        val classes = repo.allClasses()
        val snapshot = ClassDayTimeline.evaluate(classes, now)
        ClassDayNotificationScheduler.scheduleTransition(context, snapshot.nextTransitionAt, now)

        when (val state = snapshot.state) {
            ClassDayTimeline.State.None -> {
                manager.cancel(ClassDayNotification.NOTIFICATION_ID)
                prefs.edit().remove(KEY_LAST_STATE).apply()
            }
            else -> {
                createChannel()
                val stateKey = ClassDayNotification.stateKey(state, now.toLocalDate())
                val attendanceOccurrence = ClassDayNotification.attendanceOccurrence(state)
                val attendanceRecord = attendanceOccurrence?.let { occurrence ->
                    repo.attendanceForOccurrence(
                        AttendanceLog.occurrenceKey(
                            occurrence.session.id,
                            occurrence.start.toLocalDate(),
                            occurrence.session.start
                        )
                    )
                }
                val builder = ClassDayNotification.build(context, state, stateKey, attendanceRecord)
                try {
                    manager.notify(ClassDayNotification.NOTIFICATION_ID, builder.build())
                    if (prefs.getString(KEY_LAST_STATE, null) != stateKey) {
                        prefs.edit().putString(KEY_LAST_STATE, stateKey).apply()
                        runCatching {
                            repo.logNotificationEvent(
                                NotificationEvent(
                                    notificationKey = stateKey,
                                    workerName = "ClassDayNotificationWorker",
                                    notificationType = "class_day",
                                    localHour = now.hour,
                                    outcome = "FIRED"
                                )
                            )
                        }
                    }
                } catch (_: SecurityException) {
                    // Android notification permission was revoked between the
                    // permission check and notify(). The recovery worker will
                    // re-evaluate later if the user enables it again.
                }
            }
        }

        return Result.success()
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        val channel = NotificationChannel(
            ClassDayNotification.CHANNEL_ID,
            "Current class",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "A silent card for the current class, next class, and breaks between classes"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun scheduleAfterHiddenDay(classes: List<com.uplb.punla.data.entity.ClassSession>, now: LocalDateTime) {
        val tomorrow = now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(1)
        val next = ClassDayTimeline.nextOccurrenceAfter(classes, tomorrow.minusSeconds(1))
        val nextUseful = next?.start?.minusMinutes(ClassDayTimeline.DEFAULT_PRE_CLASS_MINUTES)
            ?.takeIf { it.isAfter(tomorrow) }
            ?: tomorrow
        ClassDayNotificationScheduler.scheduleTransition(context, nextUseful, now)
    }

    companion object {
        internal const val PREFS = "punla_prefs"
        internal const val KEY_HIDDEN_DATE = "class_day_hidden_date"
        internal const val KEY_LAST_STATE = "class_day_last_state"
    }
}

/** Scheduling wrapper used at app start and whenever the class table changes. */
object ClassDayNotificationScheduler {
    private const val UNIQUE_PERIODIC = "class_day_notification_recovery"
    private const val UNIQUE_REFRESH = "class_day_notification_refresh"
    private const val UNIQUE_TRANSITION = "class_day_notification_transition"

    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        val periodic = PeriodicWorkRequestBuilder<ClassDayNotificationWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
        refresh(appContext)
    }

    fun refresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<ClassDayNotificationWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_REFRESH,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleTransition(
        context: Context,
        transitionAt: LocalDateTime?,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (transitionAt == null) {
            manager.cancelUniqueWork(UNIQUE_TRANSITION)
            return
        }
        val delayMillis = Duration.between(now, transitionAt).toMillis().coerceAtLeast(1_000L)
        val request = OneTimeWorkRequestBuilder<ClassDayNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        manager.enqueueUniqueWork(UNIQUE_TRANSITION, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        manager.cancelUniqueWork(UNIQUE_PERIODIC)
        manager.cancelUniqueWork(UNIQUE_REFRESH)
        manager.cancelUniqueWork(UNIQUE_TRANSITION)
        NotificationManagerCompat.from(context).cancel(ClassDayNotification.NOTIFICATION_ID)
    }

    fun cancelTransitions(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        manager.cancelUniqueWork(UNIQUE_REFRESH)
        manager.cancelUniqueWork(UNIQUE_TRANSITION)
    }
}

/** Handles notification actions without opening the app. */
class ClassDayNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ClassDayNotification.ACTION_HIDE_TODAY -> hideToday(context)
            ClassDayNotification.ACTION_LOG_ATTENDANCE -> logAttendance(context, intent)
            else -> return
        }
    }

    private fun hideToday(context: Context) {
        context.getSharedPreferences(ClassDayNotificationWorker.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ClassDayNotificationWorker.KEY_HIDDEN_DATE, LocalDate.now().toString())
            .remove(ClassDayNotificationWorker.KEY_LAST_STATE)
            .apply()
        NotificationManagerCompat.from(context).cancel(ClassDayNotification.NOTIFICATION_ID)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ClassDayNotificationScheduler.refresh(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun logAttendance(context: Context, intent: Intent) {
        val occurrenceKey = intent.getStringExtra(ClassDayNotification.EXTRA_OCCURRENCE_KEY) ?: return
        val sessionId = intent.getStringExtra(ClassDayNotification.EXTRA_SESSION_ID) ?: return
        val classCode = intent.getStringExtra(ClassDayNotification.EXTRA_CLASS_CODE) ?: return
        val occurrenceDate = intent.getStringExtra(ClassDayNotification.EXTRA_OCCURRENCE_DATE) ?: return
        val scheduledStart = intent.getStringExtra(ClassDayNotification.EXTRA_SCHEDULED_START) ?: return
        val status = intent.getStringExtra(ClassDayNotification.EXTRA_ATTENDANCE_STATUS) ?: return
        if (!AttendanceStatus.isValid(status)) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                PunlaRepository(context.applicationContext).setAttendance(
                    AttendanceRecord(
                        occurrenceKey = occurrenceKey,
                        sessionId = sessionId,
                        classCode = classCode,
                        occurrenceDate = occurrenceDate,
                        scheduledStart = scheduledStart,
                        status = status,
                        source = "notification"
                    )
                )
                ClassDayNotificationScheduler.refresh(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private object ClassDayNotification {
    const val CHANNEL_ID = "punla_class_day_channel"
    const val NOTIFICATION_ID = 0x50434C53 // "PCLS"
    const val ACTION_HIDE_TODAY = "com.uplb.punla.HIDE_CLASS_DAY_NOTIFICATION"
    const val ACTION_LOG_ATTENDANCE = "com.uplb.punla.LOG_CLASS_ATTENDANCE"

    const val EXTRA_OCCURRENCE_KEY = "attendance_occurrence_key"
    const val EXTRA_SESSION_ID = "attendance_session_id"
    const val EXTRA_CLASS_CODE = "attendance_class_code"
    const val EXTRA_OCCURRENCE_DATE = "attendance_occurrence_date"
    const val EXTRA_SCHEDULED_START = "attendance_scheduled_start"
    const val EXTRA_ATTENDANCE_STATUS = "attendance_status"

    private val displayTime = DateTimeFormatter.ofPattern("h:mm a")

    fun stateKey(state: ClassDayTimeline.State, date: LocalDate): String = when (state) {
        ClassDayTimeline.State.None -> "class-day:$date:none"
        is ClassDayTimeline.State.PreClass -> "class-day:$date:pre:${state.next.session.id}:${state.next.start}"
        is ClassDayTimeline.State.Ongoing -> "class-day:$date:ongoing:${state.current.session.id}:${state.current.start}"
        is ClassDayTimeline.State.Break -> "class-day:$date:break:${state.next.session.id}:${state.next.start}"
        is ClassDayTimeline.State.Done -> "class-day:$date:done:${state.lastClass.session.id}:${state.lastClass.end}"
    }

    /** The class occurrence whose attendance can be recorded from this card. */
    fun attendanceOccurrence(state: ClassDayTimeline.State): ClassDayTimeline.Occurrence? = when (state) {
        is ClassDayTimeline.State.Ongoing -> state.current
        is ClassDayTimeline.State.Break -> state.previous
        is ClassDayTimeline.State.Done -> state.lastClass
        else -> null
    }

    fun build(
        context: Context,
        state: ClassDayTimeline.State,
        stateKey: String,
        attendanceRecord: AttendanceRecord?
    ): NotificationCompat.Builder {
        val content = contentFor(state, attendanceRecord)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.bigText))
            .setSubText(
                when (attendanceRecord?.status) {
                    AttendanceStatus.ATTENDED -> "Punla class day · Attended"
                    AttendanceStatus.ABSENT -> "Punla class day · Absent"
                    else -> "Punla class day"
                }
            )
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setOngoing(content.ongoing)
            .setAutoCancel(!content.ongoing)
            .setContentIntent(activityIntent(context, content.openRoute, null, stateKey, "open"))

        content.countdownTo?.let { target ->
            builder
                .setWhen(target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }

        if (state is ClassDayTimeline.State.Done) {
            builder.setTimeoutAfter(
                Duration.between(LocalDateTime.now(), state.visibleUntil).toMillis().coerceAtLeast(1_000L)
            )
        }

        when (state) {
            is ClassDayTimeline.State.PreClass -> {
                addNavigateAction(builder, context, state.next.session.room, stateKey)
                builder.addAction(R.mipmap.ic_launcher, "Schedule", activityIntent(context, "schedule", null, stateKey, "schedule"))
            }
            is ClassDayTimeline.State.Ongoing -> {
                addAttendanceActions(builder, context, state.current, attendanceRecord, stateKey)
                addNavigateAction(builder, context, state.current.session.room, stateKey)
                builder.addAction(R.mipmap.ic_launcher, "Schedule", activityIntent(context, "schedule", null, stateKey, "schedule"))
            }
            is ClassDayTimeline.State.Break -> {
                addAttendanceActions(builder, context, state.previous, attendanceRecord, stateKey)
                builder.addAction(R.mipmap.ic_launcher, "Start focus", activityIntent(context, "pomodoro", null, stateKey, "focus"))
                addNavigateAction(builder, context, state.next.session.room, stateKey)
            }
            is ClassDayTimeline.State.Done -> {
                addAttendanceActions(builder, context, state.lastClass, attendanceRecord, stateKey)
                builder.addAction(R.mipmap.ic_launcher, "Schedule", activityIntent(context, "schedule", null, stateKey, "schedule"))
            }
            ClassDayTimeline.State.None -> Unit
        }
        builder.addAction(R.mipmap.ic_launcher, "Hide today", hideTodayIntent(context, stateKey))
        return builder
    }

    private data class Content(
        val title: String,
        val text: String,
        val bigText: String,
        val countdownTo: LocalDateTime?,
        val ongoing: Boolean,
        val openRoute: String
    )

    private fun contentFor(
        state: ClassDayTimeline.State,
        attendanceRecord: AttendanceRecord?
    ): Content = when (state) {
        ClassDayTimeline.State.None -> Content("", "", "", null, false, "schedule")

        is ClassDayTimeline.State.PreClass -> {
            val c = state.next.session
            val place = placeLabel(c.room)
            Content(
                title = "Leave soon · ${c.code}",
                text = "${state.next.start.format(displayTime)} · $place",
                bigText = buildString {
                    append(c.code)
                    c.title?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    append("\n").append(place)
                    append("\nStarts at ").append(state.next.start.format(displayTime))
                },
                countdownTo = state.next.start,
                ongoing = true,
                openRoute = "schedule"
            )
        }

        is ClassDayTimeline.State.Ongoing -> {
            val c = state.current.session
            val place = placeLabel(c.room)
            Content(
                title = "${c.code} · In class",
                text = "$place · Ends ${state.current.end.format(displayTime)}",
                bigText = buildString {
                    append(c.code)
                    c.section?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    c.title?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
                    append("\n").append(place)
                    c.instructor?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
                    append("\nEnds at ").append(state.current.end.format(displayTime))
                    append("\n").append(attendanceLine(attendanceRecord, "Log attendance below."))
                },
                countdownTo = state.current.end,
                ongoing = true,
                openRoute = "schedule"
            )
        }

        is ClassDayTimeline.State.Break -> {
            val c = state.next.session
            val focusCopy = when {
                state.focusSessions >= 2 -> "Enough time for ${state.focusSessions} focus sessions."
                state.focusSessions == 1 -> "Enough time for one focus session."
                else -> "Take a short reset before class."
            }
            Content(
                title = "Free time · Next ${c.code}",
                text = "${placeLabel(c.room)} · Starts ${state.next.start.format(displayTime)}",
                bigText = buildString {
                    append(state.freeMinutes).append(" minutes free before ").append(c.code).append(".\n")
                    append(focusCopy).append("\n").append(placeLabel(c.room))
                    append("\nPrevious class: ").append(state.previous.session.code).append(" · ")
                    append(attendanceLine(attendanceRecord, "not logged"))
                },
                countdownTo = state.next.start,
                ongoing = true,
                openRoute = "schedule"
            )
        }

        is ClassDayTimeline.State.Done -> Content(
            title = "You're done for today",
            text = "${state.classesCompleted} ${if (state.classesCompleted == 1) "class" else "classes"} completed",
            bigText = buildString {
                append("No more classes today. Your last class ended at ")
                    .append(state.lastClass.end.format(displayTime)).append(".\n")
                append(state.lastClass.session.code).append(" attendance: ")
                append(attendanceLine(attendanceRecord, "not logged"))
            },
            countdownTo = null,
            ongoing = false,
            openRoute = "schedule"
        )
    }

    private fun attendanceLine(record: AttendanceRecord?, missing: String): String = when (record?.status) {
        AttendanceStatus.ATTENDED -> "Attended ✓"
        AttendanceStatus.ABSENT -> "Absent"
        else -> missing
    }

    private fun placeLabel(room: String?): String {
        if (room.isNullOrBlank()) return "Room TBA"
        val building = CampusDirectory.findBuildingForRoom(room)
        return if (building == null) room else "$room · ${building.name}"
    }

    private fun addAttendanceActions(
        builder: NotificationCompat.Builder,
        context: Context,
        occurrence: ClassDayTimeline.Occurrence,
        record: AttendanceRecord?,
        stateKey: String
    ) {
        val attendedLabel = if (record?.status == AttendanceStatus.ATTENDED) "Attended ✓" else "Attended"
        val absentLabel = if (record?.status == AttendanceStatus.ABSENT) "Absent ✓" else "Absent"
        builder.addAction(
            R.mipmap.ic_launcher,
            attendedLabel,
            attendanceIntent(context, occurrence, AttendanceStatus.ATTENDED, stateKey)
        )
        builder.addAction(
            R.mipmap.ic_launcher,
            absentLabel,
            attendanceIntent(context, occurrence, AttendanceStatus.ABSENT, stateKey)
        )
    }

    private fun addNavigateAction(
        builder: NotificationCompat.Builder,
        context: Context,
        room: String?,
        stateKey: String
    ) {
        if (room.isNullOrBlank() || CampusDirectory.findBuildingForRoom(room) == null) return
        builder.addAction(R.mipmap.ic_launcher, "Navigate", activityIntent(context, "campus", room, stateKey, "navigate"))
    }

    private fun activityIntent(
        context: Context,
        route: String,
        mapQuery: String?,
        stateKey: String,
        suffix: String
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.uplb.punla.CLASS_DAY_OPEN:$stateKey:$suffix"
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_START_ROUTE, route)
            mapQuery?.let { putExtra(MainActivity.EXTRA_MAP_QUERY, it) }
            putExtra(TrackedNotification.EXTRA_KEY, stateKey)
            putExtra(TrackedNotification.EXTRA_WORKER, "ClassDayNotificationWorker")
            putExtra(TrackedNotification.EXTRA_TYPE, "class_day")
            putExtra(TrackedNotification.EXTRA_OUTCOME, "OPENED")
            putExtra(TrackedNotification.EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context,
            "$stateKey:$suffix".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun attendanceIntent(
        context: Context,
        occurrence: ClassDayTimeline.Occurrence,
        status: String,
        stateKey: String
    ): PendingIntent {
        val session = occurrence.session
        val date = occurrence.start.toLocalDate()
        val occurrenceKey = AttendanceLog.occurrenceKey(session.id, date, session.start)
        val intent = Intent(context, ClassDayNotificationActionReceiver::class.java).apply {
            action = ACTION_LOG_ATTENDANCE
            putExtra(EXTRA_OCCURRENCE_KEY, occurrenceKey)
            putExtra(EXTRA_SESSION_ID, session.id)
            putExtra(EXTRA_CLASS_CODE, session.code)
            putExtra(EXTRA_OCCURRENCE_DATE, date.toString())
            putExtra(EXTRA_SCHEDULED_START, session.start)
            putExtra(EXTRA_ATTENDANCE_STATUS, status)
        }
        return PendingIntent.getBroadcast(
            context,
            "$stateKey:attendance:$status".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun hideTodayIntent(context: Context, stateKey: String): PendingIntent {
        val intent = Intent(context, ClassDayNotificationActionReceiver::class.java).apply {
            action = ACTION_HIDE_TODAY
        }
        return PendingIntent.getBroadcast(
            context,
            "$stateKey:hide".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
