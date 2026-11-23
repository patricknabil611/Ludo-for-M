package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PlayerAvatarCodec
import com.example.engine.GameModeId
import com.example.engine.GameState
import com.example.engine.PlayerState
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.ConfettiOverlay
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.online.OnlineRematchUi
import com.example.ui.theme.AvatarArtPalette
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.ludoShadow
import com.example.ui.theme.palette

@Composable
fun WinnerScreen(
    gameState: GameState,
    onlineRematchUi: OnlineRematchUi? = null,
    onRematchClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    val theme = LocalLudoTheme.current
    val rankedPlayers = remember(gameState.finishOrder, gameState.players) {
        gameState.finishOrder.mapNotNull { color -> gameState.playerByColor(color) }
    }
    val winner = rankedPlayers.firstOrNull()
    val podiumScale = remember { Animatable(0.6f) }

    LaunchedEffect(Unit) {
        podiumScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 300f))
    }

    val titleColor = theme.accessibleAccentOn(theme.blue, theme.bg)
    val winnerHeadlineColor = theme.accessibleAccentOn(
        winner?.color?.palette(theme)?.primary ?: theme.blue,
        theme.bg
    )
    val medalBlend = if (theme.isDarkSurface) 0.24f else 0.20f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .safeDrawingPadding()
            .testTag("winner_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = theme.crownIcon,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.winner_title),
                    style = MaterialTheme.typography.displayMedium,
                    color = titleColor,
                    textAlign = TextAlign.Center
                )
                val headline = when {
                    gameState.winningTeamLabel != null ->
                        "${gameState.winningTeamLabel} Win!"
                    winner != null && gameState.mode == GameModeId.TIMED ->
                        "${winner.nickname} Wins (${winner.score} pts)!"
                    winner != null ->
                        "${winner.nickname} takes the Crown!"
                    else -> ""
                }
                if (headline.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleLarge,
                        color = winnerHeadlineColor,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.ludoShadow(theme.cardShadow, 999.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.chipLine)
                ) {
                    Text(
                        text = "${gameState.mode.title} • ${gameState.matchFormat.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.surfaceMutedText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            // 1st / 2nd / 3rd Podium
            CartoonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = podiumScale.value
                        scaleY = podiumScale.value
                    },
                borderColor = theme.crownIcon.copy(alpha = 0.70f),
                cornerRadius = 26.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    PodiumPillar(
                        rank = 2,
                        player = rankedPlayers.getOrNull(1),
                        mode = gameState.mode,
                        pillarHeight = 112.dp,
                        pillarColor = lerp(theme.surface, AvatarArtPalette.medalSilver, medalBlend),
                        badgeColor = AvatarArtPalette.medalSilver,
                        modifier = Modifier.weight(1f)
                    )
                    PodiumPillar(
                        rank = 1,
                        player = rankedPlayers.getOrNull(0),
                        mode = gameState.mode,
                        pillarHeight = 152.dp,
                        pillarColor = lerp(theme.surface, AvatarArtPalette.medalGold, medalBlend),
                        badgeColor = AvatarArtPalette.medalGold,
                        modifier = Modifier.weight(1f)
                    )
                    PodiumPillar(
                        rank = 3,
                        player = rankedPlayers.getOrNull(2),
                        mode = gameState.mode,
                        pillarHeight = 86.dp,
                        pillarColor = lerp(theme.surface, AvatarArtPalette.medalBronze, medalBlend),
                        badgeColor = AvatarArtPalette.medalBronze,
                        modifier = Modifier.weight(1f)
                    )
                }

                val fourth = rankedPlayers.getOrNull(3)
                if (fourth != null) {
                    Spacer(Modifier.height(14.dp))
                    val fourthPalette = fourth.color.palette(theme)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = fourthPalette.light,
                        border = BorderStroke(1.dp, theme.chipLine)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PlayerAvatarBadge(
                                    avatar = PlayerAvatarCodec.resolve(
                                        raw = fourth.avatarId,
                                        fallbackColor = fourth.color,
                                        isBot = fourth.setup.isBot,
                                        seed = fourth.setup.seatIndex
                                    ),
                                    size = 32.dp,
                                    contentDescription = fourth.nickname
                                )
                                Text(
                                    text = "4th Place • ${fourth.nickname}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = theme.surfaceText
                                )
                            }
                            val fourthStat = buildString {
                                append(
                                    if (gameState.mode == GameModeId.TIMED) {
                                        "${fourth.score} pts"
                                    } else {
                                        val goal = if (gameState.mode == GameModeId.QUICK) 1 else 4
                                        "${fourth.finishedTokenCount}/$goal Home"
                                    }
                                )
                                if (fourth.capturesMade > 0) {
                                    append(" • ${fourth.capturesMade} KO")
                                }
                            }
                            Text(
                                text = fourthStat,
                                style = MaterialTheme.typography.labelMedium,
                                color = theme.surfaceMutedText
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onlineRematchUi != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = theme.surface,
                        border = BorderStroke(1.5.dp, theme.blue.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .ludoShadow(theme.cardShadow, 16.dp)
                            .testTag("online_rematch_status_card")
                    ) {
                        Text(
                            text = if (onlineRematchUi.myVoted) {
                                "Rematch voted! Waiting for others (${onlineRematchUi.votedCount}/${onlineRematchUi.totalRemainingHumans})"
                            } else {
                                "Rematch Votes: ${onlineRematchUi.votedCount}/${onlineRematchUi.totalRemainingHumans} • Starts when everyone agrees"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = theme.surfaceText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                CartoonButton(
                    text = when {
                        onlineRematchUi == null -> stringResource(R.string.winner_rematch)
                        onlineRematchUi.myVoted -> "Voted Rematch (${onlineRematchUi.votedCount}/${onlineRematchUi.totalRemainingHumans})"
                        else -> stringResource(R.string.online_vote_rematch)
                    },
                    onClick = onRematchClick,
                    tag = "winner_rematch_button",
                    icon = Icons.Filled.Refresh,
                    enabled = onlineRematchUi == null || (!onlineRematchUi.myVoted && !onlineRematchUi.isSubmitting),
                    containerColor = theme.green,
                    modifier = Modifier.fillMaxWidth()
                )
                CartoonButton(
                    text = if (onlineRematchUi != null) stringResource(R.string.online_leave_room) else stringResource(R.string.winner_home),
                    onClick = onHomeClick,
                    tag = "winner_home_button",
                    icon = Icons.Filled.Home,
                    containerColor = theme.primaryActionBg,
                    contentColor = theme.onPrimaryAction,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        ConfettiOverlay(
            triggerKey = 1,
            burstFromCenter = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PodiumPillar(
    rank: Int,
    player: PlayerState?,
    mode: GameModeId,
    pillarHeight: Dp,
    pillarColor: Color,
    badgeColor: Color,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current
    val resolvedPillarBg = if (player != null) pillarColor else theme.surfaceVariant
    val resolvedBadgeBg = if (player != null) badgeColor else theme.chipLine
    val badgeTextColor = theme.contrastingTextOn(resolvedBadgeBg)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (player != null) {
            PlayerAvatarBadge(
                avatar = PlayerAvatarCodec.resolve(
                    raw = player.avatarId,
                    fallbackColor = player.color,
                    isBot = player.setup.isBot,
                    seed = player.setup.seatIndex
                ),
                size = if (rank == 1) 60.dp else 52.dp,
                contentDescription = player.nickname
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = player.nickname,
                style = MaterialTheme.typography.titleMedium,
                color = theme.surfaceText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (player.setup.isBot) {
                Text(
                    text = "Bot • ${player.setup.botDifficulty.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.surfaceMutedText,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(6.dp))
        } else {
            Spacer(Modifier.height(78.dp))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(pillarHeight),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
            color = resolvedPillarBg,
            border = BorderStroke(1.5.dp, if (player != null) badgeColor else theme.chipLine)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(resolvedBadgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.titleMedium,
                        color = badgeTextColor
                    )
                }
                if (player != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (mode == GameModeId.TIMED) {
                            "${player.score} pts"
                        } else {
                            val goal = if (mode == GameModeId.QUICK) 1 else 4
                            "${player.finishedTokenCount}/$goal"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.surfaceMutedText
                    )
                    if (player.capturesMade > 0) {
                        Text(
                            text = "${player.capturesMade} KO",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.surfaceText
                        )
                    }
                }
            }
        }
    }
}
