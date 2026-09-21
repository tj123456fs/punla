package com.uplb.punla

import android.app.Application
import com.uplb.punla.diagnostics.PunlaDiagnostics
import com.uplb.punla.notification.PunlaNotifications
import com.uplb.punla.worker.CoreReliabilityScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Installs local crash logging before any Activity/ViewModel is created. */
class PunlaApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        runCatching { PunlaNotifications.ensureChannels(this) }
            .onFailure { PunlaDiagnostics.warn(this, "Notifications", "Could not create notification channels at app start", it) }
        // Persistent job verification can touch preferences and WorkManager. Keep it
        // off the startup/main thread so first-frame rendering is never delayed.
        appScope.launch {
            runCatching { CoreReliabilityScheduler.ensureScheduled(this@PunlaApplication) }
                .onFailure { PunlaDiagnostics.warn(this@PunlaApplication, "CoreScheduler", "Could not verify background jobs at app start", it) }
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                PunlaDiagnostics.fatal(
                    this,
                    "Uncaught",
                    "Fatal exception on ${thread.name}",
                    error
                )
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
}
