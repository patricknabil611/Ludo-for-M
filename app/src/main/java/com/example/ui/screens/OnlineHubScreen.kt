package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.GameModeId
import com.example.online.OnlineRoom
import com.example.online.RoomStatus
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.online.OnlineAuthUiState
import com.example.ui.online.OnlineHubUiState
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

/**
 * Online Multiplayer Hub:
 * - Active Match Rejoin card (for reconnects)
 * - Online Matchmaking (mode + player count + 30s countdown + fill with bots offer)
 * - Play with Friends (create private 6-char room code OR join by code)
 * - Global Leaderboard shortcut & Sign Out
 */
@Composable
fun OnlineHubScreen(
    authState: OnlineAuthUiState,
    hubState: OnlineHubUiState,
    onCreatePrivateRoom: (GameModeId, Int, Int) -> Unit,
    onJoinByCode: (String) -> Unit,
    onStartMatchmaking: (GameModeId, Int, Int) -> Unit,
    onCancelMatchmaking: () -> Unit,
    onFillMatchmakingWithBots: () -> Unit,
    onRejoinRoom: (OnlineRoom) -> Unit,
    onOpenLeaderboard: () -> Unit,
    onSignOut: () -> Unit,
    onBackClick: () -> Unit
) {
    var selectedMode by rememberSaveable { mutableStateOf(GameModeId.CLASSIC) }
    var selectedPlayerCount by rememberSaveable { mutableIntStateOf(2) }
    var timedDurationSeconds by rememberSaveable { mutableIntStateOf(300) }
    var joinCodeInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(selectedMode) {
        if (selectedMode == GameModeId.TEAMS_2V2) {
            selectedPlayerCount = 4
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTopTint, CreamBackground)))
            .safeDrawingPadding()
            .testTag("online_hub_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CartoonIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        onClick = {
                            if (hubState.isSearchingMatchmaking) onCancelMatchmaking()
                            onBackClick()
                        },
                        modifier = Modifier.testTag("online_hub_back_button")
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.online_hub_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = InkDark
                        )
                        Text(
                            text = authState.effectiveNickname,
                            style = MaterialTheme.typography.labelMedium,
                            color = CobaltPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CartoonIconButton(
                        icon = Icons.Filled.EmojiEvents,
                        contentDescription = stringResource(R.string.cd_leaderboard),
                        onClick = onOpenLeaderboard,
                        containerColor = SunnyGold,
                        iconTint = Color(0xFF2C1E00),
                        borderColor = SunnyGoldDark,
                        modifier = Modifier.testTag("online_hub_leaderboard_button")
                    )
                    CartoonIconButton(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(R.string.cd_sign_out),
                        onClick = onSignOut,
                        modifier = Modifier.testTag("online_hub_sign_out_button")
                    )
                }
            }

            // Status / Upgrade Success Banner
            authState.statusBanner?.let { banner ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MintGreen.copy(alpha = 0.14f),
                    border = BorderStroke(1.5.dp, MintGreenDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkDark,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Error Banner
            hubState.errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CoralPink.copy(alpha = 0.14f),
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

            // Active Match / Lobby Rejoin Card (for players who left/disconnected and want their seat back)
            val rejoinRoom = hubState.activeRooms.firstOrNull {
                it.status == RoomStatus.PLAYING && !hubState.isSearchingMatchmaking
            } ?: hubState.activeRooms.firstOrNull {
                it.status == RoomStatus.LOBBY && !hubState.isSearchingMatchmaking
            }
            if (rejoinRoom != null) {
                CartoonCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = MintGreenDark,
                    containerColor = Color(0xFFE9FBF2),
                    cornerRadius = 24.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (rejoinRoom.status == RoomStatus.PLAYING) {
                                    stringResource(R.string.online_rejoin_title)
                                } else {
                                    "Open Lobby Waiting"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = InkDark,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Room ${rejoinRoom.roomCode} • ${rejoinRoom.mode.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        CartoonButton(
                            text = stringResource(R.string.online_rejoin_cta),
                            onClick = { onRejoinRoom(rejoinRoom) },
                            tag = "online_rejoin_button",
                            icon = Icons.Filled.PlayArrow,
                            containerColor = MintGreen,
                            lipColor = MintGreenDark,
                            minHeight = 48.dp,
                            modifier = Modifier.width(132.dp)
                        )
                    }
                }
            }

            // Active Matchmaking Search Overlay Card
            if (hubState.isSearchingMatchmaking) {
                MatchmakingSearchCard(
                    mode = selectedMode,
                    targetCount = selectedPlayerCount,
                    joinedCount = hubState.currentLobbyRoom?.memberUids?.size ?: 1,
                    elapsedSeconds = hubState.matchmakingElapsedSeconds,
                    offerBotFill = hubState.offerMatchmakingBotFill,
                    isBusy = hubState.isBusy,
                    onFillWithBots = onFillMatchmakingWithBots,
                    onCancel = onCancelMatchmaking
                )
            } else {
                // Match Settings Card (shared by Matchmaking & Create Friend Room)
                CartoonCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = CobaltPrimary.copy(alpha = 0.4f),
                    cornerRadius = 24.dp
                ) {
                    Text(
                        text = stringResource(R.string.online_match_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = InkDark
                    )
                    Spacer(Modifier.height(10.dp))

                    // Mode picker chips
                    Text(
                        text = stringResource(R.string.setup_mode_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GameModeId.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                label = { Text(mode.title) },
                                modifier = Modifier.testTag("online_mode_chip_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Player count selector
                    Text(
                        text = stringResource(R.string.setup_players_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 4).forEach { count ->
                            val enabled = selectedMode != GameModeId.TEAMS_2V2 || count == 4
                            FilterChip(
                                selected = selectedPlayerCount == count,
                                enabled = enabled,
                                onClick = { selectedPlayerCount = count },
                                label = { Text("$count Players") },
                                modifier = Modifier.testTag("online_players_chip_$count")
                            )
                        }
                    }

                    // Timed duration picker
                    if (selectedMode == GameModeId.TIMED) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Match Clock",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkMuted
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = timedDurationSeconds == 300,
                                onClick = { timedDurationSeconds = 300 },
                                label = { Text("5 Minutes") }
                            )
                            FilterChip(
                                selected = timedDurationSeconds == 600,
                                onClick = { timedDurationSeconds = 600 },
                                label = { Text("10 Minutes") }
                            )
                        }
                    }
                }

                // Online Matchmaking Card
                CartoonCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = MintGreenDark,
                    cornerRadius = 24.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            tint = MintGreenDark,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.online_matchmaking_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = InkDark
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.online_matchmaking_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    CartoonButton(
                        text = stringResource(R.string.online_find_match),
                        onClick = {
                            onStartMatchmaking(selectedMode, selectedPlayerCount, timedDurationSeconds)
                        },
                        tag = "online_find_match_button",
                        icon = Icons.Filled.Search,
                        enabled = !hubState.isBusy,
                        containerColor = MintGreen,
                        lipColor = MintGreenDark,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Play with Friends Card (Create Room + Join by 6-char Code)
                CartoonCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = CobaltPrimary,
                    cornerRadius = 24.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = null,
                            tint = CobaltPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.online_friends_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = InkDark
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.online_friends_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(12.dp))

                    CartoonButton(
                        text = stringResource(R.string.online_create_room),
                        onClick = {
                            onCreatePrivateRoom(selectedMode, selectedPlayerCount, timedDurationSeconds)
                        },
                        tag = "online_create_room_button",
                        icon = Icons.Filled.AddCircle,
                        enabled = !hubState.isBusy,
                        containerColor = CobaltPrimary,
                        lipColor = CobaltDark,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = joinCodeInput,
                            onValueChange = { raw ->
                                val clean = raw.uppercase().filter { it.isLetterOrDigit() }.take(6)
                                joinCodeInput = clean
                            },
                            singleLine = true,
                            label = { Text(stringResource(R.string.online_room_code_hint)) },
                            leadingIcon = {
                                Icon(Icons.Filled.Key, contentDescription = null, tint = CobaltPrimary)
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("online_join_code_input")
                        )

                        CartoonButton(
                            text = stringResource(R.string.online_join_cta),
                            onClick = { onJoinByCode(joinCodeInput) },
                            tag = "online_join_room_button",
                            enabled = !hubState.isBusy && joinCodeInput.length == 6,
                            containerColor = SunnyGold,
                            lipColor = SunnyGoldDark,
                            contentColor = Color(0xFF2C1E00),
                            minHeight = 56.dp,
                            modifier = Modifier.width(104.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MatchmakingSearchCard(
    mode: GameModeId,
    targetCount: Int,
    joinedCount: Int,
    elapsedSeconds: Int,
    offerBotFill: Boolean,
    isBusy: Boolean,
    onFillWithBots: () -> Unit,
    onCancel: () -> Unit
) {
    CartoonCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("matchmaking_searching_card"),
        borderColor = CobaltPrimary,
        cornerRadius = 28.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = CobaltPrimary)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Finding ${mode.title} Match...",
                style = MaterialTheme.typography.headlineSmall,
                color = InkDark
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$joinedCount / $targetCount players found • ${elapsedSeconds}s elapsed",
                style = MaterialTheme.typography.titleMedium,
                color = CobaltPrimary
            )

            if (offerBotFill) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFF8DF),
                    border = BorderStroke(1.5.dp, SunnyGoldDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No full match found within 30 seconds",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkDark,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Fill the remaining empty seats with Medium Bots and start right away, or keep waiting!",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                        Spacer(Modifier.height(10.dp))
                        CartoonButton(
                            text = stringResource(R.string.online_fill_bots_and_play),
                            onClick = onFillWithBots,
                            tag = "matchmaking_fill_bots_button",
                            icon = Icons.Filled.SmartToy,
                            enabled = !isBusy,
                            containerColor = MintGreen,
                            lipColor = MintGreenDark,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            CartoonButton(
                text = stringResource(R.string.online_cancel_search),
                onClick = onCancel,
                tag = "matchmaking_cancel_button",
                containerColor = CoralPink,
                lipColor = CoralPinkDark,
                minHeight = 50.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
