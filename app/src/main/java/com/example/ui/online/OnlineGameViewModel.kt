package com.example.ui.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.LudoHapticsManager
import com.example.audio.SoundManager
import com.example.bot.BotPowerUpAction
import com.example.data.MatchHistoryRepository
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.LudoBoardGeometry
import com.example.engine.LudoEngine
import com.example.engine.PlayerColor
import com.example.engine.PlayerSetup
import com.example.engine.PowerUpType
import com.example.engine.RuleSetRegistry
import com.example.engine.TokenRef
import com.example.engine.TurnPhase
import com.example.online.OnlineActionType
import com.example.online.OnlineProfileAndLeaderboardRepository
import com.example.online.OnlineRoom
import com.example.online.OnlineRoomRepository
import com.example.online.RoomStatus
import com.example.online.friendlyNetworkErrorMessage
import com.example.online.unwrapFirestoreError
import com.example.ui.game.CaptureBurstState
import com.example.ui.game.ChaosAnnouncementState
import com.example.ui.game.DiceCube3DEngine
import com.example.ui.game.GameUiState
import com.example.ui.game.PowerUpBannerState
import com.example.ui.game.PowerUpTargetingState
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

data class OnlineRematchUi(
    val roomCode: String,
    val votedCount: Int,
    val totalRemainingHumans: Int,
    val myVoted: Boolean,
    val isSubmitting: Boolean
)

data class OnlineGameWrapperState(
    val room: OnlineRoom? = null,
    val gameUiState: GameUiState = GameUiState(
        gameState = LudoEngine().createGame(
            listOf(
                PlayerSetup(0, "Player 1", PlayerColor.RED),
                PlayerSetup(1, "Player 2", PlayerColor.YELLOW)
            )
        )
    ),
    val localColor: PlayerColor? = null,
    val isLocalPlayersTurn: Boolean = false,
    val isRoomClosed: Boolean = false,
    val isSubmittingAction: Boolean = false,
    val errorMessage: String? = null,
    val rematchUi: OnlineRematchUi? = null
)

/**
 * Auth-scoped ViewModel (keyed by uid + roomCode) driving synchronized online Ludo gameplay.
 * Firestore `/rooms/{roomCode}` is the single source of truth; all clients replay matching
 * dice/hop/power-up/capture animations whenever `turnSeq` advances.
 */
class OnlineGameViewModel(
    private val uid: String,
    private val roomCode: String,
    private val myNickname: String,
    private val roomRepo: OnlineRoomRepository,
    private val profileRepo: OnlineProfileAndLeaderboardRepository,
    private val soundManager: SoundManager,
    private val hapticsManager: LudoHapticsManager,
    private val matchHistoryRepo: MatchHistoryRepository? = null
) : ViewModel() {

    private val _state = MutableStateFlow(OnlineGameWrapperState())
    val state: StateFlow<OnlineGameWrapperState> = _state.asStateFlow()

    private var lastObservedTurnSeq: Int = -1
    private var lastObservedRollSeq: Int = -1
    private var lastTimedStartMs: Long = 0L
    private var recordedFinishedTurnSeq: Int = -1
    private var lastTimeoutRequestedTurnSeq: Int = -1
    private var lastWarnedSecond: Int = -1
    private var powerUpBannerCounter: Int = 0

    private var animationJob: Job? = null
    private var botCoordinatorJob: Job? = null
    private var autoSingleMoveJob: Job? = null
    private var bannerJob: Job? = null
    private var powerUpBannerJob: Job? = null
    private var timerLoopJob: Job? = null
    private var heartbeatJob: Job? = null

    init {
        startRoomListener()
        startTurnAndMatchTimerLoop()
        startHeartbeatLoop()
    }

    private fun startRoomListener() {
        viewModelScope.launch {
            roomRepo.observeRoom(roomCode)
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (error is CancellationException) {
                        throw error
                    } else if (root is FirebaseFirestoreException) {
                        _state.update {
                            it.copy(
                                errorMessage = friendlyNetworkErrorMessage(
                                    error,
                                    "Connection interrupted. Reconnecting..."
                                )
                            )
                        }
                    }
                }
                .collect { room ->
                    if (room == null || room.status == RoomStatus.CLOSED) {
                        _state.update { it.copy(room = room, isRoomClosed = true) }
                        return@collect
                    }
                    onRoomUpdated(room)
                }
        }
    }

    private fun onRoomUpdated(room: OnlineRoom) {
        val prevRoom = _state.value.room
        val newGameState = room.gameState ?: return
        val localSeat = room.seatForUid(uid)
        val localColor = localSeat?.color
        val activeColor = newGameState.activePlayer.color
        val activeSeat = room.seatForColor(activeColor)
        val isLocalTurn = room.status == RoomStatus.PLAYING &&
            localSeat != null &&
            localSeat.color == activeColor &&
            !localSeat.isEffectiveBot &&
            !newGameState.isMatchFinished

        val remainingHumans = room.seats.filter { it.uid in room.memberUids }
        val rematchUi = if (room.status == RoomStatus.FINISHED) {
            OnlineRematchUi(
                roomCode = room.roomCode,
                votedCount = remainingHumans.count { it.rematchVoted },
                totalRemainingHumans = remainingHumans.size.coerceAtLeast(1),
                myVoted = localSeat?.rematchVoted == true,
                isSubmitting = false
            )
        } else null

        if (room.status == RoomStatus.PLAYING && (prevRoom == null || prevRoom.status != RoomStatus.PLAYING)) {
            lastTimedStartMs = room.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
            recordedFinishedTurnSeq = -1
        }

        val turnAdvanced = room.turnSeq != lastObservedTurnSeq
        val rollAdvanced = room.rollSeq != lastObservedRollSeq
        val prevState = prevRoom?.gameState

        _state.update { current ->
            val nextGameUi = current.gameUiState.copy(
                gameState = newGameState,
                powerUpTargeting = if (isLocalTurn && newGameState.phase == TurnPhase.AWAITING_ROLL) {
                    current.gameUiState.powerUpTargeting
                } else null,
                displayedDiceValue = newGameState.lastRawDiceValue
                    ?: newGameState.lastDiceValue
                    ?: current.gameUiState.displayedDiceValue,
                displayedSecondDiceValue = if (newGameState.activePlayer.doubleDiceActive) {
                    newGameState.lastSecondDiceValue ?: 6
                } else {
                    newGameState.lastSecondDiceValue
                }
            )
            current.copy(
                room = room,
                gameUiState = nextGameUi,
                localColor = localColor,
                isLocalPlayersTurn = isLocalTurn,
                isRoomClosed = false,
                rematchUi = rematchUi
            )
        }

        if (turnAdvanced) {
            lastObservedTurnSeq = room.turnSeq
            lastWarnedSecond = -1
            triggerSyncedAnimations(room, prevState, newGameState, rollAdvanced)
        }
        if (rollAdvanced) {
            lastObservedRollSeq = room.rollSeq
        }

        // Record online leaderboard stats exactly once when the match completes
        if (room.status == RoomStatus.FINISHED && recordedFinishedTurnSeq != room.turnSeq) {
            recordedFinishedTurnSeq = room.turnSeq
            soundManager.playWin()
            hapticsManager.vibrateWin()
            recordStatsIfParticipant(newGameState, localColor)
        }

        // Schedule single-legal-move auto-play for local human (after synced dice tumble finishes)
        autoSingleMoveJob?.cancel()
        if (isLocalTurn &&
            newGameState.phase == TurnPhase.AWAITING_MOVE &&
            newGameState.legalMoves.size == 1
        ) {
            val soleMove = newGameState.legalMoves.first()
            val expectedSeq = room.turnSeq
            autoSingleMoveJob = viewModelScope.launch {
                delay(650L)
                val latest = _state.value.room ?: return@launch
                if (latest.turnSeq == expectedSeq && latest.status == RoomStatus.PLAYING) {
                    roomRepo.submitTokenMove(
                        roomCode = roomCode,
                        actorUid = uid,
                        expectedTurnSeq = expectedSeq,
                        tokenColor = soleMove.color,
                        tokenId = soleMove.tokenId,
                        asBotCoordinator = false
                    )
                }
            }
        }

        // Schedule deterministic Bot / Bot-Takeover coordinator step
        botCoordinatorJob?.cancel()
        if (room.status == RoomStatus.PLAYING &&
            !newGameState.isMatchFinished &&
            activeSeat?.isEffectiveBot == true &&
            room.isBotCoordinator(uid)
        ) {
            val expectedSeq = room.turnSeq
            botCoordinatorJob = viewModelScope.launch {
                delay(1250L)
                val latest = _state.value.room ?: return@launch
                if (latest.turnSeq == expectedSeq && latest.status == RoomStatus.PLAYING) {
                    roomRepo.executeBotStep(
                        roomCode = roomCode,
                        coordinatorUid = uid,
                        expectedTurnSeq = expectedSeq
                    )
                }
            }
        }
    }

    private fun triggerSyncedAnimations(
        room: OnlineRoom,
        prevState: GameState?,
        newState: GameState,
        rollAdvanced: Boolean
    ) {
        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            when (room.lastActionType) {
                OnlineActionType.ROLL -> {
                    if (rollAdvanced) {
                        val finalD1 = newState.lastRawDiceValue ?: newState.lastDiceValue ?: 6
                        val finalD2 = newState.lastSecondDiceValue
                        soundManager.playDiceRoll()
                        hapticsManager.vibrateDiceRoll()
                        _state.update {
                            it.copy(
                                gameUiState = it.gameUiState.copy(
                                    isDiceRolling = true,
                                    displayedDiceValue = finalD1,
                                    displayedSecondDiceValue = finalD2,
                                    diceRollTrigger = it.gameUiState.diceRollTrigger + 1
                                )
                            )
                        }
                        delay(DiceCube3DEngine.ROLL_DURATION_MS.toLong() + 30L)
                        _state.update {
                            it.copy(
                                gameUiState = it.gameUiState.copy(
                                    isDiceRolling = false,
                                    displayedDiceValue = finalD1,
                                    displayedSecondDiceValue = finalD2
                                )
                            )
                        }
                    }
                }

                OnlineActionType.MOVE -> {
                    val movedColor = room.lastMovedColor
                    val tokenId = room.lastMovedTokenId
                    val fromP = room.lastMovedFromProgress
                    val toP = room.lastMovedToProgress
                    if (movedColor != null && tokenId in 0..3 && fromP >= -1 && toP >= 0) {
                        animateSyncedHop(movedColor, tokenId, fromP, toP)
                    }
                    // Synced capture / home / bomb / powerup feedback
                    if (prevState != null) {
                        detectAndPlayMoveFeedback(prevState, newState, room)
                    }
                }

                OnlineActionType.SHIELD -> {
                    soundManager.playPowerUpActivate(PowerUpType.SHIELD)
                    hapticsManager.vibratePowerUpActivate(PowerUpType.SHIELD)
                    showPowerUpBanner(
                        type = PowerUpType.SHIELD,
                        eyebrow = "POWER-UP!",
                        title = "Shield Active!",
                        detail = room.lastActionSummary,
                        durationMs = 1350L
                    )
                }

                OnlineActionType.DOUBLE_DICE -> {
                    soundManager.playPowerUpActivate(PowerUpType.DOUBLE_DICE)
                    hapticsManager.vibratePowerUpActivate(PowerUpType.DOUBLE_DICE)
                    showPowerUpBanner(
                        type = PowerUpType.DOUBLE_DICE,
                        eyebrow = "POWER-UP!",
                        title = "Double Dice Ready!",
                        detail = room.lastActionSummary,
                        durationMs = 1350L
                    )
                }

                OnlineActionType.BOMB -> {
                    soundManager.playPowerUpActivate(PowerUpType.BOMB_TRAP)
                    hapticsManager.vibratePowerUpActivate(PowerUpType.BOMB_TRAP)
                    showPowerUpBanner(
                        type = PowerUpType.BOMB_TRAP,
                        eyebrow = "WATCH OUT!",
                        title = "Bomb Hidden!",
                        detail = "A hidden bomb trap was placed on the track!",
                        durationMs = 1350L
                    )
                }

                else -> Unit
            }

            // Chaos & Elimination synced banners
            if (prevState != null) {
                val newAnnId = newState.chaosState.announcementId
                if (newAnnId > prevState.chaosState.announcementId && newState.chaosState.activeAnnouncement != null) {
                    val ev = newState.chaosState.activeAnnouncement
                    soundManager.playTokenHome()
                    hapticsManager.vibrateCapture()
                    bannerJob?.cancel()
                    bannerJob = viewModelScope.launch {
                        _state.update {
                            it.copy(
                                gameUiState = it.gameUiState.copy(
                                    activeChaosBanner = ChaosAnnouncementState(
                                        event = ev,
                                        detail = newState.chaosState.announcementDetail,
                                        triggerKey = newAnnId
                                    )
                                )
                            )
                        }
                        delay(2400L)
                        _state.update { it.copy(gameUiState = it.gameUiState.copy(activeChaosBanner = null)) }
                    }
                } else if (newState.eliminatedOrder.size > prevState.eliminatedOrder.size) {
                    val elimColor = newState.eliminatedOrder.lastOrNull()
                    if (elimColor != null) {
                        soundManager.playCapture()
                        hapticsManager.vibrateCapture()
                        bannerJob?.cancel()
                        bannerJob = viewModelScope.launch {
                            _state.update {
                                it.copy(gameUiState = it.gameUiState.copy(eliminatedBannerColor = elimColor))
                            }
                            delay(2200L)
                            _state.update {
                                it.copy(gameUiState = it.gameUiState.copy(eliminatedBannerColor = null))
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun animateSyncedHop(
        color: PlayerColor,
        tokenId: Int,
        fromProgress: Int,
        toProgress: Int
    ) {
        val ref = TokenRef(color, tokenId)
        val steps = if (fromProgress < 0) {
            listOf(0)
        } else if (toProgress > fromProgress && (toProgress - fromProgress) <= 12) {
            (fromProgress + 1..toProgress).toList()
        } else {
            listOf(toProgress)
        }

        var prevP = fromProgress
        for (stepP in steps) {
            val (fromR, fromC) = LudoBoardGeometry.tokenBoardPosition(color, tokenId, prevP, false)
            val (toR, toC) = LudoBoardGeometry.tokenBoardPosition(color, tokenId, stepP, false)
            soundManager.playTokenMove()
            val subFrames = 4
            val stepMs = if (steps.size > 6) 80L else 110L
            for (f in 1..subFrames) {
                val t = f.toFloat() / subFrames
                val arc = kotlin.math.sin(t * Math.PI).toFloat()
                _state.update {
                    it.copy(
                        gameUiState = it.gameUiState.copy(
                            isAnimatingMove = true,
                            animatingToken = ref,
                            animatingRow = fromR + (toR - fromR) * t,
                            animatingCol = fromC + (toC - fromC) * t,
                            hopScale = 1f + arc * 0.32f
                        )
                    )
                }
                delay(stepMs / subFrames)
            }
            prevP = stepP
        }
        _state.update {
            it.copy(
                gameUiState = it.gameUiState.copy(
                    isAnimatingMove = false,
                    animatingToken = null,
                    hopScale = 1f
                )
            )
        }
    }

    private fun detectAndPlayMoveFeedback(prev: GameState, next: GameState, room: OnlineRoom) {
        val movedColor = room.lastMovedColor ?: return
        val tokenId = room.lastMovedTokenId
        val toP = room.lastMovedToProgress

        // Bomb detonation check: fewer bombs and moved token sent to base
        val movedTokenNow = next.playerByColor(movedColor)?.tokens?.firstOrNull { it.id == tokenId }
        if (next.powerUpState.bombs.size < prev.powerUpState.bombs.size &&
            movedTokenNow?.isInBase == true &&
            room.lastActionSummary.contains("bomb", ignoreCase = true)
        ) {
            soundManager.playBombExplosion()
            hapticsManager.vibrateBombExplosion()
            val (r, c) = LudoBoardGeometry.tokenBoardPosition(movedColor, tokenId, toP.coerceAtLeast(0), false)
            _state.update {
                it.copy(
                    gameUiState = it.gameUiState.copy(
                        bombExplosionBurst = CaptureBurstState(
                            color = movedColor,
                            row = r,
                            col = c,
                            triggerKey = (it.gameUiState.bombExplosionBurst?.triggerKey ?: 0) + 1
                        )
                    )
                )
            }
            showPowerUpBanner(
                type = PowerUpType.BOMB_TRAP,
                isExplosion = true,
                eyebrow = "KABOOM!",
                title = "Bomb Trap Triggered!",
                detail = room.lastActionSummary,
                durationMs = 1500L
            )
            return
        }

        // Token reached home
        if (toP == 56) {
            soundManager.playTokenHome()
            _state.update {
                it.copy(
                    gameUiState = it.gameUiState.copy(
                        homeConfettiTrigger = it.gameUiState.homeConfettiTrigger + 1
                    )
                )
            }
        }

        // Capture detection: any opponent token went from track to base
        val capturedPlayer = next.players.firstOrNull { np ->
            if (np.color == movedColor) return@firstOrNull false
            val op = prev.playerByColor(np.color) ?: return@firstOrNull false
            np.tokens.indices.any { idx ->
                !op.tokens[idx].isInBase && np.tokens[idx].isInBase
            }
        }
        if (capturedPlayer != null) {
            val (hitR, hitC) = LudoBoardGeometry.tokenBoardPosition(movedColor, tokenId, toP.coerceAtLeast(0), false)
            soundManager.playCapture()
            hapticsManager.vibrateCapture()
            _state.update {
                it.copy(
                    gameUiState = it.gameUiState.copy(
                        captureBurst = CaptureBurstState(
                            color = capturedPlayer.color,
                            row = hitR,
                            col = hitC,
                            triggerKey = (it.gameUiState.captureBurst?.triggerKey ?: 0) + 1
                        )
                    )
                )
            }
        }

        // Power-up tile pickup check
        val prevHeld = prev.playerByColor(movedColor)?.heldPowerUps.orEmpty()
        val nextHeld = next.playerByColor(movedColor)?.heldPowerUps.orEmpty()
        if (nextHeld.size > prevHeld.size) {
            val collected = nextHeld.last()
            soundManager.playPowerUpPickup(collected)
            hapticsManager.vibratePowerUpPickup(collected)
        }
    }

    private fun startTurnAndMatchTimerLoop() {
        timerLoopJob?.cancel()
        timerLoopJob = viewModelScope.launch {
            while (true) {
                delay(250L)
                val current = _state.value
                val room = current.room ?: continue
                if (room.status != RoomStatus.PLAYING) continue
                val gs = room.gameState ?: continue
                if (gs.isMatchFinished) continue

                val turnStartMs = room.turnStartedAt?.toDate()?.time ?: System.currentTimeMillis()
                val elapsedMs = (System.currentTimeMillis() - turnStartMs).coerceAtLeast(0L)
                val totalMs = GameUiState.TURN_DURATION_SECONDS * 1000L
                val remainingMs = (totalMs - elapsedMs).coerceAtLeast(0L)
                val remainingSec = ((remainingMs + 999L) / 1000L).toInt()
                    .coerceIn(0, GameUiState.TURN_DURATION_SECONDS)
                val progress = (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

                if (remainingSec in 1..5 && remainingSec != lastWarnedSecond) {
                    lastWarnedSecond = remainingSec
                    soundManager.playTimerWarning()
                }

                // Update Timed mode display clock
                val timedRemaining = if (room.mode == GameModeId.TIMED && lastTimedStartMs > 0L) {
                    val elapsedMatchSec = ((System.currentTimeMillis() - lastTimedStartMs) / 1000L).toInt()
                    (room.timedDurationSeconds - elapsedMatchSec).coerceAtLeast(0)
                } else {
                    gs.timedRemainingSeconds
                }

                _state.update {
                    it.copy(
                        gameUiState = it.gameUiState.copy(
                            remainingSeconds = remainingSec,
                            timerProgress = progress,
                            gameState = if (room.mode == GameModeId.TIMED) {
                                it.gameUiState.gameState.copy(timedRemainingSeconds = timedRemaining)
                            } else {
                                it.gameUiState.gameState
                            }
                        )
                    )
                }

                // Trigger Timed match expiry on coordinator
                if (room.mode == GameModeId.TIMED && timedRemaining <= 0 && room.isBotCoordinator(uid)) {
                    roomRepo.submitTimedClockExpired(roomCode, uid, room.turnSeq)
                }

                // Trigger synced 30s turn timeout (active player at 0s, or coordinator after 1.5s grace)
                val shouldSubmitTimeout = (remainingMs == 0L && current.isLocalPlayersTurn) ||
                    (elapsedMs >= totalMs + 1500L && room.isBotCoordinator(uid))

                if (shouldSubmitTimeout && lastTimeoutRequestedTurnSeq != room.turnSeq) {
                    lastTimeoutRequestedTurnSeq = room.turnSeq
                    viewModelScope.launch {
                        roomRepo.submitTurnTimeout(roomCode, uid, room.turnSeq)
                    }
                }
            }
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            // Immediate heartbeat on entry so reconnecting players reclaim their seat right away
            roomRepo.sendHeartbeatAndSyncPresence(roomCode, uid)
            while (true) {
                delay(15_000L)
                val room = _state.value.room
                if (room?.status == RoomStatus.PLAYING) {
                    roomRepo.sendHeartbeatAndSyncPresence(roomCode, uid)
                }
            }
        }
    }

    private fun recordStatsIfParticipant(finalState: GameState, localColor: PlayerColor?) {
        if (localColor == null) return
        val winnerColor = finalState.finishOrder.firstOrNull()
        val won = when {
            finalState.mode == GameModeId.TEAMS_2V2 && winnerColor != null ->
                localColor == winnerColor || localColor == winnerColor.opposite()
            else -> localColor == winnerColor
        }
        viewModelScope.launch {
            profileRepo.recordOnlineMatchResult(
                uid = uid,
                fallbackNickname = myNickname,
                won = won
            )
            matchHistoryRepo?.recordMatch(
                entry = MatchHistoryRepository.buildEntryFromGameState(
                    finalState = finalState,
                    onlineMatch = true,
                    roomCode = roomCode,
                    localColor = localColor
                ),
                authenticatedUid = uid
            )
        }
    }

    fun onRollDiceTapped() {
        val current = _state.value
        val room = current.room ?: return
        if (!current.isLocalPlayersTurn || current.isSubmittingAction) return
        if (current.gameUiState.gameState.phase != TurnPhase.AWAITING_ROLL) return

        viewModelScope.launch {
            _state.update { it.copy(isSubmittingAction = true, errorMessage = null) }
            val res = roomRepo.submitDiceRoll(
                roomCode = roomCode,
                actorUid = uid,
                expectedTurnSeq = room.turnSeq,
                asBotCoordinator = false
            )
            _state.update {
                it.copy(
                    isSubmittingAction = false,
                    errorMessage = res.exceptionOrNull()?.let { err ->
                        friendlyNetworkErrorMessage(err, "Could not roll right now. Please try again.")
                    }
                )
            }
        }
    }

    fun onTokenTapped(tokenRef: TokenRef) {
        val current = _state.value
        val room = current.room ?: return
        if (!current.isLocalPlayersTurn || current.isSubmittingAction) return
        if (current.gameUiState.gameState.phase != TurnPhase.AWAITING_MOVE) return

        val valid = current.gameUiState.gameState.legalMoves.any {
            it.color == tokenRef.color && it.tokenId == tokenRef.tokenId
        }
        if (!valid) return

        viewModelScope.launch {
            _state.update { it.copy(isSubmittingAction = true, errorMessage = null) }
            val res = roomRepo.submitTokenMove(
                roomCode = roomCode,
                actorUid = uid,
                expectedTurnSeq = room.turnSeq,
                tokenColor = tokenRef.color,
                tokenId = tokenRef.tokenId,
                asBotCoordinator = false
            )
            _state.update {
                it.copy(
                    isSubmittingAction = false,
                    errorMessage = res.exceptionOrNull()?.let { err ->
                        friendlyNetworkErrorMessage(err, "Could not submit move. Please try again.")
                    }
                )
            }
        }
    }

    fun onPowerUpButtonTapped(type: PowerUpType) {
        val current = _state.value
        val room = current.room ?: return
        val gs = current.gameUiState.gameState
        if (!current.isLocalPlayersTurn ||
            current.isSubmittingAction ||
            gs.mode != GameModeId.POWER_UP ||
            gs.phase != TurnPhase.AWAITING_ROLL ||
            type !in gs.activePlayer.heldPowerUps
        ) return

        if (current.gameUiState.powerUpTargeting?.powerUpType == type) {
            onCancelPowerUpTargeting()
            return
        }

        val engine = LudoEngine(RuleSetRegistry.forMode(gs.mode))
        when (type) {
            PowerUpType.DOUBLE_DICE -> {
                if (gs.activePlayer.doubleDiceActive) return
                submitPowerUp(room.turnSeq, BotPowerUpAction.UseDoubleDice)
            }
            PowerUpType.SHIELD -> {
                val validIds = engine.computeValidShieldTokenIds(gs, gs.activePlayer.color)
                if (validIds.size == 1) {
                    submitPowerUp(room.turnSeq, BotPowerUpAction.UseShield(validIds.first()))
                } else if (validIds.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            gameUiState = it.gameUiState.copy(
                                powerUpTargeting = PowerUpTargetingState.ShieldTarget(validIds)
                            )
                        )
                    }
                }
            }
            PowerUpType.BOMB_TRAP -> {
                val validSquares = engine.computeValidBombSquares(gs, gs.activePlayer.color)
                if (validSquares.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            gameUiState = it.gameUiState.copy(
                                powerUpTargeting = PowerUpTargetingState.BombPlacement(validSquares)
                            )
                        )
                    }
                }
            }
        }
    }

    fun onCancelPowerUpTargeting() {
        _state.update {
            it.copy(gameUiState = it.gameUiState.copy(powerUpTargeting = null))
        }
    }

    fun onShieldTokenSelected(tokenId: Int) {
        val room = _state.value.room ?: return
        if (!_state.value.isLocalPlayersTurn) return
        submitPowerUp(room.turnSeq, BotPowerUpAction.UseShield(tokenId))
    }

    fun onBombSquareSelected(trackIndex: Int) {
        val room = _state.value.room ?: return
        if (!_state.value.isLocalPlayersTurn) return
        submitPowerUp(room.turnSeq, BotPowerUpAction.UseBombTrap(trackIndex))
    }

    private fun submitPowerUp(expectedTurnSeq: Int, action: BotPowerUpAction) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSubmittingAction = true,
                    gameUiState = it.gameUiState.copy(powerUpTargeting = null)
                )
            }
            val res = roomRepo.submitPowerUpAction(
                roomCode = roomCode,
                actorUid = uid,
                expectedTurnSeq = expectedTurnSeq,
                action = action,
                asBotCoordinator = false
            )
            _state.update {
                it.copy(
                    isSubmittingAction = false,
                    errorMessage = res.exceptionOrNull()?.let { err ->
                        friendlyNetworkErrorMessage(err, "Could not activate power-up. Please try again.")
                    }
                )
            }
        }
    }

    fun voteRematch() {
        viewModelScope.launch {
            _state.update {
                it.copy(rematchUi = it.rematchUi?.copy(isSubmitting = true))
            }
            val res = roomRepo.voteRematch(roomCode, uid)
            _state.update {
                it.copy(
                    rematchUi = it.rematchUi?.copy(isSubmitting = false),
                    errorMessage = res.exceptionOrNull()?.let { err ->
                        friendlyNetworkErrorMessage(err, "Could not send rematch vote.")
                    }
                )
            }
        }
    }

    fun leaveOnlineMatch(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            roomRepo.leaveRoom(roomCode, uid)
            onDone()
        }
    }

    fun pauseMenuOpen(open: Boolean) {
        _state.update { it.copy(gameUiState = it.gameUiState.copy(isPaused = open)) }
    }

    private fun showPowerUpBanner(
        type: PowerUpType?,
        isExplosion: Boolean = false,
        eyebrow: String,
        title: String,
        detail: String,
        durationMs: Long
    ) {
        powerUpBannerCounter += 1
        val key = powerUpBannerCounter
        powerUpBannerJob?.cancel()
        powerUpBannerJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    gameUiState = it.gameUiState.copy(
                        activePowerUpBanner = PowerUpBannerState(
                            type = type,
                            isExplosion = isExplosion,
                            eyebrow = eyebrow,
                            title = title,
                            detail = detail,
                            triggerKey = key
                        )
                    )
                )
            }
            delay(durationMs)
            _state.update { curr ->
                if (curr.gameUiState.activePowerUpBanner?.triggerKey == key) {
                    curr.copy(gameUiState = curr.gameUiState.copy(activePowerUpBanner = null))
                } else curr
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        animationJob?.cancel()
        botCoordinatorJob?.cancel()
        autoSingleMoveJob?.cancel()
        bannerJob?.cancel()
        powerUpBannerJob?.cancel()
        timerLoopJob?.cancel()
        heartbeatJob?.cancel()
    }

    class Factory(
        private val uid: String,
        private val roomCode: String,
        private val myNickname: String,
        private val roomRepo: OnlineRoomRepository,
        private val profileRepo: OnlineProfileAndLeaderboardRepository,
        private val soundManager: SoundManager,
        private val hapticsManager: LudoHapticsManager,
        private val matchHistoryRepo: MatchHistoryRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnlineGameViewModel(
                uid = uid,
                roomCode = roomCode,
                myNickname = myNickname,
                roomRepo = roomRepo,
                profileRepo = profileRepo,
                soundManager = soundManager,
                hapticsManager = hapticsManager,
                matchHistoryRepo = matchHistoryRepo
            ) as T
        }
    }
}
