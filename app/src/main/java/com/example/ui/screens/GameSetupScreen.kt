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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.bot.MatchSeatPlanner
import com.example.bot.SeatConfig
import com.example.bot.SeatOccupancy
import com.example.engine.BotDifficulty
import com.example.engine.GameModeId
import com.example.engine.PlayerColor
import com.example.engine.PlayerSetup
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.game.PowerUpGuideDialog
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.CreamSurfaceVariant
import com.example.ui.theme.InkDark
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenDark
import com.example.ui.theme.OutlineSoft
import com.example.ui.theme.SkyTopTint
import com.example.ui.theme.SunnyGold
import com.example.ui.theme.palette

@Composable
fun GameSetupScreen(
    selectedMode: GameModeId,
    showPowerUpGuide: Boolean = false,
    onDismissPowerUpGuide: () -> Unit = {},
    onBackClick: () -> Unit,
    onStartMatch: (List<PlayerSetup>, GameModeId, Int) -> Unit
) {
    val defaultCount = when (selectedMode) {
        GameModeId.TEAMS_2V2 -> 4
        GameModeId.ELIMINATION -> 4
        else -> 2
    }
    var targetPlayerCount by remember(selectedMode) { mutableIntStateOf(defaultCount) }
    var timedMinutes by remember(selectedMode) { mutableIntStateOf(5) }

    val seats = remember(selectedMode) {
        mutableStateListOf(
            SeatConfig(0, SeatOccupancy.HUMAN, "Player 1", PlayerColor.RED, BotDifficulty.MEDIUM),
            SeatConfig(
                1,
                if (selectedMode == GameModeId.TEAMS_2V2) SeatOccupancy.HUMAN else SeatOccupancy.HUMAN,
                "Player 2",
                if (selectedMode == GameModeId.TEAMS_2V2) PlayerColor.GREEN else PlayerColor.YELLOW,
                BotDifficulty.MEDIUM
            ),
            SeatConfig(
                2,
                if (selectedMode.minPlayers >= 3) SeatOccupancy.BOT else SeatOccupancy.EMPTY,
                "Bot 3",
                if (selectedMode == GameModeId.TEAMS_2V2) PlayerColor.YELLOW else PlayerColor.GREEN,
                BotDifficulty.MEDIUM
            ),
            SeatConfig(
                3,
                if (selectedMode.minPlayers >= 4) SeatOccupancy.BOT else SeatOccupancy.EMPTY,
                "Bot 4",
                PlayerColor.BLUE,
                BotDifficulty.MEDIUM
            )
        )
    }

    fun syncColorsForTwoActiveSeats() {
        if (selectedMode == GameModeId.TEAMS_2V2) return
        val activeIndices = seats.indices.filter { seats[it].occupancy != SeatOccupancy.EMPTY }
        if (activeIndices.size == 2) {
            val i0 = activeIndices[0]
            val i1 = activeIndices[1]
            seats[i1] = seats[i1].copy(color = seats[i0].color.opposite())
        }
    }

    fun onPlayerCountSelected(count: Int) {
        if (count !in selectedMode.minPlayers..selectedMode.maxPlayers) return
        targetPlayerCount = count
        val activeIndices = seats.indices.filter { seats[it].occupancy != SeatOccupancy.EMPTY }
        if (activeIndices.size > count) {
            val toDisable = activeIndices
                .sortedBy { if (seats[it].occupancy == SeatOccupancy.HUMAN) 0 else 1 }
                .drop(count)
            for (idx in toDisable) {
                seats[idx] = seats[idx].copy(occupancy = SeatOccupancy.EMPTY)
            }
        }
        if (count == 2) {
            syncColorsForTwoActiveSeats()
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
        if (activeCount > targetPlayerCount) {
            targetPlayerCount = activeCount.coerceIn(selectedMode.minPlayers, selectedMode.maxPlayers)
        }
        if (targetPlayerCount == 2) {
            syncColorsForTwoActiveSeats()
        }
    }

    fun selectColorForSeat(seatIdx: Int, newColor: PlayerColor) {
        val activeIndices = seats.indices.filter { seats[it].occupancy != SeatOccupancy.EMPTY }
        if (targetPlayerCount == 2 && activeIndices.size == 2 && seatIdx in activeIndices) {
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

    val activeSeatsCount = seats.count { it.occupancy != SeatOccupancy.EMPTY }
    val autoFillBotsCount = (targetPlayerCount - activeSeatsCount).coerceAtLeast(0)
    val modeAccent = modeAccentColor(selectedMode)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(SkyTopTint, CreamBackground))
            )
            .safeDrawingPadding()
            .testTag("game_setup_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp)
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
                        onClick = onBackClick,
                        modifier = Modifier.testTag("setup_back_button")
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.setup_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = InkDark
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = modeAccent.copy(alpha = 0.14f),
                    border = BorderStroke(1.5.dp, modeAccent)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = modeIcon(selectedMode),
                            contentDescription = null,
                            tint = modeAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = selectedMode.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = InkDark
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
                // Timed Mode Duration Selector
                if (selectedMode == GameModeId.TIMED) {
                    CartoonCard(modifier = Modifier.fillMaxWidth(), borderColor = modeAccent.copy(alpha = 0.45f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                tint = modeAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Match Duration",
                                style = MaterialTheme.typography.titleLarge,
                                color = InkDark
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
                                        .height(50.dp)
                                        .testTag("timed_duration_${mins}m"),
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (chosen) modeAccent else CreamSurfaceVariant,
                                    border = BorderStroke(2.dp, if (chosen) modeAccent else OutlineSoft)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$mins Minutes",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (chosen) Color.White else InkDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. Number of Players
                CartoonCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.setup_players_label),
                        style = MaterialTheme.typography.titleLarge,
                        color = InkDark
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(2, 3, 4).forEach { count ->
                            val allowed = count in selectedMode.minPlayers..selectedMode.maxPlayers
                            val selected = targetPlayerCount == count
                            Surface(
                                onClick = { if (allowed) onPlayerCountSelected(count) },
                                enabled = allowed,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("player_count_$count"),
                                shape = RoundedCornerShape(16.dp),
                                color = when {
                                    !allowed -> CreamSurfaceVariant.copy(alpha = 0.45f)
                                    selected -> CobaltPrimary
                                    else -> CreamSurfaceVariant
                                },
                                border = BorderStroke(
                                    2.dp,
                                    if (selected && allowed) CobaltPrimary else OutlineSoft
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$count Players",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = when {
                                            !allowed -> InkMuted.copy(alpha = 0.5f)
                                            selected -> Color.White
                                            else -> InkDark
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (selectedMode == GameModeId.TEAMS_2V2) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Teams 2v2: Team Sun (Red + Yellow) vs Team Sky (Green + Blue)",
                            style = MaterialTheme.typography.labelMedium,
                            color = modeAccent
                        )
                    } else if (selectedMode == GameModeId.ELIMINATION) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Elimination requires 3 or 4 players",
                            style = MaterialTheme.typography.labelMedium,
                            color = modeAccent
                        )
                    }

                    if (autoFillBotsCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SunnyGold.copy(alpha = 0.22f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SmartToy,
                                    contentDescription = null,
                                    tint = InkDark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "$autoFillBotsCount empty seat${if (autoFillBotsCount > 1) "s" else ""} will auto-fill with Bots on start",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = InkDark
                                )
                            }
                        }
                    } else if (targetPlayerCount == 2) {
                        val activeSeats = seats.filter { it.occupancy != SeatOccupancy.EMPTY }
                        if (activeSeats.size == 2) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Opposite corners: ${activeSeats[0].color.displayName} vs ${activeSeats[1].color.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = CobaltPrimary
                            )
                        }
                    }
                }

                // 2. 4 Seats Configuration
                CartoonCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.setup_seats_label),
                        style = MaterialTheme.typography.titleLarge,
                        color = InkDark
                    )
                    Text(
                        text = stringResource(R.string.setup_seats_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (idx in 0 until 4) {
                            val seat = seats[idx]
                            val palette = seat.color.palette()
                            val isEmpty = seat.occupancy == SeatOccupancy.EMPTY
                            val teamTag = if (selectedMode == GameModeId.TEAMS_2V2) {
                                if (seat.color == PlayerColor.RED || seat.color == PlayerColor.YELLOW) {
                                    "Team Red & Yellow"
                                } else {
                                    "Team Green & Blue"
                                }
                            } else null

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (isEmpty) CreamSurfaceVariant.copy(alpha = 0.7f) else palette.light,
                                border = BorderStroke(
                                    2.dp,
                                    if (isEmpty) OutlineSoft else palette.primary.copy(alpha = 0.6f)
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
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isEmpty) InkMuted.copy(alpha = 0.25f) else palette.primary
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (seat.occupancy == SeatOccupancy.BOT) {
                                                        Icons.Filled.SmartToy
                                                    } else {
                                                        Icons.Filled.Person
                                                    },
                                                    contentDescription = null,
                                                    tint = if (isEmpty) InkMuted else palette.onPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "Seat ${idx + 1}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = if (isEmpty) InkMuted else InkDark
                                                )
                                                if (teamTag != null) {
                                                    Text(
                                                        text = teamTag,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = palette.dark
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
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (chosen) CobaltPrimary else Color.White,
                                                    border = BorderStroke(
                                                        1.5.dp,
                                                        if (chosen) CobaltPrimary else OutlineSoft
                                                    )
                                                ) {
                                                    Box(
                                                        modifier = Modifier.padding(horizontal = 10.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = occ.label,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = if (chosen) Color.White else InkDark
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
                                                Text(stringResource(R.string.setup_nickname_hint))
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = Color.White,
                                                unfocusedContainerColor = Color.White,
                                                focusedBorderColor = palette.primary,
                                                unfocusedBorderColor = OutlineSoft,
                                                focusedTextColor = InkDark,
                                                unfocusedTextColor = InkDark
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
                                                    color = InkDark
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
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = if (chosen) palette.primary else Color.White,
                                                            border = BorderStroke(
                                                                1.5.dp,
                                                                if (chosen) palette.dark else OutlineSoft
                                                            )
                                                        ) {
                                                            Box(
                                                                modifier = Modifier.padding(horizontal = 10.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = diff.displayName,
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    color = if (chosen) palette.onPrimary else InkDark
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
                                                color = InkDark
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                PlayerColor.entries.forEach { candidate ->
                                                    val isChosen = candidate == seat.color
                                                    val cPalette = candidate.palette()
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
                                                                    color = if (isChosen) InkDark else Color.White,
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
            }

            Spacer(Modifier.height(12.dp))

            CartoonButton(
                text = stringResource(R.string.setup_start_game),
                onClick = {
                    val resolvedSetups = MatchSeatPlanner.buildMatchSetups(
                        seats = seats.toList(),
                        targetPlayerCount = targetPlayerCount,
                        mode = selectedMode
                    )
                    onStartMatch(resolvedSetups, selectedMode, timedMinutes * 60)
                },
                tag = "setup_start_match_button",
                icon = Icons.Filled.PlayArrow,
                containerColor = MintGreen,
                lipColor = MintGreenDark,
                minHeight = 58.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showPowerUpGuide) {
            PowerUpGuideDialog(onDismiss = onDismissPowerUpGuide)
        }
    }
}
