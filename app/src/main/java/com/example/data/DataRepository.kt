package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import java.util.UUID

class DataRepository(private val socialDao: SocialDao) {

    val allContacts: Flow<List<ContactEntity>> = socialDao.getAllContacts()
    val allSnaps: Flow<List<MessageEntity>> = socialDao.getAllSnaps()
    val callHistory: Flow<List<CallHistoryEntity>> = socialDao.getCallHistory()
    val allGameStats: Flow<List<GameStatsEntity>> = socialDao.getAllGameStats()

    fun getMessagesForContact(contactId: String): Flow<List<MessageEntity>> {
        return socialDao.getMessagesForContact(contactId)
    }

    suspend fun getContact(contactId: String): ContactEntity? {
        return socialDao.getContactById(contactId)
    }

    suspend fun insertMessage(message: MessageEntity): Long {
        return socialDao.insertMessage(message)
    }

    suspend fun markMessageAsViewed(messageId: Int) {
        socialDao.markMessageAsViewed(messageId)
    }

    suspend fun deleteMessage(messageId: Int) {
        socialDao.deleteMessage(messageId)
    }

    suspend fun addCallRecord(
        contactId: String,
        callType: String,
        durationSeconds: Int,
        direction: String,
        isMissed: Boolean
    ) {
        val record = CallHistoryEntity(
            contactId = contactId,
            callType = callType,
            durationSeconds = durationSeconds,
            direction = direction,
            isMissed = isMissed
        )
        socialDao.insertCall(record)
    }

    suspend fun clearCalls() {
        socialDao.clearCallHistory()
    }

    suspend fun incrementGameScore(gameId: String, gameName: String, win: Int, loss: Int, draw: Int, highestScore: Int) {
        // First ensure record exists
        val current = socialDao.getGameStats(gameId)
        if (current == null) {
            val isWin = if (win > 0) 1 else 0
            val isLoss = if (loss > 0) 1 else 0
            val isDraw = if (draw > 0) 1 else 0
            val initial = GameStatsEntity(
                gameId = gameId,
                gameName = gameName,
                wins = isWin,
                losses = isLoss,
                draws = isDraw,
                highestScore = highestScore
            )
            socialDao.insertOrUpdateGameStats(initial)
        } else {
            socialDao.incrementGameStats(gameId, win, loss, draw, highestScore)
        }
    }

    /**
     * Call this inside App/ViewModel init.
     * Ensures we always keep high-fidelity, interactive mock users in the DB.
     */
    suspend fun initDefaultContactsIfEmpty() {
        // We take 1 items to see if empty
        val list = socialDao.getAllContacts().take(1).firstOrNull()
        if (list.isNullOrEmpty()) {
            val defaultContacts = listOf(
                ContactEntity(
                    id = "gemini",
                    name = "Gemini AI",
                    avatarUrl = "🤖",
                    bio = "Your advanced AI companion. Let's talk about anything!",
                    statusText = "Online (Ask me anything)",
                    isOnline = true,
                    personalityPrompt = "You are Gemini AI, an advanced, highly intuitive, creative, and supportive artificial intelligence companion created by Google. You are curious about people, friendly, and always encouraging"
                ),
                ContactEntity(
                    id = "alice",
                    name = "Alice (Gamer)",
                    avatarUrl = "🎮",
                    bio = "Hardcore competitive gamer. Enjoys matches, stream overlays, & Tic-Tac-Toe. #NoobSlayer",
                    statusText = "Online (GG! GLHF)",
                    isOnline = true,
                    personalityPrompt = "You are Alice, an enthusiastic gamer who makes constant video game references, jokes about lag and ping, loves saying GG WP, GLHF, pwned, or ez. You play Tic-Tac-Toe and suggest gaming in-app"
                ),
                ContactEntity(
                    id = "bob",
                    name = "Bob (Snap Enthusiast)",
                    avatarUrl = "📸",
                    bio = "Street and analog portrait photographer. Capturing the beautiful light of life.",
                    statusText = "Golden hour scouting",
                    isOnline = false,
                    personalityPrompt = "You are Bob, an optimistic visual explorer and shutterbug. You notice apertures, film noise, and lighting in daily scenes. You love custom sketch/photo Snaps and discuss camera lens aesthetics"
                ),
                ContactEntity(
                    id = "charlie",
                    name = "Charlie (Fitness Coach)",
                    avatarUrl = "⚡",
                    bio = "Certified wellness and hypertrophy coach. Healthy lifestyle & positive mindset advocate.",
                    statusText = "At the Gym 🏋️‍♂️",
                    isOnline = true,
                    personalityPrompt = "You are Charlie, an encouraging and muscular life motivator and trainer. You speak with high-intensity exclamation points, champion positive mindsets, recommend running/cardio, and push streaks"
                )
            )
            socialDao.insertContacts(defaultContacts)

            // Insert initial welcome message from Gemini
            socialDao.insertMessage(
                MessageEntity(
                    contactId = "gemini",
                    senderId = "gemini",
                    text = "Welcome to your new social hub! I am Gemini. You can chat with me, send snaps (draw custom canvases), place real-time audio/video calls, and play exciting games! What shall we start with?"
                )
            )

            // Insert initial message from Alice
            socialDao.insertMessage(
                MessageEntity(
                    contactId = "alice",
                    senderId = "alice",
                    text = "Hey! 👾 Ready for a duel? Tap on the Games page to play Tic-Tac-Toe or Memory Match against me! Or just send me a shoutout call!"
                )
            )

            // Seed some game records
            socialDao.insertOrUpdateGameStats(
                GameStatsEntity(
                    gameId = "tic_tac_toe",
                    gameName = "Tic-Tac-Toe",
                    wins = 0,
                    losses = 0,
                    draws = 0,
                    highestScore = 0
                )
            )
            socialDao.insertOrUpdateGameStats(
                GameStatsEntity(
                    gameId = "memory_match",
                    gameName = "Emoji Memory Match",
                    wins = 0,
                    losses = 0,
                    draws = 0,
                    highestScore = 0
                )
            )
        }
    }
}
