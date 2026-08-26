package com.uplb.punla.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.uplb.punla.data.entity.JsonImportRecord

@Dao
interface JsonImportDao {
    @Query("SELECT EXISTS(SELECT 1 FROM json_import_records WHERE fileType = :fileType AND contentId = :contentId)")
    suspend fun exists(fileType: String, contentId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: JsonImportRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<JsonImportRecord>)

    @Query("SELECT * FROM json_import_records ORDER BY importedAt DESC")
    suspend fun getAll(): List<JsonImportRecord>

    @Query("DELETE FROM json_import_records")
    suspend fun clearAll()
}
