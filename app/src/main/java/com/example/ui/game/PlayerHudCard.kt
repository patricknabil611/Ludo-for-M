package com.example.ui.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.GameModeId
import com.example.engine.PlayerColor
import com.example.engine.PlayerState
import com.example.engine.PowerUpType
import com.example.ui.theme.CoralPink
import com.example.ui.theme.InkDark
import com.example.ui.theme.InkMuted
import com.example.ui.theme.OutlineSoft
import com.example.ui.theme.SunnyGold
import com.example.ui.theme.SunnyGoldDark
import com.example.ui.theme.palette

/**
 * Player HUD card placed near each corner, displaying the 30-second countdown ring,
 * bot icon + difficulty label, Timed score, Teams 2v2 badge, Master lock, Dice Storm charge,
 * and held Power-Up buttons (Power-Up mode only).
 */
@Composable
fun PlayerHudCard(
    player: PlayerState?,
    mode: GameModeId,
    isActive: Boolean,
    finishRank: Int?,
    timerProgress: Float,
    remainingSeconds: Int,
    alignEnd: Boolean = false,
    canUsePowerUps: Boolean = false,
    activeTargetingType: PowerUpType? = null,
    onPowerUpClick: (PowerUpType) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (player == null) {
        Spacer(modifier = modifier)
        return
    }

    val palette = player.color.palette()
    val isBot = player.setup.isBot
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 0.97f,
        animationSpec = tween(220),
        label = "hudScale"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            player.isEliminated -> OutlineSoft
            isActive && remainingSeconds <= 5 -> CoralPink
            isActive -> palette.primary
            else -> OutlineSoft
        },
        label = "hudBorder"
    )

    Surface(
        modifier = modifier
            .alpha(if (player.isEliminated) 0.55f else 1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(18.dp),
        color = when {
            player.isEliminated -> Color(0xFFECEAE4)
            isActive -> palette.light
            else -> Color.White.copy(alpha = 0.90f)
        },
        border = BorderStroke(if (isActive) 2.5.dp else 1.5.dp, borderColor),
        shadowElevation = if (isActive) 8.dp else 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
            ) {
                if (!alignEnd) {
                    AvatarWithTimerRing(
                        player = player,
                        isActive = isActive,
                        finishRank = finishRank,
                        timerProgress = timerProgress,
                        remainingSeconds = remainingSeconds
                    )
                    Spacer(Modifier.width(7.dp))
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
                ) {
                    Text(
                        text = player.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        color = InkDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Mode & Bot mini badges row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isBot) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = palette.primary.copy(alpha = 0.16f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SmartToy,
                                        contentDescription = null,
                                        tint = palette.dark,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = player.setup.botDifficulty.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.dark,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (mode == GameModeId.TIMED) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFF0096C7).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${player.score} pts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF005F80),
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (mode == GameModeId.TEAMS_2V2) {
                            val isTeamSun = player.color == PlayerColor.RED || player.color == PlayerColor.YELLOW
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (isTeamSun) Color(0xFFFFE4B5) else Color(0xFFD8EEFF)
                            ) {
                                Text(
                                    text = if (isTeamSun) "Team A" else "Team B",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkDark,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (mode == GameModeId.MASTER) {
                            Icon(
                                imageVector = if (player.hasCapturedOpponent) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                contentDescription = if (player.hasCapturedOpponent) "Home unlocked" else "Home locked",
                                tint = if (player.hasCapturedOpponent) palette.dark else CoralPink,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        if (player.diceStormCharges > 0) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = SunnyGold.copy(alpha = 0.35f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bolt,
                                        contentDescription = "Dice Storm 2x",
                                        tint = InkDark,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "2×",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkDark
                                    )
                                }
                            }
                        }

                        if (mode == GameModeId.POWER_UP && player.doubleDiceActive) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = SunnyGold.copy(alpha = 0.40f),
                                border = BorderStroke(1.dp, SunnyGoldDark)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Casino,
                                        contentDescription = "Double Dice active",
                                        tint = InkDark,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "2×",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkDark,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    // Progress pips / Rank / Eliminated status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        when {
                            player.isEliminated -> {
                                Text(
                                    text = "Eliminated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CoralPink,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            finishRank != null -> {
                                val rankLabel = when (finishRank) {
                                    1 -> "1st Place"
                                    2 -> "2nd Place"
                                    3 -> "3rd Place"
                                    else -> "${finishRank}th"
                                }
                                Icon(
                                    imageVector = Icons.Filled.EmojiEvents,
                                    contentDescription = null,
                                    tint = SunnyGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = rankLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.dark,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            else -> {
                                val pipsCount = if (mode == GameModeId.ONE_TOKEN_RACE) 1 else 4
                                repeat(pipsCount) { idx ->
                                    val isHome = idx < player.finishedTokenCount
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isHome) palette.primary else palette.primary.copy(alpha = 0.20f)
                                            )
                                    )
                                }
                                if (isActive) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "${remainingSeconds}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (remainingSeconds <= 5) CoralPink else InkMuted
                                    )
                                }
                            }
                        }
                    }
                }

                if (alignEnd) {
                    Spacer(Modifier.width(7.dp))
                    AvatarWithTimerRing(
                        player = player,
                        isActive = isActive,
                        finishRank = finishRank,
                        timerProgress = timerProgress,
                        remainingSeconds = remainingSeconds
                    )
                }
            }

            // Held Power-Up buttons (Power-Up mode only)
            if (mode == GameModeId.POWER_UP && player.heldPowerUps.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    player.heldPowerUps.forEachIndexed { idx, powerUp ->
                        val isClickable = isActive && canUsePowerUps && !isBot
                        val isSelected = isActive && activeTargetingType == powerUp
                        val accent = powerUpAccentColor(powerUp)
                        Surface(
                            onClick = { if (isClickable) onPowerUpClick(powerUp) },
                            enabled = isClickable,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 32.dp)
                                .testTag("powerup_btn_${player.color.name.lowercase()}_$idx")
                                .semantics {
                                    contentDescription = if (isClickable) {
                                        "Use ${powerUp.title} power-up"
                                    } else {
                                        "${player.nickname} holds ${powerUp.title}"
                                    }
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isSelected -> accent
                                isClickable -> accent
                                else -> powerUpSoftBg(powerUp)
                            },
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.2.dp,
                                color = if (isSelected) InkDark else accent
                            ),
                            shadowElevation = if (isClickable) 3.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = powerUpIcon(powerUp),
                                    contentDescription = null,
                                    tint = if (isClickable || isSelected) Color.White else accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = powerUp.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isClickable || isSelected) Color.White else InkDark,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarWithTimerRing(
    player: PlayerState,
    isActive: Boolean,
    finishRank: Int?,
    timerProgress: Float,
    remainingSeconds: Int
) {
    val palette = player.color.palette()
    val ringColor = if (remainingSeconds <= 5) CoralPink else palette.primary

    Box(
        modifier = Modifier.size(42.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isActive && !player.isEliminated) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                drawArc(
                    color = palette.primary.copy(alpha = 0.18f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * timerProgress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (player.isEliminated) InkMuted else palette.primary),
            contentAlignment = Alignment.Center
        ) {
            val avatarIcon = when {
                player.isEliminated -> Icons.Filled.Close
                finishRank == 1 -> Icons.Filled.EmojiEvents
                player.setup.isBot -> Icons.Filled.SmartToy
                else -> Icons.Filled.Person
            }
            Icon(
                imageVector = avatarIcon,
                contentDescription = "${player.nickname} avatar",
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
