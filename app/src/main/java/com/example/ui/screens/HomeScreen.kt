package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.SavedGameSummary
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.online.OnlineAuthUiState
import com.example.ui.theme.CobaltDark
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralPink
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.InkDark
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenDark
import com.example.ui.theme.OutlineSoft
import com.example.ui.theme.SkyTopTint
import com.example.ui.theme.SunnyGold
import com.example.ui.theme.SunnyGoldDark

@Composable
fun HomeScreen(
    authState: OnlineAuthUiState = OnlineAuthUiState(),
    savedGameSummary: SavedGameSummary? = null,
    onResumeSavedGame: () -> Unit = {},
    onDiscardSavedGame: () -> Unit = {},
    onPlayClick: () -> Unit,
    onPlayOnlineClick: () -> Unit = {},
    onLeaderboardClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    onTutorialClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SkyTopTint, CreamBackground)
                )
            )
            .safeDrawingPadding()
            .testTag("home_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header Row: Account/Guest status pill + Tutorial + Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onAccountClick,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("home_account_pill"),
                    shape = RoundedCornerShape(999.dp),
                    color = if (authState.isSignedInWithGoogle) Color(0xFFEBF3FF) else Color.White,
                    border = BorderStroke(
                        2.dp,
                        if (authState.isSignedInWithGoogle) CobaltPrimary else OutlineSoft
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (authState.isSignedInWithGoogle) Icons.Filled.AccountCircle else Icons.Filled.Groups,
                            contentDescription = null,
                            tint = CobaltPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when {
                                authState.isSignedInWithGoogle -> authState.effectiveNickname
                                authState.guestProfile.guestModeActive -> "Guest: ${authState.guestProfile.nickname}"
                                else -> stringResource(R.string.home_sign_in_badge)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = InkDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CartoonIconButton(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = stringResource(R.string.cd_tutorial),
                        onClick = onTutorialClick,
                        modifier = Modifier.testTag("home_tutorial_icon_button")
                    )
                    CartoonIconButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.cd_settings),
                        onClick = onSettingsClick,
                        modifier = Modifier.testTag("home_settings_icon_button")
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Resume Local Game Banner (appears automatically if an unfinished local match exists)
            if (savedGameSummary != null) {
                CartoonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_resume_card"),
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
                                text = stringResource(R.string.home_resume_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = InkDark,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${savedGameSummary.mode.title} • ${savedGameSummary.activePlayerName}'s turn (Turn ${savedGameSummary.turnNumber})",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        CartoonIconButton(
                            icon = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_discard_saved_game),
                            onClick = onDiscardSavedGame,
                            iconTint = CoralPink,
                            borderColor = CoralPink.copy(alpha = 0.45f),
                            modifier = Modifier.testTag("home_discard_saved_button")
                        )
                        Spacer(Modifier.width(6.dp))
                        CartoonButton(
                            text = stringResource(R.string.home_resume_cta),
                            onClick = onResumeSavedGame,
                            tag = "home_resume_button",
                            icon = Icons.Filled.PlayArrow,
                            containerColor = MintGreen,
                            lipColor = MintGreenDark,
                            minHeight = 48.dp,
                            modifier = Modifier.width(128.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // Hero Card
            CartoonCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = CobaltPrimary.copy(alpha = 0.25f),
                cornerRadius = 32.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_ludo_hero),
                        contentDescription = stringResource(R.string.cd_hero_art),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(195.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(2.dp, OutlineSoft, RoundedCornerShape(22.dp))
                    )

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayMedium,
                        color = CobaltPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.splash_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RulePill(
                            icon = Icons.Filled.Casino,
                            label = "9 Modes",
                            tint = Color(0xFFFF3B4E),
                            modifier = Modifier.weight(1f)
                        )
                        RulePill(
                            icon = Icons.Filled.Star,
                            label = "Power-Ups",
                            tint = SunnyGoldDark,
                            modifier = Modifier.weight(1f)
                        )
                        RulePill(
                            icon = Icons.Filled.Timer,
                            label = "Online & Bots",
                            tint = CobaltPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Primary Pass & Play / vs Bots CTA
            CartoonButton(
                text = stringResource(R.string.home_play),
                onClick = onPlayClick,
                tag = "home_play_button",
                icon = Icons.Filled.PlayArrow,
                containerColor = MintGreen,
                lipColor = MintGreenDark,
                contentColor = Color.White,
                minHeight = 60.dp,
                cornerRadius = 26.dp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Play Online Multiplayer CTA
            CartoonButton(
                text = stringResource(R.string.home_play_online),
                onClick = onPlayOnlineClick,
                tag = "home_play_online_button",
                icon = Icons.Filled.Public,
                containerColor = CobaltPrimary,
                lipColor = CobaltDark,
                contentColor = Color.White,
                minHeight = 58.dp,
                cornerRadius = 26.dp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Secondary Row: Global Leaderboard + Stats & History
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CartoonButton(
                    text = stringResource(R.string.home_leaderboard),
                    onClick = onLeaderboardClick,
                    tag = "home_leaderboard_button",
                    icon = Icons.Filled.EmojiEvents,
                    containerColor = SunnyGold,
                    lipColor = SunnyGoldDark,
                    contentColor = Color(0xFF2C1E00),
                    minHeight = 54.dp,
                    cornerRadius = 22.dp,
                    modifier = Modifier.weight(1f)
                )
                CartoonButton(
                    text = stringResource(R.string.home_stats),
                    onClick = onStatsClick,
                    tag = "home_stats_button",
                    icon = Icons.Filled.BarChart,
                    containerColor = Color.White,
                    lipColor = OutlineSoft,
                    contentColor = InkDark,
                    minHeight = 54.dp,
                    cornerRadius = 22.dp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RulePill(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = InkDark,
                maxLines = 1
            )
        }
    }
}
