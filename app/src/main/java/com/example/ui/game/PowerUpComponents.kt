package com.example.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.engine.PowerUpType
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.theme.CoralPink
import com.example.ui.theme.InkDark
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenDark
import com.example.ui.theme.SunnyGoldDark

fun powerUpAccentColor(type: PowerUpType): Color = when (type) {
    PowerUpType.SHIELD -> Color(0xFF00B4D8)
    PowerUpType.DOUBLE_DICE -> Color(0xFFE09F00)
    PowerUpType.BOMB_TRAP -> Color(0xFFE63946)
}

fun powerUpSoftBg(type: PowerUpType): Color = when (type) {
    PowerUpType.SHIELD -> Color(0xFFE0F7FA)
    PowerUpType.DOUBLE_DICE -> Color(0xFFFFF4D2)
    PowerUpType.BOMB_TRAP -> Color(0xFFFFE5E8)
}

fun powerUpIcon(type: PowerUpType): ImageVector = when (type) {
    PowerUpType.SHIELD -> Icons.Filled.Shield
    PowerUpType.DOUBLE_DICE -> Icons.Filled.Casino
    PowerUpType.BOMB_TRAP -> Icons.Filled.LocalFireDepartment
}

@Composable
fun PowerUpActivationBanner(
    banner: PowerUpBannerState
) {
    val accent = when {
        banner.isExplosion -> CoralPink
        banner.type != null -> powerUpAccentColor(banner.type)
        else -> Color(0xFF8338EC)
    }
    val icon = when {
        banner.isExplosion -> Icons.Filled.LocalFireDepartment
        banner.type != null -> powerUpIcon(banner.type)
        else -> Icons.Filled.AutoAwesome
    }

    CartoonCard(
        modifier = Modifier
            .fillMaxWidth(0.84f)
            .testTag("powerup_activation_banner"),
        borderColor = accent,
        containerColor = Color.White,
        cornerRadius = 26.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = banner.eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = banner.title,
                style = MaterialTheme.typography.headlineMedium,
                color = InkDark,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = banner.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Short "How power-ups work" popup shown the first time a player opens Power-Up mode
 * (and re-openable via the help button on the game board).
 */
@Composable
fun PowerUpGuideDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        CartoonCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("powerup_guide_dialog"),
            borderColor = Color(0xFF8338EC),
            containerColor = Color.White,
            cornerRadius = 28.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8338EC).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF8338EC),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.powerup_guide_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkDark,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.powerup_guide_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    textAlign = TextAlign.Center
                )

                PowerUpGuideRow(
                    type = PowerUpType.SHIELD,
                    title = stringResource(R.string.powerup_shield_title),
                    detail = stringResource(R.string.powerup_shield_desc)
                )
                PowerUpGuideRow(
                    type = PowerUpType.DOUBLE_DICE,
                    title = stringResource(R.string.powerup_double_dice_title),
                    detail = stringResource(R.string.powerup_double_dice_desc)
                )
                PowerUpGuideRow(
                    type = PowerUpType.BOMB_TRAP,
                    title = stringResource(R.string.powerup_bomb_title),
                    detail = stringResource(R.string.powerup_bomb_desc)
                )

                Spacer(Modifier.height(4.dp))

                CartoonButton(
                    text = stringResource(R.string.powerup_guide_got_it),
                    onClick = onDismiss,
                    tag = "powerup_guide_dismiss_button",
                    icon = Icons.Filled.Check,
                    containerColor = MintGreen,
                    lipColor = MintGreenDark,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PowerUpGuideRow(
    type: PowerUpType,
    title: String,
    detail: String
) {
    val accent = powerUpAccentColor(type)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = powerUpSoftBg(type)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = powerUpIcon(type),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (type == PowerUpType.DOUBLE_DICE) SunnyGoldDark else accent,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkDark
                )
            }
        }
    }
}
