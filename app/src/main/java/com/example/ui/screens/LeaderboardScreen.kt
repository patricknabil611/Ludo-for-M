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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PlayerAvatarCodec
import com.example.online.RankedLeaderboardRow
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.online.LeaderboardTab
import com.example.ui.online.LeaderboardUiState
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralPink
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.InkDark
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MintGreenDark
import com.example.ui.theme.OutlineSoft
import com.example.ui.theme.SkyTopTint
import com.example.ui.theme.SunnyGold
import com.example.ui.theme.SunnyGoldDark

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
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTopTint, CreamBackground)))
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
                        color = InkDark
                    )
                    Text(
                        text = stringResource(R.string.leaderboard_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted
                    )
                }
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = SunnyGoldDark,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // All-Time / This Week Tabs
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(2.dp, OutlineSoft)
            ) {
                TabRow(
                    selectedTabIndex = if (selectedTab == LeaderboardTab.ALL_TIME) 0 else 1,
                    containerColor = Color.Transparent,
                    contentColor = CobaltPrimary
                ) {
                    Tab(
                        selected = selectedTab == LeaderboardTab.ALL_TIME,
                        onClick = { onSelectTab(LeaderboardTab.ALL_TIME) },
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

            Spacer(Modifier.height(12.dp))

            // Body
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (uiState) {
                    LeaderboardUiState.Loading -> {
                        CircularProgressIndicator(
                            color = CobaltPrimary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    is LeaderboardUiState.Error -> {
                        CartoonCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center),
                            borderColor = CoralPink
                        ) {
                            Text(
                                text = uiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoralPink
                            )
                        }
                    }

                    is LeaderboardUiState.Success -> {
                        val entries = uiState.snapshot.topEntries
                        if (entries.isEmpty()) {
                            CartoonCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center),
                                borderColor = OutlineSoft
                            ) {
                                Text(
                                    text = "No ranked matches yet for this period. Play an online match to claim #1!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkMuted
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
                        .testTag("leaderboard_my_rank_footer"),
                    shape = RoundedCornerShape(20.dp),
                    color = CobaltPrimary,
                    border = BorderStroke(2.dp, Color.White),
                    shadowElevation = 6.dp
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
                                    color = SunnyGold
                                ) {
                                    Text(
                                        text = "#${myRow.rank}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF2C1E00),
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
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${myRow.wins}W • ${myRow.gamesPlayed}G • ${myRow.winRatePercent}% Win Rate",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.88f)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Your Rank: Unranked — Play an online match to get ranked!",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
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
    val badgeBg = when (row.rank) {
        1 -> SunnyGold
        2 -> Color(0xFFC5CFE0)
        3 -> Color(0xFFE09F67)
        else -> OutlineSoft
    }
    val badgeTextColor = if (row.rank <= 3) Color(0xFF2C1E00) else InkDark

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrentUser) Color(0xFFEBF3FF) else Color.White,
        border = BorderStroke(
            width = if (isCurrentUser) 2.dp else 1.5.dp,
            color = if (isCurrentUser) CobaltPrimary else OutlineSoft
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
                    color = InkDark,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${row.gamesPlayed} games played",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${row.wins} Wins",
                    style = MaterialTheme.typography.titleSmall,
                    color = CobaltPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${row.winRatePercent}% WR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MintGreenDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
