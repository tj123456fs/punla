package com.uplb.punla.push

/*
 * Background push via FCM.
 *
 * There's no backend yet to actually send pushes (the original web app's
 * Cloudflare Worker sync layer was never ported to this Android project —
 * see CHANGES.md / README "What's simplified"), so onNewToken has nowhere
 * to POST the token to. It's stashed locally via PunlaRepository.fcmToken
 * so it's ready to send the moment a registration endpoint exists.
 *
 * onMessageReceived builds a local notification the same way
 * DeadlineWorker/ClassReminderWorker do, so a pushed reminder looks
 * identical to a locally generated one. WorkManager keeps running
 * regardless — this is additive, not a replacement.
 */

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.uplb.punla.MainActivity
import com.uplb.punla.R
import com.uplb.punla.data.PunlaRepository

class PunlaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Persist locally; there's no /subscribe endpoint to POST it to yet
        // (see class doc above). Re-send from here once one exists.
        PunlaRepository(applicationContext).fcmToken = token
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val repo = PunlaRepository(applicationContext)
        if (!repo.notificationsEnabled) return

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Punla"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return

        showNotification(title, body)
    }

    private fun showNotification(title: String, content: String) {
        val channelId = "punla_push_channel"
        val notificationManager = NotificationManagerCompat.from(this)

        // minSdk is 26 (O), so NotificationChannel is always available here — no SDK_INT guard needed.
        val channel = NotificationChannel(
            channelId,
            "Push Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Reminders pushed from the server" }
        val sysManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        sysManager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            notificationManager.notify(2, builder.build())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted
        }
    }
}
