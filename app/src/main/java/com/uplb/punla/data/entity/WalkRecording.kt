package com.uplb.punla.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

object WalkSessionStatus {
    const val RECORDING = "RECORDING"
    const val PAUSED = "PAUSED"
    const val COMPLETED = "COMPLETED"
}

@Entity(
    tableName = "walk_sessions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["startedAt"])
    ]
)
data class WalkSession(
    @androidx.room.PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: String = WalkSessionStatus.RECORDING,
    val pausedAt: Long? = null,
    val accumulatedPauseMillis: Long = 0L,
    val distanceMeters: Double = 0.0,
    val pointCount: Int = 0
)

@Entity(
    tableName = "walk_points",
    primaryKeys = ["sessionId", "sequence"],
    foreignKeys = [
        ForeignKey(
            entity = WalkSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class WalkPoint(
    val sessionId: String,
    val sequence: Int,
    val lat: Double,
    val lon: Double,
    @ColumnInfo(defaultValue = "0") val segment: Int = 0,
    val accuracyMeters: Float?,
    val capturedAt: Long
)
