package com.uplb.punla.data.dao

import androidx.room.*
import com.uplb.punla.data.entity.ClassSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassSessionDao {
    @Query("SELECT * FROM class_sessions ORDER BY CASE day WHEN 'Mon' THEN 1 WHEN 'Tue' THEN 2 WHEN 'Wed' THEN 3 WHEN 'Thu' THEN 4 WHEN 'Fri' THEN 5 WHEN 'Sat' THEN 6 WHEN 'Sun' THEN 7 ELSE 8 END, start")
    fun observeAll(): Flow<List<ClassSession>>

    @Query("SELECT * FROM class_sessions ORDER BY CASE day WHEN 'Mon' THEN 1 WHEN 'Tue' THEN 2 WHEN 'Wed' THEN 3 WHEN 'Thu' THEN 4 WHEN 'Fri' THEN 5 WHEN 'Sat' THEN 6 WHEN 'Sun' THEN 7 ELSE 8 END, start")
    suspend fun getAll(): List<ClassSession>

    @Upsert
    suspend fun upsert(session: ClassSession)

    // Roadmap #4 — simple absence tally, incremented/decremented from a tap
    // rather than requiring a full row re-upsert from the UI layer.
    @Query("UPDATE class_sessions SET absences = absences + 1 WHERE id = :id")
    suspend fun incrementAbsence(id: String)

    @Query("UPDATE class_sessions SET absences = CASE WHEN absences > 0 THEN absences - 1 ELSE 0 END WHERE id = :id")
    suspend fun decrementAbsence(id: String)

    @Delete
    suspend fun delete(session: ClassSession)

    @Query("DELETE FROM class_sessions")
    suspend fun clearAll()
}
