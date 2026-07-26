package com.uplb.punla.data.dao

import androidx.room.*
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.data.entity.DeadlineRule
import kotlinx.coroutines.flow.Flow

@Dao
interface DeadlineDao {
    @Query("SELECT * FROM deadlines ORDER BY due")
    fun observeAll(): Flow<List<Deadline>>

    @Query("SELECT * FROM deadlines ORDER BY due")
    suspend fun getAll(): List<Deadline>

    @Query("SELECT * FROM deadlines WHERE done = 0 ORDER BY due ASC LIMIT 1")
    suspend fun getNextPending(): Deadline?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(deadline: Deadline)

    @Delete
    suspend fun delete(deadline: Deadline)

    @Query("DELETE FROM deadlines")
    suspend fun clearAll()

    @Query("SELECT * FROM deadline_rules")
    suspend fun getAllRules(): List<DeadlineRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: DeadlineRule)

    @Query("DELETE FROM deadline_rules WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: String)

    @Query("DELETE FROM deadline_rules")
    suspend fun clearAllRules()

    @Query("UPDATE deadlines SET isRecurring = 0, ruleId = NULL WHERE ruleId = :ruleId")
    suspend fun detachRecurrence(ruleId: String)
}
