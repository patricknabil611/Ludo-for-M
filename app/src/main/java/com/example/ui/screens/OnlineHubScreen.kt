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
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.GameModeId
import com.example.engine.MatchFormat
import com.example.engine.TurnTimerOption
import com.example.online.OnlineRoom
import com.example.online.RoomStatus
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.online.OnlineAuthUiState
import com.example.ui.online.OnlineHubUiState
import com.example.ui.theme.LocalLudoTheme

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
    onCreatePrivateRoom: (GameModeId, MatchFormat, Int, Int) -> Unit,
    onJoinByCode: (String) -> Unit,
    onStartMatchmaking: (GameModeId, MatchFormat, Int, Int) -> Unit,
    onCancelMatchmaking: () -> Unit,
    onFillMatchmakingWithBots: () -> Unit,
    onRejoinRoom: (OnlineRoom) -> Unit,
    onOpenLeaderboard: () -> Unit,
    onSignOut: () -> Unit,
    onBackClick: () -> Unit
) {
    val theme = LocalLudoTheme.current
    var selectedMode by rememberSaveable { mutableStateOf(GameModeId.CLASSIC) }
    var selectedFormat by rememberSaveable { mutableStateOf(MatchFormat.ONE_VS_ONE) }
    var selectedTimer by rememberSaveable { mutableStateOf(TurnTimerOption.FIFTEEN) }
    var timedDurationSeconds by rememberSaveable { mutableIntStateOf(300) }
    var joinCodeInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(selectedMode) {
        if (selectedMode == GameModeId.ELIMINATION && selectedFormat == MatchFormat.ONE_VS_ONE) {
            selectedFormat = MatchFormat.FOUR_PLAYERS
        }
    }

    val blueOnBg = theme.accessibleAccentOn(theme.blue, theme.bg)
    val blueOnSurface = theme.accessibleAccentOn(theme.blue, theme.surface)
    val greenOnSurface = theme.accessibleAccentOn(theme.green, theme.surface)
    val statusBg = lerp(theme.surface, theme.green, if (theme.isDarkSurface) 0.20f else 0.12f)
    val errorBg = lerp(theme.surface, theme.red, if (theme.isDarkSurface) 0.22f else 0.10f)
    val errorTextColor = theme.accessibleAccentOn(theme.red, errorBg)
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = theme.surfaceVariant,
        labelColor = theme.surfaceText,
        selectedContainerColor = theme.primaryActionBg,
        selectedLabelColor = theme.onPrimaryAction,
        disabledContainerColor = theme.surfaceVariant.copy(alpha = 0.5f),
        disabledLabelColor = theme.surfaceMutedText.copy(alpha = 0.5f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
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
                            color = theme.text
                        )
                        Text(
                            text = authState.effectiveNickname,
                            style = MaterialTheme.typography.labelMedium,
                            color = blueOnBg,
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
                        containerColor = theme.yellow,
                        iconTint = theme.contrastingTextOn(theme.yellow),
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
                    color = statusBg,
                    border = BorderStroke(1.5.dp, theme.green.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.surfaceText,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Error Banner
            hubState.errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
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

            // Active Match / Lobby Rejoin Card
            val rejoinRoom = hubState.activeRooms.firstOrNull {
                it.status == RoomStatus.PLAYING && !hubState.isSearchingMatchmaking
            } ?: hubState.activeRooms.firstOrNull {
                it.status == RoomStatus.LOBBY && !hubState.isSearchingMatchmaking
            }
            if (rejoinRoom != null) {
                CartoonCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = theme.green.copy(alpha = 0.65f),
                    containerColor = statusBg,
                    cornerRadius = 22.dp
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
                                color = theme.surfaceText,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Room ${rejoinRoom.roomCode} • ${rejoinRoom.mode.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.surfaceMutedText
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        CartoonButton(
                            text = stringResource(R.string.online_rejoin_cta),
                            onClick = { onRejoinRoom(rejoinRoom) },
                            tag = "online_rejoin_button",
                            icon = Icons.Filled.PlayArrow,
                            containerColor = theme.green,
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
                    targetCount = selectedFormat.playerCount,
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
                    borderColor = theme.blue.copy(alpha = 0.40f),
                    cornerRadius = 24.dp
                ) {
                    Text(
                        text = stringResource(R.string.online_match_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = theme.surfaceText
                    )
                    Spacer(Modifier.height(10.dp))

                    // Mode picker chips
                    Text(
                        text = stringResource(R.string.setup_mode_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.surfaceMutedText
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GameModeId.entries.forEach { mode ->
                            val isSelected = selectedMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedMode = mode },
                                label = { Text(mode.title) },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = theme.chipLine,
                                    selectedBorderColor = theme.primaryActionBg
                                ),
                                modifier = Modifier.testTag("online_mode_chip_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Match format selector (1v1 / 4 Players / 2v2 Team)
                    Text(
                        text = "Match Format",
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.surfaceMutedText
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MatchFormat.entries.forEach { format ->
                            val enabled = format.playerCount in selectedMode.minPlayers..selectedMode.maxPlayers
                            val isSelected = selectedFormat == format
                            FilterChip(
                                selected = isSelected,
                                enabled = enabled,
                                onClick = { selectedFormat = format },
                                label = { Text(format.label) },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = enabled,
                                    selected = isSelected,
                                    borderColor = theme.chipLine,
                                    selectedBorderColor = theme.primaryActionBg
                                ),
                                modifier = Modifier.testTag("online_format_chip_${format.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Turn timer selector (Off / 10s / 15s / 20s)
                    Text(
                        text = "Turn Timer",
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.surfaceMutedText
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TurnTimerOption.entries.forEach { option ->
                            val isSelected = selectedTimer == option
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTimer = option },
                                label = { Text(option.label) },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = theme.chipLine,
                                    selectedBorderColor = theme.primaryActionBg
                                ),
                                modifier = Modifier.testTag("online_timer_chip_${option.name.lowercase()}")
                            )
                        }
                    }

                    // Timed duration picker
                    if (selectedMode == GameModeId.TIMED) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Match Clock",
                            style = MaterialTheme.typography.labelLarge,
                            color = theme.surfaceMutedText
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = timedDurationSeconds == 300,
                                onClick = { timedDurationSeconds = 300 },
                                label = { Text("5 Minutes") },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = timedDurationSeconds == 300,
                                    borderColor = theme.chipLine,
                                    selectedBorderColor = theme.primaryActionBg
                                )
                            )
                            FilterChip(
                                selected = timedDurationSeconds == 600,
                                onClick = { timedDurationSeconds = 600 },
                                label = { Text("10 Minutes") },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = timedDurationSeconds == 600,
                                    borderColor = theme.chipLine,
                                    selectedBorderColor = theme.primaryActionBg
                                )
                            )
                        }
                    }
                }

                // Online Matchmaking Card
                CartoonCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = theme.green.copy(alpha = 0.55f),
                    cornerRadius = 24.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            tint = greenOnSurface,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.online_matchmaking_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = theme.surfaceText
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.online_matchmaking_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.surfaceMutedText
                    )
                    Spacer(Modifier.height(12.dp))
                    CartoonButton(
                        text = stringResource(R.string.online_find_match),
                        onClick = {
                            onStartMatchmaking(
                                selectedMode,
                                selectedFormat,
                                timedDurationSeconds,
                                selectedTimer.seconds
                            )
                        },
                        tag = "online_find_match_button",
                        icon = Icons.Filled.Search,
                        enabled = !hubState.isBusy,
                        containerColor = theme.green,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Play with Friends Card (Create Room + Join by 6-char Code)
                CartoonCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = theme.blue.copy(alpha = 0.55f),
                    cornerRadius = 24.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = null,
                            tint = blueOnSurface,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.online_friends_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = theme.surfaceText
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.online_friends_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.surfaceMutedText
                    )
                    Spacer(Modifier.height(12.dp))

                    CartoonButton(
                        text = stringResource(R.string.online_create_room),
                        onClick = {
                            onCreatePrivateRoom(
                                selectedMode,
                                selectedFormat,
                                timedDurationSeconds,
                                selectedTimer.seconds
                            )
                        },
                        tag = "online_create_room_button",
                        icon = Icons.Filled.AddCircle,
                        enabled = !hubState.isBusy,
                        containerColor = theme.primaryActionBg,
                        contentColor = theme.onPrimaryAction,
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
                                Icon(Icons.Filled.Key, contentDescription = null, tint = blueOnSurface)
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
                            containerColor = theme.yellow,
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
    val theme = LocalLudoTheme.current
    val blueOnSurface = theme.accessibleAccentOn(theme.blue, theme.surface)
    val offerBg = lerp(theme.surface, theme.yellow, if (theme.isDarkSurface) 0.20f else 0.14f)

    CartoonCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("matchmaking_searching_card"),
        borderColor = theme.blue.copy(alpha = 0.55f),
        cornerRadius = 26.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = theme.blue)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Finding ${mode.title} Match...",
                style = MaterialTheme.typography.headlineSmall,
                color = theme.surfaceText
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$joinedCount / $targetCount players found • ${elapsedSeconds}s elapsed",
                style = MaterialTheme.typography.titleMedium,
                color = blueOnSurface
            )

            if (offerBotFill) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = offerBg,
                    border = BorderStroke(1.5.dp, theme.yellow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No full match found within 30 seconds",
                            style = MaterialTheme.typography.titleSmall,
                            color = theme.surfaceText,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Fill the remaining empty seats with Medium Bots and start right away, or keep waiting!",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.surfaceMutedText
                        )
                        Spacer(Modifier.height(10.dp))
                        CartoonButton(
                            text = stringResource(R.string.online_fill_bots_and_play),
                            onClick = onFillWithBots,
                            tag = "matchmaking_fill_bots_button",
                            icon = Icons.Filled.SmartToy,
                            enabled = !isBusy,
                            containerColor = theme.green,
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
                containerColor = theme.red,
                minHeight = 50.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
