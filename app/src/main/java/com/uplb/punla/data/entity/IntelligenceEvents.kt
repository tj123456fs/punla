package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** One interaction with a generated free-time study suggestion. */
@Entity(
    tableName = "study_suggestion_events",
    indices = [Index("suggestionId"), Index("occurredAt")]
)
data class StudySuggestionEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val suggestionId: String,
    val occurredAt: Long = System.currentTimeMillis(),
    val outcome: String, // SHOWN | DISMISSED | STARTED | COMPLETED | STOPPED
    val slotHour: Int,
    val dayOfWeek: Int,
    val urgencyDays: Int,
    val availableMinutes: Int,
    val deadlineId: String? = null,
    val courseCode: String? = null,
    val sessionId: String? = null
)

/** Append-only notification lifecycle event used to learn useful reminder hours. */
@Entity(
    tableName = "notification_events",
    indices = [Index("notificationKey"), Index("occurredAt"), Index("workerName")]
)
data class NotificationEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val notificationKey: String,
    val workerName: String,
    val notificationType: String,
    val occurredAt: Long = System.currentTimeMillis(),
    val localHour: Int,
    val outcome: String // FIRED | OPENED | DISMISSED | ACTION_USED | EXPIRED
)
