package com.example.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.LudoHapticsManager
import com.example.audio.SoundManager
import com.example.bot.BotDecisionEngine
import com.example.bot.LudoBotBrain
import com.example.data.SavedGameRepository
import com.example.engine.BotDifficulty
import com.example.engine.ChaosEventType
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.LegalMove
import com.example.engine.LudoBoardGeometry
import com.example.engine.LudoEngine
import com.example.engine.LudoRuleSet
import com.example.engine.MatchFormat
import com.example.engine.PlayerColor
import com.example.engine.PlayerSetup
import com.example.engine.PowerUpType
import com.example.engine.RollOutcome
import com.example.engine.RuleSetRegistry
import com.example.engine.TokenRef
import com.example.engine.TurnManager
import com.example.engine.TurnPhase
import com.example.engine.TurnTimerOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class CaptureBurstState(
    val color: PlayerColor,
    val row: Float,
    val col: Float,
    val triggerKey: Int
)

data class ChaosAnnouncementState(
    val event: ChaosEventType,
    val detail: String,
    val triggerKey: Int
)

sealed interface PowerUpTargetingState {
    val powerUpType: PowerUpType

    data class ShieldTarget(val validTokenIds: List<Int>) : PowerUpTargetingState {
        override val powerUpType: PowerUpType = PowerUpType.SHIELD
    }

    data class BombPlacement(val validTrackIndices: Set<Int>) : PowerUpTargetingState {
        override val powerUpType: PowerUpType = PowerUpType.BOMB_TRAP
    }
}

data class PowerUpBannerState(
    val type: PowerUpType?,
    val isExplosion: Boolean = false,
    val eyebrow: String,
    val title: String,
    val detail: String,
    val triggerKey: Int
)

data class FlyingCapturedToken(
    val color: PlayerColor,
    val tokenId: Int,
    val row: Float,
    val col: Float,
    val scale: Float = 1f
)

data class GameUiState(
    val gameState: GameState,
    val isPaused: Boolean = false,
    val isGuideOverlayVisible: Boolean = false,
    val remainingSeconds: Int = TurnTimerOption.DEFAULT_SECONDS,
    val timerProgress: Float = 1f,
    val isDiceRolling: Boolean = false,
    val displayedDiceValue: Int = 6,
    val displayedSecondDiceValue: Int? = null,
    val diceRollTrigger: Int = 0,
    val isAnimatingMove: Boolean = false,
    val animatingToken: TokenRef? = null,
    val animatingRow: Float = 0f,
    val animatingCol: Float = 0f,
    val hopScale: Float = 1f,
    val flyingCapturedTokens: List<FlyingCapturedToken> = emptyList(),
    val captureBurst: CaptureBurstState? = null,
    val bombExplosionBurst: CaptureBurstState? = null,
    val homeConfettiTrigger: Int = 0,
    val homeConfettiColor: PlayerColor? = null,
    val topToast: TopToastMessage? = null,
    val activeChaosBanner: ChaosAnnouncementState? = null,
    val activePowerUpBanner: PowerUpBannerState? = null,
    val eliminatedBannerColor: PlayerColor? = null,
    val powerUpTargeting: PowerUpTargetingState? = null,
    val allTokensHomeCelebration: AllTokensHomeCelebrationState? = null
) {
    companion object {
        const val TURN_DURATION_SECONDS = TurnTimerOption.DEFAULT_SECONDS
    }
}

class GameViewModel(
    private val soundManager: SoundManager,
    private val hapticsManager: LudoHapticsManager,
    private val botDecisionEngine: BotDecisionEngine = LudoBotBrain(),
    private val diceRandom: Random = Random.Default,
    private val savedGameRepository: SavedGameRepository? = null
) : ViewModel() {

    private var currentRuleSet: LudoRuleSet = RuleSetRegistry.forMode(GameModeId.CLASSIC, MatchFormat.ONE_VS_ONE)
    private var engine = LudoEngine(currentRuleSet)
    private var lastSetups: List<PlayerSetup> = listOf(
        PlayerSetup(0, "Player 1", PlayerColor.RED),
        PlayerSetup(1, "Player 2", PlayerColor.YELLOW)
    )
    private var lastMode: GameModeId = GameModeId.CLASSIC
    private var lastMatchFormat: MatchFormat = MatchFormat.ONE_VS_ONE
    private var lastTimedDurationSeconds: Int = 300
    private var lastTurnTimerSeconds: Int = TurnTimerOption.DEFAULT_SECONDS

    private val _uiState = MutableStateFlow(
        GameUiState(
            gameState = engine.createGame(
                playerSetups = lastSetups,
                mode = lastMode,
                matchFormat = lastMatchFormat,
                timedDurationSeconds = lastTimedDurationSeconds,
                turnTimerSeconds = lastTurnTimerSeconds
            )
        )
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var timedClockJob: Job? = null
    private var turnActionJob: Job? = null
    private var botTurnJob: Job? = null
    private var bannerJob: Job? = null
    private var powerUpBannerJob: Job? = null
    private var topToastJob: Job? = null
    private var powerUpBannerCounter: Int = 0
    private var topToastCounter: Int = 0
    private var celebrationCounter: Int = 0
    private var remainingMs: Long = TurnTimerOption.DEFAULT_SECONDS * 1000L
    private var lastWarnedSecond: Int = -1

    fun startMatch(
        setups: List<PlayerSetup>,
        mode: GameModeId = GameModeId.CLASSIC,
        matchFormat: MatchFormat = if (setups.size <= 2) MatchFormat.ONE_VS_ONE else MatchFormat.FOUR_PLAYERS,
        timedDurationSeconds: Int = 300,
        turnTimerSeconds: Int = TurnTimerOption.DEFAULT_SECONDS
    ) {
        lastSetups = setups
        lastMode = mode
        lastMatchFormat = matchFormat
        lastTimedDurationSeconds = timedDurationSeconds
        lastTurnTimerSeconds = turnTimerSeconds
        currentRuleSet = RuleSetRegistry.forMode(mode, matchFormat)
        engine = LudoEngine(currentRuleSet)

        turnActionJob?.cancel()
        botTurnJob?.cancel()
        timerJob?.cancel()
        timedClockJob?.cancel()
        bannerJob?.cancel()
        powerUpBannerJob?.cancel()

        val initialState = engine.createGame(
            playerSetups = setups,
            mode = mode,
            matchFormat = matchFormat,
            timedDurationSeconds = timedDurationSeconds,
            turnTimerSeconds = turnTimerSeconds
        )
        val initialTimerSec = turnTimerSeconds.coerceAtLeast(0)
        remainingMs = initialTimerSec * 1000L
        lastWarnedSecond = -1
        _uiState.value = GameUiState(
            gameState = initialState,
            isPaused = false,
            remainingSeconds = initialTimerSec,
            timerProgress = 1f,
            displayedDiceValue = 6,
            displayedSecondDiceValue = null
        )
        if (initialTimerSec > 0) {
            startTimerLoop()
        }
        if (mode == GameModeId.TIMED) {
            startTimedMatchClockLoop()
        }
        persistCurrentState(initialState)
        scheduleBotTurnIfNeeded()
    }

    fun resumeSavedMatch(savedState: GameState) {
        if (savedState.isMatchFinished) {
            clearSavedState()
            return
        }
        lastSetups = savedState.players.map { it.setup }
        lastMode = savedState.mode
        lastMatchFormat = savedState.matchFormat
        lastTimedDurationSeconds = savedState.timedDurationSeconds
        lastTurnTimerSeconds = savedState.turnTimerSeconds
        currentRuleSet = RuleSetRegistry.forMode(savedState.mode, savedState.matchFormat)
        engine = LudoEngine(currentRuleSet)

        turnActionJob?.cancel()
        botTurnJob?.cancel()
        timerJob?.cancel()
        timedClockJob?.cancel()
        bannerJob?.cancel()
        powerUpBannerJob?.cancel()

        val timerSec = savedState.turnTimerSeconds.coerceAtLeast(0)
        remainingMs = timerSec * 1000L
        lastWarnedSecond = -1
        val pendingBombTargeting = if (savedState.phase == TurnPhase.AWAITING_BOMB_PLACEMENT &&
            !savedState.activePlayer.setup.isBot
        ) {
            savedState.powerUpState.pendingBombPlacement?.let {
                PowerUpTargetingState.BombPlacement(it.validTrackIndices.toSet())
            }
        } else null

        _uiState.value = GameUiState(
            gameState = savedState,
            isPaused = false,
            remainingSeconds = timerSec,
            timerProgress = 1f,
            displayedDiceValue = savedState.lastRawDiceValue ?: savedState.lastDiceValue ?: 6,
            displayedSecondDiceValue = null,
            powerUpTargeting = pendingBombTargeting
        )
        if (timerSec > 0) {
            startTimerLoop()
        }
        if (savedState.mode == GameModeId.TIMED) {
            startTimedMatchClockLoop()
        }
        scheduleBotTurnIfNeeded()
    }

    private fun persistCurrentState(state: GameState) {
        val repo = savedGameRepository ?: return
        viewModelScope.launch {
            if (state.isMatchFinished) repo.clearSavedGame() else repo.saveGame(state)
        }
    }

    private fun clearSavedState() {
        val repo = savedGameRepository ?: return
        viewModelScope.launch { repo.clearSavedGame() }
    }

    fun rematch() {
        startMatch(
            setups = lastSetups,
            mode = lastMode,
            matchFormat = lastMatchFormat,
            timedDurationSeconds = lastTimedDurationSeconds,
            turnTimerSeconds = lastTurnTimerSeconds
        )
    }

    fun pauseGame() {
        _uiState.update { it.copy(isPaused = true) }
    }

    fun resumeGame() {
        _uiState.update { it.copy(isPaused = false) }
    }

    fun setGuideOverlayVisible(visible: Boolean) {
        _uiState.update { it.copy(isGuideOverlayVisible = visible) }
    }

    fun dismissAllTokensHomeCelebration() {
        _uiState.update { it.copy(allTokensHomeCelebration = null) }
    }

    fun saveCurrentProgressIfActive() {
        val state = _uiState.value.gameState
        if (!state.isMatchFinished) {
            persistCurrentState(state)
        }
    }

    fun stopMatch() {
        timerJob?.cancel()
        timedClockJob?.cancel()
        turnActionJob?.cancel()
        botTurnJob?.cancel()
        bannerJob?.cancel()
        powerUpBannerJob?.cancel()
        persistCurrentState(_uiState.value.gameState)
    }

    fun onBombSquareSelected(trackIndex: Int) {
        val current = _uiState.value
        val state = current.gameState
        if (!currentRuleSet.magicItemsEnabled ||
            current.isPaused ||
            current.allTokensHomeCelebration != null ||
            current.isDiceRolling ||
            current.isAnimatingMove ||
            state.phase != TurnPhase.AWAITING_BOMB_PLACEMENT ||
            state.activePlayer.setup.isBot
        ) {
            return
        }
        val pending = state.powerUpState.pendingBombPlacement ?: return
        if (trackIndex !in pending.validTrackIndices) return

        turnActionJob?.cancel()
        turnActionJob = viewModelScope.launch {
            commitPendingBombPlacement(trackIndex)
        }
    }

    private suspend fun commitPendingBombPlacement(trackIndex: Int) {
        val prevState = _uiState.value.gameState
        val ownerName = prevState.activePlayer.nickname
        val updated = engine.placePendingBomb(prevState, trackIndex)
        soundManager.playPowerUpActivate(PowerUpType.BOMB_TRAP)
        hapticsManager.vibratePowerUpActivate(PowerUpType.BOMB_TRAP)
        showTopToast(
            text = "$ownerName hid a Bomb Trap!",
            tone = TopToastTone.MAGIC_BOMB
        )
        handlePostBombState(prevState, updated)
    }

    private suspend fun handlePostBombState(prevState: GameState, updated: GameState) {
        if (updated.phase == TurnPhase.AWAITING_MOVE && updated.activePlayerIndex == prevState.activePlayerIndex) {
            resetTimerForNextRoll()
            _uiState.update {
                it.copy(
                    gameState = updated,
                    powerUpTargeting = null
                )
            }
            persistCurrentState(updated)
            continueRemainingMovesIfAutoOrBot()
        } else {
            applyTurnTransitionSideEffects(nextState = updated)
            scheduleBotTurnIfNeeded()
        }
    }

    fun onHumanDiceRollStarted() {
        val current = _uiState.value
        if (current.isPaused ||
            current.isGuideOverlayVisible ||
            current.allTokensHomeCelebration != null ||
            current.isDiceRolling ||
            current.isAnimatingMove ||
            current.gameState.phase != TurnPhase.AWAITING_ROLL ||
            current.gameState.activePlayer.setup.isBot
        ) {
            return
        }
        soundManager.playDiceRoll()
        hapticsManager.vibrateDiceRoll()
        _uiState.update {
            it.copy(
                isDiceRolling = true,
                powerUpTargeting = null
            )
        }
    }

    fun onHumanDiceRollCompleted(rolledValue: Int) {
        val current = _uiState.value
        if (current.gameState.isMatchFinished ||
            current.gameState.phase != TurnPhase.AWAITING_ROLL ||
            current.gameState.activePlayer.setup.isBot
        ) {
            _uiState.update { it.copy(isDiceRolling = false) }
            return
        }
        val cleanFace = if (current.gameState.activePlayer.goldenDiceCharges > 0) {
            6
        } else {
            rolledValue.coerceIn(1, 6)
        }

        _uiState.update {
            it.copy(
                isDiceRolling = false,
                displayedDiceValue = cleanFace
            )
        }

        turnActionJob?.cancel()
        turnActionJob = viewModelScope.launch {
            commitDiceRollOutcome(
                rolledValue = cleanFace,
                isBot = false,
                autoPlayedOnTimeout = false
            )
        }
    }

    fun onSelectDieValue(dieValue: Int) {
        val current = _uiState.value
        if (current.isPaused ||
            current.isGuideOverlayVisible ||
            current.allTokensHomeCelebration != null ||
            current.isDiceRolling ||
            current.isAnimatingMove ||
            current.gameState.activePlayer.setup.isBot ||
            current.gameState.phase != TurnPhase.AWAITING_MOVE
        ) {
            return
        }
        val updated = engine.selectDieValue(current.gameState, dieValue)
        if (updated !== current.gameState) {
            _uiState.update { it.copy(gameState = updated) }
        }
    }

    fun onRollDiceTapped() {
        val current = _uiState.value
        if (current.isPaused ||
            current.isGuideOverlayVisible ||
            current.allTokensHomeCelebration != null ||
            current.isDiceRolling ||
            current.isAnimatingMove ||
            current.gameState.phase != TurnPhase.AWAITING_ROLL ||
            current.gameState.activePlayer.setup.isBot
        ) {
            return
        }

        _uiState.update { it.copy(powerUpTargeting = null) }
        turnActionJob?.cancel()
        turnActionJob = viewModelScope.launch {
            executeDiceRollAndTurnFlow(isBot = false, autoPlayedOnTimeout = false)
        }
    }

    fun onTokenTapped(tokenRef: TokenRef) {
        val current = _uiState.value
        if (current.isPaused ||
            current.isGuideOverlayVisible ||
            current.allTokensHomeCelebration != null ||
            current.isDiceRolling ||
            current.isAnimatingMove ||
            current.gameState.activePlayer.setup.isBot
        ) {
            return
        }
        val guard = TurnManager.validateTokenTap(
            state = current.gameState,
            ruleSet = currentRuleSet,
            tokenRef = tokenRef,
            actingColor = current.gameState.activePlayer.color
        )
        if (!guard.isAllowed) return

        val move = current.gameState.resolveMoveForToken(tokenRef) ?: return
        onMoveSelected(move)
    }

    /**
     * Executes a specific chosen [LegalMove] immediately with double-tap protection.
     */
    fun onMoveSelected(move: LegalMove) {
        onMoveSequenceSelected(listOf(move))
    }

    /**
     * Executes 1 or more sequential legal moves (e.g. combined [9] = 6 then 3) on the same token.
     * Synchronously locks `isAnimatingMove` to guarantee no double moves on rapid tapping.
     */
    fun onMoveSequenceSelected(moves: List<LegalMove>) {
        if (moves.isEmpty()) return
        val current = _uiState.value
        if (current.isPaused ||
            current.isGuideOverlayVisible ||
            current.allTokensHomeCelebration != null ||
            current.isDiceRolling ||
            current.isAnimatingMove ||
            current.gameState.activePlayer.setup.isBot ||
            current.gameState.phase != TurnPhase.AWAITING_MOVE
        ) {
            return
        }

        val firstMove = current.gameState.legalMoves.firstOrNull {
            it.color == moves.first().color &&
                it.includesToken(moves.first().tokenId) &&
                it.dieValueUsed == moves.first().dieValueUsed
        } ?: return

        // Synchronous double-tap lock before coroutine begins
        _uiState.update { it.copy(isAnimatingMove = true) }

        turnActionJob?.cancel()
        turnActionJob = viewModelScope.launch {
            val activeIdxAtStart = _uiState.value.gameState.activePlayerIndex
            animateAndCommitMove(
                move = firstMove,
                skipAutoContinue = moves.size > 1
            )
            if (moves.size > 1) {
                for (stepIdx in 1 until moves.size) {
                    val snap = _uiState.value.gameState
                    if (snap.isMatchFinished ||
                        snap.phase != TurnPhase.AWAITING_MOVE ||
                        snap.activePlayerIndex != activeIdxAtStart
                    ) {
                        break
                    }
                    val target = moves[stepIdx]
                    val nextLegal = snap.legalMoves.firstOrNull {
                        it.color == target.color &&
                            it.includesToken(target.tokenId) &&
                            it.dieValueUsed == target.dieValueUsed
                    } ?: break
                    animateAndCommitMove(
                        move = nextLegal,
                        skipAutoContinue = stepIdx < moves.lastIndex
                    )
                }
            }
            scheduleBotTurnIfNeeded()
        }
    }

    fun endMatchEarly() {
        val current = _uiState.value
        if (!current.gameState.hasWinner || current.gameState.isMatchFinished) return
        timerJob?.cancel()
        timedClockJob?.cancel()
        turnActionJob?.cancel()
        botTurnJob?.cancel()
        val finalState = engine.endMatchEarly(current.gameState)
        soundManager.playWin()
        hapticsManager.vibrateWin()
        _uiState.update {
            it.copy(
                gameState = finalState,
                isPaused = false,
                isAnimatingMove = false,
                isDiceRolling = false,
                animatingToken = null,
                powerUpTargeting = null
            )
        }
        clearSavedState()
    }

    private fun scheduleBotTurnIfNeeded() {
        val s = _uiState.value.gameState
        if (s.isMatchFinished || !s.activePlayer.setup.isBot) {
            return
        }

        botTurnJob?.cancel()
        botTurnJob = viewModelScope.launch {
            while (_uiState.value.activeChaosBanner != null ||
                _uiState.value.eliminatedBannerColor != null ||
                _uiState.value.isGuideOverlayVisible ||
                _uiState.value.allTokensHomeCelebration != null
            ) {
                delay(120L)
            }
            delayWhileUnpaused(850L)

            val current = _uiState.value
            val gs = current.gameState
            if (gs.isMatchFinished || !gs.activePlayer.setup.isBot) {
                return@launch
            }

            when (gs.phase) {
                TurnPhase.AWAITING_ROLL -> {
                    executeDiceRollAndTurnFlow(isBot = true, autoPlayedOnTimeout = false)
                }

                TurnPhase.AWAITING_BOMB_PLACEMENT -> {
                    val pending = gs.powerUpState.pendingBombPlacement
                    if (pending != null && pending.validTrackIndices.isNotEmpty()) {
                        val chosenSquare = botDecisionEngine.choosePendingBombSquare(
                            state = gs,
                            ruleSet = currentRuleSet,
                            validTrackIndices = pending.validTrackIndices,
                            difficulty = gs.activePlayer.setup.botDifficulty
                        )
                        commitPendingBombPlacement(chosenSquare)
                    }
                }

                TurnPhase.AWAITING_MOVE -> {
                    if (gs.legalMoves.isNotEmpty()) {
                        val chosenMove = botDecisionEngine.chooseMove(
                            state = gs,
                            ruleSet = currentRuleSet,
                            legalMoves = gs.legalMoves,
                            difficulty = gs.activePlayer.setup.botDifficulty
                        )
                        animateAndCommitMove(chosenMove)
                        scheduleBotTurnIfNeeded()
                    }
                }

                else -> Unit
            }
        }
    }

    private suspend fun executeDiceRollAndTurnFlow(
        isBot: Boolean,
        autoPlayedOnTimeout: Boolean
    ) {
        val currentState = _uiState.value.gameState
        if (currentState.isMatchFinished || currentState.phase != TurnPhase.AWAITING_ROLL) return

        val hasGoldenDice = currentState.activePlayer.goldenDiceCharges > 0
        val rolledValue = if (hasGoldenDice) 6 else DiceCube3DEngine.rollRandomFace(diceRandom)

        soundManager.playDiceRoll()
        hapticsManager.vibrateDiceRoll()

        _uiState.update {
            it.copy(
                isDiceRolling = true,
                powerUpTargeting = null,
                displayedDiceValue = rolledValue,
                diceRollTrigger = it.diceRollTrigger + 1
            )
        }

        delay(DiceCube3DEngine.ROLL_DURATION_MS.toLong() + 30L)

        _uiState.update {
            it.copy(
                isDiceRolling = false,
                displayedDiceValue = rolledValue
            )
        }

        commitDiceRollOutcome(
            rolledValue = rolledValue,
            isBot = isBot,
            autoPlayedOnTimeout = autoPlayedOnTimeout
        )
    }

    private suspend fun commitDiceRollOutcome(
        rolledValue: Int,
        isBot: Boolean,
        autoPlayedOnTimeout: Boolean
    ) {
        val outcome = engine.applyDiceRoll(_uiState.value.gameState, rolledValue)
        _uiState.update { it.copy(gameState = outcome.state) }
        persistCurrentState(outcome.state)

        when (outcome) {
            is RollOutcome.RollAgainStacked -> {
                resetTimerForNextRoll()
                if (isBot || autoPlayedOnTimeout) {
                    delayWhileUnpaused(680L)
                    executeDiceRollAndTurnFlow(isBot = isBot, autoPlayedOnTimeout = autoPlayedOnTimeout)
                }
            }

            is RollOutcome.TripleSixCancelled -> {
                showTopToast(
                    text = "Three 6s — turn lost",
                    tone = TopToastTone.TRIPLE_SIX
                )
                delayWhileUnpaused(1100L)
                val next = engine.advanceTurnAfterSkip(_uiState.value.gameState)
                applyTurnTransitionSideEffects(next)
                scheduleBotTurnIfNeeded()
            }

            is RollOutcome.NoLegalMoves -> {
                val activeName = outcome.state.activePlayer.nickname
                showTopToast(
                    text = "$activeName has no legal moves",
                    tone = TopToastTone.NEUTRAL
                )
                delayWhileUnpaused(900L)
                val next = engine.advanceTurnAfterSkip(_uiState.value.gameState)
                applyTurnTransitionSideEffects(next)
                scheduleBotTurnIfNeeded()
            }

            is RollOutcome.SingleLegalMove -> {
                resetTimerForNextRoll()
                delayWhileUnpaused(if (isBot) 760L else 300L)
                val snap = _uiState.value.gameState
                if (!snap.isMatchFinished && snap.phase == TurnPhase.AWAITING_MOVE && snap.legalMoves.isNotEmpty()) {
                    val toPlay = snap.legalMoves.firstOrNull {
                        it.color == outcome.move.color && it.tokenId == outcome.move.tokenId && it.dieValueUsed == outcome.move.dieValueUsed
                    } ?: snap.legalMoves.first()
                    animateAndCommitMove(toPlay, autoChainOnTimeout = autoPlayedOnTimeout)
                    scheduleBotTurnIfNeeded()
                }
            }

            is RollOutcome.MultipleLegalMoves -> {
                resetTimerForNextRoll()
                if (isBot || autoPlayedOnTimeout) {
                    delayWhileUnpaused(if (isBot) 860L else 320L)
                    val snapshot = _uiState.value.gameState
                    val difficulty = if (isBot) snapshot.activePlayer.setup.botDifficulty else BotDifficulty.HARD
                    if (!snapshot.isMatchFinished &&
                        snapshot.phase == TurnPhase.AWAITING_MOVE &&
                        snapshot.legalMoves.isNotEmpty()
                    ) {
                        val chosenMove = botDecisionEngine.chooseMove(
                            state = snapshot,
                            ruleSet = currentRuleSet,
                            legalMoves = snapshot.legalMoves,
                            difficulty = difficulty
                        )
                        animateAndCommitMove(chosenMove, autoChainOnTimeout = autoPlayedOnTimeout)
                        scheduleBotTurnIfNeeded()
                    }
                } else {
                    // Auto-move when all tokens are in base and a 6 is rolled (equivalent base exits)
                    val autoMove = TokenMoveOptionResolver.findAutoMove(outcome.state)
                    if (autoMove != null) {
                        delayWhileUnpaused(300L)
                        val snap = _uiState.value.gameState
                        val verified = TokenMoveOptionResolver.findAutoMove(snap)
                        if (verified != null && !_uiState.value.isAnimatingMove) {
                            animateAndCommitMove(verified)
                            scheduleBotTurnIfNeeded()
                        }
                    }
                }
            }
        }
    }

    private suspend fun delayWhileUnpaused(totalMs: Long) {
        var remaining = totalMs
        val slice = 80L
        while (remaining > 0L) {
            if (_uiState.value.isPaused ||
                _uiState.value.isGuideOverlayVisible ||
                _uiState.value.allTokensHomeCelebration != null
            ) {
                delay(slice)
            } else {
                val step = minOf(slice, remaining)
                delay(step)
                remaining -= step
            }
        }
    }

    private suspend fun animateAndCommitMove(
        move: LegalMove,
        autoChainOnTimeout: Boolean = false,
        skipAutoContinue: Boolean = false
    ) {
        val tokenRef = TokenRef(move.color, move.tokenId)
        var prevProgress = move.fromProgress
        var prevWrap51 = move.fromOuterWrap51

        // Square-by-square hop (~110 ms per step) with small arc and soft tick
        for (step in move.stepPath) {
            val (fromR, fromC) = LudoBoardGeometry.tokenBoardPosition(
                move.color, move.tokenId, prevProgress, prevWrap51
            )
            val (toR, toC) = LudoBoardGeometry.tokenBoardPosition(
                move.color, move.tokenId, step.progress, step.outerWrap51
            )

            soundManager.playTokenMove()

            val subFrames = 6
            val stepDurationMs = when {
                move.leavesBase -> 145L
                move.stepPath.size > 6 -> 86L
                else -> 110L
            }
            val frameDelay = (stepDurationMs / subFrames).coerceAtLeast(12L)
            for (f in 1..subFrames) {
                val t = f.toFloat() / subFrames
                val arc = kotlin.math.sin(t * Math.PI).toFloat()
                val interpR = fromR + (toR - fromR) * t - arc * 0.24f
                val interpC = fromC + (toC - fromC) * t
                _uiState.update {
                    it.copy(
                        isAnimatingMove = true,
                        animatingToken = tokenRef,
                        animatingRow = interpR,
                        animatingCol = interpC,
                        hopScale = 1f + arc * 0.28f
                    )
                }
                delay(frameDelay)
            }
            prevProgress = step.progress
            prevWrap51 = step.outerWrap51
        }

        // Smooth curved Arrow shortcut slide (Arrow mode)
        val arrowDest = move.arrowToTrackIndex
        if (arrowDest != null) {
            val (startR, startC) = LudoBoardGeometry.tokenBoardPosition(
                move.color, move.tokenId, prevProgress, prevWrap51
            )
            val (destR, destC) = LudoBoardGeometry.outerTrackCenter(arrowDest)
            soundManager.playPowerUpActivate(PowerUpType.ROCKET)
            hapticsManager.vibratePowerUpActivate(PowerUpType.ROCKET)
            animateCurvedGlide(
                tokenRef = tokenRef,
                startR = startR,
                startC = startC,
                destR = destR,
                destC = destC,
                frames = 12,
                frameDelayMs = 22L,
                bowTowardCenter = true
            )
        }

        // Portal teleport animation (Chaos mode)
        val portalDest = move.teleportedToTrackIndex
        if (portalDest != null) {
            val (destR, destC) = LudoBoardGeometry.outerTrackCenter(portalDest)
            soundManager.playTokenHome()
            _uiState.update {
                it.copy(
                    animatingRow = destR,
                    animatingCol = destC,
                    hopScale = 1.42f
                )
            }
            delay(170L)
        }

        // Commit move to pure deterministic engine so Rocket boost & final outcomes are known
        val prevState = _uiState.value.gameState
        val outcome = engine.applyMove(prevState, move)
        val resolvedMove = outcome.move

        // Smooth Rocket glide if Magic Rocket launched the token forward
        if (outcome.rocketBoostSteps > 0 && outcome.triggeredBomb == null) {
            val (startR, startC) = LudoBoardGeometry.tokenBoardPosition(
                move.color, move.tokenId, move.toProgress, move.toOuterWrap51
            )
            val (destR, destC) = LudoBoardGeometry.tokenBoardPosition(
                move.color, move.tokenId, resolvedMove.toProgress, resolvedMove.toOuterWrap51
            )
            soundManager.playPowerUpActivate(PowerUpType.ROCKET)
            hapticsManager.vibratePowerUpActivate(PowerUpType.ROCKET)
            animateCurvedGlide(
                tokenRef = tokenRef,
                startR = startR,
                startC = startC,
                destR = destR,
                destC = destC,
                frames = 13,
                frameDelayMs = 21L,
                bowTowardCenter = false
            )
        }

        // Bomb trap detonation effect + top toast
        val hitBomb = outcome.triggeredBomb
        if (hitBomb != null) {
            val (bombR, bombC) = LudoBoardGeometry.outerTrackCenter(hitBomb.trackIndex)
            soundManager.playBombExplosion()
            hapticsManager.vibrateBombExplosion()
            _uiState.update {
                it.copy(
                    bombExplosionBurst = CaptureBurstState(
                        color = move.color,
                        row = bombR,
                        col = bombC,
                        triggerKey = (it.bombExplosionBurst?.triggerKey ?: 0) + 1
                    )
                )
            }
            val victimName = prevState.playerByColor(move.color)?.nickname ?: move.color.displayName
            val bomberName = prevState.playerByColor(hitBomb.ownerColor)?.nickname
                ?: hitBomb.ownerColor.displayName
            showTopToast(
                text = "Bomb! $victimName hit $bomberName's trap",
                tone = TopToastTone.MAGIC_BOMB
            )
            animateCapturedTokensReturnToBase(
                victims = listOf(TokenRef(move.color, move.tokenId)),
                originRow = bombR,
                originCol = bombC
            )
        }

        // Instant Magic item top toast & sound
        val pickedUp = outcome.collectedPowerUp
        if (pickedUp != null && hitBomb == null) {
            soundManager.playPowerUpPickup(pickedUp)
            hapticsManager.vibratePowerUpPickup(pickedUp)
            val pickerName = prevState.activePlayer.nickname
            val (toastText, toastTone) = when (pickedUp) {
                PowerUpType.ROCKET -> "$pickerName Rocket +${outcome.rocketBoostSteps}!" to TopToastTone.MAGIC_ROCKET
                PowerUpType.GOLDEN_DICE -> "$pickerName got Golden Dice (next roll 6)!" to TopToastTone.MAGIC_GOLDEN
                PowerUpType.SHIELD -> "$pickerName shielded for 2 turns!" to TopToastTone.MAGIC_SHIELD
                PowerUpType.BOMB_TRAP -> if (outcome.awaitingBombPlacement) {
                    "Bomb Trap — tap a glowing square ahead!" to TopToastTone.MAGIC_BOMB
                } else {
                    "No open square ahead for Bomb Trap" to TopToastTone.NEUTRAL
                }
            }
            showTopToast(text = toastText, tone = toastTone)
        }

        // Capture impact flash + sound + captured token(s) flying back to base
        if (resolvedMove.capturedTokens.isNotEmpty()) {
            val (hitR, hitC) = LudoBoardGeometry.tokenBoardPosition(
                move.color, move.tokenId, resolvedMove.toProgress, resolvedMove.toOuterWrap51
            )
            soundManager.playCapture()
            hapticsManager.vibrateCapture()
            _uiState.update {
                it.copy(
                    captureBurst = CaptureBurstState(
                        color = resolvedMove.capturedTokens.first().color,
                        row = hitR,
                        col = hitC,
                        triggerKey = (it.captureBurst?.triggerKey ?: 0) + 1
                    )
                )
            }
            animateCapturedTokensReturnToBase(
                victims = resolvedMove.capturedTokens,
                originRow = hitR,
                originCol = hitC
            )
        }

        if (outcome.unlockedMasterHome) {
            showTopToast(
                text = "${prevState.activePlayer.nickname} unlocked Home!",
                tone = TopToastTone.MASTER_UNLOCK
            )
        }

        if (resolvedMove.reachesHome) {
            soundManager.playTokenHome()
            _uiState.update {
                it.copy(
                    homeConfettiTrigger = it.homeConfettiTrigger + 1,
                    homeConfettiColor = move.color
                )
            }
        }

        val celebration = CelebrationSequenceHelper.detectAllFourTokensHome(
            prevState = prevState,
            nextState = outcome.state,
            nextTriggerKey = celebrationCounter + 1
        )?.also { celebrationCounter += 1 }

        if (outcome.state.isMatchFinished) {
            timerJob?.cancel()
            timedClockJob?.cancel()
            soundManager.playWin()
            hapticsManager.vibrateWin()
            _uiState.update {
                it.copy(
                    gameState = outcome.state,
                    isAnimatingMove = false,
                    animatingToken = null,
                    flyingCapturedTokens = emptyList(),
                    hopScale = 1f,
                    powerUpTargeting = null,
                    allTokensHomeCelebration = celebration ?: it.allTokensHomeCelebration
                )
            }
            clearSavedState()
        } else if (outcome.awaitingBombPlacement) {
            val pending = outcome.state.powerUpState.pendingBombPlacement
            val isBotPlayer = outcome.state.activePlayer.setup.isBot
            resetTimerForNextRoll()
            _uiState.update {
                it.copy(
                    gameState = outcome.state,
                    isAnimatingMove = false,
                    animatingToken = null,
                    flyingCapturedTokens = emptyList(),
                    hopScale = 1f,
                    powerUpTargeting = if (!isBotPlayer && pending != null) {
                        PowerUpTargetingState.BombPlacement(pending.validTrackIndices.toSet())
                    } else null,
                    allTokensHomeCelebration = celebration ?: it.allTokensHomeCelebration
                )
            }
            persistCurrentState(outcome.state)
            if ((isBotPlayer || autoChainOnTimeout) && pending != null && pending.validTrackIndices.isNotEmpty()) {
                delayWhileUnpaused(650L)
                val diff = if (isBotPlayer) outcome.state.activePlayer.setup.botDifficulty else BotDifficulty.HARD
                val bestSq = botDecisionEngine.choosePendingBombSquare(
                    state = outcome.state,
                    ruleSet = currentRuleSet,
                    validTrackIndices = pending.validTrackIndices,
                    difficulty = diff
                )
                commitPendingBombPlacement(bestSq)
            }
        } else if (outcome.hasRemainingDiceMoves) {
            if (celebration != null) {
                soundManager.playWin()
                hapticsManager.vibrateWin()
            }
            resetTimerForNextRoll()
            _uiState.update {
                it.copy(
                    gameState = outcome.state,
                    isAnimatingMove = false,
                    animatingToken = null,
                    flyingCapturedTokens = emptyList(),
                    hopScale = 1f,
                    powerUpTargeting = null,
                    allTokensHomeCelebration = celebration ?: it.allTokensHomeCelebration
                )
            }
            persistCurrentState(outcome.state)
            if (!skipAutoContinue) {
                continueRemainingMovesIfAutoOrBot(autoChainOnTimeout = autoChainOnTimeout)
            }
        } else {
            if (celebration != null) {
                soundManager.playWin()
                hapticsManager.vibrateWin()
            }
            applyTurnTransitionSideEffects(
                nextState = outcome.state,
                chaosEvent = outcome.triggeredChaosEvent,
                eliminatedColor = outcome.eliminatedPlayer,
                celebration = celebration
            )
        }
    }

    private suspend fun animateCurvedGlide(
        tokenRef: TokenRef,
        startR: Float,
        startC: Float,
        destR: Float,
        destC: Float,
        frames: Int,
        frameDelayMs: Long,
        bowTowardCenter: Boolean
    ) {
        val midR = (startR + destR) * 0.5f
        val midC = (startC + destC) * 0.5f
        val ctrlR = if (bowTowardCenter) midR + (7.5f - midR) * 0.24f else midR - 0.55f
        val ctrlC = if (bowTowardCenter) midC + (7.5f - midC) * 0.24f else midC
        for (f in 1..frames) {
            val rawT = f.toFloat() / frames
            val t = 1f - (1f - rawT) * (1f - rawT) // smooth ease-out
            val inv = 1f - t
            val r = inv * inv * startR + 2f * inv * t * ctrlR + t * t * destR
            val c = inv * inv * startC + 2f * inv * t * ctrlC + t * t * destC
            val arc = kotlin.math.sin(rawT * Math.PI).toFloat()
            _uiState.update {
                it.copy(
                    isAnimatingMove = true,
                    animatingToken = tokenRef,
                    animatingRow = r,
                    animatingCol = c,
                    hopScale = 1.06f + arc * 0.38f
                )
            }
            delay(frameDelayMs)
        }
    }

    private suspend fun animateCapturedTokensReturnToBase(
        victims: List<TokenRef>,
        originRow: Float,
        originCol: Float
    ) {
        if (victims.isEmpty()) return
        val targets = victims.map { v ->
            val (br, bc) = LudoBoardGeometry.baseTokenCenter(v.color, v.tokenId)
            Triple(v, br, bc)
        }
        val frames = 12
        for (f in 1..frames) {
            val rawT = f.toFloat() / frames
            val t = 1f - (1f - rawT) * (1f - rawT)
            val lift = kotlin.math.sin(rawT * Math.PI).toFloat()
            val flying = targets.map { (v, destR, destC) ->
                FlyingCapturedToken(
                    color = v.color,
                    tokenId = v.tokenId,
                    row = originRow + (destR - originRow) * t - lift * 0.9f,
                    col = originCol + (destC - originCol) * t,
                    scale = 1f + lift * 0.25f
                )
            }
            _uiState.update { it.copy(flyingCapturedTokens = flying) }
            delay(22L)
        }
        _uiState.update { it.copy(flyingCapturedTokens = emptyList()) }
    }

    private suspend fun continueRemainingMovesIfAutoOrBot(autoChainOnTimeout: Boolean = false) {
        val current = _uiState.value.gameState
        if (current.isMatchFinished || current.phase != TurnPhase.AWAITING_MOVE) return
        val isBotTurn = current.activePlayer.setup.isBot
        if (isBotTurn || autoChainOnTimeout) {
            delayWhileUnpaused(if (isBotTurn) 720L else 320L)
            val snapshot = _uiState.value.gameState
            if (!snapshot.isMatchFinished &&
                snapshot.phase == TurnPhase.AWAITING_MOVE &&
                snapshot.legalMoves.isNotEmpty()
            ) {
                val diff = if (isBotTurn) snapshot.activePlayer.setup.botDifficulty else BotDifficulty.HARD
                val nextMove = if (snapshot.legalMoves.size == 1) {
                    snapshot.legalMoves.first()
                } else {
                    botDecisionEngine.chooseMove(
                        state = snapshot,
                        ruleSet = currentRuleSet,
                        legalMoves = snapshot.legalMoves,
                        difficulty = diff
                    )
                }
                animateAndCommitMove(nextMove, autoChainOnTimeout = autoChainOnTimeout)
            }
        } else {
            val autoMove = TokenMoveOptionResolver.findAutoMove(current)
            if (autoMove != null) {
                delayWhileUnpaused(300L)
                val snapshot = _uiState.value.gameState
                val verified = TokenMoveOptionResolver.findAutoMove(snapshot)
                if (verified != null && !_uiState.value.isAnimatingMove) {
                    animateAndCommitMove(verified)
                }
            }
        }
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
            _uiState.update {
                it.copy(
                    topToast = TopToastMessage(
                        text = text,
                        tone = tone,
                        triggerKey = key
                    )
                )
            }
            delay(durationMs)
            _uiState.update { current ->
                if (current.topToast?.triggerKey == key) {
                    current.copy(topToast = null)
                } else current
            }
        }
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
            _uiState.update {
                it.copy(
                    activePowerUpBanner = PowerUpBannerState(
                        type = type,
                        isExplosion = isExplosion,
                        eyebrow = eyebrow,
                        title = title,
                        detail = detail,
                        triggerKey = key
                    )
                )
            }
            delay(durationMs)
            _uiState.update { current ->
                if (current.activePowerUpBanner?.triggerKey == key) {
                    current.copy(activePowerUpBanner = null)
                } else current
            }
        }
    }

    private fun applyTurnTransitionSideEffects(
        nextState: GameState,
        chaosEvent: ChaosEventType? = null,
        eliminatedColor: PlayerColor? = null,
        celebration: AllTokensHomeCelebrationState? = null
    ) {
        val prevAnnouncementId = _uiState.value.gameState.chaosState.announcementId
        val newAnnouncementId = nextState.chaosState.announcementId
        val resolvedChaos = chaosEvent
            ?: if (newAnnouncementId > prevAnnouncementId) nextState.chaosState.activeAnnouncement else null

        val prevElimCount = _uiState.value.gameState.eliminatedOrder.size
        val resolvedElim = eliminatedColor
            ?: if (nextState.eliminatedOrder.size > prevElimCount) nextState.eliminatedOrder.lastOrNull() else null

        resetTimerForNextRoll()
        _uiState.update {
            it.copy(
                gameState = nextState,
                isDiceRolling = false,
                isAnimatingMove = false,
                animatingToken = null,
                hopScale = 1f,
                displayedSecondDiceValue = null,
                powerUpTargeting = null,
                allTokensHomeCelebration = celebration ?: it.allTokensHomeCelebration
            )
        }
        persistCurrentState(nextState)

        if (resolvedChaos != null) {
            soundManager.playTokenHome()
            hapticsManager.vibrateCapture()
            showTopToast(
                text = "${resolvedChaos.title} ${nextState.chaosState.announcementDetail}",
                tone = TopToastTone.CHAOS
            )
        } else if (resolvedElim != null) {
            soundManager.playCapture()
            hapticsManager.vibrateCapture()
            val elimName = nextState.playerByColor(resolvedElim)?.nickname ?: resolvedElim.displayName
            showTopToast(
                text = "$elimName eliminated!",
                tone = TopToastTone.ELIMINATION
            )
        }
    }

    private fun resetTimerForNextRoll() {
        val timerSec = _uiState.value.gameState.turnTimerSeconds.coerceAtLeast(0)
        remainingMs = timerSec * 1000L
        lastWarnedSecond = -1
        _uiState.update {
            it.copy(
                remainingSeconds = timerSec,
                timerProgress = 1f
            )
        }
    }

    /**
     * Turn timer loop: respects Off (0s) / 10s / 15s / 20s.
     * On timeout: auto-rolls or auto-plays the best legal move via the bot brain,
     * keeping the human in full control of their seat (NO bot takeover).
     */
    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val tickMs = 100L
            while (true) {
                delay(tickMs)
                val s = _uiState.value
                val gs = s.gameState
                if (gs.isMatchFinished) break
                val totalSeconds = gs.turnTimerSeconds
                if (totalSeconds <= 0) break

                val timerPaused = s.isPaused ||
                    s.isGuideOverlayVisible ||
                    s.allTokensHomeCelebration != null ||
                    s.isDiceRolling ||
                    s.isAnimatingMove ||
                    s.activeChaosBanner != null ||
                    s.eliminatedBannerColor != null ||
                    gs.phase == TurnPhase.NO_LEGAL_MOVES ||
                    gs.phase == TurnPhase.TURN_CANCELLED_TRIPLE_SIX

                if (timerPaused) continue

                remainingMs = (remainingMs - tickMs).coerceAtLeast(0L)
                val totalMs = totalSeconds * 1000L
                val wholeSecs = ((remainingMs + 999L) / 1000L).toInt().coerceIn(0, totalSeconds)
                val progress = (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

                if (wholeSecs in 1..3 && wholeSecs != lastWarnedSecond) {
                    lastWarnedSecond = wholeSecs
                    soundManager.playTimerWarning()
                }

                if (remainingMs <= 0L) {
                    resetTimerForNextRoll()
                    handleTurnTimerAutoPlay()
                } else {
                    _uiState.update {
                        it.copy(
                            remainingSeconds = wholeSecs,
                            timerProgress = progress
                        )
                    }
                }
            }
        }
    }

    private fun handleTurnTimerAutoPlay() {
        val snapshot = _uiState.value.gameState
        if (snapshot.isMatchFinished) return
        turnActionJob?.cancel()
        botTurnJob?.cancel()
        turnActionJob = viewModelScope.launch {
            when (snapshot.phase) {
                TurnPhase.AWAITING_ROLL -> {
                    executeDiceRollAndTurnFlow(
                        isBot = snapshot.activePlayer.setup.isBot,
                        autoPlayedOnTimeout = true
                    )
                }

                TurnPhase.AWAITING_BOMB_PLACEMENT -> {
                    val pending = snapshot.powerUpState.pendingBombPlacement
                    if (pending != null && pending.validTrackIndices.isNotEmpty()) {
                        val bestSquare = botDecisionEngine.choosePendingBombSquare(
                            state = snapshot,
                            ruleSet = currentRuleSet,
                            validTrackIndices = pending.validTrackIndices,
                            difficulty = BotDifficulty.HARD
                        )
                        commitPendingBombPlacement(bestSquare)
                    }
                }

                TurnPhase.AWAITING_MOVE -> {
                    if (snapshot.legalMoves.isNotEmpty()) {
                        val bestMove = botDecisionEngine.chooseMove(
                            state = snapshot,
                            ruleSet = currentRuleSet,
                            legalMoves = snapshot.legalMoves,
                            difficulty = BotDifficulty.HARD
                        )
                        animateAndCommitMove(bestMove, autoChainOnTimeout = true)
                        scheduleBotTurnIfNeeded()
                    } else {
                        val next = engine.advanceTurnAfterSkip(snapshot)
                        applyTurnTransitionSideEffects(next)
                        scheduleBotTurnIfNeeded()
                    }
                }

                else -> Unit
            }
        }
    }

    private fun startTimedMatchClockLoop() {
        timedClockJob?.cancel()
        timedClockJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val s = _uiState.value
                if (s.gameState.isMatchFinished) break
                if (s.isPaused || s.isGuideOverlayVisible || s.allTokensHomeCelebration != null) continue

                val ticked = engine.tickTimedClock(_uiState.value.gameState, secondsElapsed = 1)
                if (ticked.isMatchFinished) {
                    timerJob?.cancel()
                    turnActionJob?.cancel()
                    botTurnJob?.cancel()
                    soundManager.playWin()
                    hapticsManager.vibrateWin()
                    _uiState.update {
                        it.copy(
                            gameState = ticked,
                            isAnimatingMove = false,
                            isDiceRolling = false,
                            animatingToken = null,
                            powerUpTargeting = null
                        )
                    }
                    clearSavedState()
                    break
                } else {
                    _uiState.update { it.copy(gameState = ticked) }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        timedClockJob?.cancel()
        turnActionJob?.cancel()
        botTurnJob?.cancel()
        bannerJob?.cancel()
        powerUpBannerJob?.cancel()
        topToastJob?.cancel()
    }

    class Factory(
        private val soundManager: SoundManager,
        private val hapticsManager: LudoHapticsManager,
        private val savedGameRepository: SavedGameRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameViewModel(
                soundManager = soundManager,
                hapticsManager = hapticsManager,
                savedGameRepository = savedGameRepository
            ) as T
        }
    }
}
