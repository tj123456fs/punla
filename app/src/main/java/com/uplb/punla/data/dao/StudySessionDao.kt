package com.uplb.punla.data.dao

import androidx.room.*
import com.uplb.punla.data.entity.StudySession
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions ORDER BY startedAt DESC")
    suspend fun getAll(): List<StudySession>

    @Query("SELECT * FROM study_sessions WHERE startedAt >= :sinceEpochMillis ORDER BY startedAt DESC")
    fun observeSince(sinceEpochMillis: Long): Flow<List<StudySession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: StudySession)

    @Delete
    suspend fun delete(session: StudySession)

    @Query("DELETE FROM study_sessions")
    suspend fun clearAll()
}
