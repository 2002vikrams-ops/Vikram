package com.example.data

import androidx.room.*

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUrl: String, // can be resource name or local emoji
    val bio: String,
    val statusText: String,
    val isOnline: Boolean = false,
    val personalityPrompt: String = "" // Custom instruction for AI response
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactId: String,
    val senderId: String, // "user" or the contact's ID
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val snapUri: String? = null, // Path or code for shared snap
    val snapDuration: Int? = null, // Disappearing snap duration (seconds), null for normal msg
    val isViewed: Boolean = false
)

@Entity(tableName = "call_history")
data class CallHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactId: String,
    val callType: String, // "AUDIO" or "VIDEO"
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int,
    val direction: String, // "INCOMING", "OUTGOING", "MISSED"
    val isMissed: Boolean = false
)

@Entity(tableName = "game_stats")
data class GameStatsEntity(
    @PrimaryKey val gameId: String, // "tic_tac_toe", "memory_match"
    val gameName: String,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val highestScore: Int = 0,
    val lastPlayed: Long = System.currentTimeMillis()
)
