package com.uplb.punla.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.data.entity.AttendanceLog
import com.uplb.punla.data.entity.AttendanceStatus
import com.uplb.punla.diagnostics.PunlaDiagnostics
import kotlinx.coroutines.CancellationException
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Schedule-based attendance auto logging.
 *
 * This never overwrites a manual decision. After the grace period has passed,
 * an occurrence with no record is marked ATTENDED. The setting is opt-in
 * because schedule presence alone cannot prove physical attendance.
 */
class AttendanceAutoLogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = PunlaRepository(applicationContext)
        if (!repo.autoAttendanceEnabled) return Result.success()

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        if (today.isBefore(repo.termStartDate) || today.isAfter(repo.termEndDate)) return Result.success()

        val day = DAY_ABBREV[now.dayOfWeek] ?: return Result.success()
        val sessions = try {
            repo.allClasses().filter { it.day.equals(day, ignoreCase = true) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            PunlaDiagnostics.error(applicationContext, "AttendanceAutoLog", "Couldn't read today's schedule", error)
            return if (runAttemptCount < 2) Result.retry() else Result.success()
        }

        var logged = 0
        for (session in sessions) {
            val start = runCatching { LocalTime.parse(session.start) }.getOrNull() ?: continue
            val eligibleAt = today.atTime(start).plusMinutes(GRACE_MINUTES)
            if (now.isBefore(eligibleAt)) continue

            val key = AttendanceLog.occurrenceKey(session.id, today, session.start)
            try {
                if (repo.attendanceForOccurrence(key) != null) continue
                repo.setAttendance(
                    AttendanceLog.forOccurrence(
                        session = session,
                        date = today,
                        status = AttendanceStatus.ATTENDED,
                        source = "auto_schedule"
                    )
                )
                logged++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                PunlaDiagnostics.error(
                    applicationContext,
                    "AttendanceAutoLog",
                    "Couldn't auto-log a scheduled class occurrence",
                    error
                )
            }
        }

        if (logged > 0) {
            PunlaDiagnostics.info(applicationContext, "AttendanceAutoLog", "Auto-logged $logged scheduled class occurrence(s)")
        }
        return Result.success()
    }

    companion object {
        const val GRACE_MINUTES = 10L
        private val DAY_ABBREV = mapOf(
            DayOfWeek.MONDAY to "Mon",
            DayOfWeek.TUESDAY to "Tue",
            DayOfWeek.WEDNESDAY to "Wed",
            DayOfWeek.THURSDAY to "Thu",
            DayOfWeek.FRIDAY to "Fri",
            DayOfWeek.SATURDAY to "Sat",
            DayOfWeek.SUNDAY to "Sun"
        )
    }
}

object AttendanceAutoLogScheduler {
    private const val UNIQUE_PERIODIC = "attendance_auto_log_periodic"
    private const val UNIQUE_NOW = "attendance_auto_log_now"

    fun sync(context: Context, enabled: Boolean = PunlaRepository(context.applicationContext).autoAttendanceEnabled) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            manager.cancelUniqueWork(UNIQUE_PERIODIC)
            manager.cancelUniqueWork(UNIQUE_NOW)
            return
        }

        val periodic = PeriodicWorkRequestBuilder<AttendanceAutoLogWorker>(15, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
        val now = OneTimeWorkRequestBuilder<AttendanceAutoLogWorker>().build()
        manager.enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.REPLACE, now)
    }
}
