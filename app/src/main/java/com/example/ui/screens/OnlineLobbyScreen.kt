package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.PlayerAvatarCodec
import com.example.engine.BotDifficulty
import com.example.engine.PlayerColor
import com.example.engine.TurnTimerOption
import com.example.online.OnlineRoom
import com.example.online.OnlineRoomSeat
import com.example.online.SeatControl
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.palette

/**
 * Live Friend Room Lobby:
 * - Prominent 6-character room code with Share & Copy buttons
 * - Real-time seat list showing joined players, bots, and empty seats
 * - Host can fill empty seats with Easy/Medium/Hard bots and start the match
 */
@Composable
fun OnlineLobbyScreen(
    room: OnlineRoom?,
    myUid: String,
    isBusy: Boolean,
    errorMessage: String?,
    onSetSeatBot: (seatIndex: Int, difficulty: BotDifficulty?) -> Unit,
    onStartMatch: () -> Unit,
    onLeaveLobby: () -> Unit
) {
    val theme = LocalLudoTheme.current
    val context = LocalContext.current
    var copiedNotice by remember { mutableStateOf(false) }

    BackHandler { onLeaveLobby() }

    val blueOnBg = theme.accessibleAccentOn(theme.blue, theme.bg)
    val blueOnSurface = theme.accessibleAccentOn(theme.blue, theme.surface)
    val greenOnSurface = theme.accessibleAccentOn(theme.green, theme.surface)
    val errorBg = lerp(theme.surface, theme.red, if (theme.isDarkSurface) 0.22f else 0.10f)
    val errorTextColor = theme.accessibleAccentOn(theme.red, errorBg)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .safeDrawingPadding()
            .testTag("online_lobby_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        if (room == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.blue)
            }
            return@Box
        }

        val isHost = room.hostUid == myUid
        val filledCount = room.seats.count { it.controllerType != SeatControl.OPEN }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CartoonIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    onClick = onLeaveLobby,
                    modifier = Modifier.testTag("lobby_back_button")
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.online_lobby_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = theme.text
                    )
                    val timerLabel = TurnTimerOption.fromSeconds(room.turnTimerSeconds).label
                    Text(
                        text = "${room.mode.title} • ${room.matchFormat.label} • Timer: $timerLabel",
                        style = MaterialTheme.typography.labelLarge,
                        color = blueOnBg
                    )
                }
            }

            // 6-Character Room Code Share Card
            CartoonCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = theme.blue.copy(alpha = 0.55f),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.online_room_code_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.surfaceMutedText
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = room.roomCode,
                        style = MaterialTheme.typography.displayMedium.copy(
                            letterSpacing = 6.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = blueOnSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("lobby_room_code_text")
                    )
                    if (copiedNotice) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Code copied!",
                            style = MaterialTheme.typography.labelMedium,
                            color = greenOnSurface
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CartoonButton(
                            text = stringResource(R.string.online_copy_code),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Room Code", room.roomCode))
                                copiedNotice = true
                            },
                            tag = "lobby_copy_code_button",
                            icon = Icons.Filled.ContentCopy,
                            containerColor = theme.yellow,
                            minHeight = 48.dp,
                            modifier = Modifier.weight(1f)
                        )
                        CartoonButton(
                            text = stringResource(R.string.online_share_code),
                            onClick = {
                                val shareText = "Join my ${room.mode.title} match in Ludo for M! Room code: ${room.roomCode}"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Room Code"))
                            },
                            tag = "lobby_share_code_button",
                            icon = Icons.Filled.Share,
                            containerColor = theme.primaryActionBg,
                            contentColor = theme.onPrimaryAction,
                            minHeight = 48.dp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = errorBg,
                    border = BorderStroke(1.5.dp, theme.red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = errorTextColor,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Seats List
            CartoonCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp
            ) {
                Text(
                    text = "Players ($filledCount/${room.seats.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.surfaceText
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isHost) {
                        "Wait for friends to join or fill empty seats with bots before starting."
                    } else {
                        "Waiting for the host to start the match..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.surfaceMutedText
                )
                Spacer(Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    room.seats.forEach { seat ->
                        val teamLabel = if (room.matchFormat.isTeamFormat) {
                            if (seat.color == PlayerColor.RED || seat.color == PlayerColor.YELLOW) "Team A" else "Team B"
                        } else null
                        LobbySeatRow(
                            seat = seat,
                            teamLabel = teamLabel,
                            isRoomHostSeat = seat.uid.isNotBlank() && seat.uid == room.hostUid,
                            isMe = seat.uid == myUid,
                            canConfigureBot = isHost && seat.controllerType != SeatControl.HUMAN,
                            onSetBot = { diff -> onSetSeatBot(seat.seatIndex, diff) }
                        )
                    }
                }
            }

            if (isHost) {
                CartoonButton(
                    text = stringResource(R.string.setup_start_game),
                    onClick = onStartMatch,
                    tag = "lobby_start_match_button",
                    icon = Icons.Filled.PlayArrow,
                    enabled = !isBusy,
                    containerColor = theme.green,
                    minHeight = 60.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            CartoonButton(
                text = stringResource(R.string.online_leave_lobby),
                onClick = onLeaveLobby,
                tag = "lobby_leave_button",
                containerColor = theme.red,
                minHeight = 50.dp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LobbySeatRow(
    seat: OnlineRoomSeat,
    teamLabel: String?,
    isRoomHostSeat: Boolean,
    isMe: Boolean,
    canConfigureBot: Boolean,
    onSetBot: (BotDifficulty?) -> Unit
) {
    val theme = LocalLudoTheme.current
    val palette = seat.color.palette(theme)
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = theme.surface,
        labelColor = theme.surfaceText,
        selectedContainerColor = theme.primaryActionBg,
        selectedLabelColor = theme.onPrimaryAction
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = palette.light,
        border = BorderStroke(1.5.dp, palette.primary.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (seat.controllerType == SeatControl.OPEN) {
                    val emptyAvatarBg = palette.primary.copy(alpha = 0.72f)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(emptyAvatarBg)
                            .border(2.dp, theme.tokenEdge, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonOutline,
                            contentDescription = seat.nickname,
                            tint = theme.contrastingTextOn(palette.primary),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    PlayerAvatarBadge(
                        avatar = PlayerAvatarCodec.resolve(
                            raw = seat.avatarId,
                            fallbackColor = seat.color,
                            isBot = seat.controllerType == SeatControl.BOT,
                            seed = seat.seatIndex
                        ),
                        size = 42.dp,
                        contentDescription = seat.nickname
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    val badge = buildString {
                        if (isRoomHostSeat) append("Host")
                        if (isMe) {
                            if (isNotEmpty()) append(" • ")
                            append("You")
                        }
                    }
                    Text(
                        text = seat.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.surfaceText,
                        fontWeight = FontWeight.Bold
                    )
                    val prefix = if (teamLabel != null) "${seat.color.displayName} ($teamLabel)" else seat.color.displayName
                    Text(
                        text = when (seat.controllerType) {
                            SeatControl.HUMAN -> if (badge.isNotEmpty()) "$prefix • $badge" else prefix
                            SeatControl.BOT -> "$prefix • ${seat.botDifficulty.displayName} Bot"
                            SeatControl.OPEN -> "$prefix • Waiting for friend..."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.surfaceMutedText
                    )
                }
            }

            if (canConfigureBot) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isOpen = seat.controllerType == SeatControl.OPEN
                    FilterChip(
                        selected = isOpen,
                        onClick = { onSetBot(null) },
                        label = { Text("Open") },
                        colors = chipColors,
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isOpen,
                            borderColor = theme.chipLine,
                            selectedBorderColor = theme.primaryActionBg
                        )
                    )
                    BotDifficulty.entries.forEach { diff ->
                        val isBotDiff = seat.controllerType == SeatControl.BOT && seat.botDifficulty == diff
                        FilterChip(
                            selected = isBotDiff,
                            onClick = { onSetBot(diff) },
                            label = { Text(diff.displayName) },
                            colors = chipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isBotDiff,
                                borderColor = theme.chipLine,
                                selectedBorderColor = theme.primaryActionBg
                            )
                        )
                    }
                }
            }
        }
    }
}
