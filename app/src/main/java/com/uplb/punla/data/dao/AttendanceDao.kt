package com.uplb.punla.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.uplb.punla.data.entity.AttendanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records ORDER BY occurrenceDate DESC, scheduledStart DESC")
    fun observeAll(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records ORDER BY occurrenceDate DESC, scheduledStart DESC")
    suspend fun getAll(): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE occurrenceKey = :occurrenceKey LIMIT 1")
    suspend fun getByOccurrenceKey(occurrenceKey: String): AttendanceRecord?

    @Upsert
    suspend fun upsert(record: AttendanceRecord)

    @Query("DELETE FROM attendance_records WHERE occurrenceKey = :occurrenceKey")
    suspend fun deleteByOccurrenceKey(occurrenceKey: String)

    @Query("DELETE FROM attendance_records WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("DELETE FROM attendance_records")
    suspend fun clearAll()
}
