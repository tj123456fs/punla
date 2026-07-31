package com.uplb.punla.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.uplb.punla.data.PunlaRepository
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/** Aligns non-urgent daily checks to an optional locally learned hour. */
object ReminderScheduler {
    fun scheduleDaily(context: Context, updateExisting: Boolean = false) {
        val repo = PunlaRepository(context)
        val delayMinutes = repo.preferredReminderHour?.let(::minutesUntilNextHour)
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

        manager.enqueueUniquePeriodicWork("DeadlineWorker", policy, deadline)
        manager.enqueueUniquePeriodicWork("budget_nudge_work", policy, budget)
        manager.enqueueUniquePeriodicWork("checklist_reminder_work", policy, checklist)
    }

    private fun minutesUntilNextHour(hour: Int): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour.coerceIn(0, 23), 0)
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        return ChronoUnit.MINUTES.between(now, next).coerceAtLeast(1)
    }
}
