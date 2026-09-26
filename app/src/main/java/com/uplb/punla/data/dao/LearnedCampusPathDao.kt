package com.uplb.punla.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.uplb.punla.data.entity.LearnedPathEdge
import com.uplb.punla.data.entity.LearnedPathNode
import com.uplb.punla.data.entity.LearnedWalkSession

@Dao
interface LearnedCampusPathDao {
    @Query("SELECT * FROM learned_path_nodes")
    suspend fun getAllNodes(): List<LearnedPathNode>

    @Query("SELECT * FROM learned_path_edges")
    suspend fun getAllEdges(): List<LearnedPathEdge>

    @Query("SELECT * FROM learned_path_edges WHERE observationCount >= :minimumObservations")
    suspend fun getTrustedEdges(minimumObservations: Int): List<LearnedPathEdge>

    @Query("SELECT * FROM learned_walk_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getProcessedSession(sessionId: String): LearnedWalkSession?

    @Query(
        """
        SELECT ws.id
        FROM walk_sessions AS ws
        LEFT JOIN learned_walk_sessions AS learned ON learned.sessionId = ws.id
        WHERE ws.status = 'COMPLETED' AND learned.sessionId IS NULL
        ORDER BY ws.startedAt ASC
        LIMIT :limit
        """
    )
    suspend fun getUnprocessedCompletedSessionIds(limit: Int): List<String>

    @Upsert
    suspend fun upsertNodes(nodes: List<LearnedPathNode>)

    @Upsert
    suspend fun upsertEdges(edges: List<LearnedPathEdge>)

    @Upsert
    suspend fun upsertProcessedSession(session: LearnedWalkSession)
}
