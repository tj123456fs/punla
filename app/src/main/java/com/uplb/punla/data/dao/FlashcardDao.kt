package com.uplb.punla.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.uplb.punla.data.entity.Flashcard
import com.uplb.punla.data.entity.FlashcardDeck
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcard_decks ORDER BY updatedAt DESC, name COLLATE NOCASE ASC")
    fun observeDecks(): Flow<List<FlashcardDeck>>

    @Query("SELECT * FROM flashcard_decks ORDER BY updatedAt DESC, name COLLATE NOCASE ASC")
    suspend fun getDecks(): List<FlashcardDeck>

    @Query("SELECT * FROM flashcards ORDER BY createdAt ASC")
    fun observeAllCards(): Flow<List<Flashcard>>

    @Query("SELECT * FROM flashcards ORDER BY createdAt ASC")
    suspend fun getAllCards(): List<Flashcard>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY createdAt ASC")
    fun observeCards(deckId: String): Flow<List<Flashcard>>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY createdAt ASC")
    suspend fun getCards(deckId: String): List<Flashcard>

    @Query("SELECT COUNT(*) FROM flashcards WHERE deckId = :deckId")
    fun observeCardCount(deckId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM flashcards WHERE deckId = :deckId AND (dueAt = 0 OR dueAt <= :now)")
    fun observeDueCount(deckId: String, now: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeck(deck: FlashcardDeck)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDecks(decks: List<FlashcardDeck>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: Flashcard)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCards(cards: List<Flashcard>)

    @Transaction
    suspend fun importDeck(deck: FlashcardDeck, cards: List<Flashcard>) {
        upsertDeck(deck)
        if (cards.isNotEmpty()) upsertCards(cards)
    }

    @Query("UPDATE flashcard_decks SET topicId = NULL WHERE topicId = :topicId")
    suspend fun clearTopicAssociation(topicId: String)

    @Delete
    suspend fun deleteDeck(deck: FlashcardDeck)

    @Delete
    suspend fun deleteCard(card: Flashcard)

    @Query("DELETE FROM flashcard_decks")
    suspend fun clearDecks()

    @Query("DELETE FROM flashcards")
    suspend fun clearCards()

    @Transaction
    suspend fun clearAll() {
        clearCards()
        clearDecks()
    }
}
