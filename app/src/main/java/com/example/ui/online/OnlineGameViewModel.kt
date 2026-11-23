package com.example.ui.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.LudoHapticsManager
import com.example.audio.SoundManager
import com.example.data.MatchHistoryRepository
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.LegalMove
import com.example.engine.LudoBoardGeometry
import com.example.engine.LudoEngine
import com.example.engine.PlayerColor
import com.example.engine.PlayerSetup
import com.example.engine.PowerUpType
import com.example.engine.Token
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
import com.example.ui.game.CelebrationSequenceHelper
import com.example.ui.game.ChaosAnnouncementState
import com.example.ui.game.DiceCube3DEngine
import com.example.ui.game.FlyingCapturedToken
import com.example.ui.game.GameUiState
import com.example.ui.game.PowerUpBannerState
import com.example.ui.game.PowerUpTargetingState
import com.example.ui.game.TokenMoveOptionResolver
import com.example.ui.game.TopToastMessage
import com.example.ui.game.TopToastTone
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
 * Auth-scoped ViewModel (keyed by uid + roomCode) driving synchronized online Ludo v2.1 gameplay.
 * Firestore `/rooms/{roomCode}` is the single source of truth; all clients replay matching
 * single-die roll stacking, hop, Magic item, Master unlock, and capture animations whenever `turnSeq` advances.
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

    private data class PendingOnlineFollowUp(
        val tokenColor: PlayerColor,
        val tokenId: Int,
        val dieValueUsed: Int,
        val afterTurnSeq: Int
    )

    private var lastObservedTurnSeq: Int = -1
    private var lastObservedRollSeq: Int = -1
    private var lastTimedStartMs: Long = 0L
    private var recordedFinishedTurnSeq: Int = -1
    private var lastTimeoutRequestedTurnSeq: Int = -1
    private var lastWarnedSecond: Int = -1
    private var powerUpBannerCounter: Int = 0
    private var topToastCounter: Int = 0
    private var celebrationCounter: Int = 0
    private var pendingFollowUpMove: PendingOnlineFollowUp? = null

    private var animationJob: Job? = null
    private var botCoordinatorJob: Job? = null
    private var autoSingleMoveJob: Job? = null
    private var bannerJob: Job? = null
    private var powerUpBannerJob: Job? = null
    private var topToastJob: Job? = null
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
        val isRematchRestart = room.status == RoomStatus.PLAYING && prevRoom?.status == RoomStatus.FINISHED

        val detectedCelebration = if (turnAdvanced && prevState != null) {
            CelebrationSequenceHelper.detectAllFourTokensHome(
                prevState = prevState,
                nextState = newGameState,
                nextTriggerKey = celebrationCounter + 1
            )?.also {
                celebrationCounter += 1
                soundManager.playWin()
                hapticsManager.vibrateWin()
            }
        } else null

        val pendingBombTargeting = if (isLocalTurn && newGameState.phase == TurnPhase.AWAITING_BOMB_PLACEMENT) {
            newGameState.powerUpState.pendingBombPlacement?.let {
                PowerUpTargetingState.BombPlacement(it.validTrackIndices.toSet())
            }
        } else null

        _state.update { current ->
            val mergedGameState = if (!turnAdvanced &&
                newGameState.phase == TurnPhase.AWAITING_MOVE &&
                current.gameUiState.gameState.selectedDieValue in newGameState.playableDiceValues()
            ) {
                newGameState.copy(selectedDieValue = current.gameUiState.gameState.selectedDieValue)
            } else {
                newGameState
            }
            val nextCelebration = when {
                detectedCelebration != null -> detectedCelebration
                isRematchRestart -> null
                else -> current.gameUiState.allTokensHomeCelebration
            }
            val nextGameUi = current.gameUiState.copy(
                gameState = mergedGameState,
                powerUpTargeting = pendingBombTargeting,
                displayedDiceValue = mergedGameState.lastRawDiceValue
                    ?: mergedGameState.lastDiceValue
                    ?: current.gameUiState.displayedDiceValue,
                displayedSecondDiceValue = null,
                allTokensHomeCelebration = nextCelebration
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

        if (room.status == RoomStatus.FINISHED && recordedFinishedTurnSeq != room.turnSeq) {
            recordedFinishedTurnSeq = room.turnSeq
            if (detectedCelebration == null) {
                soundManager.playWin()
                hapticsManager.vibrateWin()
            }
            recordStatsIfParticipant(newGameState, localColor)
        }

        // Execute queued step 2 of a combined chip (e.g. [9] = 6 then 3) once turnSeq advances
        val queued = pendingFollowUpMove
        if (queued != null && room.turnSeq > queued.afterTurnSeq) {
            pendingFollowUpMove = null
            if (isLocalTurn && newGameState.phase == TurnPhase.AWAITING_MOVE) {
                val matchingNext = newGameState.legalMoves.firstOrNull {
                    it.color == queued.tokenColor &&
                        it.includesToken(queued.tokenId) &&
                        it.dieValueUsed == queued.dieValueUsed
                }
                if (matchingNext != null) {
                    val expectedSeq = room.turnSeq
                    autoSingleMoveJob?.cancel()
                    autoSingleMoveJob = viewModelScope.launch {
                        delay(260L)
                        val latest = _state.value.room ?: return@launch
                        if (latest.turnSeq == expectedSeq && latest.status == RoomStatus.PLAYING) {
                            roomRepo.submitTokenMove(
                                roomCode = roomCode,
                                actorUid = uid,
                                expectedTurnSeq = expectedSeq,
                                tokenColor = matchingNext.color,
                                tokenId = matchingNext.tokenId,
                                dieValueUsed = matchingNext.dieValueUsed,
                                asBotCoordinator = false
                            )
                        }
                    }
                    return
                }
            }
        }

        // Auto-move after 300 ms when 1 legal move exists for the whole tray OR all tokens are in base on a 6
        autoSingleMoveJob?.cancel()
        val autoMove = if (isLocalTurn) TokenMoveOptionResolver.findAutoMove(newGameState) else null
        if (autoMove != null) {
            val expectedSeq = room.turnSeq
            autoSingleMoveJob = viewModelScope.launch {
                delay(300L)
                val latest = _state.value.room ?: return@launch
                if (latest.turnSeq == expectedSeq && latest.status == RoomStatus.PLAYING) {
                    roomRepo.submitTokenMove(
                        roomCode = roomCode,
                        actorUid = uid,
                        expectedTurnSeq = expectedSeq,
                        tokenColor = autoMove.color,
                        tokenId = autoMove.tokenId,
                        dieValueUsed = autoMove.dieValueUsed,
                        asBotCoordinator = false
                    )
                }
            }
        }

        // Schedule deterministic Bot coordinator step (for actual bot seats or left players)
        botCoordinatorJob?.cancel()
        if (room.status == RoomStatus.PLAYING &&
            !newGameState.isMatchFinished &&
            activeSeat?.isEffectiveBot == true &&
            room.isBotCoordinator(uid)
        ) {
            val expectedSeq = room.turnSeq
            botCoordinatorJob = viewModelScope.launch {
                delay(1100L)
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
                        val finalFace = newState.lastRawDiceValue ?: newState.lastDiceValue ?: 6
                        soundManager.playDiceRoll()
                        hapticsManager.vibrateDiceRoll()
                        _state.update {
                            it.copy(
                                gameUiState = it.gameUiState.copy(
                                    isDiceRolling = true,
                                    displayedDiceValue = finalFace,
                                    diceRollTrigger = it.gameUiState.diceRollTrigger + 1
                                )
                            )
                        }
                        delay(DiceCube3DEngine.ROLL_DURATION_MS.toLong() + 30L)
                        _state.update {
                            it.copy(
                                gameUiState = it.gameUiState.copy(
                                    isDiceRolling = false,
                                    displayedDiceValue = finalFace
                                )
                            )
                        }
                        if (newState.phase == TurnPhase.TURN_CANCELLED_TRIPLE_SIX ||
                            room.lastActionSummary.contains("three 6s", ignoreCase = true)
                        ) {
                            showTopToast("Three 6s — turn lost", TopToastTone.TRIPLE_SIX)
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
                    if (prevState != null) {
                        detectAndPlayMoveFeedback(prevState, newState, room)
                    }
                }

                OnlineActionType.BOMB -> {
                    soundManager.playPowerUpActivate(PowerUpType.BOMB_TRAP)
                    hapticsManager.vibratePowerUpActivate(PowerUpType.BOMB_TRAP)
                    showTopToast("Bomb Trap hidden on the track!", TopToastTone.MAGIC_BOMB)
                }

                else -> Unit
            }

            if (prevState != null) {
                // Master mode home-column unlock toast
                if (newState.lastHomeUnlockEventId > prevState.lastHomeUnlockEventId &&
                    newState.lastHomeUnlockedColor != null
                ) {
                    val unlockedName = newState.playerByColor(newState.lastHomeUnlockedColor)?.nickname
                        ?: newState.lastHomeUnlockedColor.displayName
                    showTopToast("$unlockedName unlocked Home!", TopToastTone.MASTER_UNLOCK)
                }

                val newAnnId = newState.chaosState.announcementId
                if (newAnnId > prevState.chaosState.announcementId && newState.chaosState.activeAnnouncement != null) {
                    val ev = newState.chaosState.activeAnnouncement
                    soundManager.playTokenHome()
                    hapticsManager.vibrateCapture()
                    showTopToast(
                        "${ev.title} ${newState.chaosState.announcementDetail}",
                        TopToastTone.CHAOS
                    )
                } else if (newState.eliminatedOrder.size > prevState.eliminatedOrder.size) {
                    val elimColor = newState.eliminatedOrder.lastOrNull()
                    if (elimColor != null) {
                        soundManager.playCapture()
                        hapticsManager.vibrateCapture()
                        val elimName = newState.playerByColor(elimColor)?.nickname ?: elimColor.displayName
                        showTopToast("$elimName eliminated!", TopToastTone.ELIMINATION)
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
        } else if (toProgress > fromProgress && (toProgress - fromProgress) <= 14) {
            (fromProgress + 1..toProgress).toList()
        } else {
            listOf(toProgress)
        }

        var prevP = fromProgress
        for (stepP in steps) {
            val (fromR, fromC) = LudoBoardGeometry.tokenBoardPosition(color, tokenId, prevP, false)
            val (toR, toC) = LudoBoardGeometry.tokenBoardPosition(color, tokenId, stepP, false)
            soundManager.playTokenMove()
            val subFrames = 5
            val stepMs = if (steps.size > 6) 80L else 110L
            for (f in 1..subFrames) {
                val t = f.toFloat() / subFrames
                val arc = kotlin.math.sin(t * Math.PI).toFloat()
                _state.update {
                    it.copy(
                        gameUiState = it.gameUiState.copy(
                            isAnimatingMove = true,
                            animatingToken = ref,
                            animatingRow = fromR + (toR - fromR) * t - arc * 0.24f,
                            animatingCol = fromC + (toC - fromC) * t,
                            hopScale = 1f + arc * 0.28f
                        )
                    )
                }
                delay((stepMs / subFrames).coerceAtLeast(14L))
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

    private suspend fun detectAndPlayMoveFeedback(prev: GameState, next: GameState, room: OnlineRoom) {
        val movedColor = room.lastMovedColor ?: return
        val tokenId = room.lastMovedTokenId
        val toP = room.lastMovedToProgress

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
            showTopToast(room.lastActionSummary.ifBlank { "Bomb Trap triggered!" }, TopToastTone.MAGIC_BOMB)
            animateOnlineCapturedReturn(listOf(TokenRef(movedColor, tokenId)), r, c)
            return
        }

        if (toP == Token.HOME_PROGRESS) {
            soundManager.playTokenHome()
            _state.update {
                it.copy(
                    gameUiState = it.gameUiState.copy(
                        homeConfettiTrigger = it.gameUiState.homeConfettiTrigger + 1,
                        homeConfettiColor = movedColor
                    )
                )
            }
        }

        val capturedRefs = mutableListOf<TokenRef>()
        for (np in next.players) {
            if (np.color == movedColor) continue
            val op = prev.playerByColor(np.color) ?: continue
            np.tokens.indices.forEach { idx ->
                if (!op.tokens[idx].isInBase && np.tokens[idx].isInBase) {
                    capturedRefs += TokenRef(np.color, np.tokens[idx].id)
                }
            }
        }
        if (capturedRefs.isNotEmpty()) {
            val (hitR, hitC) = LudoBoardGeometry.tokenBoardPosition(movedColor, tokenId, toP.coerceAtLeast(0), false)
            soundManager.playCapture()
            hapticsManager.vibrateCapture()
            _state.update {
                it.copy(
                    gameUiState = it.gameUiState.copy(
                        captureBurst = CaptureBurstState(
                            color = capturedRefs.first().color,
                            row = hitR,
                            col = hitC,
                            triggerKey = (it.gameUiState.captureBurst?.triggerKey ?: 0) + 1
                        )
                    )
                )
            }
            animateOnlineCapturedReturn(capturedRefs, hitR, hitC)
        }

        // Instant Magic item pickup detection (tile removed from board)
        if (next.mode == GameModeId.MAGIC && next.powerUpState.tiles.size < prev.powerUpState.tiles.size) {
            val nextIds = next.powerUpState.tiles.map { it.id }.toSet()
            val collectedTile = prev.powerUpState.tiles.firstOrNull { it.id !in nextIds }
            if (collectedTile != null) {
                soundManager.playPowerUpPickup(collectedTile.type)
                hapticsManager.vibratePowerUpPickup(collectedTile.type)
                val tone = when (collectedTile.type) {
                    PowerUpType.ROCKET -> TopToastTone.MAGIC_ROCKET
                    PowerUpType.GOLDEN_DICE -> TopToastTone.MAGIC_GOLDEN
                    PowerUpType.SHIELD -> TopToastTone.MAGIC_SHIELD
                    PowerUpType.BOMB_TRAP -> TopToastTone.MAGIC_BOMB
                }
                showTopToast(
                    room.lastActionSummary.ifBlank { "${collectedTile.type.title} collected!" },
                    tone
                )
            }
        }
    }

    private suspend fun animateOnlineCapturedReturn(
        victims: List<TokenRef>,
        originRow: Float,
        originCol: Float
    ) {
        if (victims.isEmpty()) return
        val targets = victims.map { v ->
            val (br, bc) = LudoBoardGeometry.baseTokenCenter(v.color, v.tokenId)
            Triple(v, br, bc)
        }
        val frames = 11
        for (f in 1..frames) {
            val rawT = f.toFloat() / frames
            val t = 1f - (1f - rawT) * (1f - rawT)
            val lift = kotlin.math.sin(rawT * Math.PI).toFloat()
            val flying = targets.map { (v, destR, destC) ->
                FlyingCapturedToken(
                    color = v.color,
                    tokenId = v.tokenId,
                    row = originRow + (destR - originRow) * t - lift * 0.85f,
                    col = originCol + (destC - originCol) * t,
                    scale = 1f + lift * 0.24f
                )
            }
            _state.update { it.copy(gameUiState = it.gameUiState.copy(flyingCapturedTokens = flying)) }
            delay(22L)
        }
        _state.update { it.copy(gameUiState = it.gameUiState.copy(flyingCapturedTokens = emptyList())) }
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

                val turnTimerSec = room.turnTimerSeconds.coerceAtLeast(0)
                val timerEnabled = turnTimerSec > 0

                var remainingSec = turnTimerSec
                var progress = 1f
                var remainingMs = turnTimerSec * 1000L
                var elapsedMs = 0L
                val totalMs = turnTimerSec * 1000L

                if (timerEnabled) {
                    val turnStartMs = room.turnStartedAt?.toDate()?.time ?: System.currentTimeMillis()
                    elapsedMs = (System.currentTimeMillis() - turnStartMs).coerceAtLeast(0L)
                    remainingMs = (totalMs - elapsedMs).coerceAtLeast(0L)
                    remainingSec = ((remainingMs + 999L) / 1000L).toInt().coerceIn(0, turnTimerSec)
                    progress = (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

                    if (remainingSec in 1..3 && remainingSec != lastWarnedSecond) {
                        lastWarnedSecond = remainingSec
                        soundManager.playTimerWarning()
                    }
                }

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

                if (room.mode == GameModeId.TIMED && timedRemaining <= 0 && room.isBotCoordinator(uid)) {
                    roomRepo.submitTimedClockExpired(roomCode, uid, room.turnSeq)
                }

                if (timerEnabled) {
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
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
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
            finalState.isTeamMatch && winnerColor != null ->
                localColor == winnerColor || localColor == winnerColor.opposite()
            else -> localColor == winnerColor
        }
        viewModelScope.launch {
            profileRepo.recordOnlineMatchResult(
                uid = uid,
                fallbackNickname = myNickname,
                won = won,
                mode = finalState.mode
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

    fun onSelectDieValue(dieValue: Int) {
        val current = _state.value
        if (!current.isLocalPlayersTurn || current.isSubmittingAction) return
        val gs = current.gameUiState.gameState
        if (gs.phase != TurnPhase.AWAITING_MOVE) return
        if (dieValue !in gs.playableDiceValues()) return
        _state.update {
            it.copy(
                gameUiState = it.gameUiState.copy(
                    gameState = gs.copy(selectedDieValue = dieValue)
                )
            )
        }
    }

    fun onTokenTapped(tokenRef: TokenRef) {
        val current = _state.value
        if (!current.isLocalPlayersTurn || current.isSubmittingAction || current.gameUiState.isAnimatingMove) return
        if (current.gameUiState.gameState.phase != TurnPhase.AWAITING_MOVE) return

        val chosenMove = current.gameUiState.gameState.resolveMoveForToken(tokenRef) ?: return
        onMoveSelected(chosenMove)
    }

    fun onMoveSelected(move: LegalMove) {
        onMoveSequenceSelected(listOf(move))
    }

    fun onMoveSequenceSelected(moves: List<LegalMove>) {
        if (moves.isEmpty()) return
        val current = _state.value
        val room = current.room ?: return
        if (!current.isLocalPlayersTurn || current.isSubmittingAction || current.gameUiState.isAnimatingMove) return
        if (current.gameUiState.gameState.phase != TurnPhase.AWAITING_MOVE) return

        val firstMove = moves.first()
        val secondMove = moves.getOrNull(1)
        pendingFollowUpMove = if (secondMove != null) {
            PendingOnlineFollowUp(
                tokenColor = secondMove.color,
                tokenId = secondMove.tokenId,
                dieValueUsed = secondMove.dieValueUsed,
                afterTurnSeq = room.turnSeq
            )
        } else null

        autoSingleMoveJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isSubmittingAction = true, errorMessage = null) }
            val res = roomRepo.submitTokenMove(
                roomCode = roomCode,
                actorUid = uid,
                expectedTurnSeq = room.turnSeq,
                tokenColor = firstMove.color,
                tokenId = firstMove.tokenId,
                dieValueUsed = firstMove.dieValueUsed,
                asBotCoordinator = false
            )
            if (res.isFailure) {
                pendingFollowUpMove = null
            }
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

    fun onBombSquareSelected(trackIndex: Int) {
        val current = _state.value
        val room = current.room ?: return
        val gs = current.gameUiState.gameState
        if (!current.isLocalPlayersTurn ||
            current.isSubmittingAction ||
            gs.phase != TurnPhase.AWAITING_BOMB_PLACEMENT
        ) return
        val pending = gs.powerUpState.pendingBombPlacement ?: return
        if (trackIndex !in pending.validTrackIndices) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSubmittingAction = true,
                    errorMessage = null,
                    gameUiState = it.gameUiState.copy(powerUpTargeting = null)
                )
            }
            val res = roomRepo.submitBombPlacement(
                roomCode = roomCode,
                actorUid = uid,
                expectedTurnSeq = room.turnSeq,
                trackIndex = trackIndex,
                asBotCoordinator = false
            )
            _state.update {
                it.copy(
                    isSubmittingAction = false,
                    errorMessage = res.exceptionOrNull()?.let { err ->
                        friendlyNetworkErrorMessage(err, "Could not place Bomb Trap. Please try again.")
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

    fun dismissAllTokensHomeCelebration() {
        _state.update { it.copy(gameUiState = it.gameUiState.copy(allTokensHomeCelebration = null)) }
    }

    fun showTopToast(
        text: String,
        tone: TopToastTone = TopToastTone.NEUTRAL,
        durationMs: Long = 1500L
    ) {
        topToastCounter += 1
        val key = topToastCounter
        topToastJob?.cancel()
        topToastJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    gameUiState = it.gameUiState.copy(
                        topToast = TopToastMessage(
                            text = text,
                            tone = tone,
                            triggerKey = key
                        )
                    )
                )
            }
            delay(durationMs)
            _state.update { curr ->
                if (curr.gameUiState.topToast?.triggerKey == key) {
                    curr.copy(gameUiState = curr.gameUiState.copy(topToast = null))
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
        topToastJob?.cancel()
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
