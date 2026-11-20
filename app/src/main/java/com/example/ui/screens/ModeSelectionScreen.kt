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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.GameModeId
import com.example.ui.components.CartoonIconButton
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralPink
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.CreamSurfaceVariant
import com.example.ui.theme.InkDark
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MintGreen
import com.example.ui.theme.OutlineSoft
import com.example.ui.theme.SkyTopTint
import com.example.ui.theme.SunnyGoldDark

@Composable
fun ModeSelectionScreen(
    onBackClick: () -> Unit,
    onModeSelected: (GameModeId) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(SkyTopTint, CreamBackground))
            )
            .safeDrawingPadding()
            .testTag("mode_selection_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
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
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.mode_select_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = InkDark
                    )
                    Text(
                        text = stringResource(R.string.mode_select_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(GameModeId.entries) { mode ->
                    ModeCard(
                        mode = mode,
                        onClick = {
                            if (mode.availableNow) onModeSelected(mode)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    mode: GameModeId,
    onClick: () -> Unit
) {
    val accent = modeAccentColor(mode)
    val icon = modeIcon(mode)
    val isPlayable = mode.availableNow

    Surface(
        onClick = onClick,
        enabled = isPlayable,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mode_card_${mode.name.lowercase()}"),
        shape = RoundedCornerShape(22.dp),
        color = if (isPlayable) Color.White else CreamSurfaceVariant.copy(alpha = 0.78f),
        border = BorderStroke(
            2.dp,
            if (isPlayable) accent.copy(alpha = 0.55f) else OutlineSoft
        ),
        shadowElevation = if (isPlayable) 5.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPlayable) accent.copy(alpha = 0.14f) else InkMuted.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPlayable) accent else InkMuted,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isPlayable) InkDark else InkMuted
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = mode.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isPlayable) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play ${mode.title}",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = InkMuted.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = InkMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Next update",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                }
            }
        }
    }
}

fun modeIcon(mode: GameModeId): ImageVector = when (mode) {
    GameModeId.CLASSIC -> Icons.Filled.Casino
    GameModeId.QUICK -> Icons.Filled.FlashOn
    GameModeId.MASTER -> Icons.Filled.WorkspacePremium
    GameModeId.POWER_UP -> Icons.Filled.AutoAwesome
    GameModeId.TIMED -> Icons.Filled.Timer
    GameModeId.TEAMS_2V2 -> Icons.Filled.Groups
    GameModeId.ONE_TOKEN_RACE -> Icons.Filled.DirectionsRun
    GameModeId.CHAOS -> Icons.Filled.Whatshot
    GameModeId.ELIMINATION -> Icons.Filled.Gavel
}

fun modeAccentColor(mode: GameModeId): Color = when (mode) {
    GameModeId.CLASSIC -> CobaltPrimary
    GameModeId.QUICK -> MintGreen
    GameModeId.MASTER -> SunnyGoldDark
    GameModeId.POWER_UP -> Color(0xFF8338EC)
    GameModeId.TIMED -> Color(0xFF0096C7)
    GameModeId.TEAMS_2V2 -> Color(0xFF7B2CBF)
    GameModeId.ONE_TOKEN_RACE -> Color(0xFFFF6B35)
    GameModeId.CHAOS -> CoralPink
    GameModeId.ELIMINATION -> Color(0xFFB5179E)
}
