package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.R
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.LegalMove
import com.example.engine.PlayerColor
import com.example.engine.PowerUpType
import com.example.engine.RuleSetRegistry
import com.example.engine.TokenRef
import com.example.engine.TurnGuardResult
import com.example.engine.TurnManager
import com.example.engine.TurnPhase
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.ConfettiOverlay
import com.example.ui.game.ActiveDiceAndTrayDock
import com.example.ui.game.AllTokensHomeCelebrationOverlay
import com.example.ui.game.GameUiState
import com.example.ui.game.GhostDestinationState
import com.example.ui.game.LudoBoardCanvas
import com.example.ui.game.ModeRulesDialog
import com.example.ui.game.PlayerHudCard
import com.example.ui.game.PowerUpTargetingState
import com.example.ui.game.TokenMoveOption
import com.example.ui.game.TokenMoveOptionBubble
import com.example.ui.game.TokenMoveOptionResolver
import com.example.ui.game.TopEventToastBar
import com.example.ui.game.TopToastMessage
import com.example.ui.game.TopToastTone
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.ludoShadow
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class DockAnchorAlignment {
    START,
    END,
    CENTER
}

private data class CornerDockAnchor(
    val point: Offset,
    val alignment: DockAnchorAlignment
)

@Composable
fun GameBoardScreen(
    uiState: GameUiState,
    showPowerUpGuide: Boolean = false,
    onOpenPowerUpGuide: () -> Unit = {},
    onDismissPowerUpGuide: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
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
    onDiceRollResult: ((Int) -> Unit)? = null,
    onSelectDieValue: (Int) -> Unit = {},
    onTokenTapped: (TokenRef) -> Unit,
    onMoveSelected: ((LegalMove) -> Unit)? = null,
    onMoveSequenceSelected: ((List<LegalMove>) -> Unit)? = null,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onEndMatchEarly: () -> Unit,
    onQuitToMenu: () -> Unit,
    onMatchFinished: () -> Unit,
    onDismissAllTokensHomeCelebration: () -> Unit = {}
) {
    val theme = LocalLudoTheme.current
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
    val activeRuleSet = remember(gameState.mode, gameState.matchFormat) {
        RuleSetRegistry.forMode(gameState.mode, gameState.matchFormat)
    }
    val turnTimerEnabled = gameState.turnTimerSeconds > 0

    val canHumanRoll = !uiState.isPaused &&
        !showPowerUpGuide &&
        uiState.allTokensHomeCelebration == null &&
        !onlineLoading &&
        !uiState.isDiceRolling &&
        !uiState.isAnimatingMove &&
        !activePlayer.setup.isBot &&
        isLocalPlayersTurn &&
        gameState.phase == TurnPhase.AWAITING_ROLL &&
        !gameState.isMatchFinished

    var selectedBubbleToken by remember { mutableStateOf<TokenRef?>(null) }
    var selectedBubbleOptions by remember { mutableStateOf<List<TokenMoveOption>>(emptyList()) }
    var selectedBubbleAnchorPx by remember { mutableStateOf(Offset.Zero) }
    var ghostDestination by remember { mutableStateOf<GhostDestinationState?>(null) }

    fun dismissMoveBubble() {
        selectedBubbleToken = null
        selectedBubbleOptions = emptyList()
        ghostDestination = null
    }

    LaunchedEffect(
        gameState.activePlayerIndex,
        gameState.phase,
        gameState.remainingDice,
        uiState.isAnimatingMove,
        uiState.isDiceRolling
    ) {
        dismissMoveBubble()
    }

    BackHandler(enabled = !gameState.isMatchFinished || uiState.allTokensHomeCelebration != null) {
        when {
            uiState.allTokensHomeCelebration != null -> onDismissAllTokensHomeCelebration()
            selectedBubbleToken != null -> dismissMoveBubble()
            showPowerUpGuide -> onDismissPowerUpGuide()
            uiState.isPaused -> onResume()
            else -> onPause()
        }
    }

    var localGuardToast by remember { mutableStateOf<TopToastMessage?>(null) }
    var localGuardCounter by remember { mutableIntStateOf(0) }
    LaunchedEffect(localGuardCounter) {
        if (localGuardToast != null) {
            delay(1500L)
            localGuardToast = null
        }
    }
    LaunchedEffect(gameState.activePlayerIndex, gameState.phase) {
        localGuardToast = null
    }

    fun showLocalToast(text: String, tone: TopToastTone = TopToastTone.WARNING) {
        localGuardCounter += 1
        localGuardToast = TopToastMessage(text = text, tone = tone, triggerKey = localGuardCounter)
    }

    val effectiveTopToast: TopToastMessage? = uiState.topToast ?: localGuardToast

    fun rankFor(color: PlayerColor): Int? {
        val idx = gameState.finishOrder.indexOf(color)
        return if (idx >= 0) idx + 1 else null
    }

    val executeMoveOption: (TokenMoveOption) -> Unit = { option ->
        dismissMoveBubble()
        when {
            option.moves.size > 1 && onMoveSequenceSelected != null ->
                onMoveSequenceSelected(option.moves)
            option.moves.isNotEmpty() && onMoveSelected != null ->
                onMoveSelected(option.moves.first())
            option.moves.isNotEmpty() -> {
                onSelectDieValue(option.moves.first().dieValueUsed)
                onTokenTapped(TokenRef(option.color, option.moves.first().tokenId))
            }
        }
    }

    val handleTokenTapWithAnchor: (TokenRef, Offset) -> Unit = { tappedRef, anchorPx ->
        val options = TokenMoveOptionResolver.optionsForToken(gameState, tappedRef)
        when {
            options.isEmpty() -> dismissMoveBubble()
            options.size == 1 -> executeMoveOption(options.first())
            else -> {
                selectedBubbleToken = tappedRef
                selectedBubbleOptions = options
                selectedBubbleAnchorPx = anchorPx
                ghostDestination = null
            }
        }
    }

    val handleOutOfTurnTap: (TokenRef?) -> Unit = { tappedRef ->
        dismissMoveBubble()
        val msg = when {
            activePlayer.setup.isBot ->
                "${activePlayer.nickname} (Bot) is playing"
            isOnlineMode && !isLocalPlayersTurn ->
                "Waiting for ${activePlayer.nickname}"
            gameState.phase == TurnPhase.AWAITING_BOMB_PLACEMENT ->
                "Tap a glowing square to hide your Bomb"
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
                        val expectedOwner = gameState.playerByColor(guard.expectedColor)?.nickname
                            ?: guard.expectedColor.displayName
                        "It's $expectedOwner's turn"
                    }
                    is TurnGuardResult.InvalidPhase -> {
                        if (guard.actual == TurnPhase.AWAITING_ROLL) {
                            "Tap the die to roll, ${activePlayer.nickname}"
                        } else null
                    }
                    is TurnGuardResult.IllegalTokenMove ->
                        "Pick a glowing token to move"
                    else -> null
                }
            }
            else -> null
        }
        if (msg != null) {
            showLocalToast(msg)
        }
    }

    // Contextual short bottom hint text (Part 5)
    val bottomHintText = remember(
        gameState.phase,
        gameState.activePlayerIndex,
        gameState.legalMoves,
        gameState.isMatchFinished,
        activePlayer.goldenDiceCharges,
        isLocalPlayersTurn,
        uiState.isDiceRolling,
        uiState.powerUpTargeting,
        selectedBubbleToken
    ) {
        when {
            gameState.isMatchFinished -> "Match complete!"
            uiState.powerUpTargeting is PowerUpTargetingState.BombPlacement ||
                gameState.phase == TurnPhase.AWAITING_BOMB_PLACEMENT ->
                "Tap a glowing square to place Bomb"
            uiState.powerUpTargeting is PowerUpTargetingState.ShieldTarget ->
                "Tap a token to Shield"
            activePlayer.setup.isBot ->
                "${activePlayer.nickname} is playing…"
            isOnlineMode && !isLocalPlayersTurn ->
                "Waiting for ${activePlayer.nickname}"
            uiState.isDiceRolling ->
                "Rolling…"
            gameState.phase == TurnPhase.AWAITING_ROLL -> {
                if (activePlayer.goldenDiceCharges > 0) "Golden Dice ready · Tap for 6"
                else "Tap the die to roll"
            }
            gameState.phase == TurnPhase.AWAITING_MOVE -> {
                val captureMove = gameState.legalMoves.firstOrNull { it.capturedTokens.isNotEmpty() }
                if (captureMove != null) {
                    val victimColor = captureMove.capturedTokens.first().color
                    val victimName = gameState.playerByColor(victimColor)?.nickname ?: victimColor.displayName
                    "Play ${captureMove.dieValueUsed} to capture $victimName"
                } else if (selectedBubbleToken != null) {
                    "Pick a number to move"
                } else {
                    "Tap a token to move"
                }
            }
            else -> "${activePlayer.nickname}'s turn"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .safeDrawingPadding()
            .pointerInput(selectedBubbleToken) {
                if (selectedBubbleToken != null) {
                    detectTapGestures { dismissMoveBubble() }
                }
            }
            .testTag("game_board_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Part 5 Top Bar: 44dp Back · Center "<Mode> · <Format>" chip with info icon · 44dp Settings
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                contentAlignment = Alignment.Center
            ) {
                TopControlsRow(
                    gameState = gameState,
                    onBack = onPause,
                    onOpenModeRules = onOpenPowerUpGuide,
                    onOpenSettings = onOpenSettings
                )

                TopEventToastBar(
                    toast = effectiveTopToast,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 52.dp)
                        .zIndex(4f)
                )
            }

            // Optional slim status badges (Timed clock, Chaos/Elimination countdown, Room code, End Early)
            ModeStatusBadgeStrip(
                gameState = gameState,
                isOnlineMode = isOnlineMode,
                onlineRoomCode = onlineRoomCode,
                onEndMatchEarly = onEndMatchEarly
            )

            OnlineErrorBanner(
                onlineErrorMessage = onlineErrorMessage,
                modifier = Modifier.widthIn(max = 720.dp)
            )

            // Main play stage: Player Cards + Square Board + Gliding Die/Tray Dock
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val isLandscapeExpanded = maxWidth >= 680.dp && maxWidth > maxHeight
                val isCompactPortrait = !isLandscapeExpanded && maxHeight < 640.dp
                val dieSize = if (isCompactPortrait) 50.dp else 58.dp

                var stageRootOrigin by remember { mutableStateOf(Offset.Zero) }
                val cornerAnchors = remember { mutableStateMapOf<PlayerColor, CornerDockAnchor>() }
                var dockSize by remember { mutableStateOf(IntSize.Zero) }

                fun registerAnchor(
                    color: PlayerColor,
                    alignment: DockAnchorAlignment,
                    coords: androidx.compose.ui.layout.LayoutCoordinates
                ) {
                    val pos = coords.positionInRoot() - stageRootOrigin
                    val w = coords.size.width.toFloat()
                    val h = coords.size.height.toFloat()
                    val anchorPoint = when (alignment) {
                        DockAnchorAlignment.START -> Offset(pos.x, pos.y + h / 2f)
                        DockAnchorAlignment.END -> Offset(pos.x + w, pos.y + h / 2f)
                        DockAnchorAlignment.CENTER -> Offset(pos.x + w / 2f, pos.y + h / 2f)
                    }
                    cornerAnchors[color] = CornerDockAnchor(anchorPoint, alignment)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { stageRootOrigin = it.positionInRoot() }
                ) {
                    if (isLandscapeExpanded) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(156.dp)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PlayerHudCard(
                                        player = gameState.playerByColor(PlayerColor.RED),
                                        mode = gameState.mode,
                                        matchFormat = gameState.matchFormat,
                                        isActive = activePlayer.color == PlayerColor.RED && !gameState.isMatchFinished,
                                        finishRank = rankFor(PlayerColor.RED),
                                        timerProgress = uiState.timerProgress,
                                        remainingSeconds = uiState.remainingSeconds,
                                        turnTimerEnabled = turnTimerEnabled,
                                        canTapToRoll = canHumanRoll && activePlayer.color == PlayerColor.RED,
                                        onCardClick = onRollDice,
                                        compact = true
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(width = 148.dp, height = 64.dp)
                                            .onGloballyPositioned {
                                                registerAnchor(PlayerColor.RED, DockAnchorAlignment.CENTER, it)
                                            }
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 148.dp, height = 64.dp)
                                            .onGloballyPositioned {
                                                registerAnchor(PlayerColor.BLUE, DockAnchorAlignment.CENTER, it)
                                            }
                                    )
                                    PlayerHudCard(
                                        player = gameState.playerByColor(PlayerColor.BLUE),
                                        mode = gameState.mode,
                                        matchFormat = gameState.matchFormat,
                                        isActive = activePlayer.color == PlayerColor.BLUE && !gameState.isMatchFinished,
                                        finishRank = rankFor(PlayerColor.BLUE),
                                        timerProgress = uiState.timerProgress,
                                        remainingSeconds = uiState.remainingSeconds,
                                        turnTimerEnabled = turnTimerEnabled,
                                        canTapToRoll = canHumanRoll && activePlayer.color == PlayerColor.BLUE,
                                        onCardClick = onRollDice,
                                        compact = true
                                    )
                                }
                            }

                            BoardStageBox(
                                uiState = uiState,
                                isLocalPlayersTurn = isLocalPlayersTurn,
                                localViewerColor = localViewerColor,
                                selectedBubbleToken = selectedBubbleToken,
                                selectedBubbleOptions = selectedBubbleOptions,
                                selectedBubbleAnchorPx = selectedBubbleAnchorPx,
                                ghostDestination = ghostDestination,
                                onTokenTapped = onTokenTapped,
                                onTokenTappedWithAnchor = handleTokenTapWithAnchor,
                                onEmptyBoardTap = { dismissMoveBubble() },
                                onPreviewOption = { opt ->
                                    ghostDestination = opt?.let {
                                        GhostDestinationState(
                                            row = it.destinationRow,
                                            col = it.destinationCol,
                                            color = it.color,
                                            stepsLabel = it.label,
                                            isCapture = it.isCapture
                                        )
                                    }
                                },
                                onSelectOption = executeMoveOption,
                                onShieldTokenSelected = onShieldTokenSelected,
                                onBombSquareSelected = onBombSquareSelected,
                                onOutOfTurnTap = handleOutOfTurnTap,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )

                            Column(
                                modifier = Modifier
                                    .width(156.dp)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PlayerHudCard(
                                        player = gameState.playerByColor(PlayerColor.GREEN),
                                        mode = gameState.mode,
                                        matchFormat = gameState.matchFormat,
                                        isActive = activePlayer.color == PlayerColor.GREEN && !gameState.isMatchFinished,
                                        finishRank = rankFor(PlayerColor.GREEN),
                                        timerProgress = uiState.timerProgress,
                                        remainingSeconds = uiState.remainingSeconds,
                                        turnTimerEnabled = turnTimerEnabled,
                                        alignEnd = true,
                                        canTapToRoll = canHumanRoll && activePlayer.color == PlayerColor.GREEN,
                                        onCardClick = onRollDice,
                                        compact = true
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(width = 148.dp, height = 64.dp)
                                            .onGloballyPositioned {
                                                registerAnchor(PlayerColor.GREEN, DockAnchorAlignment.CENTER, it)
                                            }
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 148.dp, height = 64.dp)
                                            .onGloballyPositioned {
                                                registerAnchor(PlayerColor.YELLOW, DockAnchorAlignment.CENTER, it)
                                            }
                                    )
                                    PlayerHudCard(
                                        player = gameState.playerByColor(PlayerColor.YELLOW),
                                        mode = gameState.mode,
                                        matchFormat = gameState.matchFormat,
                                        isActive = activePlayer.color == PlayerColor.YELLOW && !gameState.isMatchFinished,
                                        finishRank = rankFor(PlayerColor.YELLOW),
                                        timerProgress = uiState.timerProgress,
                                        remainingSeconds = uiState.remainingSeconds,
                                        turnTimerEnabled = turnTimerEnabled,
                                        alignEnd = true,
                                        canTapToRoll = canHumanRoll && activePlayer.color == PlayerColor.YELLOW,
                                        onCardClick = onRollDice,
                                        compact = true
                                    )
                                }
                            }
                        }
                    } else {
                        // Portrait layout: Top Row -> 12dp gap -> Square Board -> 12dp gap -> Bottom Row
                        val rowGap = if (isCompactPortrait) 8.dp else 12.dp
                        val cardEdgePadding = if (isCompactPortrait) 92.dp else 102.dp

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 680.dp)
                                    .fillMaxWidth()
                            ) {
                                PlayerHudCard(
                                    player = gameState.playerByColor(PlayerColor.RED),
                                    mode = gameState.mode,
                                    matchFormat = gameState.matchFormat,
                                    isActive = activePlayer.color == PlayerColor.RED && !gameState.isMatchFinished,
                                    finishRank = rankFor(PlayerColor.RED),
                                    timerProgress = uiState.timerProgress,
                                    remainingSeconds = uiState.remainingSeconds,
                                    turnTimerEnabled = turnTimerEnabled,
                                    canTapToRoll = canHumanRoll && activePlayer.color == PlayerColor.RED,
                                    onCardClick = onRollDice,
                                    compact = isCompactPortrait,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = cardEdgePadding)
                                        .size(width = 12.dp, height = 64.dp)
                                        .onGloballyPositioned {
                                            registerAnchor(PlayerColor.RED, DockAnchorAlignment.START, it)
                                        }
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = cardEdgePadding)
                                        .size(width = 12.dp, height = 64.dp)
                                        .onGloballyPositioned {
                                            registerAnchor(PlayerColor.GREEN, DockAnchorAlignment.END, it)
                                        }
                                )

                                PlayerHudCard(
                                    player = gameState.playerByColor(PlayerColor.GREEN),
                                    mode = gameState.mode,
                                    matchFormat = gameState.matchFormat,
                                    isActive = activePlayer.color == PlayerColor.GREEN && !gameState.isMatchFinished,
                                    finishRank = rankFor(PlayerColor.GREEN),
                                    timerProgress = uiState.timerProgress,
                                    remainingSeconds = uiState.remainingSeconds,
                                    turnTimerEnabled = turnTimerEnabled,
                                    alignEnd = true,
                                    canTapToRoll = canHumanRoll && activePlayer.color == PlayerColor.GREEN,
                                    onCardClick = onRollDice,
                                    compact = isCompactPortrait,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }

                            Spacer(Modifier.height(rowGap))

                            BoardStageBox(
                                uiState = uiState,
                                isLocalPlayersTurn = isLocalPlayersTurn,
                                localViewerColor = localViewerColor,
                                selectedBubbleToken = selectedBubbleToken,
                                selectedBubbleOptions = selectedBubbleOptions,
                                selectedBubbleAnchorPx = selectedBubbleAnchorPx,
                                ghostDestination = ghostDestination,
                                onTokenTapped = onTokenTapped,
                                onTokenTappedWithAnchor = handleTokenTapWithAnchor,
                                onEmptyBoardTap = { dismissMoveBubble() },
                                onPreviewOption = { opt ->
                                    ghostDestination = opt?.let {
                                        GhostDestinationState(
                                            row = it.destinationRow,
                                            col = it.destinationCol,
                                            color = it.color,
                                            stepsLabel = it.label,
                                            isCapture = it.isCapture
                                        )
                                    }
                                },
                                onSelectOption = executeMoveOption,
                                onShieldTokenSelected = onShieldTokenSelected,
                                onBombSquareSelected = onBombSquareSelected,
                                onOutOfTurnTap = handleOutOfTurnTap,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .fillMaxWidth()
                            )

                            Spacer(Modifier.height(rowGap))

                            Box(
                                modifier = Modifier
                                    .widthIn(max = 680.dp)
                                    .fillMaxWidth()
                            ) {
                                PlayerHudCard(
                                    player = gameState.playerByColor(PlayerColor.BLUE),
                                    mode = gameState.mode,
                                    matchFormat = gameState.matchFormat,
                                    isActive = activePlayer.color == PlayerColor.BLUE && !gameState.isMatchFinished,
                                    finishRank = rankFor(PlayerColor.BLUE),
                                    timerProgress = uiState.timerProgress,
                                    remainingSeconds = uiState.remainingSeconds,
                                    turnTimerEnabled = turnTimerEnabled,
                                    canTapToRoll = canHumanRoll && activePlayer.color == PlayerColor.BLUE,
                                    onCardClick = onRollDice,
                                    compact = isCompactPortrait,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = cardEdgePadding)
                                        .size(width = 12.dp, height = 64.dp)
                                        .onGloballyPositioned {
                                            registerAnchor(PlayerColor.BLUE, DockAnchorAlignment.START, it)
                                        }
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = cardEdgePadding)
                                        .size(width = 12.dp, height = 64.dp)
                                        .onGloballyPositioned {
                                            registerAnchor(PlayerColor.YELLOW, DockAnchorAlignment.END, it)
                                        }
                                )

                                PlayerHudCard(
                                    player = gameState.playerByColor(PlayerColor.YELLOW),
                                    mode = gameState.mode,
                                    matchFormat = gameState.matchFormat,
                                    isActive = activePlayer.color == PlayerColor.YELLOW && !gameState.isMatchFinished,
                                    finishRank = rankFor(PlayerColor.YELLOW),
                                    timerProgress = uiState.timerProgress,
                                    remainingSeconds = uiState.remainingSeconds,
                                    turnTimerEnabled = turnTimerEnabled,
                                    alignEnd = true,
                                    canTapToRoll = canHumanRoll && activePlayer.color == PlayerColor.YELLOW,
                                    onCardClick = onRollDice,
                                    compact = isCompactPortrait,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }

                    // Single gliding Die + Roll Tray dock springing smoothly next to active player's card
                    val activeAnchor = cornerAnchors[activePlayer.color]
                    if (activeAnchor != null && !gameState.isMatchFinished) {
                        val trayOnLeft = !isLandscapeExpanded &&
                            (activePlayer.color == PlayerColor.GREEN || activePlayer.color == PlayerColor.YELLOW)

                        val targetTopLeft = when (activeAnchor.alignment) {
                            DockAnchorAlignment.START -> Offset(
                                x = activeAnchor.point.x,
                                y = activeAnchor.point.y - dockSize.height / 2f
                            )
                            DockAnchorAlignment.END -> Offset(
                                x = activeAnchor.point.x - dockSize.width.toFloat(),
                                y = activeAnchor.point.y - dockSize.height / 2f
                            )
                            DockAnchorAlignment.CENTER -> Offset(
                                x = activeAnchor.point.x - dockSize.width / 2f,
                                y = activeAnchor.point.y - dockSize.height / 2f
                            )
                        }

                        var hasInitializedDock by remember { mutableStateOf(false) }
                        val animatedTopLeft by animateOffsetAsState(
                            targetValue = targetTopLeft,
                            animationSpec = if (hasInitializedDock) {
                                spring(
                                    dampingRatio = 0.78f,
                                    stiffness = Spring.StiffnessLow * 1.6f
                                )
                            } else {
                                spring(stiffness = Spring.StiffnessHigh)
                            },
                            label = "gliding_die_dock_offset"
                        )
                        LaunchedEffect(dockSize) {
                            if (dockSize.width > 0 && !hasInitializedDock) {
                                hasInitializedDock = true
                            }
                        }

                        ActiveDiceAndTrayDock(
                            uiState = uiState,
                            gameState = gameState,
                            canRoll = canHumanRoll,
                            isOnlineMode = isOnlineMode,
                            trayOnLeft = trayOnLeft,
                            dieSize = dieSize,
                            onRollDice = onRollDice,
                            onDiceRollStarted = onDiceRollStarted,
                            onDiceRollResult = onDiceRollResult,
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = animatedTopLeft.x.roundToInt(),
                                        y = animatedTopLeft.y.roundToInt()
                                    )
                                }
                                .onSizeChanged { dockSize = it }
                                .zIndex(6f)
                        )
                    }
                }
            }

            // Part 5 Bottom Hint Pill (theme.surface, 999dp corners, 14sp semibold surfaceText, cardShadow)
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .ludoShadow(theme.cardShadow, 999.dp)
                    .testTag("game_bottom_hint_pill"),
                shape = RoundedCornerShape(999.dp),
                color = theme.surface
            ) {
                Text(
                    text = bottomHintText,
                    fontFamily = theme.font,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.surfaceText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }

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
                    .background(theme.bg.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                CartoonCard(borderColor = theme.blue, cornerRadius = 22.dp) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(color = theme.blue)
                        Text(
                            text = "Syncing live match…",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.surfaceText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (showPowerUpGuide && !onlineLoading) {
            ModeRulesDialog(
                mode = gameState.mode,
                matchFormat = gameState.matchFormat,
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
    onBack: () -> Unit,
    onOpenModeRules: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 44dp Back / Pause button
        Surface(
            onClick = onBack,
            shape = RoundedCornerShape(14.dp),
            color = theme.surface,
            modifier = Modifier
                .size(44.dp)
                .ludoShadow(theme.cardShadow, 14.dp)
                .testTag("pause_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_pause),
                    tint = theme.surfaceText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Center 44dp mode chip "<Mode> · <Format>" + info icon
        Surface(
            onClick = onOpenModeRules,
            shape = RoundedCornerShape(999.dp),
            color = theme.surface,
            modifier = Modifier
                .height(44.dp)
                .ludoShadow(theme.cardShadow, 999.dp)
                .testTag("mode_rules_help_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${gameState.mode.title} · ${gameState.matchFormat.shortLabel}",
                    fontFamily = theme.font,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.surfaceText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = stringResource(R.string.cd_mode_rules_help),
                    tint = theme.surfaceMutedText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 44dp Settings button (allows switching themes mid-game without restarting!)
        Surface(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(14.dp),
            color = theme.surface,
            modifier = Modifier
                .size(44.dp)
                .ludoShadow(theme.cardShadow, 14.dp)
                .testTag("game_settings_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = theme.surfaceText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeStatusBadgeStrip(
    gameState: GameState,
    isOnlineMode: Boolean,
    onlineRoomCode: String?,
    onEndMatchEarly: () -> Unit
) {
    val theme = LocalLudoTheme.current
    val showRoom = isOnlineMode && !onlineRoomCode.isNullOrBlank()
    val showTimed = gameState.mode == GameModeId.TIMED && !gameState.isMatchFinished
    val showChaos = gameState.mode == GameModeId.CHAOS && !gameState.isMatchFinished
    val showElim = gameState.mode == GameModeId.ELIMINATION && !gameState.isMatchFinished
    val showEndEarly = gameState.hasWinner && !gameState.isMatchFinished && !isOnlineMode

    if (!showRoom && !showTimed && !showChaos && !showElim && !showEndEarly) return

    Row(
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showRoom) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = theme.primaryActionBg,
                modifier = Modifier.testTag("online_room_code_badge")
            ) {
                Text(
                    text = "Room $onlineRoomCode",
                    fontFamily = theme.font,
                    fontSize = 12.sp,
                    color = theme.onPrimaryAction,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        if (showTimed) {
            val mins = gameState.timedRemainingSeconds / 60
            val secs = gameState.timedRemainingSeconds % 60
            val urgent = gameState.timedRemainingSeconds <= 30
            val bg = if (urgent) theme.red else theme.primaryActionBg
            val fg = if (urgent) theme.contrastingTextOn(theme.red) else theme.onPrimaryAction
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = bg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = "Match clock",
                        tint = fg,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "%02d:%02d".format(mins, secs),
                        fontFamily = theme.font,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = fg
                    )
                }
            }
        }

        if (showChaos) {
            val roundsUntil = 3 - (gameState.fullRoundsCompleted % 3)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = theme.surface,
                modifier = Modifier.ludoShadow(theme.cardShadow, 999.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Whatshot,
                        contentDescription = "Rounds to Chaos event",
                        tint = theme.red,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Chaos in ${roundsUntil}r",
                        fontFamily = theme.font,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.surfaceText
                    )
                }
            }
        }

        if (showElim) {
            val roundsUntil = 5 - (gameState.fullRoundsCompleted % 5)
            val elimAccent = lerp(theme.red, theme.blue, 0.35f)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = theme.surface,
                modifier = Modifier.ludoShadow(theme.cardShadow, 999.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Gavel,
                        contentDescription = "Rounds to Elimination",
                        tint = elimAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Elim in ${roundsUntil}r",
                        fontFamily = theme.font,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.surfaceText
                    )
                }
            }
        }

        if (showEndEarly) {
            val fg = theme.contrastingTextOn(theme.yellow)
            Surface(
                onClick = onEndMatchEarly,
                modifier = Modifier.testTag("end_match_early_button"),
                shape = RoundedCornerShape(999.dp),
                color = theme.yellow
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = "Finish match",
                        tint = fg,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "End",
                        fontFamily = theme.font,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = fg
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineErrorBanner(
    onlineErrorMessage: String?,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current
    if (!onlineErrorMessage.isNullOrBlank()) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            shape = RoundedCornerShape(14.dp),
            color = theme.red.copy(alpha = 0.14f),
            border = BorderStroke(1.5.dp, theme.red)
        ) {
            Text(
                text = onlineErrorMessage,
                style = MaterialTheme.typography.labelMedium,
                color = theme.text,
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
    isLocalPlayersTurn: Boolean,
    localViewerColor: PlayerColor?,
    selectedBubbleToken: TokenRef?,
    selectedBubbleOptions: List<TokenMoveOption>,
    selectedBubbleAnchorPx: Offset,
    ghostDestination: GhostDestinationState?,
    onTokenTapped: (TokenRef) -> Unit,
    onTokenTappedWithAnchor: (TokenRef, Offset) -> Unit,
    onEmptyBoardTap: () -> Unit,
    onPreviewOption: (TokenMoveOption?) -> Unit,
    onSelectOption: (TokenMoveOption) -> Unit,
    onShieldTokenSelected: (Int) -> Unit,
    onBombSquareSelected: (Int) -> Unit,
    onOutOfTurnTap: (TokenRef?) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val squareSide = min(maxWidth, maxHeight)
        var boardBoxSizePx by remember { mutableStateOf(IntSize.Zero) }

        Box(
            modifier = Modifier
                .size(squareSide)
                .onSizeChanged { boardBoxSizePx = it },
            contentAlignment = Alignment.Center
        ) {
            LudoBoardCanvas(
                uiState = uiState,
                onTokenTapped = onTokenTapped,
                onTokenTappedWithAnchor = onTokenTappedWithAnchor,
                onEmptyBoardTap = onEmptyBoardTap,
                selectedTokenRef = selectedBubbleToken,
                ghostDestination = ghostDestination,
                onShieldTokenSelected = onShieldTokenSelected,
                onBombSquareSelected = onBombSquareSelected,
                isLocalPlayersTurn = isLocalPlayersTurn,
                onOutOfTurnTap = onOutOfTurnTap,
                localViewerColor = localViewerColor,
                modifier = Modifier.fillMaxSize()
            )

            ConfettiOverlay(
                triggerKey = uiState.homeConfettiTrigger,
                burstFromCenter = true,
                accentColor = uiState.homeConfettiColor?.let { theme.playerColor(it) },
                modifier = Modifier.matchParentSize()
            )

            if (selectedBubbleToken != null && selectedBubbleOptions.isNotEmpty()) {
                Box(modifier = Modifier.matchParentSize()) {
                    TokenMoveOptionBubble(
                        options = selectedBubbleOptions,
                        anchorPx = selectedBubbleAnchorPx,
                        containerSizePx = boardBoxSizePx,
                        onPreviewOption = onPreviewOption,
                        onSelectOption = onSelectOption
                    )
                }
            }
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
    val theme = LocalLudoTheme.current

    Dialog(onDismissRequest = onResume) {
        CartoonCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pause_dialog"),
            borderColor = theme.blue,
            cornerRadius = 22.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isOnlineMode) "Online Match" else stringResource(R.string.game_pause_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.surfaceText
                )

                if (isOnlineMode) {
                    Text(
                        text = "The match continues live while you look away. If you leave, a Medium Bot will take over your seat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.surfaceMutedText,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(4.dp))

                CartoonButton(
                    text = stringResource(R.string.game_resume),
                    onClick = onResume,
                    tag = "pause_resume_button",
                    icon = Icons.Filled.PlayArrow,
                    containerColor = theme.green,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isOnlineMode) {
                    CartoonButton(
                        text = stringResource(R.string.game_restart),
                        onClick = onRestart,
                        tag = "pause_restart_button",
                        icon = Icons.Filled.Refresh,
                        containerColor = theme.primaryActionBg,
                        contentColor = theme.onPrimaryAction,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                CartoonButton(
                    text = if (isOnlineMode) stringResource(R.string.online_leave_match) else stringResource(R.string.game_quit),
                    onClick = onQuit,
                    tag = "pause_quit_button",
                    icon = Icons.Filled.Home,
                    containerColor = theme.red,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
