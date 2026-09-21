package com.uplb.punla

import android.app.Application
import com.uplb.punla.diagnostics.PunlaDiagnostics
import com.uplb.punla.notification.PunlaNotifications
import com.uplb.punla.worker.CoreReliabilityScheduler

/** Installs local crash logging before any Activity/ViewModel is created. */
class PunlaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { PunlaNotifications.ensureChannels(this) }
            .onFailure { PunlaDiagnostics.warn(this, "Notifications", "Could not create notification channels at app start", it) }
        runCatching { CoreReliabilityScheduler.ensureScheduled(this) }
            .onFailure { PunlaDiagnostics.warn(this, "CoreScheduler", "Could not verify background jobs at app start", it) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                PunlaDiagnostics.error(
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
