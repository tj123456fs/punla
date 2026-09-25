package com.uplb.punla.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.uplb.punla.data.entity.WalkPoint
import com.uplb.punla.data.entity.WalkSession
import kotlinx.coroutines.flow.Flow

@Dao
interface WalkRecordingDao {
    @Query("""
        SELECT * FROM walk_sessions
        WHERE status IN ('RECORDING', 'PAUSED')
        ORDER BY startedAt DESC
        LIMIT 1
    """)
    fun observeActiveSession(): Flow<WalkSession?>

    @Query("""
        SELECT * FROM walk_sessions
        WHERE status IN ('RECORDING', 'PAUSED')
        ORDER BY startedAt DESC
        LIMIT 1
    """)
    suspend fun getActiveSession(): WalkSession?

    @Query("SELECT * FROM walk_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int = 20): Flow<List<WalkSession>>

    @Query("SELECT * FROM walk_points WHERE sessionId = :sessionId ORDER BY sequence ASC")
    fun observePoints(sessionId: String): Flow<List<WalkPoint>>

    @Query("SELECT * FROM walk_points WHERE sessionId = :sessionId ORDER BY sequence ASC")
    suspend fun getPoints(sessionId: String): List<WalkPoint>

    @Query("SELECT * FROM walk_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): WalkSession?

    @Query("SELECT * FROM walk_points WHERE sessionId = :sessionId ORDER BY sequence DESC LIMIT 1")
    suspend fun getLastPoint(sessionId: String): WalkPoint?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: WalkSession)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPoint(point: WalkPoint)

    @Update
    suspend fun updateSession(session: WalkSession)

    @Delete
    suspend fun deleteSession(session: WalkSession)
}
