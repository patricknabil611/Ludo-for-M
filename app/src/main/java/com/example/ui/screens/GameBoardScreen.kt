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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.engine.GameModeId
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
import com.example.ui.game.GameUiState
import com.example.ui.game.LudoBoardCanvas
import com.example.ui.game.LudoDice3D
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
    onTokenTapped: (TokenRef) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onEndMatchEarly: () -> Unit,
    onQuitToMenu: () -> Unit,
    onMatchFinished: () -> Unit
) {
    val gameState = uiState.gameState

    LaunchedEffect(gameState.isMatchFinished) {
        if (gameState.isMatchFinished) {
            delay(750L)
            onMatchFinished()
        }
    }

    BackHandler(enabled = !gameState.isMatchFinished) {
        when {
            showPowerUpGuide -> onDismissPowerUpGuide()
            uiState.powerUpTargeting != null -> onCancelPowerUpTargeting()
            uiState.isPaused -> onResume()
            else -> onPause()
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
        !uiState.isDiceRolling &&
        !uiState.isAnimatingMove &&
        !activePlayer.setup.isBot &&
        isLocalPlayersTurn &&
        gameState.phase == TurnPhase.AWAITING_ROLL

    fun rankFor(color: PlayerColor): Int? {
        val idx = gameState.finishOrder.indexOf(color)
        return if (idx >= 0) idx + 1 else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(SkyTopTint, CreamBackground))
            )
            .safeDrawingPadding()
            .testTag("game_board_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Controls + Mode Status / Timed Clock / Power-Up Help / Early End
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CartoonIconButton(
                    icon = Icons.Filled.Pause,
                    contentDescription = stringResource(R.string.cd_pause),
                    onClick = onPause,
                    modifier = Modifier.testTag("pause_button")
                )

                Spacer(Modifier.width(8.dp))

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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                    color = activePalette.light,
                    border = BorderStroke(2.dp, activePalette.primary)
                ) {
                    Text(
                        text = gameState.lastEventMessage,
                        style = MaterialTheme.typography.labelLarge,
                        color = InkDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }

                // Mode-specific live pill (Timed clock / Chaos / Elimination / Early End) + universal '?' Rules button
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = "Match clock",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "%02d:%02d".format(mins, secs),
                                style = MaterialTheme.typography.labelLarge,
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = null,
                                tint = InkDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "End",
                                style = MaterialTheme.typography.labelMedium,
                                color = InkDark
                            )
                        }
                    }
                }

                // Universal '?' Mode Rules button available in every mode
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

            if (!onlineErrorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Top Corner Player HUDs (RED = Top-Left, GREEN = Top-Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Center 15x15 Ludo Board + Celebrations + Chaos/Elimination/Power-Up Announcements
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                LudoBoardCanvas(
                    uiState = uiState,
                    onTokenTapped = onTokenTapped,
                    onShieldTokenSelected = onShieldTokenSelected,
                    onBombSquareSelected = onBombSquareSelected,
                    isLocalPlayersTurn = isLocalPlayersTurn,
                    onOutOfTurnTap = { tappedRef ->
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
                    },
                    localViewerColor = localViewerColor
                )

                // Crisp turn-ownership feedback toast when tapping out of turn
                androidx.compose.animation.AnimatedVisibility(
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
                            border = BorderStroke(1.5.dp, activePalette.primary),
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

                // Big Chaos Event Announcement Overlay
                androidx.compose.animation.AnimatedVisibility(
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

                // Power-Up Pickup / Activation / Bomb Explosion Banner
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.activePowerUpBanner != null && uiState.activeChaosBanner == null,
                    enter = fadeIn() + scaleIn(spring(dampingRatio = 0.58f, stiffness = 400f)),
                    exit = fadeOut() + scaleOut()
                ) {
                    uiState.activePowerUpBanner?.let { banner ->
                        PowerUpActivationBanner(banner = banner)
                    }
                }

                // Elimination Knockout Announcement Overlay
                androidx.compose.animation.AnimatedVisibility(
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

            Spacer(Modifier.height(6.dp))

            // Bottom Corner Player HUDs (BLUE = Bottom-Left, YELLOW = Bottom-Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Bottom Dice & Active Turn Prompt Bar
            val showDoubleDice = activePlayer.doubleDiceActive || uiState.displayedSecondDiceValue != null
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(2.5.dp, activePalette.primary),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
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
                            style = MaterialTheme.typography.titleLarge,
                            color = activePalette.dark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val hintText = when {
                            uiState.powerUpTargeting is PowerUpTargetingState.ShieldTarget ->
                                "Tap one of your tokens to Shield!"
                            uiState.powerUpTargeting is PowerUpTargetingState.BombPlacement ->
                                "Tap a glowing square to hide your Bomb!"
                            uiState.isDiceRolling ->
                                if (showDoubleDice) "Rolling two dice..." else "Rolling..."
                            uiState.isAnimatingMove -> "Hopping..."
                            activePlayer.setup.isBot && gameState.phase == TurnPhase.AWAITING_ROLL ->
                                "Bot is thinking..."
                            activePlayer.setup.isBot && gameState.phase == TurnPhase.AWAITING_MOVE ->
                                "Bot is picking a token..."
                            isOnlineMode && !isLocalPlayersTurn ->
                                "Waiting for ${activePlayer.nickname}..."
                            gameState.phase == TurnPhase.AWAITING_ROLL -> when {
                                activePlayer.doubleDiceActive -> "Double Dice armed! Tap to roll both!"
                                partnerName != null -> "Tap die to roll for $partnerName!"
                                gameState.mode == GameModeId.POWER_UP && activePlayer.heldPowerUps.isNotEmpty() ->
                                    "Use a power-up or tap the die!"
                                else -> "Tap the die to roll!"
                            }
                            gameState.phase == TurnPhase.AWAITING_MOVE -> "Tap a glowing token!"
                            gameState.phase == TurnPhase.TURN_CANCELLED_TRIPLE_SIX -> "Three 6s! Turn cancelled!"
                            gameState.phase == TurnPhase.NO_LEGAL_MOVES -> "No legal moves..."
                            else -> "Match complete!"
                        }
                        Text(
                            text = hintText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (turnCycle.size > 1) {
                            Spacer(Modifier.height(5.dp))
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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bolt,
                                        contentDescription = null,
                                        tint = InkDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "2×=${gameState.lastDiceValue}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = InkDark
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                        } else if (gameState.wasDoubleDiceRoll && gameState.lastDiceValue != null && !uiState.isDiceRolling) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SunnyGold.copy(alpha = 0.30f),
                                border = BorderStroke(1.5.dp, SunnyGoldDark)
                            ) {
                                Text(
                                    text = "=${gameState.lastDiceValue}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = InkDark,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Spacer(Modifier.width(10.dp))
                        }

                        val canRoll = !uiState.isPaused &&
                            !showPowerUpGuide &&
                            !uiState.isDiceRolling &&
                            !uiState.isAnimatingMove &&
                            !activePlayer.setup.isBot &&
                            isLocalPlayersTurn &&
                            uiState.activeChaosBanner == null &&
                            uiState.eliminatedBannerColor == null &&
                            gameState.phase == TurnPhase.AWAITING_ROLL

                        val useSelfRollingDice = !isOnlineMode && onDiceRollResult != null
                        if (showDoubleDice) {
                            LudoDoubleDice3D(
                                firstValue = uiState.displayedDiceValue,
                                secondValue = uiState.displayedSecondDiceValue ?: 6,
                                rollTrigger = uiState.diceRollTrigger,
                                isRolling = uiState.isDiceRolling,
                                canRoll = canRoll,
                                activeColor = activePlayer.color,
                                onRollClick = onRollDice,
                                onRollStarted = if (useSelfRollingDice) onDiceRollStarted else null,
                                onDoubleRollResult = if (useSelfRollingDice) {
                                    { v1, v2 -> onDiceRollResult.invoke(v1, v2) }
                                } else null,
                                dieSize = 58.dp
                            )
                        } else {
                            LudoDice3D(
                                value = uiState.displayedDiceValue,
                                rollTrigger = uiState.diceRollTrigger,
                                isRolling = uiState.isDiceRolling,
                                canRoll = canRoll,
                                activeColor = activePlayer.color,
                                onRollClick = onRollDice,
                                onRollStarted = if (useSelfRollingDice) onDiceRollStarted else null,
                                onRollResult = if (useSelfRollingDice) {
                                    { rolled -> onDiceRollResult.invoke(rolled, null) }
                                } else null,
                                size = 72.dp,
                                testTag = "dice_roll_button"
                            )
                        }
                    }
                }
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

