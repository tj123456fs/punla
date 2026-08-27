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
import com.uplb.punla.data.PunlaDatabase
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.notification.PunlaNotifications
import com.uplb.punla.notification.TrackedNotification
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Low-priority contextual study nudges: a tiny pre-class refresher, a
 * post-class review cue, and one evening Smart Study summary. Every nudge is
 * deduped and honors routine quiet hours.
 */
class StudyNudgeWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = PunlaRepository(context)
        if (!repo.notificationsEnabled || !repo.studyRemindersEnabled) return Result.success()
        if (PunlaNotifications.isRoutineQuietHours(repo.quietHoursEnabled)) return Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()

        val db = PunlaDatabase.get(context)
        val decks = db.flashcardDao().getDecks()
        val cards = db.flashcardDao().getAllCards()
        val today = LocalDate.now()
        val now = LocalTime.now()
        val dayKey = dayKey(today.dayOfWeek)
        val prefs = context.getSharedPreferences("punla_study_nudges", Context.MODE_PRIVATE)
        val sent = (prefs.getStringSet("sent_keys", emptySet()) ?: emptySet()).toMutableSet()
        fun remember(key: String) {
            sent += key
            val trimmed = if (sent.size > 300) sent.toList().takeLast(300).toSet() else sent
            prefs.edit().putStringSet("sent_keys", trimmed).apply()
        }

        // Before/after class: only if that course already has study material.
        for (session in repo.allClasses().filter { it.day == dayKey }) {
            val courseDeckIds = decks.filter { it.courseCode.equals(session.code, true) }.map { it.id }.toSet()
            if (courseDeckIds.isEmpty()) continue
            val courseCards = cards.filter { it.deckId in courseDeckIds }
            val due = courseCards.count { it.isDue() }
            val start = runCatching { LocalTime.parse(session.start) }.getOrNull() ?: continue
            val end = runCatching { LocalTime.parse(session.end) }.getOrNull() ?: continue
            val until = ChronoUnit.MINUTES.between(now, start)
            val sinceEnd = ChronoUnit.MINUTES.between(end, now)
            // WorkManager periodic jobs are inexact. Keep these windows wide enough
            // that a delayed 30-minute run does not silently miss the reminder.
            if (until in 5..40 && due > 0) {
                val key = "pre:${today}:${session.id}"
                if (key !in sent && show("Quick ${session.code} refresher", "$due due card${if (due == 1) "" else "s"} · review a few before class", key)) {
                    remember(key)
                    return Result.success()
                }
            }
            if (sinceEnd in 0..45 && courseCards.isNotEmpty()) {
                val key = "post:${today}:${session.id}"
                if (key !in sent && show("Review ${session.code} while it's fresh", "A 5-minute recall pass now can strengthen today's class.", key)) {
                    remember(key)
                    return Result.success()
                }
            }
        }

        // One evening summary; the Study Hub decides exact queue order.
        if (now.hour in 18..20) {
            val key = "evening:$today"
            if (key !in sent) {
                val mistakes = db.studyMaterialDao().getMistakes().count { !it.resolved && it.retryAt <= System.currentTimeMillis() }
                val plans = db.studyMaterialDao().getPlanItems().count { !it.completed && it.plannedDate <= today.toString() }
                val dueCards = cards.count { it.isDue() }
                val exams = repo.getDeadlines().mapNotNull { d ->
                    if (d.done || !Regex("(?i)exam|midterm|final|quiz").containsMatchIn(d.title)) return@mapNotNull null
                    val date = runCatching { LocalDate.parse(d.due) }.getOrNull() ?: return@mapNotNull null
                    val days = ChronoUnit.DAYS.between(today, date)
                    if (days in 0..7) d.title to days else null
                }.sortedBy { it.second }
                if (mistakes + plans + dueCards > 0 || exams.isNotEmpty()) {
                    val examText = exams.firstOrNull()?.let { " · ${it.first} in ${it.second}d" }.orEmpty()
                    if (show("Your study queue is ready", "$dueCards cards · $mistakes mistakes · $plans plan items$examText", key)) {
                        remember(key)
                    }
                }
            }
        }
        return Result.success()
    }

    private suspend fun show(title: String, body: String, key: String): Boolean {
        PunlaNotifications.ensureChannels(context)
        val builder = PunlaNotifications.routine(NotificationCompat.Builder(context, PunlaNotifications.CHANNEL_ROUTINE))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
        return runCatching {
            TrackedNotification.post(context, NotificationManagerCompat.from(context), PunlaNotifications.ID_STUDY_NUDGE + Math.floorMod(key.hashCode(), 40), builder, "StudyNudgeWorker", "study", "study")
            true
        }.getOrDefault(false)
    }

    private fun dayKey(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Mon"; DayOfWeek.TUESDAY -> "Tue"; DayOfWeek.WEDNESDAY -> "Wed"; DayOfWeek.THURSDAY -> "Thu";
        DayOfWeek.FRIDAY -> "Fri"; DayOfWeek.SATURDAY -> "Sat"; DayOfWeek.SUNDAY -> "Sun"
    }
}
