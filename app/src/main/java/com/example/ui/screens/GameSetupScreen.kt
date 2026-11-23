package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.bot.MatchSeatPlanner
import com.example.bot.SeatConfig
import com.example.bot.SeatOccupancy
import com.example.data.PlayerAvatarCodec
import com.example.engine.BotDifficulty
import com.example.engine.GameModeId
import com.example.engine.MatchFormat
import com.example.engine.PlayerColor
import com.example.engine.PlayerSetup
import com.example.engine.TurnTimerOption
import com.example.ui.components.AvatarCustomizerDialog
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.game.ModeRulesDialog
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.ludoShadow
import com.example.ui.theme.modeAccentColor

@Composable
fun GameSetupScreen(
    selectedMode: GameModeId,
    initialFormat: MatchFormat = if (selectedMode == GameModeId.ELIMINATION) MatchFormat.FOUR_PLAYERS else MatchFormat.ONE_VS_ONE,
    initialTimer: TurnTimerOption = TurnTimerOption.FIFTEEN,
    defaultProfileNickname: String = "",
    defaultProfileAvatarId: String = "",
    showPowerUpGuide: Boolean = false,
    onDismissPowerUpGuide: () -> Unit = {},
    onBackClick: () -> Unit,
    onStartMatch: (List<PlayerSetup>, GameModeId, MatchFormat, Int, Int) -> Unit
) {
    val theme = LocalLudoTheme.current
    val resolvedInitialFormat = if (initialFormat.playerCount in selectedMode.minPlayers..selectedMode.maxPlayers) {
        initialFormat
    } else {
        MatchFormat.FOUR_PLAYERS
    }

    var selectedFormat by remember(selectedMode, resolvedInitialFormat) { mutableStateOf(resolvedInitialFormat) }
    var selectedTimer by remember(selectedMode, initialTimer) { mutableStateOf(initialTimer) }
    var timedMinutes by remember(selectedMode) { mutableIntStateOf(5) }
    var editingAvatarSeatIndex by remember { mutableStateOf<Int?>(null) }
    var showRulesSheet by remember { mutableStateOf(false) }

    val seat1Name = defaultProfileNickname.trim().take(14)
        .takeIf { it.isNotBlank() && !it.equals("Player", ignoreCase = true) }
        ?: "Player 1"
    val seat1Avatar = defaultProfileAvatarId.ifBlank {
        PlayerAvatarCodec.resolve(null, PlayerColor.RED, isBot = false, seed = 0).encode()
    }

    val seats = remember(selectedMode, resolvedInitialFormat) {
        val startFour = resolvedInitialFormat.playerCount == 4
        mutableStateListOf(
            SeatConfig(
                0,
                SeatOccupancy.HUMAN,
                seat1Name,
                PlayerColor.RED,
                BotDifficulty.MEDIUM,
                avatarId = seat1Avatar
            ),
            SeatConfig(
                1,
                SeatOccupancy.HUMAN,
                "Player 2",
                if (startFour) PlayerColor.GREEN else PlayerColor.YELLOW,
                BotDifficulty.MEDIUM,
                avatarId = PlayerAvatarCodec.CURATED_PRESETS[1].config.encode()
            ),
            SeatConfig(
                2,
                if (startFour) SeatOccupancy.BOT else SeatOccupancy.EMPTY,
                "Bot 3",
                if (startFour) PlayerColor.YELLOW else PlayerColor.GREEN,
                BotDifficulty.MEDIUM,
                avatarId = PlayerAvatarCodec.resolve(null, PlayerColor.GREEN, isBot = true, seed = 2).encode()
            ),
            SeatConfig(
                3,
                if (startFour) SeatOccupancy.BOT else SeatOccupancy.EMPTY,
                "Bot 4",
                PlayerColor.BLUE,
                BotDifficulty.MEDIUM,
                avatarId = PlayerAvatarCodec.resolve(null, PlayerColor.BLUE, isBot = true, seed = 3).encode()
            )
        )
    }

    fun syncColorsForTwoActiveSeats() {
        if (selectedFormat != MatchFormat.ONE_VS_ONE) return
        val activeIndices = seats.indices.filter { seats[it].occupancy != SeatOccupancy.EMPTY }
        if (activeIndices.size == 2) {
            val i0 = activeIndices[0]
            val i1 = activeIndices[1]
            seats[i1] = seats[i1].copy(color = seats[i0].color.opposite())
        }
    }

    fun onFormatSelected(format: MatchFormat) {
        if (format.playerCount !in selectedMode.minPlayers..selectedMode.maxPlayers) return
        selectedFormat = format
        val targetCount = format.playerCount
        val activeIndices = seats.indices.filter { seats[it].occupancy != SeatOccupancy.EMPTY }
        if (activeIndices.size > targetCount) {
            val toDisable = activeIndices
                .sortedBy { if (seats[it].occupancy == SeatOccupancy.HUMAN) 0 else 1 }
                .drop(targetCount)
            for (idx in toDisable) {
                seats[idx] = seats[idx].copy(occupancy = SeatOccupancy.EMPTY)
            }
        } else if (activeIndices.size < targetCount) {
            var needed = targetCount - activeIndices.size
            for (idx in seats.indices) {
                if (needed <= 0) break
                if (seats[idx].occupancy == SeatOccupancy.EMPTY) {
                    seats[idx] = seats[idx].copy(
                        occupancy = SeatOccupancy.BOT,
                        nickname = if (seats[idx].nickname.isBlank()) "Bot ${idx + 1}" else seats[idx].nickname
                    )
                    needed -= 1
                }
            }
        }
        if (format == MatchFormat.ONE_VS_ONE) {
            syncColorsForTwoActiveSeats()
        } else if (format == MatchFormat.TEAMS_2V2) {
            val standardOrder = listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)
            standardOrder.forEachIndexed { i, color ->
                seats[i] = seats[i].copy(color = color)
            }
        }
    }

    fun setSeatOccupancy(idx: Int, newOccupancy: SeatOccupancy) {
        val current = seats[idx]
        if (current.occupancy == newOccupancy) return

        val updatedName = when {
            newOccupancy == SeatOccupancy.BOT && (current.nickname.startsWith("Player") || current.nickname.isBlank()) ->
                "Bot ${idx + 1}"
            newOccupancy == SeatOccupancy.HUMAN && (current.nickname.startsWith("Bot") || current.nickname.isBlank()) ->
                "Player ${idx + 1}"
            else -> current.nickname
        }
        seats[idx] = current.copy(occupancy = newOccupancy, nickname = updatedName)

        val activeCount = seats.count { it.occupancy != SeatOccupancy.EMPTY }
        if (activeCount > selectedFormat.playerCount && selectedFormat == MatchFormat.ONE_VS_ONE) {
            selectedFormat = MatchFormat.FOUR_PLAYERS
        }
        if (selectedFormat == MatchFormat.ONE_VS_ONE) {
            syncColorsForTwoActiveSeats()
        }
    }

    fun selectColorForSeat(seatIdx: Int, newColor: PlayerColor) {
        val activeIndices = seats.indices.filter { seats[it].occupancy != SeatOccupancy.EMPTY }
        if (selectedFormat == MatchFormat.ONE_VS_ONE && activeIndices.size == 2 && seatIdx in activeIndices) {
            val otherIdx = activeIndices.first { it != seatIdx }
            seats[seatIdx] = seats[seatIdx].copy(color = newColor)
            seats[otherIdx] = seats[otherIdx].copy(color = newColor.opposite())
            return
        }

        val existingOwner = seats.indices.firstOrNull { it != seatIdx && seats[it].color == newColor }
        if (existingOwner != null) {
            val oldColor = seats[seatIdx].color
            seats[existingOwner] = seats[existingOwner].copy(color = oldColor)
        }
        seats[seatIdx] = seats[seatIdx].copy(color = newColor)
    }

    val targetPlayerCount = selectedFormat.playerCount
    val activeSeatsCount = seats.count { it.occupancy != SeatOccupancy.EMPTY }
    val autoFillBotsCount = (targetPlayerCount - activeSeatsCount).coerceAtLeast(0)
    val modeAccent = modeAccentColor(selectedMode, theme)
    val readableBlueOnSurface = theme.accessibleAccentOn(theme.blue, theme.surface)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .safeDrawingPadding()
            .testTag("game_setup_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CartoonIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        onClick = onBackClick,
                        modifier = Modifier.testTag("setup_back_button")
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.setup_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = theme.text,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    onClick = { showRulesSheet = true },
                    shape = RoundedCornerShape(999.dp),
                    color = theme.surface,
                    modifier = Modifier
                        .ludoShadow(theme.cardShadow, 999.dp)
                        .testTag("setup_mode_rules_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = modeIcon(selectedMode),
                            contentDescription = null,
                            tint = theme.accessibleAccentOn(modeAccent, theme.surface),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = selectedMode.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.surfaceText,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "How to play ${selectedMode.title}",
                            tint = theme.surfaceMutedText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (selectedMode == GameModeId.TIMED) {
                    CartoonCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                tint = readableBlueOnSurface,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Match Duration",
                                style = MaterialTheme.typography.titleLarge,
                                color = theme.surfaceText
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf(5, 10).forEach { mins ->
                                val chosen = timedMinutes == mins
                                Surface(
                                    onClick = { timedMinutes = mins },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("timed_duration_${mins}m"),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (chosen) theme.primaryActionBg else theme.surfaceVariant,
                                    border = BorderStroke(1.5.dp, if (chosen) theme.blue else theme.chipLine)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$mins Minutes",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (chosen) theme.onPrimaryAction else theme.surfaceText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Match Format Selector
                CartoonCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = null,
                            tint = readableBlueOnSurface,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Match Format",
                            style = MaterialTheme.typography.titleLarge,
                            color = theme.surfaceText
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MatchFormat.entries.forEach { format ->
                            val allowed = format.playerCount in selectedMode.minPlayers..selectedMode.maxPlayers
                            val selected = selectedFormat == format
                            Surface(
                                onClick = { if (allowed) onFormatSelected(format) },
                                enabled = allowed,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("format_${format.name.lowercase()}"),
                                shape = RoundedCornerShape(14.dp),
                                color = when {
                                    !allowed -> theme.surfaceVariant.copy(alpha = 0.45f)
                                    selected -> theme.primaryActionBg
                                    else -> theme.surfaceVariant
                                },
                                border = BorderStroke(
                                    1.5.dp,
                                    if (selected && allowed) theme.blue else theme.chipLine
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = format.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            !allowed -> theme.surfaceMutedText.copy(alpha = 0.5f)
                                            selected -> theme.onPrimaryAction
                                            else -> theme.surfaceText
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (selectedFormat == MatchFormat.TEAMS_2V2) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "2v2 Team: Team A (Red + Yellow) vs Team B (Green + Blue). Partners sit in opposite corners!",
                            style = MaterialTheme.typography.labelMedium,
                            color = readableBlueOnSurface
                        )
                    } else if (selectedMode == GameModeId.ELIMINATION) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Elimination requires 4 Players or 2v2 Team",
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.accessibleAccentOn(modeAccent, theme.surface)
                        )
                    }

                    if (autoFillBotsCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = theme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SmartToy,
                                    contentDescription = null,
                                    tint = theme.surfaceText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "$autoFillBotsCount empty seat${if (autoFillBotsCount > 1) "s" else ""} will auto-fill with Bots on start",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = theme.surfaceText
                                )
                            }
                        }
                    } else if (selectedFormat == MatchFormat.ONE_VS_ONE) {
                        val activeSeats = seats.filter { it.occupancy != SeatOccupancy.EMPTY }
                        if (activeSeats.size == 2) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Opposite corners: ${activeSeats[0].color.displayName} vs ${activeSeats[1].color.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = readableBlueOnSurface
                            )
                        }
                    }
                }

                // 4 Seats Configuration
                CartoonCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.setup_seats_label),
                        style = MaterialTheme.typography.titleLarge,
                        color = theme.surfaceText
                    )
                    Text(
                        text = stringResource(R.string.setup_seats_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.surfaceMutedText
                    )
                    Spacer(Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (idx in 0 until 4) {
                            val seat = seats[idx]
                            val palette = theme.playerPalette(seat.color)
                            val isEmpty = seat.occupancy == SeatOccupancy.EMPTY
                            val teamTag = if (selectedFormat.isTeamFormat) {
                                if (seat.color == PlayerColor.RED || seat.color == PlayerColor.YELLOW) {
                                    "Team A (Red & Yellow)"
                                } else {
                                    "Team B (Green & Blue)"
                                }
                            } else null

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isEmpty) theme.surfaceVariant else palette.light,
                                border = BorderStroke(
                                    1.5.dp,
                                    if (isEmpty) theme.chipLine else palette.primary.copy(alpha = 0.55f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (isEmpty) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(theme.timerTrack),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Person,
                                                        contentDescription = null,
                                                        tint = theme.surfaceMutedText,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            } else {
                                                val seatAvatar = PlayerAvatarCodec.resolve(
                                                    raw = seat.avatarId,
                                                    fallbackColor = seat.color,
                                                    isBot = seat.occupancy == SeatOccupancy.BOT,
                                                    seed = idx
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(CircleShape)
                                                        .clickable { editingAvatarSeatIndex = idx }
                                                        .testTag("seat_${idx}_avatar_button"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    PlayerAvatarBadge(
                                                        avatar = seatAvatar,
                                                        size = 44.dp,
                                                        contentDescription = stringResource(
                                                            R.string.avatar_customize_seat_subtitle,
                                                            seat.nickname.ifBlank { "Seat ${idx + 1}" }
                                                        ),
                                                        showEditBadge = true
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = "Seat ${idx + 1}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = if (isEmpty) theme.surfaceMutedText else theme.surfaceText
                                                )
                                                if (teamTag != null) {
                                                    Text(
                                                        text = teamTag,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = theme.accessibleAccentOn(palette.primary, palette.light),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            SeatOccupancy.entries.forEach { occ ->
                                                val chosen = seat.occupancy == occ
                                                Surface(
                                                    onClick = { setSeatOccupancy(idx, occ) },
                                                    modifier = Modifier
                                                        .height(36.dp)
                                                        .testTag("seat_${idx}_${occ.name.lowercase()}"),
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (chosen) theme.primaryActionBg else theme.surface,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (chosen) theme.blue else theme.chipLine
                                                    )
                                                ) {
                                                    Box(
                                                        modifier = Modifier.padding(horizontal = 10.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = occ.label,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = if (chosen) theme.onPrimaryAction else theme.surfaceText
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (!isEmpty) {
                                        Spacer(Modifier.height(10.dp))

                                        OutlinedTextField(
                                            value = seat.nickname,
                                            onValueChange = { input ->
                                                if (input.length <= 14) {
                                                    seats[idx] = seat.copy(nickname = input)
                                                }
                                            },
                                            singleLine = true,
                                            placeholder = {
                                                Text(
                                                    text = stringResource(R.string.setup_nickname_hint),
                                                    color = theme.surfaceMutedText
                                                )
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = theme.surface,
                                                unfocusedContainerColor = theme.surface,
                                                focusedBorderColor = palette.primary,
                                                unfocusedBorderColor = theme.chipLine,
                                                focusedTextColor = theme.surfaceText,
                                                unfocusedTextColor = theme.surfaceText
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("nickname_input_$idx")
                                        )

                                        if (seat.occupancy == SeatOccupancy.BOT) {
                                            Spacer(Modifier.height(10.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Bot Difficulty",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = theme.surfaceText
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    BotDifficulty.entries.forEach { diff ->
                                                        val chosen = seat.botDifficulty == diff
                                                        Surface(
                                                            onClick = {
                                                                seats[idx] = seat.copy(botDifficulty = diff)
                                                            },
                                                            modifier = Modifier
                                                                .height(36.dp)
                                                                .testTag("seat_${idx}_diff_${diff.name.lowercase()}"),
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = if (chosen) palette.primary else theme.surface,
                                                            border = BorderStroke(
                                                                1.dp,
                                                                if (chosen) palette.primary else theme.chipLine
                                                            )
                                                        ) {
                                                            Box(
                                                                modifier = Modifier.padding(horizontal = 10.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = diff.displayName,
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    color = if (chosen) palette.onPrimary else theme.surfaceText
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Color: ${seat.color.displayName}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = theme.surfaceText
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                PlayerColor.entries.forEach { candidate ->
                                                    val isChosen = candidate == seat.color
                                                    val cPalette = theme.playerPalette(candidate)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(CircleShape)
                                                            .clickable {
                                                                selectColorForSeat(idx, candidate)
                                                            }
                                                            .semantics {
                                                                contentDescription =
                                                                    "Select ${candidate.displayName} for Seat ${idx + 1}"
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(if (isChosen) 34.dp else 28.dp)
                                                                .clip(CircleShape)
                                                                .background(cPalette.primary)
                                                                .border(
                                                                    width = if (isChosen) 3.dp else 1.5.dp,
                                                                    color = if (isChosen) theme.surfaceText else theme.tokenEdge,
                                                                    shape = CircleShape
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isChosen) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Check,
                                                                    contentDescription = null,
                                                                    tint = cPalette.onPrimary,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Turn Timer Setting
                CartoonCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessAlarm,
                            contentDescription = null,
                            tint = readableBlueOnSurface,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Turn Timer",
                            style = MaterialTheme.typography.titleLarge,
                            color = theme.surfaceText
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Auto-rolls or plays the best move on timeout — you always keep control of your seat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.surfaceMutedText
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TurnTimerOption.entries.forEach { option ->
                            val chosen = selectedTimer == option
                            Surface(
                                onClick = { selectedTimer = option },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("turn_timer_${option.name.lowercase()}"),
                                shape = RoundedCornerShape(14.dp),
                                color = if (chosen) theme.primaryActionBg else theme.surfaceVariant,
                                border = BorderStroke(1.5.dp, if (chosen) theme.blue else theme.chipLine)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (chosen) theme.onPrimaryAction else theme.surfaceText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            CartoonButton(
                text = stringResource(R.string.setup_start_game),
                onClick = {
                    val resolvedSetups = MatchSeatPlanner.buildMatchSetups(
                        seats = seats.toList(),
                        matchFormat = selectedFormat,
                        mode = selectedMode
                    )
                    onStartMatch(
                        resolvedSetups,
                        selectedMode,
                        selectedFormat,
                        timedMinutes * 60,
                        selectedTimer.seconds
                    )
                },
                tag = "setup_start_match_button",
                icon = Icons.Filled.PlayArrow,
                minHeight = 56.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showPowerUpGuide || showRulesSheet) {
            ModeRulesDialog(
                mode = selectedMode,
                matchFormat = selectedFormat,
                onDismiss = {
                    showRulesSheet = false
                    if (showPowerUpGuide) onDismissPowerUpGuide()
                }
            )
        }

        editingAvatarSeatIndex?.let { seatIdx ->
            val targetSeat = seats[seatIdx]
            val initialConfig = PlayerAvatarCodec.resolve(
                raw = targetSeat.avatarId,
                fallbackColor = targetSeat.color,
                isBot = targetSeat.occupancy == SeatOccupancy.BOT,
                seed = seatIdx
            )
            AvatarCustomizerDialog(
                initialAvatar = initialConfig,
                title = stringResource(R.string.avatar_studio_title),
                subtitle = targetSeat.nickname.ifBlank { "Seat ${seatIdx + 1}" },
                onDismiss = { editingAvatarSeatIndex = null },
                onSave = { picked ->
                    seats[seatIdx] = targetSeat.copy(avatarId = picked.encode())
                    editingAvatarSeatIndex = null
                }
            )
        }
    }
}
