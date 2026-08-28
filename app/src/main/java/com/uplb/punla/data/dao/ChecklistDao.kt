package com.uplb.punla.data.dao

import androidx.room.*
import com.uplb.punla.data.entity.ChecklistItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<ChecklistItem>>

    @Query("SELECT * FROM checklist_items ORDER BY sortOrder ASC")
    suspend fun getAll(): List<ChecklistItem>

    @Query("SELECT COUNT(*) FROM checklist_items")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(item: ChecklistItem)

    @Upsert
    suspend fun upsertAll(items: List<ChecklistItem>)

    @Delete
    suspend fun delete(item: ChecklistItem)

    @Query("DELETE FROM checklist_items")
    suspend fun clearAll()
}
