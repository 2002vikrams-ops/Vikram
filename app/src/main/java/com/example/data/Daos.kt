package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SocialDao {

    // --- Contacts ---
    @Query("SELECT * FROM contacts")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Query("UPDATE contacts SET isOnline = :isOnline, statusText = :status WHERE id = :id")
    suspend fun updateContactStatus(id: String, isOnline: Boolean, status: String)

    // --- Messages & Snaps ---
    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp ASC")
    fun getMessagesForContact(contactId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE snapUri IS NOT NULL ORDER BY timestamp DESC")
    fun getAllSnaps(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE messages SET isViewed = 1 WHERE id = :messageId")
    suspend fun markMessageAsViewed(messageId: Int)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Int)

    // --- Call History ---
    @Query("SELECT * FROM call_history ORDER BY timestamp DESC")
    fun getCallHistory(): Flow<List<CallHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallHistoryEntity)

    @Query("DELETE FROM call_history")
    suspend fun clearCallHistory()

    // --- Game Stats ---
    @Query("SELECT * FROM game_stats")
    fun getAllGameStats(): Flow<List<GameStatsEntity>>

    @Query("SELECT * FROM game_stats WHERE gameId = :gameId LIMIT 1")
    suspend fun getGameStats(gameId: String): GameStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateGameStats(stats: GameStatsEntity)

    @Query("UPDATE game_stats SET wins = wins + :win, losses = losses + :loss, draws = draws + :draw, highestScore = CASE WHEN :score > highestScore THEN :score ELSE highestScore END, lastPlayed = :timestamp WHERE gameId = :gameId")
    suspend fun incrementGameStats(gameId: String, win: Int, loss: Int, draw: Int, score: Int, timestamp: Long = System.currentTimeMillis())
}
