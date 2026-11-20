package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
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

/**
 * Login & Guest Upgrade screen for Online Multiplayer and Global Leaderboard.
 * Supports Google Sign-In via Credential Manager OR Guest nickname mode (with zero-loss upgrade).
 */
@Composable
fun OnlineAuthScreen(
    authState: OnlineAuthUiState,
    subtitleOverride: String? = null,
    onGoogleSignIn: (Activity) -> Unit,
    onContinueAsGuest: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val activity = LocalContext.current as? Activity
    var guestNickname by rememberSaveable(authState.guestProfile.nickname) {
        mutableStateOf(authState.guestProfile.nickname)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTopTint, CreamBackground)))
            .safeDrawingPadding()
            .testTag("online_auth_screen"),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CartoonIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    onClick = onBackClick,
                    modifier = Modifier.testTag("online_auth_back_button")
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.online_auth_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkDark
                )
            }

            Spacer(Modifier.height(18.dp))

            // Guest upgrade highlight card if user already has guest session/stats
            if (authState.hasGuestStatsToUpgrade) {
                CartoonCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = SunnyGoldDark,
                    containerColor = Color(0xFFFFF8DF),
                    cornerRadius = 24.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Upgrade,
                            contentDescription = null,
                            tint = SunnyGoldDark,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.online_upgrade_guest_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = InkDark,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (authState.guestProfile.gamesPlayed > 0) {
                                    "Signed in as Guest \"${authState.guestProfile.nickname}\" (${authState.guestProfile.wins}W / ${authState.guestProfile.gamesPlayed}G). Sign in with Google to keep all your stats and unlock online rooms & leaderboard!"
                                } else {
                                    "Upgrade Guest \"${authState.guestProfile.nickname}\" to a Google Account to unlock online multiplayer rooms and the global leaderboard."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // Primary Google Sign-In Card
            CartoonCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = CobaltPrimary,
                cornerRadius = 28.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        tint = CobaltPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.online_google_card_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = InkDark,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = subtitleOverride ?: stringResource(R.string.online_google_card_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeatureBadge(
                            icon = Icons.Filled.Public,
                            label = "Room Codes",
                            modifier = Modifier.weight(1f)
                        )
                        FeatureBadge(
                            icon = Icons.Filled.EmojiEvents,
                            label = "Leaderboard",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    if (authState.isSigningIn) {
                        CircularProgressIndicator(
                            color = CobaltPrimary,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    } else {
                        CartoonButton(
                            text = if (authState.hasGuestStatsToUpgrade) {
                                stringResource(R.string.online_upgrade_google_cta)
                            } else {
                                stringResource(R.string.online_sign_in_google)
                            },
                            onClick = { if (activity != null) onGoogleSignIn(activity) },
                            tag = "google_sign_in_button",
                            icon = Icons.Filled.AccountCircle,
                            containerColor = CobaltPrimary,
                            lipColor = CobaltDark,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    authState.errorMessage?.let { err ->
                        Spacer(Modifier.height(12.dp))
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
                }
            }

            Spacer(Modifier.height(16.dp))

            // Play as Guest Card (nickname only, no leaderboard, upgradeable anytime)
            CartoonCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = OutlineSoft,
                cornerRadius = 24.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MintGreenDark,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.online_guest_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = InkDark
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.online_guest_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = guestNickname,
                        onValueChange = { if (it.length <= 24) guestNickname = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.setup_nickname_hint)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guest_nickname_input")
                    )
                    Spacer(Modifier.height(12.dp))
                    CartoonButton(
                        text = stringResource(R.string.online_guest_cta),
                        onClick = { onContinueAsGuest(guestNickname) },
                        tag = "continue_as_guest_button",
                        icon = Icons.Filled.Person,
                        containerColor = MintGreen,
                        lipColor = MintGreenDark,
                        minHeight = 52.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FeatureBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = CobaltPrimary.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CobaltPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = InkDark
            )
        }
    }
}
