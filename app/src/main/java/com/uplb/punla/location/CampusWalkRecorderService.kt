package com.uplb.punla.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.uplb.punla.MainActivity
import com.uplb.punla.R
import com.uplb.punla.data.PunlaDatabase
import com.uplb.punla.data.dao.WalkRecordingDao
import com.uplb.punla.data.entity.WalkPoint
import com.uplb.punla.data.entity.WalkSession
import com.uplb.punla.data.entity.WalkSessionStatus
import com.uplb.punla.data.fmtDistance
import com.uplb.punla.worker.WalkPathLearningWorker
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Explicit, user-started walk recorder.
 *
 * This is a location foreground service rather than passive background tracking:
 * the user starts it from Campus, Android keeps a persistent notification visible,
 * and Pause/Resume/Stop are always reachable from that notification.
 */
class CampusWalkRecorderService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistMutex = Mutex()

    private lateinit var client: FusedLocationProviderClient
    private lateinit var dao: WalkRecordingDao

    @Volatile private var activeSession: WalkSession? = null
    private var lastPoint: WalkPoint? = null
    private var nextSequence: Int = 0
    private var activeSegment: Int = 0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val fixes = result.locations
            if (fixes.isEmpty()) return
            serviceScope.launch {
                persistMutex.withLock {
                    fixes.forEach { persistFix(it) }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        dao = PunlaDatabase.get(this).walkRecordingDao()
        ensureChannel()
        // Older completed recordings should become useful without requiring
        // the user to export/re-import or manually replay them.
        WalkPathLearningWorker.enqueueBackfill(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: WalkRecorder.ACTION_START

        if ((action == WalkRecorder.ACTION_START || action == WalkRecorder.ACTION_RESUME) && !hasPreciseLocation()) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            showForeground("Preparing walk recorder…", paused = false)
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            WalkRecorder.ACTION_PAUSE -> serviceScope.launch { pauseRecording() }
            WalkRecorder.ACTION_RESUME -> serviceScope.launch { resumeRecording() }
            WalkRecorder.ACTION_STOP -> serviceScope.launch { stopRecording() }
            else -> serviceScope.launch { startOrRestoreRecording() }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        client.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun hasPreciseLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private suspend fun startOrRestoreRecording() {
        val existing = dao.getActiveSession()
        if (existing != null) {
            activeSession = existing
            val persistedLast = dao.getLastPoint(existing.id)
            nextSequence = (persistedLast?.sequence ?: -1) + 1
            // A restored service starts a fresh segment so process downtime never
            // becomes an invented path between two unrelated GPS fixes.
            activeSegment = (persistedLast?.segment ?: -1) + 1
            lastPoint = null
            if (existing.status == WalkSessionStatus.PAUSED) {
                client.removeLocationUpdates(locationCallback)
                showForeground(summary(existing), paused = true)
            } else {
                startLocationUpdates()
                showForeground(summary(existing), paused = false)
            }
            return
        }

        val session = WalkSession(
            id = UUID.randomUUID().toString(),
            startedAt = System.currentTimeMillis()
        )
        dao.insertSession(session)
        activeSession = session
        lastPoint = null
        nextSequence = 0
        activeSegment = 0
        startLocationUpdates()
        showForeground(summary(session), paused = false)
    }

    private suspend fun pauseRecording() {
        client.removeLocationUpdates(locationCallback)
        val session = dao.getActiveSession()
        if (session == null) {
            finishForeground()
            return
        }
        if (session.status == WalkSessionStatus.RECORDING) {
            val updated = session.copy(
                status = WalkSessionStatus.PAUSED,
                pausedAt = System.currentTimeMillis()
            )
            dao.updateSession(updated)
            activeSession = updated
            lastPoint = null
            showForeground(summary(updated), paused = true)
        } else {
            showForeground(summary(session), paused = true)
        }
    }

    private suspend fun resumeRecording() {
        if (!hasPreciseLocation()) {
            finishForeground()
            return
        }
        val session = dao.getActiveSession()
        if (session == null) {
            startOrRestoreRecording()
            return
        }

        val now = System.currentTimeMillis()
        val additionalPause = session.pausedAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L
        val updated = session.copy(
            status = WalkSessionStatus.RECORDING,
            pausedAt = null,
            accumulatedPauseMillis = session.accumulatedPauseMillis + additionalPause
        )
        dao.updateSession(updated)
        activeSession = updated
        lastPoint = null
        val persistedLast = dao.getLastPoint(updated.id)
        nextSequence = (persistedLast?.sequence ?: -1) + 1
        activeSegment = (persistedLast?.segment ?: -1) + 1
        startLocationUpdates()
        showForeground(summary(updated), paused = false)
    }

    private suspend fun stopRecording() {
        client.removeLocationUpdates(locationCallback)
        val session = dao.getActiveSession()
        if (session != null) {
            val now = System.currentTimeMillis()
            val finalPause = if (session.status == WalkSessionStatus.PAUSED) {
                session.pausedAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L
            } else {
                0L
            }
            val completed = session.copy(
                endedAt = now,
                status = WalkSessionStatus.COMPLETED,
                pausedAt = null,
                accumulatedPauseMillis = session.accumulatedPauseMillis + finalPause
            )
            dao.updateSession(completed)
            // Learning runs after recording stops so location capture stays lightweight.
            WalkPathLearningWorker.enqueue(applicationContext, completed.id)
        }
        activeSession = null
        lastPoint = null
        finishForeground()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasPreciseLocation()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_LOCATION_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
            .build()
        client.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private suspend fun persistFix(location: Location) {
        var session = activeSession ?: dao.getActiveSession() ?: return
        if (session.status != WalkSessionStatus.RECORDING) return

        val now = System.currentTimeMillis()
        val capturedAt = location.time.takeIf { it > 0L } ?: now
        val candidate = WalkGpsSample(
            lat = location.latitude,
            lon = location.longitude,
            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
            capturedAt = capturedAt
        )
        val previous = lastPoint?.let {
            WalkGpsSample(
                lat = it.lat,
                lon = it.lon,
                accuracyMeters = it.accuracyMeters,
                capturedAt = it.capturedAt
            )
        }
        val decision = WalkGpsFilter.evaluate(previous, candidate, now)
        if (decision !is WalkGpsDecision.Accept) return

        if (decision.startNewSegment) {
            activeSegment += 1
        }

        val point = WalkPoint(
            sessionId = session.id,
            sequence = nextSequence,
            lat = candidate.lat,
            lon = candidate.lon,
            segment = activeSegment,
            accuracyMeters = candidate.accuracyMeters,
            capturedAt = candidate.capturedAt
        )
        dao.insertPoint(point)

        session = session.copy(
            distanceMeters = session.distanceMeters + decision.addedDistanceMeters,
            pointCount = session.pointCount + 1
        )
        dao.updateSession(session)
        activeSession = session
        lastPoint = point
        nextSequence += 1
        showForeground(summary(session), paused = false)
    }

    private fun summary(session: WalkSession): String {
        val pointLabel = if (session.pointCount == 1) "1 GPS point" else "${session.pointCount} GPS points"
        return "${fmtDistance(session.distanceMeters)} · $pointLabel"
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Walk recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while Punla is recording a campus walk."
                setShowBadge(false)
            }
        )
    }

    private fun showForeground(text: String, paused: Boolean) {
        val notification = buildNotification(text, paused)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(text: String, paused: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
            .putExtra("start_route", "campus")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            this,
            4100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleAction = if (paused) WalkRecorder.ACTION_RESUME else WalkRecorder.ACTION_PAUSE
        val toggleIntent = Intent(this, CampusWalkRecorderService::class.java).setAction(toggleAction)
        val togglePending = PendingIntent.getService(
            this,
            if (paused) 4102 else 4101,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CampusWalkRecorderService::class.java).setAction(WalkRecorder.ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this,
            4103,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (paused) "Walk recording paused" else "Recording campus walk")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, if (paused) "Resume" else "Pause", togglePending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun finishForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "walk_recording"
        private const val NOTIFICATION_ID = 4100
        private const val LOCATION_INTERVAL_MS = 3_000L
        private const val MIN_LOCATION_INTERVAL_MS = 1_500L
        private const val MIN_UPDATE_DISTANCE_METERS = 2.0f
    }
}
