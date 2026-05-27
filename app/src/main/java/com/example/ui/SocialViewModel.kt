package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface CallState {
    object Idle : CallState
    data class Active(
        val contactId: String,
        val contactName: String,
        val contactAvatar: String,
        val callType: String, // "AUDIO" or "VIDEO"
        val isMuted: Boolean = false,
        val isSpeakerOn: Boolean = false,
        val isCameraOff: Boolean = false,
        val durationSeconds: Int = 0
    ) : CallState
}

// Memory Match layout
data class MemoryCard(
    val id: Int,
    val emoji: String,
    val isFlipped: Boolean = false,
    val isMatched: Boolean = false
)

data class MemoryMatchState(
    val cards: List<MemoryCard> = emptyList(),
    val moves: Int = 0,
    val secondsElapsed: Int = 0,
    val isGameActive: Boolean = false,
    val isCompleted: Boolean = false
)

// Tic-Tac-Toe state
data class TicTacToeState(
    val opponentId: String = "alice",
    val board: List<String> = List(9) { "" }, // "", "X", "O"
    val isUserTurn: Boolean = true,
    val winner: String? = null, // "USER", "OPPONENT", "DRAW", null
    val winningLine: List<Int>? = null,
    val isThinking: Boolean = false
)

class SocialViewModel(private val repository: DataRepository) : ViewModel() {

    // --- Core Database Streams ---
    val contacts: StateFlow<List<ContactEntity>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val snaps: StateFlow<List<MessageEntity>> = repository.allSnaps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callLogs: StateFlow<List<CallHistoryEntity>> = repository.callHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val gameStats: StateFlow<List<GameStatsEntity>> = repository.allGameStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Transient UI States ---
    private val _activeCallState = MutableStateFlow<CallState>(CallState.Idle)
    val activeCallState: StateFlow<CallState> = _activeCallState.asStateFlow()

    private val _ticTacToe = MutableStateFlow(TicTacToeState())
    val ticTacToe: StateFlow<TicTacToeState> = _ticTacToe.asStateFlow()

    private val _memoryMatch = MutableStateFlow(MemoryMatchState())
    val memoryMatch: StateFlow<MemoryMatchState> = _memoryMatch.asStateFlow()

    // Active Chat Session state
    private val _activeChatContactId = MutableStateFlow<String?>(null)
    val activeChatContactId: StateFlow<String?> = _activeChatContactId.asStateFlow()

    val activeChatMessages: StateFlow<List<MessageEntity>> = _activeChatContactId
        .flatMapLatest { id ->
            if (id != null) repository.getMessagesForContact(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Ongoing call timer Job
    private var callTimerJob: Job? = null
    // Ongoing memory match timer Job
    private var memoryTimerJob: Job? = null

    init {
        viewModelScope.launch {
            repository.initDefaultContactsIfEmpty()
        }
    }

    // --- Chat Actions ---
    fun selectContactForChat(contactId: String?) {
        _activeChatContactId.value = contactId
    }

    fun sendMessage(text: String, snapUri: String? = null, snapDuration: Int? = null) {
        val contactId = _activeChatContactId.value ?: return
        if (text.isBlank() && snapUri == null) return

        viewModelScope.launch {
            // 1. Insert user message in Room
            val msg = MessageEntity(
                contactId = contactId,
                senderId = "user",
                text = text,
                snapUri = snapUri,
                snapDuration = snapDuration,
                isViewed = false
            )
            repository.insertMessage(msg)

            // 2. Trigger peer response if is a simulated contact
            triggerContactResponse(contactId, text)
        }
    }

    private fun triggerContactResponse(contactId: String, userText: String) {
        viewModelScope.launch {
            // Let user feel the contact is typing
            delay(1000)

            val contact = repository.getContact(contactId) ?: return@launch
            val messages = activeChatMessages.value

            val history = messages
                .filter { it.snapUri == null } // ignore snaps in text context
                .map { Pair(it.senderId, it.text) }

            // Call high-fidelity Gemini REST API or simulated bot
            val replyText = GeminiClient.generateChatReply(
                contactName = contact.name,
                personalityPrompt = contact.personalityPrompt,
                history = history,
                userPrompt = userText
            )

            val replyMsg = MessageEntity(
                contactId = contactId,
                senderId = contactId,
                text = replyText
            )
            repository.insertMessage(replyMsg)
        }
    }

    fun viewSnap(messageId: Int) {
        viewModelScope.launch {
            repository.markMessageAsViewed(messageId)
        }
    }

    fun deleteMessage(messageId: Int) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    // --- Calling Actions ---
    fun startCall(contactId: String, callType: String) {
        viewModelScope.launch {
            val contact = repository.getContact(contactId) ?: return@launch
            _activeCallState.value = CallState.Active(
                contactId = contactId,
                contactName = contact.name,
                contactAvatar = contact.avatarUrl,
                callType = callType,
                durationSeconds = 0
            )

            // Start call ticking
            callTimerJob?.cancel()
            callTimerJob = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    val current = _activeCallState.value
                    if (current is CallState.Active) {
                        _activeCallState.value = current.copy(
                            durationSeconds = current.durationSeconds + 1
                        )
                    } else {
                        break
                    }
                }
            }
        }
    }

    fun toggleCallMute() {
        val current = _activeCallState.value
        if (current is CallState.Active) {
            _activeCallState.value = current.copy(isMuted = !current.isMuted)
        }
    }

    fun toggleCallSpeaker() {
        val current = _activeCallState.value
        if (current is CallState.Active) {
            _activeCallState.value = current.copy(isSpeakerOn = !current.isSpeakerOn)
        }
    }

    fun toggleCallCamera() {
        val current = _activeCallState.value
        if (current is CallState.Active) {
            _activeCallState.value = current.copy(isCameraOff = !current.isCameraOff)
        }
    }

    fun endCall() {
        val current = _activeCallState.value
        if (current is CallState.Active) {
            val duration = current.durationSeconds
            val cid = current.contactId
            val type = current.callType

            _activeCallState.value = CallState.Idle
            callTimerJob?.cancel()

            // Save in logging
            viewModelScope.launch {
                repository.addCallRecord(
                    contactId = cid,
                    callType = type,
                    durationSeconds = duration,
                    direction = "OUTGOING",
                    isMissed = false
                )
            }
        }
    }

    fun clearAllCallHistory() {
        viewModelScope.launch {
            repository.clearCalls()
        }
    }

    // --- Tic-Tac-Toe Actions ---
    fun setTicTacToeOpponent(contactId: String) {
        viewModelScope.launch {
            val contact = repository.getContact(contactId)
            val name = contact?.name ?: "Opponent"
            _ticTacToe.value = TicTacToeState(
                opponentId = contactId,
                board = List(9) { "" },
                isUserTurn = true,
                winner = null,
                winningLine = null,
                isThinking = false
            )
        }
    }

    fun resetTicTacToe() {
        val cur = _ticTacToe.value
        _ticTacToe.value = cur.copy(
            board = List(9) { "" },
            isUserTurn = true,
            winner = null,
            winningLine = null,
            isThinking = false
        )
    }

    fun makeTicTacToeMove(index: Int) {
        val state = _ticTacToe.value
        if (state.board[index].isNotEmpty() || !state.isUserTurn || state.winner != null || state.isThinking) return

        // 1. Mark User move
        val nextBoard = state.board.toMutableList()
        nextBoard[index] = "X"

        val winCheck = checkTicTacToeWinner(nextBoard)
        if (winCheck != null) {
            val finalWinner = winCheck.first
            val line = winCheck.second
            _ticTacToe.value = state.copy(
                board = nextBoard,
                winner = finalWinner,
                winningLine = line,
                isUserTurn = false
            )
            saveTicTacToeResult(finalWinner)
            return
        }

        // 2. Play Bot Turn with delay
        _ticTacToe.value = state.copy(
            board = nextBoard,
            isUserTurn = false,
            isThinking = true
        )

        viewModelScope.launch {
            delay(1000) // think duration
            val finalState = _ticTacToe.value
            val currentBoard = finalState.board.toMutableList()

            // Bot algorithm (Pre-programmed Smart selection)
            val botMove = getSmartTicTacToeMove(currentBoard)
            if (botMove != -1) {
                currentBoard[botMove] = "O"
            }

            val nextWinCheck = checkTicTacToeWinner(currentBoard)
            if (nextWinCheck != null) {
                _ticTacToe.value = finalState.copy(
                    board = currentBoard,
                    winner = nextWinCheck.first,
                    winningLine = nextWinCheck.second,
                    isUserTurn = false,
                    isThinking = false
                )
                saveTicTacToeResult(nextWinCheck.first)
            } else {
                _ticTacToe.value = finalState.copy(
                    board = currentBoard,
                    isUserTurn = true,
                    isThinking = false
                )
            }
        }
    }

    private fun getSmartTicTacToeMove(board: List<String>): Int {
        // 1. Can bot win in next move?
        for (i in 0..8) {
            if (board[i].isEmpty()) {
                val test = board.toMutableList()
                test[i] = "O"
                if (checkTicTacToeWinner(test)?.first == "OPPONENT") return i
            }
        }
        // 2. Can bot block user win?
        for (i in 0..8) {
            if (board[i].isEmpty()) {
                val test = board.toMutableList()
                test[i] = "X"
                if (checkTicTacToeWinner(test)?.first == "USER") return i
            }
        }
        // 3. Take center if empty
        if (board[4].isEmpty()) return 4
        // 4. Take corners
        val corners = listOf(0, 2, 6, 8)
        val emptyCorners = corners.filter { board[it].isEmpty() }
        if (emptyCorners.isNotEmpty()) return emptyCorners.random()
        // 5. Take sides
        val sides = listOf(1, 3, 5, 7)
        val emptySides = sides.filter { board[it].isEmpty() }
        if (emptySides.isNotEmpty()) return emptySides.random()

        return -1
    }

    private fun checkTicTacToeWinner(board: List<String>): Pair<String, List<Int>>? {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // rows
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // cols
            listOf(0, 4, 8), listOf(2, 4, 6)                  // diag
        )
        for (line in lines) {
            if (board[line[0]].isNotEmpty() && board[line[0]] == board[line[1]] && board[line[0]] == board[line[2]]) {
                val winnerLabel = if (board[line[0]] == "X") "USER" else "OPPONENT"
                return Pair(winnerLabel, line)
            }
        }
        if (board.none { it.isEmpty() }) {
            return Pair("DRAW", emptyList())
        }
        return null
    }

    private fun saveTicTacToeResult(winner: String) {
        viewModelScope.launch {
            val win = if (winner == "USER") 1 else 0
            val loss = if (winner == "OPPONENT") 1 else 0
            val draw = if (winner == "DRAW") 1 else 0

            repository.incrementGameScore(
                gameId = "tic_tac_toe",
                gameName = "Tic-Tac-Toe",
                win = win,
                loss = loss,
                draw = draw,
                highestScore = if (winner == "USER") 100 else 0
            )

            // Auto send chat reaction if game was initiated from chat
            val opponent = _ticTacToe.value.opponentId
            val reaction = when (winner) {
                "USER" -> {
                    if (opponent == "alice") "Ouch! You crushed me! GG wp! 😂 Rocket speed reflexes!"
                    else "Impressive victory! You outsmarted my algorithm."
                }
                "OPPONENT" -> {
                    if (opponent == "alice") "GG! EZ win! Pwned you! Better download some skill next time! 🎮😎"
                    else "Calculation completed. Winner: ME. Better luck next time!"
                }
                else -> "Wow! Balanced block! GG!"
            }
            delay(1500)
            repository.insertMessage(
                MessageEntity(
                    contactId = opponent,
                    senderId = opponent,
                    text = reaction
                )
            )
        }
    }

    // --- Emoji Memory Match Actions ---
    fun startMemoryMatchGame() {
        val emojis = listOf("📸", "🤖", "🎮", "⚡", "❤️", "📱", "✨", "🎉")
        // Pair and shuffle
        val cards = (emojis + emojis)
            .mapIndexed { idx, emoji ->
                MemoryCard(id = idx, emoji = emoji)
            }
            .shuffled()

        _memoryMatch.value = MemoryMatchState(
            cards = cards,
            moves = 0,
            secondsElapsed = 0,
            isGameActive = true,
            isCompleted = false
        )

        // Reset timer
        memoryTimerJob?.cancel()
        memoryTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val curState = _memoryMatch.value
                if (curState.isGameActive && !curState.isCompleted) {
                    _memoryMatch.value = curState.copy(
                        secondsElapsed = curState.secondsElapsed + 1
                    )
                } else {
                    break
                }
            }
        }
    }

    fun flipMemoryCard(cardId: Int) {
        val state = _memoryMatch.value
        val cards = state.cards
        val flippedCards = cards.filter { it.isFlipped && !it.isMatched }

        // Block if 2 cards already flipped
        if (flippedCards.size >= 2) return

        val targetCard = cards.firstOrNull { it.id == cardId } ?: return
        if (targetCard.isFlipped || targetCard.isMatched) return

        // Flip the card
        val updatedCards = cards.map {
            if (it.id == cardId) it.copy(isFlipped = true) else it
        }

        val nowFlipped = updatedCards.filter { it.isFlipped && !it.isMatched }
        _memoryMatch.value = state.copy(cards = updatedCards)

        if (nowFlipped.size == 2) {
            val card1 = nowFlipped[0]
            val card2 = nowFlipped[1]

            // Register a move
            val nextMoves = state.moves + 1

            _memoryMatch.value = _memoryMatch.value.copy(moves = nextMoves)

            viewModelScope.launch {
                delay(1000) // Keep cards visible for a second

                val currentCards = _memoryMatch.value.cards
                if (card1.emoji == card2.emoji) {
                    // Match!
                    val finalCards = currentCards.map {
                        if (it.emoji == card1.emoji) it.copy(isMatched = true, isFlipped = false) else it
                    }
                    val isCompleted = finalCards.all { it.isMatched }
                    _memoryMatch.value = _memoryMatch.value.copy(
                        cards = finalCards,
                        isCompleted = isCompleted
                    )

                    if (isCompleted) {
                        saveMemoryMatchResult(nextMoves, _memoryMatch.value.secondsElapsed)
                    }
                } else {
                    // Unflip
                    val finalCards = currentCards.map {
                        if (it.id == card1.id || it.id == card2.id) it.copy(isFlipped = false) else it
                    }
                    _memoryMatch.value = _memoryMatch.value.copy(cards = finalCards)
                }
            }
        }
    }

    private fun saveMemoryMatchResult(moves: Int, seconds: Int) {
        viewModelScope.launch {
            memoryTimerJob?.cancel()

            // Calculate score (e.g., higher is better, baseline 1000 - moves * 10 - seconds)
            val score = (1000 - (moves * 12) - seconds).coerceAtLeast(10)

            repository.incrementGameScore(
                gameId = "memory_match",
                gameName = "Emoji Memory Match",
                win = 1,
                loss = 0,
                draw = 0,
                highestScore = score
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        callTimerJob?.cancel()
        memoryTimerJob?.cancel()
    }
}

class SocialViewModelFactory(private val repository: DataRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SocialViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SocialViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
