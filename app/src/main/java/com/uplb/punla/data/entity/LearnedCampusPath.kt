package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "learned_path_nodes",
    indices = [Index(value = ["lastSeenAt"])]
)
data class LearnedPathNode(
    @PrimaryKey val id: String,
    val lat: Double,
    val lon: Double,
    val observationCount: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long
)

@Entity(
    tableName = "learned_path_edges",
    foreignKeys = [
        ForeignKey(
            entity = LearnedPathNode::class,
            parentColumns = ["id"],
            childColumns = ["fromNodeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LearnedPathNode::class,
            parentColumns = ["id"],
            childColumns = ["toNodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["fromNodeId"]),
        Index(value = ["toNodeId"]),
        Index(value = ["observationCount"])
    ]
)
data class LearnedPathEdge(
    @PrimaryKey val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val distanceMeters: Double,
    val observationCount: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long
)

@Entity(
    tableName = "learned_walk_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WalkSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["processedAt"])]
)
data class LearnedWalkSession(
    @PrimaryKey val sessionId: String,
    val processedAt: Long,
    val acceptedPointCount: Int,
    val learnedEdgeCount: Int
)
