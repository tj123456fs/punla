package com.uplb.punla.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.uplb.punla.MainActivity
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.data.entity.NotificationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID

/** Adds interaction tracking to an existing notification without another wake-up. */
object TrackedNotification {
    internal const val EXTRA_KEY = "notification_key"
    internal const val EXTRA_WORKER = "notification_worker"
    internal const val EXTRA_TYPE = "notification_type"
    internal const val EXTRA_OUTCOME = "notification_outcome"
    internal const val EXTRA_ROUTE = "notification_route"

    internal const val ACTION_INTERACTION = "com.uplb.punla.NOTIFICATION_INTERACTION"
    internal const val ACTION_OPEN = "com.uplb.punla.NOTIFICATION_OPEN"

    suspend fun post(
        context: Context,
        manager: NotificationManagerCompat,
        notificationId: Int,
        builder: NotificationCompat.Builder,
        workerName: String,
        notificationType: String,
        route: String? = null,
        notificationKey: String = "$workerName:${System.currentTimeMillis()}:${UUID.randomUUID()}"
    ) {
        builder
            .setContentIntent(contentActivityIntent(context, notificationKey, workerName, notificationType, route))
            .setDeleteIntent(dismissIntent(context, notificationKey, workerName, notificationType))
            .setAutoCancel(true)

        manager.notify(notificationId, builder.build())
        try {
            PunlaRepository(context).logNotificationEvent(
                NotificationEvent(
                    notificationKey = notificationKey,
                    workerName = workerName,
                    notificationType = notificationType,
                    localHour = LocalDateTime.now().hour,
                    outcome = "FIRED"
                )
            )
        } catch (_: Exception) {
            // Reminder delivery must not fail because optional learning data
            // could not be persisted.
        }
    }

    private fun contentActivityIntent(
        context: Context,
        key: String,
        worker: String,
        type: String,
        route: String?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "$ACTION_OPEN:$key"
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_KEY, key)
            putExtra(EXTRA_WORKER, worker)
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_OUTCOME, "OPENED")
            putExtra(EXTRA_ROUTE, route)
            route?.let { putExtra(MainActivity.EXTRA_START_ROUTE, it) }
        }
        return PendingIntent.getActivity(
            context,
            ("$key:OPENED").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dismissIntent(
        context: Context,
        key: String,
        worker: String,
        type: String
    ): PendingIntent {
        val intent = Intent(context, NotificationInteractionReceiver::class.java).apply {
            action = ACTION_INTERACTION
            putExtra(EXTRA_KEY, key)
            putExtra(EXTRA_WORKER, worker)
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_OUTCOME, "DISMISSED")
        }
        return PendingIntent.getBroadcast(
            context,
            ("$key:DISMISSED").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    internal fun read(intent: Intent): Interaction? {
        if (intent.action != ACTION_INTERACTION) return null
        return Interaction(
            key = intent.getStringExtra(EXTRA_KEY) ?: return null,
            worker = intent.getStringExtra(EXTRA_WORKER) ?: "unknown",
            type = intent.getStringExtra(EXTRA_TYPE) ?: "general",
            outcome = intent.getStringExtra(EXTRA_OUTCOME) ?: return null,
            route = intent.getStringExtra(EXTRA_ROUTE)
        )
    }

    internal data class Interaction(
        val key: String,
        val worker: String,
        val type: String,
        val outcome: String,
        val route: String?
    )
}

class NotificationInteractionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val interaction = TrackedNotification.read(intent) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                try {
                    PunlaRepository(context.applicationContext).logNotificationEvent(
                        NotificationEvent(
                            notificationKey = interaction.key,
                            workerName = interaction.worker,
                            notificationType = interaction.type,
                            localHour = LocalDateTime.now().hour,
                            outcome = interaction.outcome
                        )
                    )
                } catch (_: Exception) {
                    // Opening the destination is more important than optional
                    // engagement logging.
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
