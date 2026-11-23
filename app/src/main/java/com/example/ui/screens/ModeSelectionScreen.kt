package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.GameModeId
import com.example.engine.MatchFormat
import com.example.engine.TurnTimerOption
import com.example.ui.components.CartoonIconButton
import com.example.ui.game.ModeRulesDialog
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.ludoShadow
import com.example.ui.theme.modeAccentColor

@Composable
fun ModeSelectionScreen(
    initialMode: GameModeId = GameModeId.CLASSIC,
    initialFormat: MatchFormat = MatchFormat.FOUR_PLAYERS,
    initialTimer: TurnTimerOption = TurnTimerOption.FIFTEEN,
    onBackClick: () -> Unit,
    onStartSelected: (GameModeId, MatchFormat, TurnTimerOption) -> Unit
) {
    val theme = LocalLudoTheme.current
    var selectedMode by remember { mutableStateOf(initialMode) }
    var selectedFormat by remember { mutableStateOf(initialFormat) }
    var selectedTimer by remember { mutableStateOf(initialTimer) }
    var rulesSheetMode by remember { mutableStateOf<GameModeId?>(null) }

    fun selectMode(mode: GameModeId) {
        selectedMode = mode
        if (selectedFormat.playerCount !in mode.minPlayers..mode.maxPlayers) {
            selectedFormat = MatchFormat.FOUR_PLAYERS
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .safeDrawingPadding()
            .testTag("mode_selection_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CartoonIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    onClick = onBackClick,
                    modifier = Modifier.testTag("mode_back_button")
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.mode_select_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.text,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. "Modes" 2-column grid
                Text(
                    text = "Modes",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.text,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                val mainRows = GameModeId.mainModes.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    mainRows.forEach { rowModes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowModes.forEach { mode ->
                                MainModeGridCard(
                                    mode = mode,
                                    selected = selectedMode == mode,
                                    onClick = { selectMode(mode) },
                                    onInfoClick = { rulesSheetMode = mode },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowModes.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                // 2. "Extra modes" 3-column grid
                Text(
                    text = "Extra modes",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.text,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GameModeId.extraModes.forEach { mode ->
                        ExtraModeCompactCard(
                            mode = mode,
                            selected = selectedMode == mode,
                            onClick = { selectMode(mode) },
                            onInfoClick = { rulesSheetMode = mode },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. "Players" segmented control (1v1 / 4 Players / 2v2 Team)
                Text(
                    text = "Players",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.text,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .ludoShadow(theme.cardShadow, 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = theme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MatchFormat.entries.forEach { format ->
                            val allowed = format.playerCount in selectedMode.minPlayers..selectedMode.maxPlayers
                            val chosen = selectedFormat == format
                            Surface(
                                onClick = { if (allowed) selectedFormat = format },
                                enabled = allowed,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("mode_format_${format.name.lowercase()}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (chosen) theme.primaryActionBg else Color.Transparent
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = format.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            !allowed -> theme.surfaceMutedText.copy(alpha = 0.45f)
                                            chosen -> theme.onPrimaryAction
                                            else -> theme.surfaceText
                                        },
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. "Turn timer" pills (Off / 10s / 15s / 20s)
                Text(
                    text = "Turn timer",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.text,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

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
                                .height(44.dp)
                                .ludoShadow(theme.cardShadow, 999.dp)
                                .testTag("mode_timer_${option.name.lowercase()}"),
                            shape = RoundedCornerShape(999.dp),
                            color = if (chosen) theme.primaryActionBg else theme.surface,
                            border = if (chosen) null else BorderStroke(1.dp, theme.chipLine)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (chosen) theme.onPrimaryAction else theme.surfaceText
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(12.dp))

            // 5. Full-width 56dp "Start game" button in blue
            Surface(
                onClick = { onStartSelected(selectedMode, selectedFormat, selectedTimer) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .ludoShadow(theme.cardShadow, 16.dp)
                    .testTag("mode_start_game_button"),
                shape = RoundedCornerShape(16.dp),
                color = theme.primaryActionBg
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = theme.onPrimaryAction,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Start game",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        color = theme.onPrimaryAction,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        rulesSheetMode?.let { mode ->
            ModeRulesDialog(
                mode = mode,
                matchFormat = selectedFormat,
                onDismiss = { rulesSheetMode = null }
            )
        }
    }
}

@Composable
private fun MainModeGridCard(
    mode: GameModeId,
    selected: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current
    val accent = modeAccentColor(mode, theme)
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier
            .ludoShadow(theme.cardShadow, 18.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.border(2.5.dp, theme.blue, shape) else Modifier
            )
            .clickable(onClick = onClick)
            .testTag("mode_card_${mode.name.lowercase()}"),
        shape = shape,
        color = theme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 32dp colored icon tile
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = modeIcon(mode),
                        contentDescription = null,
                        tint = theme.contrastingTextOn(accent),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Info button at top-right
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(theme.surfaceVariant)
                        .clickable(onClick = onInfoClick)
                        .semantics { contentDescription = "How to play ${mode.title}" }
                        .testTag("mode_info_${mode.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = theme.surfaceMutedText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    color = theme.surfaceText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = mode.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
                    color = theme.surfaceMutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ExtraModeCompactCard(
    mode: GameModeId,
    selected: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current
    val accent = modeAccentColor(mode, theme)
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier
            .ludoShadow(theme.cardShadow, 18.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.border(2.5.dp, theme.blue, shape) else Modifier
            )
            .clickable(onClick = onClick)
            .testTag("mode_card_${mode.name.lowercase()}"),
        shape = shape,
        color = theme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Top-right info button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(theme.surfaceVariant)
                    .clickable(onClick = onInfoClick)
                    .semantics { contentDescription = "How to play ${mode.title}" }
                    .testTag("mode_info_${mode.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = theme.surfaceMutedText,
                    modifier = Modifier.size(14.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = modeIcon(mode),
                        contentDescription = null,
                        tint = theme.contrastingTextOn(accent),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    color = theme.surfaceText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

fun modeIcon(mode: GameModeId): ImageVector = when (mode) {
    GameModeId.CLASSIC -> Icons.Filled.Casino
    GameModeId.BLOCKADES -> Icons.Filled.Shield
    GameModeId.MASTER -> Icons.Filled.WorkspacePremium
    GameModeId.QUICK -> Icons.Filled.Bolt
    GameModeId.ARROW -> Icons.Filled.FastForward
    GameModeId.MAGIC -> Icons.Filled.AutoAwesome
    GameModeId.TIMED -> Icons.Filled.Timer
    GameModeId.CHAOS -> Icons.Filled.Whatshot
    GameModeId.ELIMINATION -> Icons.Filled.Gavel
}

@Composable
fun modeAccentColor(mode: GameModeId): Color = modeAccentColor(mode, LocalLudoTheme.current)
