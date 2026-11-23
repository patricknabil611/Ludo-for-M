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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
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
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.powerUpAccentColor
import com.example.ui.theme.powerUpSoftBg

fun powerUpIcon(type: PowerUpType): ImageVector = when (type) {
    PowerUpType.ROCKET -> Icons.Filled.RocketLaunch
    PowerUpType.GOLDEN_DICE -> Icons.Filled.Casino
    PowerUpType.SHIELD -> Icons.Filled.Shield
    PowerUpType.BOMB_TRAP -> Icons.Filled.LocalFireDepartment
}

@Composable
fun PowerUpActivationBanner(
    banner: PowerUpBannerState
) {
    val theme = LocalLudoTheme.current
    val accent = when {
        banner.isExplosion -> theme.red
        banner.type != null -> powerUpAccentColor(banner.type, theme)
        else -> lerp(theme.blue, theme.red, 0.50f)
    }
    val readableAccent = theme.accessibleAccentOn(accent, theme.surface)
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
        containerColor = theme.surface,
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
                    tint = readableAccent,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = banner.eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = readableAccent,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = banner.title,
                style = MaterialTheme.typography.headlineMedium,
                color = theme.surfaceText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = banner.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.surfaceMutedText,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PowerUpGuideDialog(
    onDismiss: () -> Unit
) {
    val theme = LocalLudoTheme.current
    val magicAccent = lerp(theme.blue, theme.red, 0.50f)
    val readableMagic = theme.accessibleAccentOn(magicAccent, theme.surface)

    Dialog(onDismissRequest = onDismiss) {
        CartoonCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("powerup_guide_dialog"),
            borderColor = magicAccent,
            containerColor = theme.surface,
            cornerRadius = 28.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(magicAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = readableMagic,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "How Magic Items Work",
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.surfaceText,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Magic items spawn on random non-safe squares (up to 3 on the board). Land on one to trigger it immediately!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.surfaceMutedText,
                    textAlign = TextAlign.Center
                )

                PowerUpGuideRow(
                    type = PowerUpType.ROCKET,
                    title = "Rocket",
                    detail = "Blasts that token forward a random 1–8 squares (stops safely before overshooting Home)."
                )
                PowerUpGuideRow(
                    type = PowerUpType.GOLDEN_DICE,
                    title = "Golden Dice",
                    detail = "Guarantees your very next dice roll will be a 6!"
                )
                PowerUpGuideRow(
                    type = PowerUpType.SHIELD,
                    title = "Shield",
                    detail = "Wraps that token in a protective bubble so it cannot be captured for your next 2 turns."
                )
                PowerUpGuideRow(
                    type = PowerUpType.BOMB_TRAP,
                    title = "Bomb Trap",
                    detail = "Pick any empty non-safe square within 6 steps ahead to hide a bomb for 3 rounds!"
                )

                Spacer(Modifier.height(4.dp))

                CartoonButton(
                    text = stringResource(R.string.powerup_guide_got_it),
                    onClick = onDismiss,
                    tag = "powerup_guide_dismiss_button",
                    icon = Icons.Filled.Check,
                    containerColor = theme.primaryActionBg,
                    contentColor = theme.onPrimaryAction,
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
    val theme = LocalLudoTheme.current
    val accent = powerUpAccentColor(type, theme)
    val rowBg = powerUpSoftBg(type, theme)
    val readableTitle = theme.accessibleAccentOn(accent, rowBg)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = rowBg
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
                    tint = theme.contrastingTextOn(accent),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = readableTitle,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.surfaceText
                )
            }
        }
    }
}
