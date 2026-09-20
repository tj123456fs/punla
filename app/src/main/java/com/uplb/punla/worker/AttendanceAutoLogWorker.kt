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
import com.uplb.punla.notification.ClassDayTimeline
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedule-based attendance helper.
 *
 * When enabled, a class is marked ATTENDED after its scheduled end only when
 * that exact occurrence has no record yet. Manual ATTENDED/ABSENT choices are
 * never overwritten, so a user can correct the automatic assumption anytime.
 */
class AttendanceAutoLogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = PunlaRepository(applicationContext)
        if (!repo.attendanceAutoLogEnabled) return Result.success()

        return try {
            val now = LocalDateTime.now()
            val classes = repo.allClasses()
            ClassDayTimeline.occurrencesOn(classes, now.toLocalDate())
                .filter { !it.end.isAfter(now) }
                .forEach { occurrence ->
                    val date = occurrence.start.toLocalDate()
                    val key = AttendanceLog.occurrenceKey(
                        occurrence.session.id,
                        date,
                        occurrence.session.start
                    )
                    if (repo.attendanceForOccurrence(key) == null) {
                        repo.setAttendance(
                            AttendanceLog.forOccurrence(
                                session = occurrence.session,
                                date = date,
                                status = AttendanceStatus.ATTENDED,
                                source = "auto"
                            )
                        )
                    }
                }

            AttendanceAutoLogScheduler.scheduleNext(applicationContext, classes, now)
            ClassDayNotificationScheduler.refresh(applicationContext)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object AttendanceAutoLogScheduler {
    private const val UNIQUE_PERIODIC = "attendance_auto_log_recovery"
    private const val UNIQUE_REFRESH = "attendance_auto_log_refresh"
    private const val UNIQUE_NEXT_END = "attendance_auto_log_next_class_end"

    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        val periodic = PeriodicWorkRequestBuilder<AttendanceAutoLogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
        refresh(appContext)
    }

    fun refresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<AttendanceAutoLogWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_REFRESH,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleNext(
        context: Context,
        classes: List<com.uplb.punla.data.entity.ClassSession>,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        val manager = WorkManager.getInstance(context.applicationContext)
        val nextEnd = (0L..7L).asSequence()
            .flatMap { offset -> ClassDayTimeline.occurrencesOn(classes, now.toLocalDate().plusDays(offset)).asSequence() }
            .map { it.end }
            .filter { it.isAfter(now) }
            .minOrNull()

        if (nextEnd == null) {
            manager.cancelUniqueWork(UNIQUE_NEXT_END)
            return
        }

        // Small grace window avoids racing a clock tick at the exact end time.
        val delayMillis = Duration.between(now, nextEnd.plusSeconds(5)).toMillis().coerceAtLeast(1_000L)
        val request = OneTimeWorkRequestBuilder<AttendanceAutoLogWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        manager.enqueueUniqueWork(UNIQUE_NEXT_END, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        manager.cancelUniqueWork(UNIQUE_PERIODIC)
        manager.cancelUniqueWork(UNIQUE_REFRESH)
        manager.cancelUniqueWork(UNIQUE_NEXT_END)
    }
}
