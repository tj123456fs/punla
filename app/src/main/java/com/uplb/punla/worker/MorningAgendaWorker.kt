package com.uplb.punla.worker

import android.Manifest
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
import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.notification.PunlaNotifications
import com.uplb.punla.notification.TrackedNotification
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** One quiet daily digest instead of several unrelated morning nudges. */
class MorningAgendaWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = PunlaRepository(context)
        if (!repo.notificationsEnabled || !repo.morningAgendaEnabled || !canPost()) return Result.success()
        if (PunlaNotifications.isRoutineQuietHours(repo.quietHoursEnabled)) return Result.success()

        val today = LocalDate.now()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_DATE, null) == today.toString()) return Result.success()

        val classes = repo.allClasses()
            .filter { it.day == dayCode(today.dayOfWeek) }
            .sortedBy { it.start }
        val dueToday = repo.getDeadlines().filter { !it.done && it.due == today.toString() }

        if (classes.isEmpty() && dueToday.isEmpty()) {
            prefs.edit().putString(KEY_LAST_DATE, today.toString()).apply()
            return Result.success()
        }

        PunlaNotifications.ensureChannels(context)
        val first = classes.firstOrNull()
        val classSummary = when (classes.size) {
            0 -> "No classes today"
            1 -> "1 class today"
            else -> "${classes.size} classes today"
        }
        val deadlineSummary = when (dueToday.size) {
            0 -> null
            1 -> "1 deadline due today"
            else -> "${dueToday.size} deadlines due today"
        }
        val title = listOfNotNull(classSummary, deadlineSummary).joinToString(" · ")
        val firstLine = first?.let { "First: ${it.code} at ${displayTime(it.start)}${roomSuffix(it)}" }
        val dueLine = dueToday.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "Due: ", limit = 2, truncated = " + more") { it.title }
        val body = listOfNotNull(firstLine, dueLine).joinToString("\n").ifBlank { "Open Punla to plan your day." }

        val builder = PunlaNotifications.routine(
            NotificationCompat.Builder(context, PunlaNotifications.CHANNEL_DAILY_BRIEF)
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(firstLine ?: dueLine ?: body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(8L * 60L * 60L * 1000L)

        try {
            TrackedNotification.post(
                context = context,
                manager = NotificationManagerCompat.from(context),
                notificationId = PunlaNotifications.ID_MORNING_AGENDA,
                builder = builder,
                workerName = "MorningAgendaWorker",
                notificationType = "daily_brief",
                route = "schedule",
                notificationKey = "morning-agenda:$today"
            )
            prefs.edit().putString(KEY_LAST_DATE, today.toString()).apply()
        } catch (_: SecurityException) {
            // Permission changed between the check and notify().
        }
        return Result.success()
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun displayTime(raw: String): String = runCatching {
        LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm"))
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    }.getOrDefault(raw)

    private fun roomSuffix(session: ClassSession): String =
        session.room?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""

    private fun dayCode(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }

    companion object {
        private const val PREFS = "punla_prefs"
        private const val KEY_LAST_DATE = "morning_agenda_last_date"
    }
}
