package com.uplb.punla.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.notification.PunlaNotifications
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/** Aligns non-urgent daily checks to an optional locally learned hour. */
object ReminderScheduler {
    private const val MORNING_AGENDA_HOUR = 7
    private const val MORNING_AGENDA_MINUTE = 15

    fun scheduleDaily(context: Context, updateExisting: Boolean = false) {
        val repo = PunlaRepository(context)
        val requestedHour = repo.preferredReminderHour
        val deliveryHour = requestedHour?.let { hour ->
            // Learned routine reminders should never be scheduled inside quiet hours.
            if (repo.quietHoursEnabled && (hour >= PunlaNotifications.QUIET_START_HOUR || hour < PunlaNotifications.QUIET_END_HOUR)) {
                19
            } else hour
        }
        val delayMinutes = deliveryHour?.let(::minutesUntilNextHour)
        val policy = if (updateExisting) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
        val manager = WorkManager.getInstance(context)

        val deadline = PeriodicWorkRequestBuilder<DeadlineWorker>(24, TimeUnit.HOURS).apply {
            delayMinutes?.let { setInitialDelay(it, TimeUnit.MINUTES) }
        }.build()
        val budget = PeriodicWorkRequestBuilder<BudgetWorker>(24, TimeUnit.HOURS).apply {
            delayMinutes?.let { setInitialDelay(it, TimeUnit.MINUTES) }
        }.build()
        val checklist = PeriodicWorkRequestBuilder<ChecklistReminderWorker>(24, TimeUnit.HOURS).apply {
            delayMinutes?.let { setInitialDelay(it, TimeUnit.MINUTES) }
        }.build()
        val agenda = PeriodicWorkRequestBuilder<MorningAgendaWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(minutesUntilNextTime(MORNING_AGENDA_HOUR, MORNING_AGENDA_MINUTE), TimeUnit.MINUTES)
            .build()

        manager.enqueueUniquePeriodicWork("DeadlineWorker", policy, deadline)
        manager.enqueueUniquePeriodicWork("budget_nudge_work", policy, budget)
        manager.enqueueUniquePeriodicWork("checklist_reminder_work", policy, checklist)
        manager.enqueueUniquePeriodicWork("morning_agenda_work", policy, agenda)
    }

    private fun minutesUntilNextHour(hour: Int): Long = minutesUntilNextTime(hour, 0)

    private fun minutesUntilNextTime(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        return ChronoUnit.MINUTES.between(now, next).coerceAtLeast(1)
    }
}
