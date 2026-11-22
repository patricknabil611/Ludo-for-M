package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppSettings
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.theme.CobaltDark
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralPink
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.CreamSurfaceVariant
import com.example.ui.theme.InkDark
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MintGreen
import com.example.ui.theme.SkyTopTint
import com.example.ui.theme.SunnyGold
import com.example.ui.theme.SunnyGoldDark
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onToggleSoundEffects: (Boolean) -> Unit,
    onSoundEffectsVolumeLiveChange: (Float) -> Unit = {},
    onSoundEffectsVolumeCommit: (Float) -> Unit = {},
    onToggleMusic: (Boolean) -> Unit,
    onMusicVolumeLiveChange: (Float) -> Unit = {},
    onMusicVolumeCommit: (Float) -> Unit = {},
    onToggleVibration: (Boolean) -> Unit,
    onReplayTutorialClick: () -> Unit = {},
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(SkyTopTint, CreamBackground))
            )
            .safeDrawingPadding()
            .testTag("settings_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CartoonIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    onClick = onBackClick,
                    modifier = Modifier.testTag("settings_back_button")
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = InkDark
                )
            }

            // Sound Effects Toggle + Volume Slider Card
            SettingAudioCard(
                iconForLevel = { enabled, level ->
                    when {
                        !enabled || level <= 0f -> Icons.AutoMirrored.Filled.VolumeOff
                        level < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    }
                },
                iconTint = CobaltPrimary,
                sliderAccent = CobaltPrimary,
                title = stringResource(R.string.settings_sound_effects),
                subtitle = stringResource(R.string.settings_sound_effects_desc),
                volumeLabel = stringResource(R.string.settings_sound_effects_volume),
                checked = settings.soundEffectsEnabled,
                volume = settings.soundEffectsVolume,
                onCheckedChange = onToggleSoundEffects,
                onVolumeLiveChange = onSoundEffectsVolumeLiveChange,
                onVolumeCommit = onSoundEffectsVolumeCommit,
                toggleTag = "toggle_sound_effects",
                sliderTag = "slider_sound_effects_volume"
            )

            // Background Music Toggle + Volume Slider Card
            SettingAudioCard(
                iconForLevel = { enabled, level ->
                    if (!enabled || level <= 0f) Icons.Filled.MusicOff else Icons.Filled.MusicNote
                },
                iconTint = SunnyGoldDark,
                sliderAccent = SunnyGoldDark,
                sliderTrackHighlight = SunnyGold,
                title = stringResource(R.string.settings_music),
                subtitle = stringResource(R.string.settings_music_desc),
                volumeLabel = stringResource(R.string.settings_music_volume),
                checked = settings.musicEnabled,
                volume = settings.musicVolume,
                onCheckedChange = onToggleMusic,
                onVolumeLiveChange = onMusicVolumeLiveChange,
                onVolumeCommit = onMusicVolumeCommit,
                toggleTag = "toggle_music",
                sliderTag = "slider_music_volume"
            )

            SettingToggleRow(
                icon = Icons.Filled.Vibration,
                iconTint = CoralPink,
                title = stringResource(R.string.settings_vibration),
                subtitle = stringResource(R.string.settings_vibration_desc),
                checked = settings.vibrationEnabled,
                onCheckedChange = onToggleVibration,
                tag = "toggle_vibration"
            )

            // Replay Interactive Tutorial Card
            CartoonCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = CobaltPrimary.copy(alpha = 0.4f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(CobaltPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = null,
                                tint = CobaltPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_tutorial_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = InkDark
                            )
                            Text(
                                text = stringResource(R.string.settings_tutorial_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkMuted
                            )
                        }
                    }

                    CartoonButton(
                        text = stringResource(R.string.settings_replay_tutorial),
                        onClick = onReplayTutorialClick,
                        tag = "settings_replay_tutorial_button",
                        icon = Icons.Filled.School,
                        containerColor = CobaltPrimary,
                        lipColor = CobaltDark,
                        minHeight = 48.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Ludo for M • Pass & Play, Bots & Online",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SettingAudioCard(
    iconForLevel: (Boolean, Float) -> ImageVector,
    iconTint: Color,
    sliderAccent: Color,
    sliderTrackHighlight: Color = sliderAccent,
    title: String,
    subtitle: String,
    volumeLabel: String,
    checked: Boolean,
    volume: Float,
    onCheckedChange: (Boolean) -> Unit,
    onVolumeLiveChange: (Float) -> Unit,
    onVolumeCommit: (Float) -> Unit,
    toggleTag: String,
    sliderTag: String
) {
    val displayedTarget = if (checked) volume.coerceIn(0f, 1f) else 0f
    var sliderPosition by remember { mutableFloatStateOf(displayedTarget) }
    var isDragging by remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(displayedTarget, isDragging) {
        if (!isDragging) {
            sliderPosition = displayedTarget
        }
    }

    val effectiveChecked = if (isDragging) sliderPosition > 0f else checked
    val percent = (sliderPosition * 100f).roundToInt().coerceIn(0, 100)
    val mutedLabel = stringResource(R.string.settings_volume_muted)
    val badgeText = if (percent == 0) mutedLabel else "$percent%"
    val activeIcon = iconForLevel(effectiveChecked, sliderPosition)

    CartoonCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (effectiveChecked && percent > 0) sliderAccent.copy(alpha = 0.35f) else CreamSurfaceVariant
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = if (effectiveChecked && percent > 0) 0.16f else 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = activeIcon,
                        contentDescription = null,
                        tint = if (effectiveChecked && percent > 0) iconTint else InkMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = InkDark
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted
                    )
                }

                Switch(
                    checked = effectiveChecked,
                    onCheckedChange = { nextChecked ->
                        isDragging = false
                        onCheckedChange(nextChecked)
                    },
                    modifier = Modifier.testTag(toggleTag),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MintGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = InkMuted.copy(alpha = 0.35f)
                    )
                )
            }

            // Interactive Volume Slider Strip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CreamSurfaceVariant.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, sliderAccent.copy(alpha = 0.18f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = volumeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = InkMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (percent > 0) {
                                sliderAccent.copy(alpha = 0.14f)
                            } else {
                                InkMuted.copy(alpha = 0.14f)
                            }
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (percent > 0) sliderAccent else InkMuted,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .widthIn(min = 50.dp)
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeMute,
                            contentDescription = null,
                            tint = InkMuted,
                            modifier = Modifier.size(18.dp)
                        )

                        Slider(
                            value = sliderPosition,
                            onValueChange = { next ->
                                val clamped = next.coerceIn(0f, 1f)
                                isDragging = true
                                sliderPosition = clamped
                                onVolumeLiveChange(clamped)
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                onVolumeCommit(sliderPosition)
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(sliderTag)
                                .semantics {
                                    contentDescription = "$volumeLabel $badgeText"
                                },
                            colors = SliderDefaults.colors(
                                thumbColor = sliderAccent,
                                activeTrackColor = sliderTrackHighlight,
                                inactiveTrackColor = InkMuted.copy(alpha = 0.24f)
                            )
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = if (percent > 0) sliderAccent else InkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    CartoonCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = InkDark
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(tag),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MintGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = InkMuted.copy(alpha = 0.35f)
                )
            )
        }
    }
}
