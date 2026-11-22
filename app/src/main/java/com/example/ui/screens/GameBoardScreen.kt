package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.PlayerColor
import com.example.engine.PlayerState
import com.example.engine.PowerUpType
import com.example.engine.RuleSetRegistry
import com.example.engine.TokenRef
import com.example.engine.TurnGuardResult
import com.example.engine.TurnManager
import com.example.engine.TurnPhase
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.components.ConfettiOverlay
import com.example.ui.game.AllTokensHomeCelebrationOverlay
import com.example.ui.game.DieStepChoicePopup
import com.example.ui.game.GameUiState
import com.example.ui.game.LudoBoardCanvas
import com.example.ui.game.LudoDoubleDice3D
import com.example.ui.game.ModeRulesDialog
import com.example.ui.game.PlayerHudCard
import com.example.ui.game.PowerUpActivationBanner
import com.example.ui.game.PowerUpTargetingState
import com.example.ui.theme.CobaltDark
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralPink
import com.example.ui.theme.CoralPinkDark
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.InkDark
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenDark
import com.example.ui.theme.SkyTopTint
import com.example.ui.theme.SunnyGold
import com.example.ui.theme.SunnyGoldDark
import com.example.ui.theme.palette
import kotlinx.coroutines.delay

@Composable
fun GameBoardScreen(
    uiState: GameUiState,
    showPowerUpGuide: Boolean = false,
    onOpenPowerUpGuide: () -> Unit = {},
    onDismissPowerUpGuide: () -> Unit = {},
    onPowerUpTapped: (PowerUpType) -> Unit = {},
    onCancelPowerUpTargeting: () -> Unit = {},
    onShieldTokenSelected: (Int) -> Unit = {},
    onBombSquareSelected: (Int) -> Unit = {},
    isOnlineMode: Boolean = false,
    onlineRoomCode: String? = null,
    onlineLoading: Boolean = false,
    onlineErrorMessage: String? = null,
    isLocalPlayersTurn: Boolean = true,
    localViewerColor: PlayerColor? = null,
    onRollDice: () -> Unit,
    onDiceRollStarted: (() -> Unit)? = null,
    onDiceRollResult: ((Int, Int?) -> Unit)? = null,
    onSelectDieValue: (Int) -> Unit = {},
    onTokenTapped: (TokenRef) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onEndMatchEarly: () -> Unit,
    onQuitToMenu: () -> Unit,
    onMatchFinished: () -> Unit,
    onDismissAllTokensHomeCelebration: () -> Unit = {}
) {
    val gameState = uiState.gameState
    var hadMatchOverCelebration by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.allTokensHomeCelebration?.triggerKey) {
        if (uiState.allTokensHomeCelebration?.isMatchOver == true) {
            hadMatchOverCelebration = true
        }
    }

    LaunchedEffect(gameState.isMatchFinished, uiState.allTokensHomeCelebration) {
        if (gameState.isMatchFinished && uiState.allTokensHomeCelebration == null) {
            if (!hadMatchOverCelebration) {
                delay(750L)
            }
            onMatchFinished()
        }
    }

    val activePlayer = gameState.activePlayer
    val activePalette = activePlayer.color.palette()
    val activeRuleSet = remember(gameState.mode) { RuleSetRegistry.forMode(gameState.mode) }
    val turnCycle = remember(
        gameState.players,
        gameState.activePlayerIndex,
        gameState.finishOrder,
        gameState.isMatchFinished,
        activeRuleSet
    ) {
        TurnManager.upcomingTurnCycle(gameState, activeRuleSet)
    }

    val playableDice = gameState.playableDiceValues()
    val activeSelectedDie = gameState.effectiveSelectedDieValue()
    val canChooseDieStep = !uiState.isPaused &&
        !showPowerUpGuide &&
        uiState.allTokensHomeCelebration == null &&
        !uiState.isDiceRolling &&
        !uiState.isAnimatingMove &&
        !activePlayer.setup.isBot &&
        isLocalPlayersTurn &&
        gameState.phase == TurnPhase.AWAITING_MOVE &&
        !gameState.wasDoubleDiceRoll &&
        gameState.rolledDice.size >= 2

    val hasMultipleDieChoices = canChooseDieStep && playableDice.size >= 2
    var isDieChoicePopupVisible by remember { mutableStateOf(false) }
    val dieChoiceRollKey = remember(
        gameState.activePlayerIndex,
        gameState.rolledDice,
        gameState.remainingDice,
        uiState.diceRollTrigger
    ) {
        "${gameState.activePlayerIndex}:${gameState.rolledDice}:${gameState.remainingDice}:${uiState.diceRollTrigger}"
    }

    LaunchedEffect(hasMultipleDieChoices, dieChoiceRollKey) {
        isDieChoicePopupVisible = hasMultipleDieChoices
    }

    BackHandler(enabled = !gameState.isMatchFinished || uiState.allTokensHomeCelebration != null) {
        when {
            uiState.allTokensHomeCelebration != null -> onDismissAllTokensHomeCelebration()
            isDieChoicePopupVisible -> isDieChoicePopupVisible = false
            showPowerUpGuide -> onDismissPowerUpGuide()
            uiState.powerUpTargeting != null -> onCancelPowerUpTargeting()
            uiState.isPaused -> onResume()
            else -> onPause()
        }
    }

    var turnGuardNotice by remember { mutableStateOf<String?>(null) }
    var turnGuardTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(turnGuardTrigger) {
        if (turnGuardNotice != null) {
            delay(1800L)
            turnGuardNotice = null
        }
    }
    LaunchedEffect(gameState.activePlayerIndex, gameState.phase) {
        turnGuardNotice = null
    }

    val canUsePowerUpsNow = gameState.mode == GameModeId.POWER_UP &&
        !uiState.isPaused &&
        !showPowerUpGuide &&
        uiState.allTokensHomeCelebration == null &&
        !uiState.isDiceRolling &&
        !uiState.isAnimatingMove &&
        !activePlayer.setup.isBot &&
        isLocalPlayersTurn &&
        gameState.phase == TurnPhase.AWAITING_ROLL

    fun rankFor(color: PlayerColor): Int? {
        val idx = gameState.finishOrder.indexOf(color)
        return if (idx >= 0) idx + 1 else null
    }

    val handleOutOfTurnTap: (TokenRef?) -> Unit = { tappedRef ->
        val msg = when {
            activePlayer.setup.isBot ->
                "Hold on — ${activePlayer.nickname} (Bot) is taking their turn!"
            isOnlineMode && !isLocalPlayersTurn ->
                "Not your turn — waiting for ${activePlayer.nickname}!"
            tappedRef != null -> {
                when (
                    val guard = TurnManager.validateTokenTap(
                        state = gameState,
                        ruleSet = activeRuleSet,
                        tokenRef = tappedRef,
                        actingColor = activePlayer.color
                    )
                ) {
                    is TurnGuardResult.NotPlayersTurn -> {
                        val tappedOwner = gameState.playerByColor(guard.attemptedColor)?.nickname
                            ?: guard.attemptedColor.displayName
                        val expectedOwner = gameState.playerByColor(guard.expectedColor)?.nickname
                            ?: guard.expectedColor.displayName
                        "Not $tappedOwner's turn — it's $expectedOwner's turn!"
                    }
                    is TurnGuardResult.InvalidPhase -> {
                        if (guard.actual == TurnPhase.AWAITING_ROLL) {
                            "Roll the die first, ${activePlayer.nickname}!"
                        } else {
                            "Wait for the next turn!"
                        }
                    }
                    is TurnGuardResult.IllegalTokenMove ->
                        "That token can't move with a ${gameState.lastDiceValue ?: "roll"} — pick a glowing token!"
                    else -> null
                }
            }
            else -> "Wait for ${activePlayer.nickname}'s turn!"
        }
        if (msg != null) {
            turnGuardNotice = msg
            turnGuardTrigger += 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(SkyTopTint, CreamBackground))
            )
            .safeDrawingPadding()
            .testTag("game_board_screen")
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscapeExpanded = maxWidth >= 680.dp && maxWidth > maxHeight
            val isCompactPortrait = !isLandscapeExpanded && maxHeight < 760.dp

            if (isLandscapeExpanded) {
                // Expanded / Landscape canonical split layout: full-height board on left, HUD + dice pane on right
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoardStageBox(
                        uiState = uiState,
                        gameState = gameState,
                        activePalettePrimary = activePalette.primary,
                        isLocalPlayersTurn = isLocalPlayersTurn,
                        localViewerColor = localViewerColor,
                        turnGuardNotice = turnGuardNotice,
                        onTokenTapped = onTokenTapped,
                        onShieldTokenSelected = onShieldTokenSelected,
                        onBombSquareSelected = onBombSquareSelected,
                        onOutOfTurnTap = handleOutOfTurnTap,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    Column(
                        modifier = Modifier
                            .widthIn(min = 290.dp, max = 360.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TopControlsRow(
                                gameState = gameState,
                                isOnlineMode = isOnlineMode,
                                onlineRoomCode = onlineRoomCode,
                                onPause = onPause,
                                onEndMatchEarly = onEndMatchEarly,
                                onOpenPowerUpGuide = onOpenPowerUpGuide
                            )

                            OnlineErrorBanner(onlineErrorMessage)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PlayerHudCard(
                                    player = gameState.playerByColor(PlayerColor.RED),
                                    mode = gameState.mode,
                                    isActive = activePlayer.color == PlayerColor.RED && !gameState.isMatchFinished,
                                    finishRank = rankFor(PlayerColor.RED),
                                    timerProgress = uiState.timerProgress,
                                    remainingSeconds = uiState.remainingSeconds,
                                    alignEnd = false,
                                    canUsePowerUps = canUsePowerUpsNow,
                                    activeTargetingType = uiState.powerUpTargeting?.powerUpType,
                                    onPowerUpClick = onPowerUpTapped,
                                    compact = true,
                                    modifier = Modifier.weight(1f)
                                )
                                PlayerHudCard(
                                    player = gameState.playerByColor(PlayerColor.GREEN),
                                    mode = gameState.mode,
                                    isActive = activePlayer.color == PlayerColor.GREEN && !gameState.isMatchFinished,
                                    finishRank = rankFor(PlayerColor.GREEN),
                                    timerProgress = uiState.timerProgress,
                                    remainingSeconds = uiState.remainingSeconds,
                                    alignEnd = true,
                                    canUsePowerUps = canUsePowerUpsNow,
                                    activeTargetingType = uiState.powerUpTargeting?.powerUpType,
                                    onPowerUpClick = onPowerUpTapped,
                                    compact = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PlayerHudCard(
                                    player = gameState.playerByColor(PlayerColor.BLUE),
                                    mode = gameState.mode,
                                    isActive = activePlayer.color == PlayerColor.BLUE && !gameState.isMatchFinished,
                                    finishRank = rankFor(PlayerColor.BLUE),
                                    timerProgress = uiState.timerProgress,
                                    remainingSeconds = uiState.remainingSeconds,
                                    alignEnd = false,
                                    canUsePowerUps = canUsePowerUpsNow,
                                    activeTargetingType = uiState.powerUpTargeting?.powerUpType,
                                    onPowerUpClick = onPowerUpTapped,
                                    compact = true,
                                    modifier = Modifier.weight(1f)
                                )
                                PlayerHudCard(
                                    player = gameState.playerByColor(PlayerColor.YELLOW),
                                    mode = gameState.mode,
                                    isActive = activePlayer.color == PlayerColor.YELLOW && !gameState.isMatchFinished,
                                    finishRank = rankFor(PlayerColor.YELLOW),
                                    timerProgress = uiState.timerProgress,
                                    remainingSeconds = uiState.remainingSeconds,
                                    alignEnd = true,
                                    canUsePowerUps = canUsePowerUpsNow,
                                    activeTargetingType = uiState.powerUpTargeting?.powerUpType,
                                    onPowerUpClick = onPowerUpTapped,
                                    compact = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        BottomTurnDock(
                            uiState = uiState,
                            gameState = gameState,
                            activePlayer = activePlayer,
                            turnCycle = turnCycle,
                            playableDice = playableDice,
                            activeSelectedDie = activeSelectedDie,
                            canChooseDieStep = canChooseDieStep,
                            hasMultipleDieChoices = hasMultipleDieChoices,
                            showPowerUpGuide = showPowerUpGuide,
                            isOnlineMode = isOnlineMode,
                            isLocalPlayersTurn = isLocalPlayersTurn,
                            dieSize = 54.dp,
                            onOpenDieChoicePopup = { isDieChoicePopupVisible = true },
                            onCancelPowerUpTargeting = onCancelPowerUpTargeting,
                            onSelectDieValue = onSelectDieValue,
                            onRollDice = onRollDice,
                            onDiceRollStarted = onDiceRollStarted,
                            onDiceRollResult = onDiceRollResult
                        )
                    }
                }
            } else {
                // Portrait / Mobile-First layout: maximize board width & vertical real estate
                val hudHorizontalPad = 8.dp
                val boardHorizontalPad = 4.dp
                val sectionGap = if (isCompactPortrait) 4.dp else 6.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = if (isCompactPortrait) 4.dp else 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TopControlsRow(
                        gameState = gameState,
                        isOnlineMode = isOnlineMode,
                        onlineRoomCode = onlineRoomCode,
                        onPause = onPause,
                        onEndMatchEarly = onEndMatchEarly,
                        onOpenPowerUpGuide = onOpenPowerUpGuide,
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .padding(horizontal = hudHorizontalPad)
                    )

                    OnlineErrorBanner(
                        onlineErrorMessage = onlineErrorMessage,
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .padding(horizontal = hudHorizontalPad)
                    )

                    Spacer(Modifier.height(sectionGap))

                    // Top Corner Player HUDs (RED = Top-Left, GREEN = Top-Right)
                    Row(
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxWidth()
                            .padding(horizontal = hudHorizontalPad),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayerHudCard(
                            player = gameState.playerByColor(PlayerColor.RED),
                            mode = gameState.mode,
                            isActive = activePlayer.color == PlayerColor.RED && !gameState.isMatchFinished,
                            finishRank = rankFor(PlayerColor.RED),
                            timerProgress = uiState.timerProgress,
                            remainingSeconds = uiState.remainingSeconds,
                            alignEnd = false,
                            canUsePowerUps = canUsePowerUpsNow,
                            activeTargetingType = uiState.powerUpTargeting?.powerUpType,
                            onPowerUpClick = onPowerUpTapped,
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                        PlayerHudCard(
                            player = gameState.playerByColor(PlayerColor.GREEN),
                            mode = gameState.mode,
                            isActive = activePlayer.color == PlayerColor.GREEN && !gameState.isMatchFinished,
                            finishRank = rankFor(PlayerColor.GREEN),
                            timerProgress = uiState.timerProgress,
                            remainingSeconds = uiState.remainingSeconds,
                            alignEnd = true,
                            canUsePowerUps = canUsePowerUpsNow,
                            activeTargetingType = uiState.powerUpTargeting?.powerUpType,
                            onPowerUpClick = onPowerUpTapped,
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(sectionGap))

                    // Expanded Center 15x15 Ludo Board taking all remaining width & height
                    BoardStageBox(
                        uiState = uiState,
                        gameState = gameState,
                        activePalettePrimary = activePalette.primary,
                        isLocalPlayersTurn = isLocalPlayersTurn,
                        localViewerColor = localViewerColor,
                        turnGuardNotice = turnGuardNotice,
                        onTokenTapped = onTokenTapped,
                        onShieldTokenSelected = onShieldTokenSelected,
                        onBombSquareSelected = onBombSquareSelected,
                        onOutOfTurnTap = handleOutOfTurnTap,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = boardHorizontalPad)
                    )

                    Spacer(Modifier.height(sectionGap))

                    // Bottom Corner Player HUDs (BLUE = Bottom-Left, YELLOW = Bottom-Right)
                    Row(
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxWidth()
                            .padding(horizontal = hudHorizontalPad),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayerHudCard(
                            player = gameState.playerByColor(PlayerColor.BLUE),
                            mode = gameState.mode,
                            isActive = activePlayer.color == PlayerColor.BLUE && !gameState.isMatchFinished,
                            finishRank = rankFor(PlayerColor.BLUE),
                            timerProgress = uiState.timerProgress,
                            remainingSeconds = uiState.remainingSeconds,
                            alignEnd = false,
                            canUsePowerUps = canUsePowerUpsNow,
                            activeTargetingType = uiState.powerUpTargeting?.powerUpType,
                            onPowerUpClick = onPowerUpTapped,
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                        PlayerHudCard(
                            player = gameState.playerByColor(PlayerColor.YELLOW),
                            mode = gameState.mode,
                            isActive = activePlayer.color == PlayerColor.YELLOW && !gameState.isMatchFinished,
                            finishRank = rankFor(PlayerColor.YELLOW),
                            timerProgress = uiState.timerProgress,
                            remainingSeconds = uiState.remainingSeconds,
                            alignEnd = true,
                            canUsePowerUps = canUsePowerUpsNow,
                            activeTargetingType = uiState.powerUpTargeting?.powerUpType,
                            onPowerUpClick = onPowerUpTapped,
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(sectionGap))

                    // Slim Bottom 2-Dice Dock (no fixed die-step selector box underneath)
                    BottomTurnDock(
                        uiState = uiState,
                        gameState = gameState,
                        activePlayer = activePlayer,
                        turnCycle = turnCycle,
                        playableDice = playableDice,
                        activeSelectedDie = activeSelectedDie,
                        canChooseDieStep = canChooseDieStep,
                        hasMultipleDieChoices = hasMultipleDieChoices,
                        showPowerUpGuide = showPowerUpGuide,
                        isOnlineMode = isOnlineMode,
                        isLocalPlayersTurn = isLocalPlayersTurn,
                        dieSize = if (isCompactPortrait) 50.dp else 54.dp,
                        onOpenDieChoicePopup = { isDieChoicePopupVisible = true },
                        onCancelPowerUpTargeting = onCancelPowerUpTargeting,
                        onSelectDieValue = onSelectDieValue,
                        onRollDice = onRollDice,
                        onDiceRollStarted = onDiceRollStarted,
                        onDiceRollResult = onDiceRollResult,
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .padding(horizontal = hudHorizontalPad)
                    )
                }
            }
        }

        // Floating Popup for choosing which rolled die number to move first
        DieStepChoicePopup(
            visible = isDieChoicePopupVisible && hasMultipleDieChoices && !onlineLoading,
            playerName = activePlayer.nickname,
            rolledDice = gameState.rolledDice,
            remainingDice = gameState.remainingDice,
            selectedDieValue = activeSelectedDie,
            playableDiceValues = playableDice,
            activeColor = activePlayer.color,
            onSelectDieValue = onSelectDieValue,
            onDismiss = { isDieChoicePopupVisible = false }
        )

        if (uiState.isPaused) {
            PauseMenuDialog(
                isOnlineMode = isOnlineMode,
                onResume = onResume,
                onRestart = onRestart,
                onQuit = onQuitToMenu
            )
        }

        if (onlineLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CreamBackground.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                CartoonCard(borderColor = CobaltPrimary, cornerRadius = 26.dp) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(color = CobaltPrimary)
                        Text(
                            text = "Syncing live match...",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (showPowerUpGuide && !onlineLoading) {
            ModeRulesDialog(
                mode = gameState.mode,
                onDismiss = onDismissPowerUpGuide
            )
        }

        AnimatedVisibility(
            visible = uiState.allTokensHomeCelebration != null && !onlineLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            uiState.allTokensHomeCelebration?.let { celebration ->
                AllTokensHomeCelebrationOverlay(
                    celebration = celebration,
                    onDismiss = onDismissAllTokensHomeCelebration
                )
            }
        }
    }
}

@Composable
private fun TopControlsRow(
    gameState: GameState,
    isOnlineMode: Boolean,
    onlineRoomCode: String?,
    onPause: () -> Unit,
    onEndMatchEarly: () -> Unit,
    onOpenPowerUpGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePalette = gameState.activePlayer.color.palette()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CartoonIconButton(
            icon = Icons.Filled.Pause,
            contentDescription = stringResource(R.string.cd_pause),
            onClick = onPause,
            modifier = Modifier.testTag("pause_button")
        )

        Spacer(Modifier.width(6.dp))

        if (isOnlineMode && !onlineRoomCode.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = CobaltPrimary,
                border = BorderStroke(1.5.dp, Color.White),
                modifier = Modifier.testTag("online_room_code_badge")
            ) {
                Text(
                    text = onlineRoomCode,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
        }

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(999.dp),
            color = activePalette.light,
            border = BorderStroke(1.8.dp, activePalette.primary)
        ) {
            Text(
                text = gameState.lastEventMessage,
                style = MaterialTheme.typography.labelLarge,
                color = InkDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        if (gameState.mode == GameModeId.TIMED && !gameState.isMatchFinished) {
            Spacer(Modifier.width(6.dp))
            val mins = gameState.timedRemainingSeconds / 60
            val secs = gameState.timedRemainingSeconds % 60
            val urgent = gameState.timedRemainingSeconds <= 30
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (urgent) CoralPink else CobaltPrimary,
                border = BorderStroke(2.dp, Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = "Match clock",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "%02d:%02d".format(mins, secs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        } else if (gameState.mode == GameModeId.CHAOS && !gameState.isMatchFinished) {
            Spacer(Modifier.width(6.dp))
            val roundsUntil = 3 - (gameState.fullRoundsCompleted % 3)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = CoralPink.copy(alpha = 0.16f),
                border = BorderStroke(1.5.dp, CoralPink)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Whatshot,
                        contentDescription = null,
                        tint = CoralPink,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${roundsUntil}r",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkDark
                    )
                }
            }
        } else if (gameState.mode == GameModeId.ELIMINATION && !gameState.isMatchFinished) {
            Spacer(Modifier.width(6.dp))
            val roundsUntil = 5 - (gameState.fullRoundsCompleted % 5)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFB5179E).copy(alpha = 0.14f),
                border = BorderStroke(1.5.dp, Color(0xFFB5179E))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Gavel,
                        contentDescription = null,
                        tint = Color(0xFFB5179E),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${roundsUntil}r",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkDark
                    )
                }
            }
        } else if (gameState.hasWinner && !gameState.isMatchFinished && !isOnlineMode) {
            Spacer(Modifier.width(6.dp))
            Surface(
                onClick = onEndMatchEarly,
                modifier = Modifier.testTag("end_match_early_button"),
                shape = RoundedCornerShape(999.dp),
                color = SunnyGold,
                border = BorderStroke(2.dp, SunnyGoldDark)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = InkDark,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "End",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkDark
                    )
                }
            }
        }

        if (!gameState.isMatchFinished) {
            Spacer(Modifier.width(6.dp))
            CartoonIconButton(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = stringResource(R.string.cd_mode_rules_help),
                onClick = onOpenPowerUpGuide,
                containerColor = if (gameState.mode == GameModeId.POWER_UP) Color(0xFF8338EC) else Color.White,
                iconTint = if (gameState.mode == GameModeId.POWER_UP) Color.White else CobaltPrimary,
                borderColor = if (gameState.mode == GameModeId.POWER_UP) Color(0xFF8338EC) else CobaltPrimary.copy(alpha = 0.4f),
                modifier = Modifier.testTag("mode_rules_help_button")
            )
        }
    }
}

@Composable
private fun OnlineErrorBanner(
    onlineErrorMessage: String?,
    modifier: Modifier = Modifier
) {
    if (!onlineErrorMessage.isNullOrBlank()) {
        Spacer(Modifier.height(4.dp))
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = CoralPink.copy(alpha = 0.14f),
            border = BorderStroke(1.5.dp, CoralPink)
        ) {
            Text(
                text = onlineErrorMessage,
                style = MaterialTheme.typography.labelMedium,
                color = InkDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun BoardStageBox(
    uiState: GameUiState,
    gameState: GameState,
    activePalettePrimary: Color,
    isLocalPlayersTurn: Boolean,
    localViewerColor: PlayerColor?,
    turnGuardNotice: String?,
    onTokenTapped: (TokenRef) -> Unit,
    onShieldTokenSelected: (Int) -> Unit,
    onBombSquareSelected: (Int) -> Unit,
    onOutOfTurnTap: (TokenRef?) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        LudoBoardCanvas(
            uiState = uiState,
            onTokenTapped = onTokenTapped,
            onShieldTokenSelected = onShieldTokenSelected,
            onBombSquareSelected = onBombSquareSelected,
            isLocalPlayersTurn = isLocalPlayersTurn,
            onOutOfTurnTap = onOutOfTurnTap,
            localViewerColor = localViewerColor,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = turnGuardNotice != null,
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.7f, stiffness = 420f)),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp, start = 14.dp, end = 14.dp)
        ) {
            turnGuardNotice?.let { notice ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = InkDark.copy(alpha = 0.92f),
                    border = BorderStroke(1.5.dp, activePalettePrimary),
                    shadowElevation = 8.dp,
                    modifier = Modifier.testTag("turn_guard_notice")
                ) {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        ConfettiOverlay(
            triggerKey = uiState.homeConfettiTrigger,
            burstFromCenter = true,
            modifier = Modifier.matchParentSize()
        )

        AnimatedVisibility(
            visible = uiState.activeChaosBanner != null,
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.6f, stiffness = 360f)),
            exit = fadeOut() + scaleOut()
        ) {
            uiState.activeChaosBanner?.let { banner ->
                ChaosAnnouncementBanner(
                    title = banner.event.title,
                    detail = banner.detail
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.activePowerUpBanner != null && uiState.activeChaosBanner == null,
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.58f, stiffness = 400f)),
            exit = fadeOut() + scaleOut()
        ) {
            uiState.activePowerUpBanner?.let { banner ->
                PowerUpActivationBanner(banner = banner)
            }
        }

        AnimatedVisibility(
            visible = uiState.eliminatedBannerColor != null,
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.6f, stiffness = 360f)),
            exit = fadeOut() + scaleOut()
        ) {
            uiState.eliminatedBannerColor?.let { elimColor ->
                val elimName = gameState.playerByColor(elimColor)?.nickname ?: elimColor.displayName
                EliminationAnnouncementBanner(
                    playerName = elimName,
                    color = elimColor
                )
            }
        }
    }
}

@Composable
private fun BottomTurnDock(
    uiState: GameUiState,
    gameState: GameState,
    activePlayer: PlayerState,
    turnCycle: List<PlayerState>,
    playableDice: List<Int>,
    activeSelectedDie: Int?,
    canChooseDieStep: Boolean,
    hasMultipleDieChoices: Boolean,
    showPowerUpGuide: Boolean,
    isOnlineMode: Boolean,
    isLocalPlayersTurn: Boolean,
    dieSize: Dp,
    onOpenDieChoicePopup: () -> Unit,
    onCancelPowerUpTargeting: () -> Unit,
    onSelectDieValue: (Int) -> Unit,
    onRollDice: () -> Unit,
    onDiceRollStarted: (() -> Unit)?,
    onDiceRollResult: ((Int, Int?) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val activePalette = activePlayer.color.palette()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = BorderStroke(2.2.dp, activePalette.primary),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val isRollingForPartner = gameState.mode == GameModeId.TEAMS_2V2 && activePlayer.allTokensHome
                val partnerName = if (isRollingForPartner) {
                    gameState.playerByColor(activePlayer.color.opposite())?.nickname
                } else null

                Text(
                    text = when {
                        activePlayer.setup.isBot ->
                            "${activePlayer.nickname} (${activePlayer.setup.botDifficulty.displayName} Bot)"
                        partnerName != null ->
                            "${activePlayer.nickname} → $partnerName"
                        else ->
                            "${activePlayer.nickname}'s Turn"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = activePalette.dark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val hintText = when {
                    uiState.powerUpTargeting is PowerUpTargetingState.ShieldTarget ->
                        "Tap one of your tokens to Shield!"
                    uiState.powerUpTargeting is PowerUpTargetingState.BombPlacement ->
                        "Tap a glowing square to hide your Bomb!"
                    uiState.isDiceRolling -> "Rolling two dice..."
                    uiState.isAnimatingMove -> "Hopping..."
                    activePlayer.setup.isBot && gameState.phase == TurnPhase.AWAITING_ROLL ->
                        "Bot is thinking..."
                    activePlayer.setup.isBot && gameState.phase == TurnPhase.AWAITING_MOVE ->
                        "Bot is choosing steps & token..."
                    isOnlineMode && !isLocalPlayersTurn ->
                        "Waiting for ${activePlayer.nickname}..."
                    gameState.phase == TurnPhase.AWAITING_ROLL -> when {
                        activePlayer.doubleDiceActive -> "Double Dice armed! Tap to roll!"
                        partnerName != null -> "Tap dice to roll for $partnerName!"
                        gameState.mode == GameModeId.POWER_UP && activePlayer.heldPowerUps.isNotEmpty() ->
                            "Use a power-up or tap dice!"
                        else -> "Tap the dice to roll!"
                    }
                    gameState.phase == TurnPhase.AWAITING_MOVE -> when {
                        activeSelectedDie != null ->
                            "Moving $activeSelectedDie — tap a glowing token!"
                        else -> "Tap a glowing token!"
                    }
                    gameState.phase == TurnPhase.TURN_CANCELLED_TRIPLE_SIX -> "Three 6s! Turn cancelled!"
                    gameState.phase == TurnPhase.NO_LEGAL_MOVES -> "No legal moves..."
                    else -> "Match complete!"
                }

                Text(
                    text = hintText,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (hasMultipleDieChoices) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        onClick = onOpenDieChoicePopup,
                        shape = RoundedCornerShape(999.dp),
                        color = activePalette.light,
                        border = BorderStroke(1.5.dp, activePalette.primary),
                        modifier = Modifier.testTag("open_die_choice_popup_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Casino,
                                contentDescription = null,
                                tint = activePalette.dark,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${stringResource(R.string.die_choice_switch_cta)} (${playableDice.joinToString(" / ")})",
                                style = MaterialTheme.typography.labelSmall,
                                color = activePalette.dark,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                } else if (turnCycle.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    TurnOrderCycleStrip(
                        cycle = turnCycle,
                        modifier = Modifier.testTag("turn_order_cycle_strip")
                    )
                }
            }

            if (uiState.powerUpTargeting != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = onCancelPowerUpTargeting,
                    modifier = Modifier.testTag("cancel_powerup_targeting_button"),
                    shape = RoundedCornerShape(14.dp),
                    color = CoralPink.copy(alpha = 0.14f),
                    border = BorderStroke(1.5.dp, CoralPink)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = CoralPink,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.powerup_cancel_targeting),
                            style = MaterialTheme.typography.labelLarge,
                            color = CoralPink,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                if (gameState.wasDiceDoubled && gameState.lastDiceValue != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SunnyGold.copy(alpha = 0.28f),
                        border = BorderStroke(1.5.dp, SunnyGoldDark)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = InkDark,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "2×=${gameState.lastDiceValue}",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkDark
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                } else if (gameState.wasDoubleDiceRoll && gameState.lastDiceValue != null && !uiState.isDiceRolling) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SunnyGold.copy(alpha = 0.30f),
                        border = BorderStroke(1.5.dp, SunnyGoldDark)
                    ) {
                        Text(
                            text = "=${gameState.lastDiceValue}",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkDark,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                } else {
                    Spacer(Modifier.width(8.dp))
                }

                val canRoll = !uiState.isPaused &&
                    !showPowerUpGuide &&
                    uiState.allTokensHomeCelebration == null &&
                    !uiState.isDiceRolling &&
                    !uiState.isAnimatingMove &&
                    !activePlayer.setup.isBot &&
                    isLocalPlayersTurn &&
                    uiState.activeChaosBanner == null &&
                    uiState.eliminatedBannerColor == null &&
                    gameState.phase == TurnPhase.AWAITING_ROLL

                val useSelfRollingDice = !isOnlineMode && onDiceRollResult != null
                LudoDoubleDice3D(
                    firstValue = uiState.displayedDiceValue,
                    secondValue = uiState.displayedSecondDiceValue ?: 6,
                    rollTrigger = uiState.diceRollTrigger,
                    isRolling = uiState.isDiceRolling,
                    canRoll = canRoll,
                    activeColor = activePlayer.color,
                    rolledDice = gameState.rolledDice,
                    remainingDice = gameState.remainingDice,
                    selectedDieValue = activeSelectedDie,
                    playableDiceValues = playableDice,
                    canSelectDie = canChooseDieStep,
                    onSelectDieValue = onSelectDieValue,
                    onRollClick = onRollDice,
                    onRollStarted = if (useSelfRollingDice) onDiceRollStarted else null,
                    onDoubleRollResult = if (useSelfRollingDice) {
                        { v1, v2 -> onDiceRollResult.invoke(v1, v2) }
                    } else null,
                    dieSize = dieSize
                )
            }
        }
    }
}

@Composable
private fun ChaosAnnouncementBanner(
    title: String,
    detail: String
) {
    CartoonCard(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .testTag("chaos_announcement_banner"),
        borderColor = CoralPink,
        containerColor = Color.White,
        cornerRadius = 26.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Whatshot,
                contentDescription = null,
                tint = CoralPink,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "CHAOS EVENT!",
                style = MaterialTheme.typography.labelMedium,
                color = CoralPink
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = InkDark,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EliminationAnnouncementBanner(
    playerName: String,
    color: PlayerColor
) {
    val palette = color.palette()
    CartoonCard(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .testTag("elimination_announcement_banner"),
        borderColor = palette.primary,
        containerColor = Color.White,
        cornerRadius = 26.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Gavel,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "ELIMINATED!",
                style = MaterialTheme.typography.labelMedium,
                color = CoralPink
            )
            Text(
                text = "$playerName is knocked out!",
                style = MaterialTheme.typography.headlineMedium,
                color = InkDark,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Least total progress after 5 rounds",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PauseMenuDialog(
    isOnlineMode: Boolean,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onQuit: () -> Unit
) {
    Dialog(onDismissRequest = onResume) {
        CartoonCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pause_dialog"),
            borderColor = CobaltPrimary,
            cornerRadius = 28.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isOnlineMode) "Online Match" else stringResource(R.string.game_pause_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkDark
                )

                if (isOnlineMode) {
                    Text(
                        text = "The match continues live while you look away. If you leave, a Medium Bot will take over your seat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(4.dp))

                CartoonButton(
                    text = stringResource(R.string.game_resume),
                    onClick = onResume,
                    tag = "pause_resume_button",
                    icon = Icons.Filled.PlayArrow,
                    containerColor = MintGreen,
                    lipColor = MintGreenDark,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isOnlineMode) {
                    CartoonButton(
                        text = stringResource(R.string.game_restart),
                        onClick = onRestart,
                        tag = "pause_restart_button",
                        icon = Icons.Filled.Refresh,
                        containerColor = CobaltPrimary,
                        lipColor = CobaltDark,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                CartoonButton(
                    text = if (isOnlineMode) stringResource(R.string.online_leave_match) else stringResource(R.string.game_quit),
                    onClick = onQuit,
                    tag = "pause_quit_button",
                    icon = Icons.Filled.Home,
                    containerColor = CoralPink,
                    lipColor = CoralPinkDark,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TurnOrderCycleStrip(
    cycle: List<PlayerState>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        cycle.forEachIndexed { idx, player ->
            val palette = player.color.palette()
            val isCurrent = idx == 0
            if (isCurrent) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = palette.light,
                    border = BorderStroke(1.5.dp, palette.primary),
                    modifier = Modifier.testTag("turn_cycle_active_${player.color.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(palette.primary)
                        )
                        Text(
                            text = player.nickname.substringBefore(" ").take(8),
                            style = MaterialTheme.typography.labelSmall,
                            color = InkDark,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = InkMuted.copy(alpha = 0.6f),
                    modifier = Modifier.size(13.dp)
                )
                Surface(
                    shape = CircleShape,
                    color = palette.primary.copy(alpha = 0.85f),
                    border = BorderStroke(1.2.dp, palette.dark),
                    modifier = Modifier
                        .size(14.dp)
                        .testTag("turn_cycle_next_${player.color.name.lowercase()}")
                ) {}
            }
        }
    }
}
