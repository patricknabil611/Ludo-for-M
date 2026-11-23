package com.example.ui.game

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PlayerAvatarCodec
import com.example.engine.GameModeId
import com.example.engine.MatchFormat
import com.example.engine.PlayerColor
import com.example.engine.PlayerState
import com.example.engine.PowerUpType
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.theme.LocalLudoTheme

/**
 * Corner player card matching Part 5 spec:
 * 92dp wide column outside the board, 60dp avatar circle with 4dp outer countdown ring over
 * theme.timerTrack + 22dp blur glow at 60% when active, 3dp player-color border, theme.surface fill,
 * name in theme.text 14sp semibold, and tag line in theme.mutedText 11sp (or theme.text when active).
 */
@Composable
fun PlayerHudCard(
    player: PlayerState?,
    mode: GameModeId,
    matchFormat: MatchFormat = MatchFormat.FOUR_PLAYERS,
    isActive: Boolean,
    finishRank: Int?,
    timerProgress: Float,
    remainingSeconds: Int,
    turnTimerEnabled: Boolean = true,
    alignEnd: Boolean = false,
    canTapToRoll: Boolean = false,
    onCardClick: (() -> Unit)? = null,
    canUsePowerUps: Boolean = false,
    activeTargetingType: PowerUpType? = null,
    onPowerUpClick: (PowerUpType) -> Unit = {},
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (player == null) {
        Spacer(modifier = modifier.width(if (compact) 80.dp else 92.dp))
        return
    }

    val theme = LocalLudoTheme.current
    val playerColor = theme.playerColor(player.color)
    val isUrgentTimer = isActive && turnTimerEnabled && remainingSeconds in 1..3
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 0.98f,
        animationSpec = tween(220),
        label = "hudScale"
    )
    val cardWidth = if (compact) 84.dp else 92.dp
    val interactionSource = remember { MutableInteractionSource() }

    val statusText = when {
        player.isEliminated -> "Out"
        finishRank != null -> "#$finishRank"
        isActive && turnTimerEnabled && remainingSeconds > 0 -> {
            if (!player.setup.isBot && canTapToRoll) "Your turn · ${remainingSeconds}s"
            else if (!player.setup.isBot) "Your turn · ${remainingSeconds}s"
            else "${remainingSeconds}s"
        }
        isActive -> if (player.setup.isBot) "Thinking…" else "Your turn"
        player.setup.isBot -> "Bot · ${player.setup.botDifficulty.displayName}"
        mode == GameModeId.TIMED -> "${player.score} pts"
        else -> "Waiting"
    }

    val statusColor = when {
        player.isEliminated -> theme.accessibleAccentOn(theme.red, theme.bg)
        isUrgentTimer -> theme.accessibleAccentOn(theme.red, theme.bg)
        isActive -> theme.text
        else -> theme.mutedText
    }

    Column(
        modifier = modifier
            .width(cardWidth)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .alpha(if (player.isEliminated) 0.50f else 1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (canTapToRoll && onCardClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = playerColor.copy(alpha = 0.25f)),
                        role = Role.Button,
                        onClick = onCardClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(vertical = 2.dp)
            .testTag("player_card_${player.color.name.lowercase()}")
            .semantics {
                contentDescription = buildString {
                    append(player.nickname)
                    if (isActive) append(", active turn")
                    if (canTapToRoll) append(", tap to roll die")
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        AvatarWithTimerRing(
            player = player,
            mode = mode,
            isActive = isActive,
            finishRank = finishRank,
            timerProgress = timerProgress,
            remainingSeconds = remainingSeconds,
            turnTimerEnabled = turnTimerEnabled,
            compact = compact
        )

        Text(
            text = player.nickname,
            fontFamily = theme.font,
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (matchFormat.isTeamFormat) {
                val isTeamA = player.color == PlayerColor.RED || player.color == PlayerColor.YELLOW
                val teamBg = if (isTeamA) theme.yellow else theme.blue
                val teamFg = theme.contrastingTextOn(teamBg)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = teamBg
                ) {
                    Text(
                        text = if (isTeamA) "A" else "B",
                        fontFamily = theme.font,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = teamFg,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            Text(
                text = statusText,
                fontFamily = theme.font,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AvatarWithTimerRing(
    player: PlayerState,
    mode: GameModeId,
    isActive: Boolean,
    finishRank: Int?,
    timerProgress: Float,
    remainingSeconds: Int,
    turnTimerEnabled: Boolean,
    compact: Boolean
) {
    val theme = LocalLudoTheme.current
    val playerColor = theme.playerColor(player.color)
    val isUrgent = isActive && turnTimerEnabled && remainingSeconds in 1..3
    val ringColor = if (isUrgent) theme.red else playerColor

    // 60dp avatar circle (52dp in compact portrait) with 4dp outer countdown ring
    val avatarCircleSize = if (compact) 50.dp else 60.dp
    val ringContainerSize = avatarCircleSize + 10.dp
    val innerBadgeSize = avatarCircleSize - 10.dp

    val resolvedAvatar = PlayerAvatarCodec.resolve(
        raw = player.avatarId,
        fallbackColor = player.color,
        isBot = player.setup.isBot,
        seed = player.setup.seatIndex
    )

    Box(
        modifier = Modifier
            .size(ringContainerSize)
            .drawBehind {
                if (isActive && !player.isEliminated) {
                    val blurPx = 22.dp.toPx()
                    val glowColor = ringColor.copy(alpha = 0.60f)
                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            isAntiAlias = true
                            color = glowColor.toArgb()
                            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.nativeCanvas.drawCircle(
                            size.width / 2f,
                            size.height / 2f,
                            avatarCircleSize.toPx() / 2f,
                            paint
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 4dp outer countdown ring over theme.timerTrack
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            if (isActive && !player.isEliminated) {
                drawArc(
                    color = theme.timerTrack,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * (if (turnTimerEnabled) timerProgress.coerceIn(0f, 1f) else 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        // 60dp avatar circle: theme.surface fill + 3dp border in player color
        Box(
            modifier = Modifier
                .size(avatarCircleSize)
                .clip(CircleShape)
                .background(theme.surface)
                .border(3.dp, if (player.isEliminated) theme.timerTrack else playerColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            PlayerAvatarBadge(
                avatar = resolvedAvatar,
                size = innerBadgeSize,
                contentDescription = "${player.nickname} avatar",
                dimmed = player.isEliminated
            )
        }

        // Compact corner status indicator
        when {
            player.isEliminated -> {
                SmallAvatarBadge(
                    bg = theme.red,
                    borderColor = theme.bg,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Eliminated",
                        tint = theme.contrastingTextOn(theme.red),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            finishRank == 1 -> {
                SmallAvatarBadge(
                    bg = theme.yellow,
                    borderColor = theme.bg,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = "Winner",
                        tint = theme.contrastingTextOn(theme.yellow),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            mode == GameModeId.MASTER -> {
                val badgeBg = if (player.hasCapturedOpponent) theme.green else theme.red
                SmallAvatarBadge(
                    bg = badgeBg,
                    borderColor = theme.bg,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(
                        imageVector = if (player.hasCapturedOpponent) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        contentDescription = if (player.hasCapturedOpponent) "Home unlocked" else "Home locked",
                        tint = theme.contrastingTextOn(badgeBg),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            player.setup.isBot -> {
                SmallAvatarBadge(
                    bg = theme.surface,
                    borderColor = playerColor,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SmartToy,
                        contentDescription = "${player.setup.botDifficulty.displayName} Bot",
                        tint = theme.surfaceText,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallAvatarBadge(
    bg: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.size(18.dp),
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
