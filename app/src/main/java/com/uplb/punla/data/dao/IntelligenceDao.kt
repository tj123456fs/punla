package com.uplb.punla.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.uplb.punla.data.entity.NotificationEvent
import com.uplb.punla.data.entity.StudySuggestionEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface IntelligenceDao {
    @Query("SELECT * FROM study_suggestion_events ORDER BY occurredAt DESC")
    fun observeStudySuggestionEvents(): Flow<List<StudySuggestionEvent>>

    @Query("SELECT * FROM study_suggestion_events ORDER BY occurredAt DESC")
    suspend fun getStudySuggestionEvents(): List<StudySuggestionEvent>

    @Upsert
    suspend fun insertStudySuggestionEvent(event: StudySuggestionEvent)

    @Query("SELECT * FROM study_suggestion_events WHERE suggestionId = :suggestionId ORDER BY occurredAt DESC LIMIT 1")
    suspend fun latestStudySuggestionEvent(suggestionId: String): StudySuggestionEvent?

    @Query("DELETE FROM study_suggestion_events")
    suspend fun clearStudySuggestionEvents()

    @Query("SELECT * FROM notification_events ORDER BY occurredAt DESC")
    fun observeNotificationEvents(): Flow<List<NotificationEvent>>

    @Query("SELECT * FROM notification_events ORDER BY occurredAt DESC")
    suspend fun getNotificationEvents(): List<NotificationEvent>

    @Upsert
    suspend fun insertNotificationEvent(event: NotificationEvent)

    @Query("DELETE FROM notification_events")
    suspend fun clearNotificationEvents()
}
