package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.MatchHistoryEntry
import com.example.data.PlayerAvatarConfig
import com.example.engine.GameModeId
import com.example.ui.components.AvatarCustomizerDialog
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.online.ModeAggregateStats
import com.example.ui.online.OnlineAuthUiState
import com.example.ui.online.StatsHistoryUiState
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.ludoShadow
import com.example.ui.theme.modeAccentColor
import com.example.ui.theme.palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Player Stats & Match History screen:
 * - Overall and per-mode filter chips for Games Played, Wins, Win Rate, Captures Made,
 *   Tokens Sent Home, Favorite Mode, and Best Mode by Win Rate
 * - Editable nickname for Google & Guest accounts + zero-loss Guest-to-Google upgrade
 * - Match History list with date, mode, players, placement, captures/power-ups summary,
 *   and tap-to-open Match Details dialog
 */
@Composable
fun StatsHistoryScreen(
    authState: OnlineAuthUiState,
    historyState: StatsHistoryUiState,
    onEditNickname: (String) -> Unit,
    onEditAvatar: (PlayerAvatarConfig) -> Unit = {},
    onUpgradeOrSignInClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val theme = LocalLudoTheme.current
    val magicAccent = modeAccentColor(GameModeId.MAGIC, theme)

    var showEditNicknameDialog by remember { mutableStateOf(false) }
    var showAvatarStudioDialog by remember { mutableStateOf(false) }
    var nicknameDraft by remember(authState.effectiveNickname) {
        mutableStateOf(authState.effectiveNickname)
    }
    var selectedModeFilter by remember { mutableStateOf<GameModeId?>(null) }
    var detailMatch by remember { mutableStateOf<MatchHistoryEntry?>(null) }

    val overview = historyState.overview
    val activeStats: ModeAggregateStats = remember(selectedModeFilter, overview, authState) {
        if (selectedModeFilter == null) {
            val profileGames = if (authState.isSignedInWithGoogle) {
                (authState.userProfile?.gamesPlayed ?: 0L).toInt()
            } else {
                authState.guestProfile.gamesPlayed
            }
            val profileWins = if (authState.isSignedInWithGoogle) {
                (authState.userProfile?.wins ?: 0L).toInt()
            } else {
                authState.guestProfile.wins
            }
            val games = maxOf(overview.overall.gamesPlayed, profileGames)
            val wins = maxOf(overview.overall.wins, profileWins).coerceAtMost(games)
            overview.overall.copy(gamesPlayed = games, wins = wins)
        } else {
            overview.byMode[selectedModeFilter] ?: ModeAggregateStats()
        }
    }

    val filteredHistory = remember(historyState.entries, selectedModeFilter) {
        if (selectedModeFilter == null) historyState.entries
        else historyState.entries.filter { it.mode == selectedModeFilter }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .safeDrawingPadding()
            .testTag("stats_history_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CartoonIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        onClick = onBackClick,
                        modifier = Modifier.testTag("stats_back_button")
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.stats_screen_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = theme.text
                    )
                }
            }

            // Friendly sync warning if offline
            if (!historyState.errorMessage.isNullOrBlank()) {
                item(key = "sync_warning") {
                    val warnBg = lerp(theme.surface, theme.yellow, if (theme.isDarkSurface) 0.22f else 0.16f)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = warnBg,
                        border = BorderStroke(1.5.dp, theme.yellow)
                    ) {
                        Text(
                            text = historyState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.surfaceText,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Player Profile Header Card
            item(key = "profile_header") {
                CartoonCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = theme.blue.copy(alpha = 0.55f),
                    cornerRadius = 24.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .clickable { showAvatarStudioDialog = true }
                                    .testTag("profile_avatar_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                PlayerAvatarBadge(
                                    avatar = authState.effectiveAvatar,
                                    size = 62.dp,
                                    contentDescription = stringResource(R.string.cd_customize_avatar),
                                    showEditBadge = true
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = authState.effectiveNickname,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = theme.surfaceText,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (authState.isSignedInWithGoogle) {
                                        "Google Account • Synced to Leaderboard"
                                    } else {
                                        "Guest Profile • Local Stats"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = theme.surfaceMutedText
                                )
                            }
                        }

                        CartoonIconButton(
                            icon = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.cd_edit_nickname),
                            onClick = {
                                nicknameDraft = authState.effectiveNickname
                                showEditNicknameDialog = true
                            },
                            modifier = Modifier.testTag("edit_nickname_button")
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    CartoonButton(
                        text = stringResource(R.string.avatar_customize_cta),
                        onClick = { showAvatarStudioDialog = true },
                        tag = "customize_avatar_button",
                        icon = Icons.Filled.AutoAwesome,
                        containerColor = theme.yellow,
                        minHeight = 48.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!authState.isSignedInWithGoogle) {
                        Spacer(Modifier.height(10.dp))
                        CartoonButton(
                            text = stringResource(R.string.online_upgrade_google_cta),
                            onClick = onUpgradeOrSignInClick,
                            tag = "stats_upgrade_google_button",
                            icon = Icons.Filled.Upgrade,
                            containerColor = theme.primaryActionBg,
                            contentColor = theme.onPrimaryAction,
                            minHeight = 48.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Overall vs Per-Mode Filter Chips
            item(key = "mode_filter_chips") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("stats_mode_filter_row")
                ) {
                    item(key = "filter_overall") {
                        ModeFilterChip(
                            label = "Overall",
                            selected = selectedModeFilter == null,
                            onClick = { selectedModeFilter = null }
                        )
                    }
                    items(GameModeId.entries, key = { it.name }) { mode ->
                        val count = overview.byMode[mode]?.gamesPlayed ?: 0
                        ModeFilterChip(
                            label = if (count > 0) "${mode.title} ($count)" else mode.title,
                            selected = selectedModeFilter == mode,
                            onClick = { selectedModeFilter = mode }
                        )
                    }
                }
            }

            // Primary Stats Grid (Games Played, Wins, Win Rate, Captures Made, Tokens Home)
            item(key = "stats_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatTile(
                            label = "Games Played",
                            value = activeStats.gamesPlayed.toString(),
                            accent = theme.blue,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "Wins",
                            value = activeStats.wins.toString(),
                            accent = theme.yellow,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "Win Rate",
                            value = "${activeStats.winRatePercent}%",
                            accent = theme.green,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatTile(
                            label = "Captures Made",
                            value = activeStats.capturesMade.toString(),
                            accent = theme.red,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "Tokens Home",
                            value = activeStats.tokensHome.toString(),
                            accent = magicAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (selectedModeFilter == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HighlightModeTile(
                                icon = Icons.Filled.Favorite,
                                label = "Favorite Mode",
                                value = overview.favoriteMode?.title ?: "—",
                                sublabel = overview.favoriteMode?.let { m ->
                                    "${overview.byMode[m]?.gamesPlayed ?: 0} games"
                                } ?: "Play to unlock",
                                accent = theme.red,
                                modifier = Modifier.weight(1f)
                            )
                            HighlightModeTile(
                                icon = Icons.Filled.Star,
                                label = "Best Win Rate",
                                value = overview.bestWinRateMode?.first?.title ?: "—",
                                sublabel = overview.bestWinRateMode?.let { (_, pct) ->
                                    "$pct% wins"
                                } ?: "Play to unlock",
                                accent = theme.yellow,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else if (selectedModeFilter == GameModeId.MAGIC || activeStats.powerUpsUsed > 0) {
                        StatTile(
                            label = "Magic Items Used",
                            value = activeStats.powerUpsUsed.toString(),
                            accent = magicAccent,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Match History Section Header
            item(key = "history_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = theme.accessibleAccentOn(theme.blue, theme.bg),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (selectedModeFilter == null) {
                                stringResource(R.string.stats_match_history_title)
                            } else {
                                "${selectedModeFilter!!.title} History"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = theme.text
                        )
                    }
                    if (filteredHistory.isNotEmpty()) {
                        Text(
                            text = "Tap for details",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.mutedText
                        )
                    }
                }
            }

            if (historyState.isLoading && filteredHistory.isEmpty()) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = theme.blue)
                    }
                }
            } else if (filteredHistory.isEmpty()) {
                item(key = "empty") {
                    CartoonCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (selectedModeFilter == null) {
                                stringResource(R.string.stats_history_empty)
                            } else {
                                "No completed ${selectedModeFilter!!.title} matches yet. Play one to track stats here!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.surfaceMutedText
                        )
                    }
                }
            } else {
                items(filteredHistory, key = { it.id }) { entry ->
                    MatchHistoryRowCard(
                        entry = entry,
                        onClick = { detailMatch = entry }
                    )
                }
            }

            item(key = "bottom_spacer") { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showEditNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNicknameDialog = false },
            containerColor = theme.surface,
            titleContentColor = theme.surfaceText,
            textContentColor = theme.surfaceMutedText,
            title = { Text(stringResource(R.string.stats_edit_nickname_title)) },
            text = {
                OutlinedTextField(
                    value = nicknameDraft,
                    onValueChange = { if (it.length <= 24) nicknameDraft = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.setup_nickname_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = nicknameDraft.trim()
                        if (trimmed.isNotEmpty()) {
                            onEditNickname(trimmed)
                        }
                        showEditNicknameDialog = false
                    }
                ) {
                    Text(
                        text = "Save",
                        color = theme.accessibleAccentOn(theme.blue, theme.surface),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNicknameDialog = false }) {
                    Text(
                        text = "Cancel",
                        color = theme.surfaceMutedText
                    )
                }
            }
        )
    }

    detailMatch?.let { entry ->
        MatchDetailDialog(
            entry = entry,
            onDismiss = { detailMatch = null }
        )
    }

    if (showAvatarStudioDialog) {
        AvatarCustomizerDialog(
            initialAvatar = authState.effectiveAvatar,
            title = stringResource(R.string.avatar_studio_title),
            subtitle = authState.effectiveNickname,
            onDismiss = { showAvatarStudioDialog = false },
            onSave = { updated ->
                onEditAvatar(updated)
                showAvatarStudioDialog = false
            }
        )
    }
}

@Composable
private fun ModeFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalLudoTheme.current
    Surface(
        onClick = onClick,
        modifier = Modifier.ludoShadow(theme.cardShadow, 999.dp),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) theme.primaryActionBg else theme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) theme.primaryActionBg else theme.chipLine
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) theme.onPrimaryAction else theme.surfaceText,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current
    val accessibleAccent = theme.accessibleAccentOn(accent, theme.surface)

    CartoonCard(
        modifier = modifier,
        borderColor = accent.copy(alpha = 0.40f),
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = accessibleAccent,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = theme.surfaceMutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HighlightModeTile(
    icon: ImageVector,
    label: String,
    value: String,
    sublabel: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current
    val accessibleAccent = theme.accessibleAccentOn(accent, theme.surface)

    CartoonCard(
        modifier = modifier,
        borderColor = accent.copy(alpha = 0.40f),
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accessibleAccent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = theme.surfaceMutedText,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = theme.surfaceText,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = sublabel,
            style = MaterialTheme.typography.labelSmall,
            color = accessibleAccent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MatchHistoryRowCard(
    entry: MatchHistoryEntry,
    onClick: () -> Unit
) {
    val theme = LocalLudoTheme.current
    val winnerPalette = entry.winnerColor.palette(theme)
    val magicAccent = modeAccentColor(GameModeId.MAGIC, theme)
    val dateLabel = remember(entry.playedAtMs) {
        if (entry.playedAtMs <= 0L) ""
        else SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(entry.playedAtMs))
    }

    val typeTint = if (entry.onlineMatch) theme.blue else theme.yellow
    val typeBg = lerp(theme.surface, typeTint, if (theme.isDarkSurface) 0.22f else 0.14f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .ludoShadow(theme.cardShadow, 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("match_history_row_${entry.id}"),
        shape = RoundedCornerShape(20.dp),
        color = theme.surface,
        border = BorderStroke(
            width = if (entry.won) 1.5.dp else 1.dp,
            color = if (entry.won) theme.green.copy(alpha = 0.60f) else theme.chipLine
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = typeBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (entry.onlineMatch) Icons.Filled.Public else Icons.Filled.Person,
                                contentDescription = null,
                                tint = theme.accessibleAccentOn(typeTint, typeBg),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (entry.onlineMatch) "Online" else "Local",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.surfaceText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = entry.mode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = theme.surfaceText,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (entry.rank > 0) {
                        val badgeBg = if (entry.won) theme.green else theme.surfaceVariant
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = badgeBg
                        ) {
                            Text(
                                text = if (entry.won) "1st • Win" else "#${entry.rank} Place",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.won) theme.contrastingTextOn(theme.green) else theme.surfaceText,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "View match details",
                        tint = theme.surfaceMutedText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = theme.accessibleAccentOn(winnerPalette.primary, theme.surface),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Winner: ${entry.winnerName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.surfaceText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (entry.playerSummary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.playerSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.surfaceMutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))

            // Compact match summary chips (Captures, Tokens Home, Power-Ups Used)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniStatPill(
                    icon = Icons.Filled.Whatshot,
                    text = "${entry.capturesMade} captures",
                    tint = theme.red
                )
                MiniStatPill(
                    icon = Icons.Filled.Home,
                    text = "${entry.tokensHome}/4 home",
                    tint = theme.green
                )
                if (entry.mode == GameModeId.MAGIC || entry.powerUpsUsed > 0) {
                    MiniStatPill(
                        icon = Icons.Filled.AutoAwesome,
                        text = "${entry.powerUpsUsed} magic",
                        tint = magicAccent
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${entry.turnsPlayed} turns" +
                        if (entry.roomCode.isNotBlank()) " • Room ${entry.roomCode}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.surfaceMutedText
                )
                if (dateLabel.isNotEmpty()) {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.surfaceMutedText
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStatPill(
    icon: ImageVector,
    text: String,
    tint: Color
) {
    val theme = LocalLudoTheme.current
    val pillBg = lerp(theme.surface, tint, if (theme.isDarkSurface) 0.20f else 0.10f)
    val iconColor = theme.accessibleAccentOn(tint, pillBg)

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = pillBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = theme.surfaceText,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MatchDetailDialog(
    entry: MatchHistoryEntry,
    onDismiss: () -> Unit
) {
    val theme = LocalLudoTheme.current
    val fullDate = remember(entry.playedAtMs) {
        if (entry.playedAtMs <= 0L) "Unknown date"
        else SimpleDateFormat("EEE, MMM d yyyy • h:mm a", Locale.getDefault()).format(Date(entry.playedAtMs))
    }

    Dialog(onDismissRequest = onDismiss) {
        CartoonCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("match_detail_dialog"),
            borderColor = theme.blue.copy(alpha = 0.55f),
            cornerRadius = 26.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${entry.mode.title} Match",
                            style = MaterialTheme.typography.headlineSmall,
                            color = theme.surfaceText,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = fullDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.surfaceMutedText
                        )
                    }
                    val badgeBg = if (entry.onlineMatch) theme.primaryActionBg else theme.yellow
                    val badgeFg = if (entry.onlineMatch) theme.onPrimaryAction else theme.contrastingTextOn(theme.yellow)
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = if (entry.onlineMatch) "Online" else "Local",
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeFg,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Quick Match Overview Strip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = theme.surfaceVariant,
                    border = BorderStroke(1.dp, theme.chipLine)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        DetailMetric("Winner", entry.winnerName)
                        DetailMetric("Turns", entry.turnsPlayed.toString())
                        if (entry.roomCode.isNotBlank()) {
                            DetailMetric("Room", entry.roomCode)
                        } else if (entry.rank > 0) {
                            DetailMetric("Your Place", "#${entry.rank}")
                        }
                    }
                }

                Text(
                    text = "Player Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.surfaceText,
                    fontWeight = FontWeight.ExtraBold
                )

                if (entry.playerDetails.isEmpty()) {
                    Text(
                        text = entry.playerSummary.ifBlank { "Winner: ${entry.winnerName}" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.surfaceMutedText
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entry.playerDetails.forEach { p ->
                            val pal = p.color.palette(theme)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = pal.light,
                                border = BorderStroke(1.5.dp, pal.primary.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(pal.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "#${p.rank}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = pal.onPrimary,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = p.nickname,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = theme.surfaceText,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (p.isBot) {
                                                    Icon(
                                                        imageVector = Icons.Filled.SmartToy,
                                                        contentDescription = "Bot",
                                                        tint = theme.surfaceMutedText,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            val statsLine = buildString {
                                                append("${p.capturesMade} captures • ${p.tokensHome}/4 home")
                                                if (entry.mode == GameModeId.MAGIC || p.powerUpsUsed > 0) {
                                                    append(" • ${p.powerUpsUsed} magic")
                                                }
                                                if (entry.mode == GameModeId.TIMED) {
                                                    append(" • ${p.score} pts")
                                                }
                                            }
                                            Text(
                                                text = statsLine,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = theme.surfaceMutedText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                CartoonButton(
                    text = "Close",
                    onClick = onDismiss,
                    tag = "match_detail_close_button",
                    icon = Icons.Filled.Check,
                    containerColor = theme.green,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String) {
    val theme = LocalLudoTheme.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = theme.surfaceMutedText
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = theme.surfaceText,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
