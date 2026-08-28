package com.uplb.punla.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.uplb.punla.data.entity.JsonImportRecord

@Dao
interface JsonImportDao {
    @Query("SELECT EXISTS(SELECT 1 FROM json_import_records WHERE fileType = :fileType AND contentId = :contentId)")
    suspend fun exists(fileType: String, contentId: String): Boolean

    @Upsert
    suspend fun upsert(record: JsonImportRecord)

    @Upsert
    suspend fun upsertAll(records: List<JsonImportRecord>)

    @Query("SELECT * FROM json_import_records ORDER BY importedAt DESC")
    suspend fun getAll(): List<JsonImportRecord>

    @Query("DELETE FROM json_import_records")
    suspend fun clearAll()
}
