package com.uplb.punla.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.uplb.punla.R
import com.uplb.punla.data.PunlaDatabase
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.notification.PunlaNotifications
import com.uplb.punla.notification.TrackedNotification
import com.uplb.punla.planning.OsSetting
import com.uplb.punla.planning.StudentOsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.*
import java.util.concurrent.TimeUnit

/** A single optional evening digest; never changes attendance or tasks. */
class StudentOsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val repo = PunlaRepository(app)
        val now = LocalDateTime.now()
        if (!repo.notificationsEnabled || now.hour !in 20..21 ||
            PunlaNotifications.isRoutineQuietHours(repo.quietHoursEnabled)) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val dao = PunlaDatabase.get(app).studentOsDao()
        val prefs = dao.settings().associate { it.key to it.value }
        if (prefs["ambientRecap"] != "true" || prefs["recapDate"] == now.toLocalDate().toString()) return Result.success()
        val snapshot = withTimeoutOrNull(10000) { StudentOsRepository.get(app).state.first { it.ready } } ?: return Result.retry()
        val blocks = snapshot.blocks.filter { Instant.ofEpochMilli(it.startAt).atZone(ZoneId.systemDefault()).toLocalDate() == now.toLocalDate() }
        if (blocks.isEmpty() && snapshot.context.study.minutesToday == 0) return Result.success()
        val body = "${blocks.count { it.status == "DONE" }}/${blocks.size} blocks done · ${snapshot.context.study.minutesToday} min focused\n" +
            "${snapshot.context.attendance.attendedToday} attended · ${snapshot.context.attendance.absentToday} absent recorded · ₱${snapshot.context.budget.spentToday} logged"
        val builder = PunlaNotifications.routine(NotificationCompat.Builder(app, PunlaNotifications.CHANNEL_DAILY_BRIEF))
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Your Punla day").setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).setTimeoutAfter(7200000)
        try {
            TrackedNotification.post(app, NotificationManagerCompat.from(app), 1520, builder,
                "StudentOsWorker", "evening_recap", "student-os", "recap:${now.toLocalDate()}")
            dao.save(OsSetting("recapDate", now.toLocalDate().toString()))
        } catch (_: SecurityException) { return Result.success() }
        return Result.success()
    }
    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("student_os_recap", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<StudentOsWorker>(30, TimeUnit.MINUTES).build())
        }
    }
}
