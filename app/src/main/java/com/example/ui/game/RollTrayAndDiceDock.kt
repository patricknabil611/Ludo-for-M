package com.example.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.GameState
import com.example.engine.PlayerColor
import com.example.engine.PowerUpType
import com.example.engine.TurnPhase
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.ludoShadow
import com.example.ui.theme.powerUpAccentColor
import kotlin.math.sin

enum class TopToastTone {
    NEUTRAL,
    WARNING,
    TRIPLE_SIX,
    MASTER_UNLOCK,
    MAGIC_ROCKET,
    MAGIC_GOLDEN,
    MAGIC_SHIELD,
    MAGIC_BOMB,
    CHAOS,
    ELIMINATION
}

data class TopToastMessage(
    val text: String,
    val tone: TopToastTone = TopToastTone.NEUTRAL,
    val triggerKey: Int
)

/**
 * Compact floating dock containing the active 3D die and stacked Roll Tray chips.
 * Placed inside a gliding overlay next to the active player's corner card toward the center.
 */
@Composable
fun ActiveDiceAndTrayDock(
    uiState: GameUiState,
    gameState: GameState,
    canRoll: Boolean,
    isOnlineMode: Boolean,
    trayOnLeft: Boolean,
    dieSize: Dp,
    onRollDice: () -> Unit,
    onDiceRollStarted: (() -> Unit)?,
    onDiceRollResult: ((Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val activePlayer = gameState.activePlayer
    val isGoldenDice = activePlayer.goldenDiceCharges > 0 && gameState.phase == TurnPhase.AWAITING_ROLL
    val useSelfRollingDie = !isOnlineMode &&
        onDiceRollResult != null &&
        activePlayer.goldenDiceCharges <= 0

    val showBonusChip = gameState.pendingBonusRolls > 0 ||
        (gameState.earnedBonusRollThisTurn && gameState.phase == TurnPhase.AWAITING_MOVE)
    val bonusCount = gameState.pendingBonusRolls.coerceAtLeast(if (showBonusChip) 1 else 0)
    val isTripleSix = gameState.phase == TurnPhase.TURN_CANCELLED_TRIPLE_SIX
    val hasTrayContent = gameState.rolledDice.isNotEmpty() || isTripleSix || showBonusChip || gameState.wasDiceDoubled

    val diceComposable: @Composable () -> Unit = {
        LudoDice3D(
            value = uiState.displayedDiceValue,
            rollTrigger = uiState.diceRollTrigger,
            isRolling = uiState.isDiceRolling,
            canRoll = canRoll,
            activeColor = activePlayer.color,
            isGoldenDice = isGoldenDice,
            showTapHint = canRoll && !uiState.isDiceRolling,
            onRollClick = onRollDice,
            onRollStarted = if (useSelfRollingDie) onDiceRollStarted else null,
            onRollResult = if (useSelfRollingDie) onDiceRollResult else null,
            size = dieSize
        )
    }

    val trayComposable: @Composable () -> Unit = {
        if (hasTrayContent) {
            RollTrayChipBar(
                rolledDice = if (isTripleSix && gameState.rolledDice.size < 3) listOf(6, 6, 6) else gameState.rolledDice,
                remainingDice = gameState.remainingDice,
                playableDice = gameState.playableDiceValues(),
                isTripleSixShatter = isTripleSix,
                bonusRollCount = if (showBonusChip) bonusCount else 0,
                wasDiceDoubled = gameState.wasDiceDoubled,
                doubledValue = gameState.lastDiceValue,
                activeColor = activePlayer.color,
                alignEnd = trayOnLeft
            )
        }
    }

    Row(
        modifier = modifier.testTag("active_dice_dock"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (trayOnLeft) {
            trayComposable()
            diceComposable()
        } else {
            diceComposable()
            trayComposable()
        }
    }
}

/**
 * Themed Roll Tray displaying stacked rolled dice chips (30dp, 9dp corners, 16sp bold)
 * with a "To move" label in theme.mutedText 11sp above.
 */
@Composable
fun RollTrayChipBar(
    rolledDice: List<Int>,
    remainingDice: List<Int>,
    playableDice: List<Int>,
    isTripleSixShatter: Boolean,
    bonusRollCount: Int,
    wasDiceDoubled: Boolean,
    doubledValue: Int?,
    activeColor: PlayerColor,
    alignEnd: Boolean = false,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current

    val shatterProgress = remember { Animatable(0f) }
    LaunchedEffect(isTripleSixShatter) {
        if (isTripleSixShatter) {
            shatterProgress.snapTo(0f)
            shatterProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 980, easing = FastOutSlowInEasing)
            )
        } else {
            shatterProgress.snapTo(0f)
        }
    }

    val remainingPool = remainingDice.toMutableList()
    val showLabel = remainingDice.isNotEmpty() && !isTripleSixShatter

    Column(
        modifier = modifier.testTag("roll_tray_strip"),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (showLabel) {
            Text(
                text = "To move",
                fontFamily = theme.font,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = theme.mutedText
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            rolledDice.forEachIndexed { idx, value ->
                val remIdx = remainingPool.indexOf(value)
                val isRemaining = remIdx >= 0
                if (isRemaining) {
                    remainingPool.removeAt(remIdx)
                }
                val isPlayable = isRemaining && (playableDice.isEmpty() || value in playableDice)
                val isUnusable = isRemaining && playableDice.isNotEmpty() && value !in playableDice

                val targetAlpha = when {
                    isTripleSixShatter -> {
                        val p = shatterProgress.value
                        if (p < 0.42f) 1f else (1f - ((p - 0.42f) / 0.58f)).coerceIn(0f, 1f)
                    }
                    !isRemaining -> 0.30f
                    isUnusable -> 0.50f
                    else -> 1f
                }
                val animatedAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(220),
                    label = "trayChipAlpha"
                )
                val animatedScale by animateFloatAsState(
                    targetValue = if (!isRemaining) 0.86f else 1f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                    label = "trayChipScale"
                )

                val (shatterTx, shatterTy, shatterRot) = if (isTripleSixShatter) {
                    val p = shatterProgress.value
                    if (p < 0.42f) {
                        val shake = sin(p * 62.0).toFloat() * 7.5f
                        Triple(shake, 0f, shake * 1.4f)
                    } else {
                        val burst = (p - 0.42f) / 0.58f
                        val spreadDir = (idx - 1).toFloat()
                        Triple(
                            spreadDir * 24f * burst,
                            (-16f + idx * 10f) * burst + 20f * burst * burst,
                            (spreadDir * 42f + 18f) * burst
                        )
                    }
                } else {
                    Triple(0f, 0f, 0f)
                }

                val bgColor = when {
                    isTripleSixShatter -> theme.red
                    !isRemaining -> theme.surfaceVariant
                    isUnusable -> theme.surfaceVariant
                    else -> theme.surface
                }
                val borderColor = when {
                    isTripleSixShatter -> theme.red
                    !isRemaining -> theme.chipLine
                    isUnusable -> theme.chipLine
                    value == 6 -> theme.yellow
                    else -> theme.chipLine
                }
                val textColor = when {
                    isTripleSixShatter -> theme.contrastingTextOn(theme.red)
                    !isRemaining -> theme.surfaceMutedText
                    isUnusable -> theme.surfaceMutedText
                    else -> theme.surfaceText
                }

                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = bgColor,
                    border = BorderStroke(if (value == 6 && isPlayable) 1.6.dp else 1.2.dp, borderColor),
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            alpha = animatedAlpha
                            scaleX = animatedScale
                            scaleY = animatedScale
                            translationX = shatterTx
                            translationY = shatterTy
                            rotationZ = shatterRot
                        }
                        .then(
                            if (isPlayable && !isTripleSixShatter) {
                                Modifier.ludoShadow(theme.cardShadow, 9.dp)
                            } else Modifier
                        )
                        .testTag("roll_tray_chip_${idx}_$value")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = value.toString(),
                            fontFamily = theme.font,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
            }

            if (wasDiceDoubled && doubledValue != null && !isTripleSixShatter) {
                val dblBg = theme.yellow
                val dblFg = theme.contrastingTextOn(dblBg)
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = dblBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = dblFg,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "2×",
                            fontFamily = theme.font,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = dblFg
                        )
                    }
                }
            }

            if (bonusRollCount > 0 && !isTripleSixShatter) {
                val bonusBg = theme.green
                val bonusFg = theme.contrastingTextOn(bonusBg)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = bonusBg,
                    modifier = Modifier.testTag("bonus_roll_chip")
                ) {
                    Text(
                        text = "+$bonusRollCount",
                        fontFamily = theme.font,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = bonusFg,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact top-anchored toast banner that NEVER covers the Ludo board and auto-hides after 1.5s.
 */
@Composable
fun TopEventToastBar(
    toast: TopToastMessage?,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { -it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(180)) { -it / 2 }
        ) {
            toast?.let { msg ->
                val (bg, icon) = when (msg.tone) {
                    TopToastTone.TRIPLE_SIX -> theme.red to Icons.Filled.Casino
                    TopToastTone.MASTER_UNLOCK -> theme.green to Icons.Filled.LockOpen
                    TopToastTone.MAGIC_ROCKET -> powerUpAccentColor(PowerUpType.ROCKET, theme) to Icons.Filled.RocketLaunch
                    TopToastTone.MAGIC_GOLDEN -> theme.yellow to Icons.Filled.AutoAwesome
                    TopToastTone.MAGIC_SHIELD -> theme.blue to Icons.Filled.Shield
                    TopToastTone.MAGIC_BOMB -> theme.red to Icons.Filled.LocalFireDepartment
                    TopToastTone.CHAOS -> lerp(theme.blue, theme.red, 0.50f) to Icons.Filled.Whatshot
                    TopToastTone.ELIMINATION -> lerp(theme.red, theme.blue, 0.35f) to Icons.Filled.Gavel
                    TopToastTone.WARNING -> theme.surface to Icons.Filled.Info
                    TopToastTone.NEUTRAL -> theme.surface to Icons.Filled.Info
                }
                val fg = if (bg == theme.surface) theme.surfaceText else theme.contrastingTextOn(bg)

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = bg,
                    modifier = Modifier
                        .ludoShadow(theme.cardShadow, 999.dp)
                        .testTag(
                            if (msg.tone == TopToastTone.TRIPLE_SIX) "triple_six_toast" else "top_event_toast"
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(fg.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = fg,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.labelLarge,
                            color = fg,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
