package com.uplb.punla.planning

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "task_preferences")
data class TaskPreferences(
    @PrimaryKey val id: String,
    val estimatedMinutes: Int = 25, val progress: Int = 0,
    val importance: Int = 3, val effort: Int = 2, val dueTime: String = "23:59",
    val pinned: Boolean = false, val dismissedUntil: Long = 0
)
@Entity(tableName = "inbox_captures")
data class InboxCapture(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String, val attachment: String? = null,
    val createdAt: Long = System.currentTimeMillis(), val processed: Boolean = false
)
@Entity(tableName = "day_plan_blocks")
data class DayPlanBlock(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String, val title: String, val startAt: Long, val endAt: Long,
    val status: String = "PLANNED", val locked: Boolean = false
) {
    fun window() = TimeWindow(startAt, endAt)
}
@Entity(tableName = "life_commitments")
data class LifeCommitment(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String, val startTime: String, val endTime: String,
    val days: String = "1,2,3,4,5,6,7", val category: String = "Personal", val enabled: Boolean = true
)
@Entity(tableName = "os_settings")
data class OsSetting(@PrimaryKey val key: String, val value: String)

@Dao
interface StudentOsDao {
    @Query("SELECT * FROM task_preferences") fun observePreferences(): Flow<List<TaskPreferences>>
    @Query("SELECT * FROM inbox_captures ORDER BY createdAt DESC") fun observeCaptures(): Flow<List<InboxCapture>>
    @Query("SELECT * FROM day_plan_blocks ORDER BY startAt") fun observeBlocks(): Flow<List<DayPlanBlock>>
    @Query("SELECT * FROM life_commitments") fun observeLife(): Flow<List<LifeCommitment>>
    @Query("SELECT * FROM os_settings") fun observeSettings(): Flow<List<OsSetting>>
    @Query("SELECT * FROM task_preferences") suspend fun preferences(): List<TaskPreferences>
    @Query("SELECT * FROM inbox_captures") suspend fun captures(): List<InboxCapture>
    @Query("SELECT * FROM day_plan_blocks ORDER BY startAt") suspend fun blocks(): List<DayPlanBlock>
    @Query("SELECT * FROM life_commitments") suspend fun life(): List<LifeCommitment>
    @Query("SELECT * FROM os_settings") suspend fun settings(): List<OsSetting>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(item: TaskPreferences)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(item: InboxCapture)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(item: DayPlanBlock)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(item: LifeCommitment)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(item: OsSetting)
    @Delete suspend fun delete(item: LifeCommitment)
    @Delete suspend fun delete(item: DayPlanBlock)
    @Delete suspend fun delete(item: InboxCapture)
    @Query("DELETE FROM task_preferences") suspend fun clearPreferences()
    @Query("DELETE FROM inbox_captures") suspend fun clearCaptures()
    @Query("DELETE FROM day_plan_blocks") suspend fun clearBlocks()
    @Query("DELETE FROM life_commitments") suspend fun clearLife()
    @Query("DELETE FROM os_settings") suspend fun clearSettings()
}
