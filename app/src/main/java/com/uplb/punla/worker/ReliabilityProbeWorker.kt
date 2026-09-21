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

object ReliabilityProbe {
    private const val PREFS = "punla_reliability"
    private const val KEY_BACKGROUND_SCHEDULED_AT = "background_probe_scheduled_at"
    private const val KEY_BACKGROUND_COMPLETED_AT = "background_probe_completed_at"
    private const val KEY_REBOOT_ARMED_AT = "reboot_probe_armed_at"
    private const val KEY_REBOOT_PASSED_AT = "reboot_probe_passed_at"
    private const val UNIQUE_BACKGROUND_PROBE = "phase0_background_reliability_probe"

    const val BACKGROUND_DELAY_MINUTES = 2L
    const val BACKGROUND_TIMEOUT_MINUTES = 10L

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

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
