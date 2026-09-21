package com.uplb.punla.worker

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uplb.punla.diagnostics.PunlaDiagnostics
import java.util.concurrent.TimeUnit

/** Small Phase 0 probes used to verify real-device background/reboot behavior. */
class BackgroundReliabilityProbeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ReliabilityProbe.markBackgroundProbeCompleted(applicationContext)
        PunlaDiagnostics.info(applicationContext, "ReliabilityProbe", "Background execution probe completed")
        return Result.success()
    }
}

/**
 * Longer probe intended for Battery Saver / screen-off / idle testing.
 * It does not request expedited execution; the point is to measure how the
 * device actually treats ordinary Punla recovery work while idle.
 */
class IdleReliabilityProbeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ReliabilityProbe.markIdleProbeCompleted(applicationContext)
        PunlaDiagnostics.info(applicationContext, "ReliabilityProbe", "Long-idle battery probe completed")
        return Result.success()
    }
}

object ReliabilityProbe {
    private const val PREFS = "punla_reliability"
    private const val KEY_BACKGROUND_SCHEDULED_AT = "background_probe_scheduled_at"
    private const val KEY_BACKGROUND_COMPLETED_AT = "background_probe_completed_at"
    private const val KEY_IDLE_SCHEDULED_AT = "idle_probe_scheduled_at"
    private const val KEY_IDLE_COMPLETED_AT = "idle_probe_completed_at"
    private const val KEY_REBOOT_ARMED_AT = "reboot_probe_armed_at"
    private const val KEY_REBOOT_PASSED_AT = "reboot_probe_passed_at"
    private const val KEY_SOAK_STARTED_AT = "phase0_soak_started_at"
    private const val KEY_SOAK_BASELINE_CRASHES = "phase0_soak_baseline_crashes"
    private const val UNIQUE_BACKGROUND_PROBE = "phase0_background_reliability_probe"
    private const val UNIQUE_IDLE_PROBE = "phase0_idle_reliability_probe"

    const val BACKGROUND_DELAY_MINUTES = 2L
    const val BACKGROUND_TIMEOUT_MINUTES = 10L
    const val IDLE_DELAY_MINUTES = 20L
    const val IDLE_TIMEOUT_MINUTES = 60L
    const val SOAK_DAYS = 7L

    fun scheduleBackgroundProbe(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        prefs(app).edit()
            .putLong(KEY_BACKGROUND_SCHEDULED_AT, now)
            .putLong(KEY_BACKGROUND_COMPLETED_AT, 0L)
            .apply()

        val request = OneTimeWorkRequestBuilder<BackgroundReliabilityProbeWorker>()
            .setInitialDelay(BACKGROUND_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            UNIQUE_BACKGROUND_PROBE,
            ExistingWorkPolicy.REPLACE,
            request
        )
        PunlaDiagnostics.info(app, "ReliabilityProbe", "Scheduled a 2-minute background execution probe")
    }

    internal fun markBackgroundProbeCompleted(context: Context) {
        prefs(context).edit()
            .putLong(KEY_BACKGROUND_COMPLETED_AT, System.currentTimeMillis())
            .apply()
    }

    fun backgroundScheduledAt(context: Context): Long = prefs(context).getLong(KEY_BACKGROUND_SCHEDULED_AT, 0L)
    fun backgroundCompletedAt(context: Context): Long = prefs(context).getLong(KEY_BACKGROUND_COMPLETED_AT, 0L)

    fun scheduleIdleProbe(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        prefs(app).edit()
            .putLong(KEY_IDLE_SCHEDULED_AT, now)
            .putLong(KEY_IDLE_COMPLETED_AT, 0L)
            .apply()

        val request = OneTimeWorkRequestBuilder<IdleReliabilityProbeWorker>()
            .setInitialDelay(IDLE_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            UNIQUE_IDLE_PROBE,
            ExistingWorkPolicy.REPLACE,
            request
        )
        PunlaDiagnostics.info(app, "ReliabilityProbe", "Scheduled a 20-minute long-idle battery probe")
    }

    internal fun markIdleProbeCompleted(context: Context) {
        prefs(context).edit()
            .putLong(KEY_IDLE_COMPLETED_AT, System.currentTimeMillis())
            .apply()
    }

    fun idleScheduledAt(context: Context): Long = prefs(context).getLong(KEY_IDLE_SCHEDULED_AT, 0L)
    fun idleCompletedAt(context: Context): Long = prefs(context).getLong(KEY_IDLE_COMPLETED_AT, 0L)

    fun armRebootProbe(context: Context) {
        prefs(context).edit()
            .putLong(KEY_REBOOT_ARMED_AT, System.currentTimeMillis())
            .putLong(KEY_REBOOT_PASSED_AT, 0L)
            .apply()
        PunlaDiagnostics.info(context, "ReliabilityProbe", "Reboot recovery probe armed")
    }

    fun rebootArmedAt(context: Context): Long = prefs(context).getLong(KEY_REBOOT_ARMED_AT, 0L)
    fun rebootPassedAt(context: Context): Long = prefs(context).getLong(KEY_REBOOT_PASSED_AT, 0L)

    fun onRecoveryBroadcast(context: Context, action: String?) {
        if (action != Intent.ACTION_BOOT_COMPLETED) return
        val armedAt = rebootArmedAt(context)
        if (armedAt <= 0L) return
        prefs(context).edit()
            .putLong(KEY_REBOOT_PASSED_AT, System.currentTimeMillis())
            .apply()
        PunlaDiagnostics.info(context, "ReliabilityProbe", "Reboot recovery probe passed")
    }

    fun startStabilitySoak(context: Context) {
        prefs(context).edit()
            .putLong(KEY_SOAK_STARTED_AT, System.currentTimeMillis())
            .putLong(KEY_SOAK_BASELINE_CRASHES, PunlaDiagnostics.fatalCrashCount(context))
            .apply()
        PunlaDiagnostics.info(context, "ReliabilityProbe", "Started Phase 0 seven-day crash-free soak")
    }

    fun soakStartedAt(context: Context): Long = prefs(context).getLong(KEY_SOAK_STARTED_AT, 0L)
    fun soakBaselineCrashes(context: Context): Long = prefs(context).getLong(KEY_SOAK_BASELINE_CRASHES, 0L)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
