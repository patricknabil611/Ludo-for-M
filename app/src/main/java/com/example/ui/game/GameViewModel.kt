package com.example.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.LudoHapticsManager
import com.example.audio.SoundManager
import com.example.bot.BotDecisionEngine
import com.example.data.SavedGameRepository
import com.example.bot.BotPowerUpAction
import com.example.bot.LudoBotBrain
import com.example.engine.ChaosEventType
import com.example.engine.ClassicRuleSet
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.LegalMove
import com.example.engine.LudoBoardGeometry
import com.example.engine.LudoEngine
import com.example.engine.LudoRuleSet
import com.example.engine.PlayerColor
import com.example.engine.PlayerSetup
import com.example.engine.PowerUpType
import com.example.engine.RollOutcome
import com.example.engine.RuleSetRegistry
import com.example.engine.TokenRef
import com.example.engine.TurnManager
import com.example.engine.TurnPhase
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

data class GameUiState(
    val gameState: GameState,
    val isPaused: Boolean = false,
    val isGuideOverlayVisible: Boolean = false,
    val remainingSeconds: Int = TURN_DURATION_SECONDS,
    val timerProgress: Float = 1f,
    val isDiceRolling: Boolean = false,
    val displayedDiceValue: Int = 6,
    val displayedSecondDiceValue: Int? = 6,
    val diceRollTrigger: Int = 0,
    val isAnimatingMove: Boolean = false,
    val animatingToken: TokenRef? = null,
    val animatingRow: Float = 0f,
    val animatingCol: Float = 0f,
    val hopScale: Float = 1f,
    val captureBurst: CaptureBurstState? = null,
    val bombExplosionBurst: CaptureBurstState? = null,
    val homeConfettiTrigger: Int = 0,
    val activeChaosBanner: ChaosAnnouncementState? = null,
    val activePowerUpBanner: PowerUpBannerState? = null,
    val eliminatedBannerColor: PlayerColor? = null,
    val powerUpTargeting: PowerUpTargetingState? = null,
    val allTokensHomeCelebration: AllTokensHomeCelebrationState? = null
) {
    companion object {
        const val TURN_DURATION_SECONDS = 30
    }
}

class GameViewModel(
    private val soundManager: SoundManager,
    private val hapticsManager: LudoHapticsManager,
    private val botDecisionEngine: BotDecisionEngine = LudoBotBrain(),
    private val diceRandom: Random = Random.Default,
    private val savedGameRepository: SavedGameRepository? = null
) : ViewModel() {

    private var currentRuleSet: LudoRuleSet = ClassicRuleSet
    private var engine = LudoEngine(currentRuleSet)
    private var lastSetups: List<PlayerSetup> = listOf(
        PlayerSetup(0, "Player 1", PlayerColor.RED),
        PlayerSetup(1, "Player 2", PlayerColor.YELLOW)
    )
    private var lastMode: GameModeId = GameModeId.CLASSIC
    private var lastTimedDurationSeconds: Int = 300

    private val _uiState = MutableStateFlow(
        GameUiState(gameState = engine.createGame(lastSetups, lastMode))
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var timedClockJob: Job? = null
    private var turnActionJob: Job? = null
    private var botTurnJob: Job? = null
    private var bannerJob: Job? = null
    private var powerUpBannerJob: Job? = null
    private var powerUpBannerCounter: Int = 0
    private var celebrationCounter: Int = 0
    private var remainingMs: Long = GameUiState.TURN_DURATION_SECONDS * 1000L
    private var lastWarnedSecond: Int = -1

    fun startMatch(
        setups: List<PlayerSetup>,
        mode: GameModeId = GameModeId.CLASSIC,
        timedDurationSeconds: Int = 300
    ) {
        lastSetups = setups
        lastMode = mode
        lastTimedDurationSeconds = timedDurationSeconds
        currentRuleSet = RuleSetRegistry.forMode(mode)
        engine = LudoEngine(currentRuleSet)

        turnActionJob?.cancel()
        botTurnJob?.cancel()
        timerJob?.cancel()
        timedClockJob?.cancel()
        bannerJob?.cancel()
        powerUpBannerJob?.cancel()

        val initialState = engine.createGame(setups, mode, timedDurationSeconds)
        remainingMs = GameUiState.TURN_DURATION_SECONDS * 1000L
        lastWarnedSecond = -1
        _uiState.value = GameUiState(
            gameState = initialState,
            isPaused = false,
            remainingSeconds = GameUiState.TURN_DURATION_SECONDS,
            timerProgress = 1f,
            displayedDiceValue = 6,
            displayedSecondDiceValue = null
        )
        startTimerLoop()
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
        lastTimedDurationSeconds = savedState.timedDurationSeconds
        currentRuleSet = RuleSetRegistry.forMode(savedState.mode)
        engine = LudoEngine(currentRuleSet)

        turnActionJob?.cancel()
        botTurnJob?.cancel()
        timerJob?.cancel()
        timedClockJob?.cancel()
        bannerJob?.cancel()
        powerUpBannerJob?.cancel()

        remainingMs = GameUiState.TURN_DURATION_SECONDS * 1000L
        lastWarnedSecond = -1
        _uiState.value = GameUiState(
            gameState = savedState,
            isPaused = false,
            remainingSeconds = GameUiState.TURN_DURATION_SECONDS,
            timerProgress = 1f,
            displayedDiceValue = savedState.lastRawDiceValue ?: savedState.lastDiceValue ?: 6,
            displayedSecondDiceValue = if (savedState.activePlayer.doubleDiceActive) {
                savedState.lastSecondDiceValue ?: 6
            } else null
        )
        startTimerLoop()
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
        startMatch(lastSetups, lastMode, lastTimedDurationSeconds)
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

    fun onPowerUpButtonTapped(type: PowerUpType) {
        val current = _uiState.value
        val state = current.gameState
        if (!currentRuleSet.powerUpsEnabled ||
            current.isPaused ||
            current.isGuideOverlayVisible ||
            current.allTokensHomeCelebration != null ||
            current.isDiceRolling ||
            current.isAnimatingMove ||
            state.phase != TurnPhase.AWAITING_ROLL ||
            state.activePlayer.setup.isBot ||
            type !in state.activePlayer.heldPowerUps
        ) {
            return
        }

        // Toggle off if already targeting this power-up
        if (current.powerUpTargeting?.powerUpType == type) {
            _uiState.update { it.copy(powerUpTargeting = null) }
            return
        }

        when (type) {
            PowerUpType.DOUBLE_DICE -> {
                if (state.activePlayer.doubleDiceActive) return
                val updated = engine.activateDoubleDice(state)
                soundManager.playPowerUpActivate(PowerUpType.DOUBLE_DICE)
                hapticsManager.vibratePowerUpActivate(PowerUpType.DOUBLE_DICE)
                _uiState.update {
                    it.copy(
                        gameState = updated,
                        powerUpTargeting = null,
                        displayedSecondDiceValue = 6
                    )
                }
                persistCurrentState(updated)
                showPowerUpBanner(
                    type = PowerUpType.DOUBLE_DICE,
                    eyebrow = "POWER-UP ACTIVATED!",
                    title = "Double Dice Ready!",
                    detail = "${state.activePlayer.nickname} will roll two dice this turn!",
                    durationMs = 1350L
                )
            }

            PowerUpType.SHIELD -> {
                val validIds = engine.computeValidShieldTokenIds(state, state.activePlayer.color)
                if (validIds.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            powerUpTargeting = null,
                            gameState = it.gameState.copy(
                                lastEventMessage = "Move a token onto the track first to use Shield!"
                            )
                        )
                    }
                } else if (validIds.size == 1) {
                    onShieldTokenSelected(validIds.first())
                } else {
                    _uiState.update {
                        it.copy(
                            powerUpTargeting = PowerUpTargetingState.ShieldTarget(validIds),
                            gameState = it.gameState.copy(
                                lastEventMessage = "Tap one of your tokens to Shield it for 2 turns!"
                            )
                        )
                    }
                }
            }

            PowerUpType.BOMB_TRAP -> {
                val validSquares = engine.computeValidBombSquares(state, state.activePlayer.color)
                if (validSquares.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            powerUpTargeting = null,
                            gameState = it.gameState.copy(
                                lastEventMessage = "No empty non-safe squares within 6 steps!"
                            )
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            powerUpTargeting = PowerUpTargetingState.BombPlacement(validSquares),
                            gameState = it.gameState.copy(
                                lastEventMessage = "Tap a glowing square to hide your Bomb Trap!"
                            )
                        )
                    }
                }
            }
        }
    }

    fun onCancelPowerUpTargeting() {
        _uiState.update {
            it.copy(
                powerUpTargeting = null,
                gameState = it.gameState.copy(
                    lastEventMessage = "${it.gameState.activePlayer.nickname}'s turn!"
                )
            )
        }
    }

    fun onShieldTokenSelected(tokenId: Int) {
        val current = _uiState.value
        val state = current.gameState
        if (!currentRuleSet.powerUpsEnabled ||
            current.isPaused ||
            current.allTokensHomeCelebration != null ||
            current.isDiceRolling ||
            current.isAnimatingMove ||
            state.phase != TurnPhase.AWAITING_ROLL ||
            state.activePlayer.setup.isBot
        ) {
            return
        }
        val validIds = engine.computeValidShieldTokenIds(state, state.activePlayer.color)
        if (tokenId !in validIds || PowerUpType.SHIELD !in state.activePlayer.heldPowerUps) return

        val updated = engine.activateShield(state, tokenId)
        soundManager.playPowerUpActivate(PowerUpType.SHIELD)
        hapticsManager.vibratePowerUpActivate(PowerUpType.SHIELD)
        _uiState.update {
            it.copy(
                gameState = updated,
                powerUpTargeting = null
            )
        }
        persistCurrentState(updated)
        showPowerUpBanner(
            type = PowerUpType.SHIELD,
            eyebrow = "POWER-UP ACTIVATED!",
            title = "Shield Active!",
            detail = "Protected from capture for 2 turns!",
            durationMs = 1350L
        )
    }

    fun onBombSquareSelected(trackIndex: Int) {
        val current = _uiState.value
        val state = current.gameState
        if (!currentRuleSet.powerUpsEnabled ||
            current.isPaused ||
            current.allTokensHomeCelebration != null ||
            current.isDiceRolling ||
            current.isAnimatingMove ||
            state.phase != TurnPhase.AWAITING_ROLL ||
            state.activePlayer.setup.isBot
        ) {
            return
        }
        val validSquares = engine.computeValidBombSquares(state, state.activePlayer.color)
        if (trackIndex !in validSquares || PowerUpType.BOMB_TRAP !in state.activePlayer.heldPowerUps) return

        val updated = engine.placeBombTrap(state, trackIndex)
        soundManager.playPowerUpActivate(PowerUpType.BOMB_TRAP)
        hapticsManager.vibratePowerUpActivate(PowerUpType.BOMB_TRAP)
        _uiState.update {
            it.copy(
                gameState = updated,
                powerUpTargeting = null
            )
        }
        persistCurrentState(updated)
        showPowerUpBanner(
            type = PowerUpType.BOMB_TRAP,
            eyebrow = "TRAP ARMED!",
            title = "Bomb Hidden!",
            detail = "Only ${state.activePlayer.nickname} can see it. Lasts 5 rounds!",
            durationMs = 1400L
        )
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

    fun onHumanDiceRollCompleted(rolledValue: Int, secondRolledValue: Int? = null) {
        val current = _uiState.value
        if (current.gameState.isMatchFinished ||
            current.gameState.phase != TurnPhase.AWAITING_ROLL ||
            current.gameState.activePlayer.setup.isBot
        ) {
            _uiState.update { it.copy(isDiceRolling = false) }
            return
        }
        val cleanFirst = rolledValue.coerceIn(1, 6)
        val cleanSecond = (secondRolledValue ?: DiceCube3DEngine.rollRandomFace(diceRandom)).coerceIn(1, 6)

        _uiState.update {
            it.copy(
                isDiceRolling = false,
                displayedDiceValue = cleanFirst,
                displayedSecondDiceValue = cleanSecond
            )
        }

        turnActionJob?.cancel()
        turnActionJob = viewModelScope.launch {
            commitDiceRollOutcome(
                rolledValue1 = cleanFirst,
                rolledValue2 = cleanSecond,
                isBot = false
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
            executeDiceRollAndTurnFlow(isBot = false)
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

        turnActionJob?.cancel()
        turnActionJob = viewModelScope.launch {
            animateAndCommitMove(move)
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
        if (s.isMatchFinished || s.phase != TurnPhase.AWAITING_ROLL || !s.activePlayer.setup.isBot) {
            return
        }

        botTurnJob?.cancel()
        botTurnJob = viewModelScope.launch {
            // Wait if guide dialog, celebration modal, or a big banner is visible
            while (_uiState.value.activeChaosBanner != null ||
                _uiState.value.eliminatedBannerColor != null ||
                _uiState.value.isGuideOverlayVisible ||
                _uiState.value.allTokensHomeCelebration != null
            ) {
                delay(120L)
            }
            delayWhileUnpaused(1050L)

            var current = _uiState.value
            if (current.gameState.isMatchFinished ||
                current.gameState.phase != TurnPhase.AWAITING_ROLL ||
                !current.gameState.activePlayer.setup.isBot
            ) {
                return@launch
            }

            // Power-Up mode: bot considers activating a held power-up before rolling
            if (currentRuleSet.powerUpsEnabled && current.gameState.activePlayer.heldPowerUps.isNotEmpty()) {
                val botSetup = current.gameState.activePlayer.setup
                val action = botDecisionEngine.choosePowerUpBeforeRoll(
                    state = current.gameState,
                    ruleSet = currentRuleSet,
                    difficulty = botSetup.botDifficulty
                )
                if (action != null) {
                    applyBotPowerUpAction(action)
                    delayWhileUnpaused(1100L)
                }
            }

            current = _uiState.value
            if (current.gameState.isMatchFinished ||
                current.gameState.phase != TurnPhase.AWAITING_ROLL ||
                !current.gameState.activePlayer.setup.isBot
            ) {
                return@launch
            }
            executeDiceRollAndTurnFlow(isBot = true)
        }
    }

    private fun applyBotPowerUpAction(action: BotPowerUpAction) {
        val state = _uiState.value.gameState
        val botName = state.activePlayer.nickname
        when (action) {
            is BotPowerUpAction.UseShield -> {
                val updated = engine.activateShield(state, action.tokenId)
                soundManager.playPowerUpActivate(PowerUpType.SHIELD)
                hapticsManager.vibratePowerUpActivate(PowerUpType.SHIELD)
                _uiState.update { it.copy(gameState = updated) }
                showPowerUpBanner(
                    type = PowerUpType.SHIELD,
                    eyebrow = "BOT POWER-UP!",
                    title = "$botName used Shield!",
                    detail = "Token protected for 2 turns",
                    durationMs = 1250L
                )
            }

            BotPowerUpAction.UseDoubleDice -> {
                val updated = engine.activateDoubleDice(state)
                soundManager.playPowerUpActivate(PowerUpType.DOUBLE_DICE)
                hapticsManager.vibratePowerUpActivate(PowerUpType.DOUBLE_DICE)
                _uiState.update {
                    it.copy(
                        gameState = updated,
                        displayedSecondDiceValue = 6
                    )
                }
                showPowerUpBanner(
                    type = PowerUpType.DOUBLE_DICE,
                    eyebrow = "BOT POWER-UP!",
                    title = "$botName used Double Dice!",
                    detail = "Rolling two dice this turn!",
                    durationMs = 1250L
                )
            }

            is BotPowerUpAction.UseBombTrap -> {
                val updated = engine.placeBombTrap(state, action.trackIndex)
                soundManager.playPowerUpActivate(PowerUpType.BOMB_TRAP)
                hapticsManager.vibratePowerUpActivate(PowerUpType.BOMB_TRAP)
                _uiState.update { it.copy(gameState = updated) }
                showPowerUpBanner(
                    type = PowerUpType.BOMB_TRAP,
                    eyebrow = "WATCH OUT!",
                    title = "$botName hid a Bomb Trap!",
                    detail = "A hidden bomb was placed on the track!",
                    durationMs = 1250L
                )
            }
        }
    }

    private suspend fun executeDiceRollAndTurnFlow(isBot: Boolean) {
        val rolledValue1 = DiceCube3DEngine.rollRandomFace(diceRandom)
        val rolledValue2 = DiceCube3DEngine.rollRandomFace(diceRandom)

        soundManager.playDiceRoll()
        hapticsManager.vibrateDiceRoll()

        // Publish final rolled face(s) immediately with the trigger so the 3D cube tumbles smoothly to land on them
        _uiState.update {
            it.copy(
                isDiceRolling = true,
                powerUpTargeting = null,
                displayedDiceValue = rolledValue1,
                displayedSecondDiceValue = rolledValue2,
                diceRollTrigger = it.diceRollTrigger + 1
            )
        }

        delay(DiceCube3DEngine.ROLL_DURATION_MS.toLong() + 30L)

        _uiState.update {
            it.copy(
                isDiceRolling = false,
                displayedDiceValue = rolledValue1,
                displayedSecondDiceValue = rolledValue2
            )
        }

        commitDiceRollOutcome(
            rolledValue1 = rolledValue1,
            rolledValue2 = rolledValue2,
            isBot = isBot
        )
    }

    private suspend fun commitDiceRollOutcome(
        rolledValue1: Int,
        rolledValue2: Int?,
        isBot: Boolean
    ) {
        val outcome = engine.applyDiceRoll(_uiState.value.gameState, rolledValue1, rolledValue2)
        _uiState.update { it.copy(gameState = outcome.state) }
        persistCurrentState(outcome.state)

        when (outcome) {
            is RollOutcome.TripleSixCancelled -> {
                delayWhileUnpaused(1150L)
                val next = engine.commitTurnAdvance(_uiState.value.gameState)
                applyTurnTransitionSideEffects(next)
                scheduleBotTurnIfNeeded()
            }
            is RollOutcome.NoLegalMoves -> {
                delayWhileUnpaused(1000L)
                val next = engine.commitTurnAdvance(_uiState.value.gameState)
                applyTurnTransitionSideEffects(next)
                scheduleBotTurnIfNeeded()
            }
            is RollOutcome.SingleLegalMove -> {
                delayWhileUnpaused(if (isBot) 1050L else 220L)
                animateAndCommitMove(outcome.move)
                scheduleBotTurnIfNeeded()
            }
            is RollOutcome.MultipleLegalMoves -> {
                if (isBot) {
                    delayWhileUnpaused(1250L)
                    val snapshot = _uiState.value.gameState
                    val botSetup = snapshot.activePlayer.setup
                    if (!snapshot.isMatchFinished &&
                        snapshot.phase == TurnPhase.AWAITING_MOVE &&
                        botSetup.isBot
                    ) {
                        val chosenMove = botDecisionEngine.chooseMove(
                            state = snapshot,
                            ruleSet = currentRuleSet,
                            legalMoves = outcome.moves,
                            difficulty = botSetup.botDifficulty
                        )
                        animateAndCommitMove(chosenMove)
                        scheduleBotTurnIfNeeded()
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

    private suspend fun animateAndCommitMove(move: LegalMove) {
        val tokenRef = TokenRef(move.color, move.tokenId)
        var prevProgress = move.fromProgress
        var prevWrap51 = move.fromOuterWrap51

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
                move.leavesBase -> 180L
                move.stepPath.size > 6 -> 92L // Snappy hop for Double Dice / Dice Storm 7..12 rolls
                else -> 130L
            }
            val frameDelay = stepDurationMs / subFrames
            for (f in 1..subFrames) {
                val t = f.toFloat() / subFrames
                val interpR = fromR + (toR - fromR) * t
                val interpC = fromC + (toC - fromC) * t
                val arc = kotlin.math.sin(t * Math.PI).toFloat()
                _uiState.update {
                    it.copy(
                        isAnimatingMove = true,
                        animatingToken = tokenRef,
                        animatingRow = interpR,
                        animatingCol = interpC,
                        hopScale = 1f + arc * 0.34f
                    )
                }
                delay(frameDelay)
            }
            prevProgress = step.progress
            prevWrap51 = step.outerWrap51
        }

        // Portal teleport animation if the token landed on a linked portal
        val portalDest = move.teleportedToTrackIndex
        if (portalDest != null) {
            val (destR, destC) = LudoBoardGeometry.outerTrackCenter(portalDest)
            soundManager.playTokenHome()
            _uiState.update {
                it.copy(
                    animatingRow = destR,
                    animatingCol = destC,
                    hopScale = 1.45f
                )
            }
            delay(180L)
        }

        // Bomb trap detonation effect
        val hitBomb = move.triggeredBomb
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
            val victimName = _uiState.value.gameState.playerByColor(move.color)?.nickname ?: move.color.displayName
            val bomberName = _uiState.value.gameState.playerByColor(hitBomb.ownerColor)?.nickname
                ?: hitBomb.ownerColor.displayName
            showPowerUpBanner(
                type = PowerUpType.BOMB_TRAP,
                isExplosion = true,
                eyebrow = "KABOOM!",
                title = "Bomb Trap Triggered!",
                detail = "$victimName hit $bomberName's trap & went back to base!",
                durationMs = 1700L
            )
            delay(260L)
        }

        // Power-up tile pickup effect
        val pickedUp = move.collectedPowerUp
        if (pickedUp != null) {
            soundManager.playPowerUpPickup(pickedUp)
            hapticsManager.vibratePowerUpPickup(pickedUp)
            val pickerName = _uiState.value.gameState.playerByColor(move.color)?.nickname ?: move.color.displayName
            showPowerUpBanner(
                type = pickedUp,
                eyebrow = "POWER-UP COLLECTED!",
                title = pickedUp.title,
                detail = "$pickerName picked up ${pickedUp.title}!",
                durationMs = 1300L
            )
        }

        if (move.capturedTokens.isNotEmpty()) {
            val (hitR, hitC) = LudoBoardGeometry.tokenBoardPosition(
                move.color, move.tokenId, move.toProgress, move.toOuterWrap51
            )
            soundManager.playCapture()
            hapticsManager.vibrateCapture()
            _uiState.update {
                it.copy(
                    captureBurst = CaptureBurstState(
                        color = move.capturedTokens.first().color,
                        row = hitR,
                        col = hitC,
                        triggerKey = (it.captureBurst?.triggerKey ?: 0) + 1
                    )
                )
            }
        }

        if (move.reachesHome) {
            soundManager.playTokenHome()
            _uiState.update { it.copy(homeConfettiTrigger = it.homeConfettiTrigger + 1) }
        }

        val prevState = _uiState.value.gameState
        val outcome = engine.applyMove(prevState, move)
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
                    hopScale = 1f,
                    powerUpTargeting = null,
                    allTokensHomeCelebration = celebration ?: it.allTokensHomeCelebration
                )
            }
            clearSavedState()
        } else if (outcome.hasRemainingDiceMoves) {
            if (celebration != null) {
                soundManager.playWin()
                hapticsManager.vibrateWin()
            }
            // Same player still has another rolled die number left to play this turn
            _uiState.update {
                it.copy(
                    gameState = outcome.state,
                    isAnimatingMove = false,
                    animatingToken = null,
                    hopScale = 1f,
                    powerUpTargeting = null,
                    allTokensHomeCelebration = celebration ?: it.allTokensHomeCelebration
                )
            }
            persistCurrentState(outcome.state)

            val isBotTurn = outcome.state.activePlayer.setup.isBot
            if (isBotTurn) {
                delayWhileUnpaused(850L)
                val snapshot = _uiState.value.gameState
                if (!snapshot.isMatchFinished &&
                    snapshot.phase == TurnPhase.AWAITING_MOVE &&
                    snapshot.activePlayer.setup.isBot &&
                    snapshot.legalMoves.isNotEmpty()
                ) {
                    val nextBotMove = if (snapshot.legalMoves.size == 1) {
                        snapshot.legalMoves.first()
                    } else {
                        botDecisionEngine.chooseMove(
                            state = snapshot,
                            ruleSet = currentRuleSet,
                            legalMoves = snapshot.legalMoves,
                            difficulty = snapshot.activePlayer.setup.botDifficulty
                        )
                    }
                    animateAndCommitMove(nextBotMove)
                }
            } else if (outcome.state.legalMoves.size == 1) {
                // Only one forced token & step count remains for the human — play it smoothly
                delayWhileUnpaused(260L)
                val snapshot = _uiState.value.gameState
                if (!snapshot.isMatchFinished &&
                    snapshot.phase == TurnPhase.AWAITING_MOVE &&
                    snapshot.legalMoves.size == 1
                ) {
                    animateAndCommitMove(snapshot.legalMoves.first())
                }
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
                displayedSecondDiceValue = it.displayedSecondDiceValue ?: 6,
                powerUpTargeting = null,
                allTokensHomeCelebration = celebration ?: it.allTokensHomeCelebration
            )
        }
        persistCurrentState(nextState)

        if (resolvedChaos != null) {
            soundManager.playTokenHome()
            hapticsManager.vibrateCapture()
            bannerJob?.cancel()
            bannerJob = viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        activeChaosBanner = ChaosAnnouncementState(
                            event = resolvedChaos,
                            detail = nextState.chaosState.announcementDetail,
                            triggerKey = newAnnouncementId
                        )
                    )
                }
                delay(2500L)
                _uiState.update { it.copy(activeChaosBanner = null) }
            }
        } else if (resolvedElim != null) {
            soundManager.playCapture()
            hapticsManager.vibrateCapture()
            bannerJob?.cancel()
            bannerJob = viewModelScope.launch {
                _uiState.update { it.copy(eliminatedBannerColor = resolvedElim) }
                delay(2400L)
                _uiState.update { it.copy(eliminatedBannerColor = null) }
            }
        }
    }

    private fun resetTimerForNextRoll() {
        remainingMs = GameUiState.TURN_DURATION_SECONDS * 1000L
        lastWarnedSecond = -1
        _uiState.update {
            it.copy(
                remainingSeconds = GameUiState.TURN_DURATION_SECONDS,
                timerProgress = 1f
            )
        }
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val tickMs = 100L
            while (true) {
                delay(tickMs)
                val s = _uiState.value
                if (s.gameState.isMatchFinished) break
                val timerPaused = s.isPaused ||
                    s.isGuideOverlayVisible ||
                    s.allTokensHomeCelebration != null ||
                    s.isDiceRolling ||
                    s.isAnimatingMove ||
                    s.activeChaosBanner != null ||
                    s.eliminatedBannerColor != null ||
                    s.gameState.phase == TurnPhase.NO_LEGAL_MOVES ||
                    s.gameState.phase == TurnPhase.TURN_CANCELLED_TRIPLE_SIX

                if (timerPaused) continue

                remainingMs = (remainingMs - tickMs).coerceAtLeast(0L)
                val wholeSecs = ((remainingMs + 999L) / 1000L).toInt()
                    .coerceIn(0, GameUiState.TURN_DURATION_SECONDS)
                val progress = (remainingMs.toFloat() / (GameUiState.TURN_DURATION_SECONDS * 1000L))
                    .coerceIn(0f, 1f)

                if (wholeSecs in 1..5 && wholeSecs != lastWarnedSecond) {
                    lastWarnedSecond = wholeSecs
                    soundManager.playTimerWarning()
                }

                if (remainingMs <= 0L) {
                    turnActionJob?.cancel()
                    botTurnJob?.cancel()
                    val timedOutState = engine.handleTurnTimeout(_uiState.value.gameState)
                    applyTurnTransitionSideEffects(timedOutState)
                    scheduleBotTurnIfNeeded()
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

    private fun startTimedMatchClockLoop() {
        timedClockJob?.cancel()
        timedClockJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val s = _uiState.value
                if (s.gameState.isMatchFinished) break
                if (s.isPaused || s.isGuideOverlayVisible || s.allTokensHomeCelebration != null) continue

                val nextRemaining = (s.gameState.timedRemainingSeconds - 1).coerceAtLeast(0)
                if (nextRemaining <= 0) {
                    timerJob?.cancel()
                    turnActionJob?.cancel()
                    botTurnJob?.cancel()
                    val expiredState = engine.handleTimedClockExpired(_uiState.value.gameState)
                    soundManager.playWin()
                    hapticsManager.vibrateWin()
                    _uiState.update {
                        it.copy(
                            gameState = expiredState,
                            isAnimatingMove = false,
                            isDiceRolling = false,
                            animatingToken = null
                        )
                    }
                    clearSavedState()
                    break
                } else {
                    _uiState.update {
                        it.copy(
                            gameState = it.gameState.copy(timedRemainingSeconds = nextRemaining)
                        )
                    }
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
