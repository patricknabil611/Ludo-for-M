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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.online.OnlineRoom
import com.example.online.OnlineRoomSeat
import com.example.online.SeatControl
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.theme.CobaltDark
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralPink
import com.example.ui.theme.CoralPinkDark
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.InkDark
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenDark
import com.example.ui.theme.OutlineSoft
import com.example.ui.theme.SkyTopTint
import com.example.ui.theme.SunnyGold
import com.example.ui.theme.SunnyGoldDark
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
    val context = LocalContext.current
    var copiedNotice by remember { mutableStateOf(false) }

    BackHandler { onLeaveLobby() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTopTint, CreamBackground)))
            .safeDrawingPadding()
            .testTag("online_lobby_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        if (room == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CobaltPrimary)
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
                        color = InkDark
                    )
                    Text(
                        text = "${room.mode.title} • ${room.targetPlayerCount} Players",
                        style = MaterialTheme.typography.labelLarge,
                        color = CobaltPrimary
                    )
                }
            }

            // 6-Character Room Code Share Card
            CartoonCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = CobaltPrimary,
                cornerRadius = 26.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.online_room_code_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = room.roomCode,
                        style = MaterialTheme.typography.displayMedium.copy(
                            letterSpacing = 6.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = CobaltPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("lobby_room_code_text")
                    )
                    if (copiedNotice) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Code copied!",
                            style = MaterialTheme.typography.labelMedium,
                            color = MintGreenDark
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
                            containerColor = SunnyGold,
                            lipColor = SunnyGoldDark,
                            contentColor = Color(0xFF2C1E00),
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
                            containerColor = CobaltPrimary,
                            lipColor = CobaltDark,
                            minHeight = 48.dp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CoralPink.copy(alpha = 0.12f),
                    border = BorderStroke(1.5.dp, CoralPink),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoralPink,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Seats List
            CartoonCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = OutlineSoft,
                cornerRadius = 24.dp
            ) {
                Text(
                    text = "Players ($filledCount/${room.seats.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkDark
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isHost) {
                        "Wait for friends to join or fill empty seats with bots before starting."
                    } else {
                        "Waiting for the host to start the match..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )
                Spacer(Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    room.seats.forEach { seat ->
                        LobbySeatRow(
                            seat = seat,
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
                    containerColor = MintGreen,
                    lipColor = MintGreenDark,
                    minHeight = 60.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            CartoonButton(
                text = stringResource(R.string.online_leave_lobby),
                onClick = onLeaveLobby,
                tag = "lobby_leave_button",
                containerColor = CoralPink,
                lipColor = CoralPinkDark,
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
    isRoomHostSeat: Boolean,
    isMe: Boolean,
    canConfigureBot: Boolean,
    onSetBot: (BotDifficulty?) -> Unit
) {
    val palette = seat.color.palette()
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
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(palette.primary.copy(alpha = 0.55f))
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonOutline,
                            contentDescription = seat.nickname,
                            tint = Color.White,
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
                        color = InkDark,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (seat.controllerType) {
                            SeatControl.HUMAN -> if (badge.isNotEmpty()) "${seat.color.displayName} • $badge" else seat.color.displayName
                            SeatControl.BOT -> "${seat.color.displayName} • ${seat.botDifficulty.displayName} Bot"
                            SeatControl.OPEN -> "${seat.color.displayName} • Waiting for friend..."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted
                    )
                }
            }

            if (canConfigureBot) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = seat.controllerType == SeatControl.OPEN,
                        onClick = { onSetBot(null) },
                        label = { Text("Open") }
                    )
                    BotDifficulty.entries.forEach { diff ->
                        FilterChip(
                            selected = seat.controllerType == SeatControl.BOT && seat.botDifficulty == diff,
                            onClick = { onSetBot(diff) },
                            label = { Text(diff.displayName) }
                        )
                    }
                }
            }
        }
    }
}
