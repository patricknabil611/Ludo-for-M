package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PlayerAvatarCodec
import com.example.engine.GameModeId
import com.example.online.RankedLeaderboardRow
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.online.LeaderboardTab
import com.example.ui.online.LeaderboardUiState
import com.example.ui.theme.AvatarArtPalette
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.ludoShadow

/**
 * Global Leaderboard (Google accounts only):
 * - All-Time and This Week tabs
 * - Wins, Games Played, and Win Rate %
 * - Always displays the current player's own rank card pinned at the bottom even if outside Top 100
 */
@Composable
fun LeaderboardScreen(
    selectedTab: LeaderboardTab,
    uiState: LeaderboardUiState,
    myUid: String,
    onSelectTab: (LeaderboardTab) -> Unit,
    onBackClick: () -> Unit,
    selectedMode: GameModeId? = null,
    onSelectModeFilter: (GameModeId?) -> Unit = {}
) {
    val theme = LocalLudoTheme.current
    val selectedTabIndex = if (selectedTab == LeaderboardTab.ALL_TIME) 0 else 1
    val tabAccent = theme.accessibleAccentOn(theme.blue, theme.surface)
    val errorTextColor = theme.accessibleAccentOn(theme.red, theme.surface)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .safeDrawingPadding()
            .testTag("leaderboard_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp)
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
                    modifier = Modifier.testTag("leaderboard_back_button")
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.leaderboard_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = theme.text
                    )
                    Text(
                        text = stringResource(R.string.leaderboard_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.mutedText
                    )
                }
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = theme.crownIcon,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // All-Time / This Week Tabs
            Surface(
                modifier = Modifier.ludoShadow(theme.cardShadow, 18.dp),
                shape = RoundedCornerShape(18.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.chipLine)
            ) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = tabAccent,
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = tabAccent
                            )
                        }
                    },
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == LeaderboardTab.ALL_TIME,
                        onClick = { onSelectTab(LeaderboardTab.ALL_TIME) },
                        selectedContentColor = tabAccent,
                        unselectedContentColor = theme.surfaceMutedText,
                        modifier = Modifier.testTag("leaderboard_tab_all_time"),
                        text = {
                            Text(
                                text = stringResource(R.string.leaderboard_tab_all_time),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == LeaderboardTab.THIS_WEEK,
                        onClick = { onSelectTab(LeaderboardTab.THIS_WEEK) },
                        selectedContentColor = tabAccent,
                        unselectedContentColor = theme.surfaceMutedText,
                        modifier = Modifier.testTag("leaderboard_tab_this_week"),
                        text = {
                            Text(
                                text = stringResource(R.string.leaderboard_tab_this_week),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Per-mode wins filter chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedMode == null,
                        onClick = { onSelectModeFilter(null) },
                        label = {
                            Text(
                                text = "All Modes",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = theme.surface,
                            labelColor = theme.surfaceText,
                            selectedContainerColor = theme.primaryActionBg,
                            selectedLabelColor = theme.onPrimaryAction
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedMode == null,
                            borderColor = theme.chipLine,
                            selectedBorderColor = theme.primaryActionBg
                        ),
                        modifier = Modifier.testTag("leaderboard_mode_all")
                    )
                }
                items(GameModeId.entries, key = { it.name }) { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onSelectModeFilter(mode) },
                        label = {
                            Text(
                                text = mode.title,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = theme.surface,
                            labelColor = theme.surfaceText,
                            selectedContainerColor = theme.primaryActionBg,
                            selectedLabelColor = theme.onPrimaryAction
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedMode == mode,
                            borderColor = theme.chipLine,
                            selectedBorderColor = theme.primaryActionBg
                        ),
                        modifier = Modifier.testTag("leaderboard_mode_${mode.name.lowercase()}")
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Body
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (uiState) {
                    LeaderboardUiState.Loading -> {
                        CircularProgressIndicator(
                            color = theme.blue,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    is LeaderboardUiState.Error -> {
                        CartoonCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center),
                            borderColor = theme.red
                        ) {
                            Text(
                                text = uiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = errorTextColor
                            )
                        }
                    }

                    is LeaderboardUiState.Success -> {
                        val entries = uiState.snapshot.topEntries
                        if (entries.isEmpty()) {
                            CartoonCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center)
                            ) {
                                Text(
                                    text = "No ranked matches yet for this period. Play an online match to claim #1!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = theme.surfaceMutedText
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(entries, key = { it.entry.userId }) { row ->
                                    LeaderboardRowCard(
                                        row = row,
                                        isCurrentUser = row.entry.userId == myUid
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Pinned current user's rank footer (always visible even if rank > 100)
            if (uiState is LeaderboardUiState.Success) {
                Spacer(Modifier.height(8.dp))
                val myRow = uiState.snapshot.myRow
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .ludoShadow(theme.cardShadow, 20.dp)
                        .testTag("leaderboard_my_rank_footer"),
                    shape = RoundedCornerShape(20.dp),
                    color = theme.primaryActionBg,
                    border = BorderStroke(1.5.dp, theme.surface.copy(alpha = 0.45f))
                ) {
                    if (myRow != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = theme.yellow
                                ) {
                                    Text(
                                        text = "#${myRow.rank}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = theme.contrastingTextOn(theme.yellow),
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                PlayerAvatarBadge(
                                    avatar = PlayerAvatarCodec.resolve(
                                        raw = myRow.entry.avatarId,
                                        seed = myRow.entry.userId.hashCode()
                                    ),
                                    size = 38.dp,
                                    contentDescription = myRow.entry.nickname
                                )
                                Column {
                                    Text(
                                        text = "Your Rank • ${myRow.entry.nickname}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = theme.onPrimaryAction,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${myRow.wins}W • ${myRow.gamesPlayed}G • ${myRow.winRatePercent}% Win Rate",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = theme.onPrimaryAction.copy(alpha = 0.88f)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Your Rank: Unranked — Play an online match to get ranked!",
                            style = MaterialTheme.typography.labelLarge,
                            color = theme.onPrimaryAction,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRowCard(
    row: RankedLeaderboardRow,
    isCurrentUser: Boolean
) {
    val theme = LocalLudoTheme.current
    val badgeBg = when (row.rank) {
        1 -> AvatarArtPalette.medalGold
        2 -> AvatarArtPalette.medalSilver
        3 -> AvatarArtPalette.medalBronze
        else -> theme.surfaceVariant
    }
    val badgeTextColor = theme.contrastingTextOn(badgeBg)
    val rowBg = if (isCurrentUser) {
        lerp(theme.surface, theme.blue, if (theme.isDarkSurface) 0.20f else 0.10f)
    } else {
        theme.surface
    }
    val winsColor = theme.accessibleAccentOn(theme.blue, rowBg)
    val wrColor = theme.accessibleAccentOn(theme.green, rowBg)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .ludoShadow(theme.cardShadow, 18.dp),
        shape = RoundedCornerShape(18.dp),
        color = rowBg,
        border = BorderStroke(
            width = if (isCurrentUser) 2.dp else 1.dp,
            color = if (isCurrentUser) theme.blue else theme.chipLine
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#${row.rank}",
                    style = MaterialTheme.typography.labelLarge,
                    color = badgeTextColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            PlayerAvatarBadge(
                avatar = PlayerAvatarCodec.resolve(
                    raw = row.entry.avatarId,
                    seed = row.entry.userId.hashCode()
                ),
                size = 40.dp,
                contentDescription = row.entry.nickname
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCurrentUser) "${row.entry.nickname} (You)" else row.entry.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.surfaceText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${row.gamesPlayed} games played",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.surfaceMutedText
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${row.wins} Wins",
                    style = MaterialTheme.typography.titleSmall,
                    color = winsColor,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${row.winRatePercent}% WR",
                    style = MaterialTheme.typography.labelMedium,
                    color = wrColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
