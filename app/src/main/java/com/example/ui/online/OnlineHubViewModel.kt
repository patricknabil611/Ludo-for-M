package com.example.ui.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.engine.BotDifficulty
import com.example.engine.GameModeId
import com.example.engine.MatchFormat
import com.example.engine.TurnTimerOption
import com.example.online.OnlineRoom
import com.example.online.OnlineRoomRepository
import com.example.online.RoomStatus
import com.example.online.friendlyNetworkErrorMessage
import com.example.online.unwrapFirestoreError
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnlineHubUiState(
    val activeRooms: List<OnlineRoom> = emptyList(),
    val currentLobbyRoom: OnlineRoom? = null,
    val isBusy: Boolean = false,
    val isSearchingMatchmaking: Boolean = false,
    val matchmakingElapsedSeconds: Int = 0,
    val offerMatchmakingBotFill: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        const val MATCHMAKING_BOT_PROMPT_SECONDS = 30
    }
}

/**
 * Auth-scoped ViewModel (keyed by uid) managing private room creation/joining,
 * public matchmaking with 30-second bot-fill prompt, lobby seat controls, and active match rejoin.
 */
class OnlineHubViewModel(
    private val uid: String,
    private val roomRepo: OnlineRoomRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineHubUiState())
    val uiState: StateFlow<OnlineHubUiState> = _uiState.asStateFlow()

    private var lobbyObserverJob: Job? = null
    private var matchmakingTimerJob: Job? = null
    private var observedRoomCode: String? = null

    init {
        viewModelScope.launch {
            roomRepo.observeMyActiveRooms(uid)
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (error is CancellationException) {
                        throw error
                    } else if (root is FirebaseFirestoreException) {
                        _uiState.update {
                            it.copy(
                                errorMessage = friendlyNetworkErrorMessage(
                                    error,
                                    "Unable to load active rooms. Check your internet connection."
                                )
                            )
                        }
                    }
                }
                .collect { rooms ->
                    _uiState.update { it.copy(activeRooms = rooms) }
                }
        }
    }

    fun createPrivateRoom(
        nickname: String,
        mode: GameModeId,
        matchFormat: MatchFormat = MatchFormat.ONE_VS_ONE,
        timedDurationSeconds: Int = 300,
        turnTimerSeconds: Int = TurnTimerOption.DEFAULT_SECONDS,
        avatarId: String = "",
        onRoomCreated: (String) -> Unit
    ) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = roomRepo.createPrivateRoom(
                hostUid = uid,
                hostNickname = nickname,
                mode = mode,
                matchFormat = matchFormat,
                timedDurationSeconds = timedDurationSeconds,
                turnTimerSeconds = turnTimerSeconds,
                hostAvatarId = avatarId
            )
            result.onSuccess { code ->
                observeLobby(code, isMatchmaking = false)
                _uiState.update { it.copy(isBusy = false) }
                onRoomCreated(code)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = friendlyNetworkErrorMessage(err, "Could not create room.")
                    )
                }
            }
        }
    }

    fun joinRoomByCode(
        roomCode: String,
        nickname: String,
        avatarId: String = "",
        onJoined: (OnlineRoom) -> Unit
    ) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = roomRepo.joinRoomByCode(
                roomCode = roomCode,
                uid = uid,
                nickname = nickname,
                autoStartWhenFull = false,
                avatarId = avatarId
            )
            result.onSuccess { room ->
                if (room.status == RoomStatus.LOBBY) {
                    observeLobby(room.roomCode, isMatchmaking = false)
                }
                _uiState.update { it.copy(isBusy = false) }
                onJoined(room)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = friendlyNetworkErrorMessage(err, "Unable to join room.")
                    )
                }
            }
        }
    }

    fun startMatchmaking(
        nickname: String,
        mode: GameModeId,
        matchFormat: MatchFormat = MatchFormat.ONE_VS_ONE,
        timedDurationSeconds: Int = 300,
        turnTimerSeconds: Int = TurnTimerOption.DEFAULT_SECONDS,
        avatarId: String = ""
    ) {
        if (_uiState.value.isBusy || _uiState.value.isSearchingMatchmaking) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    isSearchingMatchmaking = true,
                    matchmakingElapsedSeconds = 0,
                    offerMatchmakingBotFill = false,
                    errorMessage = null
                )
            }
            val result = roomRepo.findOrCreateMatchmakingRoom(
                uid = uid,
                nickname = nickname,
                mode = mode,
                matchFormat = matchFormat,
                timedDurationSeconds = timedDurationSeconds,
                turnTimerSeconds = turnTimerSeconds,
                avatarId = avatarId
            )
            result.onSuccess { code ->
                _uiState.update { it.copy(isBusy = false) }
                observeLobby(code, isMatchmaking = true)
                startMatchmakingTimer()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        isSearchingMatchmaking = false,
                        errorMessage = friendlyNetworkErrorMessage(err, "Matchmaking failed.")
                    )
                }
            }
        }
    }

    fun cancelMatchmaking() {
        val code = observedRoomCode
        stopMatchmakingTimer()
        lobbyObserverJob?.cancel()
        lobbyObserverJob = null
        observedRoomCode = null
        _uiState.update {
            it.copy(
                isSearchingMatchmaking = false,
                matchmakingElapsedSeconds = 0,
                offerMatchmakingBotFill = false,
                currentLobbyRoom = null
            )
        }
        if (code != null) {
            viewModelScope.launch {
                roomRepo.leaveRoom(code, uid)
            }
        }
    }

    fun fillMatchmakingWithBotsAndStart() {
        val code = _uiState.value.currentLobbyRoom?.roomCode ?: observedRoomCode ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = roomRepo.startLobbyMatch(
                roomCode = code,
                actorUid = uid,
                fillOpenSeatsWithBots = true,
                defaultBotDifficulty = BotDifficulty.MEDIUM
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = result.exceptionOrNull()?.let { err ->
                        friendlyNetworkErrorMessage(err, "Could not start match.")
                    }
                )
            }
        }
    }

    fun observeLobby(roomCode: String, isMatchmaking: Boolean = false) {
        val clean = roomCode.trim().uppercase()
        if (observedRoomCode == clean && lobbyObserverJob?.isActive == true) return
        observedRoomCode = clean
        lobbyObserverJob?.cancel()
        lobbyObserverJob = viewModelScope.launch {
            roomRepo.observeRoom(clean)
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (error is CancellationException) {
                        throw error
                    } else if (root is FirebaseFirestoreException) {
                        _uiState.update {
                            it.copy(
                                isSearchingMatchmaking = false,
                                errorMessage = friendlyNetworkErrorMessage(error, "Lost connection to lobby.")
                            )
                        }
                    }
                }
                .collect { room ->
                    if (room == null || room.status == RoomStatus.CLOSED) {
                        stopMatchmakingTimer()
                        _uiState.update {
                            it.copy(
                                currentLobbyRoom = room,
                                isSearchingMatchmaking = false,
                                offerMatchmakingBotFill = false
                            )
                        }
                    } else {
                        if (room.status == RoomStatus.PLAYING) {
                            stopMatchmakingTimer()
                        }
                        _uiState.update {
                            it.copy(
                                currentLobbyRoom = room,
                                isSearchingMatchmaking = isMatchmaking && room.status == RoomStatus.LOBBY
                            )
                        }
                    }
                }
        }
    }

    fun setLobbySeatBot(seatIndex: Int, difficulty: BotDifficulty?) {
        val room = _uiState.value.currentLobbyRoom ?: return
        viewModelScope.launch {
            val res = roomRepo.setLobbySeatBot(room.roomCode, uid, seatIndex, difficulty)
            if (res.isFailure) {
                _uiState.update {
                    it.copy(
                        errorMessage = res.exceptionOrNull()?.let { err ->
                            friendlyNetworkErrorMessage(err, "Could not update seat.")
                        }
                    )
                }
            }
        }
    }

    fun startLobbyMatch(fillOpenWithBots: Boolean = true) {
        val room = _uiState.value.currentLobbyRoom ?: return
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val res = roomRepo.startLobbyMatch(
                roomCode = room.roomCode,
                actorUid = uid,
                fillOpenSeatsWithBots = fillOpenWithBots
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = res.exceptionOrNull()?.let { err ->
                        friendlyNetworkErrorMessage(err, "Could not start match.")
                    }
                )
            }
        }
    }

    fun leaveLobby(onDone: () -> Unit = {}) {
        val code = _uiState.value.currentLobbyRoom?.roomCode ?: observedRoomCode
        stopMatchmakingTimer()
        lobbyObserverJob?.cancel()
        lobbyObserverJob = null
        observedRoomCode = null
        _uiState.update {
            it.copy(
                currentLobbyRoom = null,
                isSearchingMatchmaking = false,
                offerMatchmakingBotFill = false
            )
        }
        viewModelScope.launch {
            if (code != null) {
                roomRepo.leaveRoom(code, uid)
            }
            onDone()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun startMatchmakingTimer() {
        stopMatchmakingTimer()
        matchmakingTimerJob = viewModelScope.launch {
            var elapsed = 0
            while (_uiState.value.isSearchingMatchmaking) {
                delay(1000L)
                elapsed += 1
                _uiState.update {
                    it.copy(
                        matchmakingElapsedSeconds = elapsed,
                        offerMatchmakingBotFill = elapsed >= OnlineHubUiState.MATCHMAKING_BOT_PROMPT_SECONDS
                    )
                }
            }
        }
    }

    private fun stopMatchmakingTimer() {
        matchmakingTimerJob?.cancel()
        matchmakingTimerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopMatchmakingTimer()
        lobbyObserverJob?.cancel()
    }

    class Factory(
        private val uid: String,
        private val roomRepo: OnlineRoomRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnlineHubViewModel(uid, roomRepo) as T
        }
    }
}
