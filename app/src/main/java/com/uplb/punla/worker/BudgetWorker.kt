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
import com.uplb.punla.data.BudgetPeriod
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.notification.TrackedNotification
import com.uplb.punla.notification.PunlaNotifications
import java.time.LocalDate

/**
 * Feature plan — Budget Low-Balance Notification, extended by the Weekly
 * Budgeting feature (WEEKLY_BUDGET_INSTRUCTIONS.md #4.4) to also nudge on
 * the weekly figure when [BudgetPeriod] includes it. Daily check that
 * nudges once when the relevant period's spend crosses 80%, and again if
 * it crosses 100% — reuses the same notification channel/shape for both,
 * per the plan doc's "reuse existing alarm/notification infrastructure".
 */
class BudgetWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val WARN_THRESHOLD = 80
        private const val OVER_THRESHOLD = 100
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

        val period = repo.budgetPeriod
        if (period == BudgetPeriod.MONTHLY || period == BudgetPeriod.BOTH) {
            checkMonthly(repo)
        }
        if (period == BudgetPeriod.WEEKLY || period == BudgetPeriod.BOTH) {
            checkWeekly(repo)
        }
        return Result.success()
    }

    private suspend fun checkMonthly(repo: PunlaRepository) {
        if (repo.monthlyBudget <= 0) return

        val now = LocalDate.now()
        val currentMonth = "%04d-%02d".format(now.year, now.monthValue)

        // A new calendar month since the last nudge means we start fresh —
        // don't carry last month's crossed threshold forward.
        val lastThreshold = if (repo.lastBudgetNudgeMonth == currentMonth) repo.lastBudgetNudgeThreshold else 0

        val budget = repo.monthlyBudget
        val spent = repo.budgetSpentThisMonth()
        val ratioPercent = ((spent / budget) * 100).toInt()

        val thresholdToNudge = when {
            ratioPercent >= OVER_THRESHOLD && lastThreshold < OVER_THRESHOLD -> OVER_THRESHOLD
            ratioPercent >= WARN_THRESHOLD && lastThreshold < WARN_THRESHOLD -> WARN_THRESHOLD
            else -> null
        } ?: return

        val remaining = budget - spent
        val safeToday = repo.safeToSpendToday(now)
        val text = if (thresholdToNudge == OVER_THRESHOLD) {
            "You're over budget this month by \u20b1${"%,.0f".format(-remaining)}. Pause discretionary spending and review category limits."
        } else {
            val pace = if (safeToday > 0.0) " Safe pace: about \u20b1${"%,.0f".format(safeToday)}/day." else " Your safe discretionary pace is already exhausted."
            "You've used 80% of this month's budget (\u20b1${"%,.0f".format(remaining)} left).$pace"
        }
        showNotification("Budget check-in", text, notificationId = PunlaNotifications.ID_BUDGET_MONTH)

        repo.lastBudgetNudgeThreshold = thresholdToNudge
        repo.lastBudgetNudgeMonth = currentMonth
        repo.lastBudgetNudgeAt = System.currentTimeMillis()
    }

    private suspend fun checkWeekly(repo: PunlaRepository) {
        val weekStart = repo.currentWeekStart()
        val weekKey = weekStart.toString()

        // A new week since the last nudge means we start fresh — don't
        // carry last week's crossed threshold forward.
        val lastThreshold = if (repo.lastWeeklyBudgetNudgeWeekStart == weekKey) repo.lastWeeklyBudgetNudgeThreshold else 0

        val weeklyBudget = repo.weeklyBudgetAmount()
        if (weeklyBudget <= 0) return
        val weeklySpent = repo.weeklyBudgetSpent()
        val ratioPercent = ((weeklySpent / weeklyBudget) * 100).toInt()

        val thresholdToNudge = when {
            ratioPercent >= OVER_THRESHOLD && lastThreshold < OVER_THRESHOLD -> OVER_THRESHOLD
            ratioPercent >= WARN_THRESHOLD && lastThreshold < WARN_THRESHOLD -> WARN_THRESHOLD
            else -> null
        } ?: return

        val remaining = weeklyBudget - weeklySpent
        val safeToday = repo.safeToSpendToday()
        val text = if (thresholdToNudge == OVER_THRESHOLD) {
            "You're over budget this week by \u20b1${"%,.0f".format(-remaining)}. Pause discretionary spending and rebalance the rest of the week."
        } else {
            val pace = if (safeToday > 0.0) " Safe pace: about \u20b1${"%,.0f".format(safeToday)}/day." else " Your safe discretionary pace is already exhausted."
            "You've used 80% of this week's budget (\u20b1${"%,.0f".format(remaining)} left).$pace"
        }
        showNotification("Weekly budget check-in", text, notificationId = PunlaNotifications.ID_BUDGET_WEEK)

        repo.lastWeeklyBudgetNudgeThreshold = thresholdToNudge
        repo.lastWeeklyBudgetNudgeWeekStart = weekKey
        repo.lastBudgetNudgeAt = System.currentTimeMillis()
    }

    private suspend fun showNotification(title: String, content: String, notificationId: Int) {
        PunlaNotifications.ensureChannels(context)
        val channelId = PunlaNotifications.CHANNEL_BUDGET
        val notificationManager = NotificationManagerCompat.from(context)

        val builder = PunlaNotifications.routine(NotificationCompat.Builder(context, channelId)).setGroup(PunlaNotifications.GROUP_FINANCE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)

        try {
            TrackedNotification.post(
                context = context,
                manager = notificationManager,
                notificationId = notificationId,
                builder = builder,
                workerName = "BudgetWorker",
                notificationType = "budget",
                route = "budget"
            )
        } catch (e: SecurityException) {
            // Permission wasn't granted
        }
    }
}
