package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppSettings
import com.example.ui.components.CartoonButton
import com.example.ui.components.CartoonCard
import com.example.ui.components.CartoonIconButton
import com.example.ui.theme.AllLudoThemes
import com.example.ui.theme.BaseCornerStyle
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.LudoTheme
import com.example.ui.theme.LudoThemeId
import com.example.ui.theme.ludoShadow
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSelectTheme: (LudoThemeId) -> Unit = {},
    onToggleSoundEffects: (Boolean) -> Unit,
    onSoundEffectsVolumeLiveChange: (Float) -> Unit = {},
    onSoundEffectsVolumeCommit: (Float) -> Unit = {},
    onToggleVibration: (Boolean) -> Unit,
    onReplayTutorialClick: () -> Unit = {},
    onBackClick: () -> Unit
) {
    val theme = LocalLudoTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .safeDrawingPadding()
            .testTag("settings_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
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
                    color = theme.text
                )
            }

            // Theme Picker Section (2x2 preview cards)
            ThemePickerSection(
                selectedThemeId = settings.themeId,
                onSelectTheme = onSelectTheme
            )

            // Sound Effects Toggle + Volume Slider Card
            SettingAudioCard(
                iconForLevel = { enabled, level ->
                    when {
                        !enabled || level <= 0f -> Icons.AutoMirrored.Filled.VolumeOff
                        level < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    }
                },
                iconTint = theme.accessibleAccentOn(theme.blue, theme.surface),
                sliderAccent = theme.accessibleAccentOn(theme.blue, theme.surface),
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

            SettingToggleRow(
                icon = Icons.Filled.Vibration,
                iconTint = theme.accessibleAccentOn(theme.red, theme.surface),
                title = stringResource(R.string.settings_vibration),
                subtitle = stringResource(R.string.settings_vibration_desc),
                checked = settings.vibrationEnabled,
                onCheckedChange = onToggleVibration,
                tag = "toggle_vibration"
            )

            // Replay Interactive Tutorial Card
            CartoonCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val accent = theme.accessibleAccentOn(theme.blue, theme.surface)
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_tutorial_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = theme.surfaceText
                            )
                            Text(
                                text = stringResource(R.string.settings_tutorial_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.surfaceMutedText
                            )
                        }
                    }

                    CartoonButton(
                        text = stringResource(R.string.settings_replay_tutorial),
                        onClick = onReplayTutorialClick,
                        tag = "settings_replay_tutorial_button",
                        icon = Icons.Filled.School,
                        minHeight = 48.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Ludo for M • Pass & Play, Bots & Online",
                style = MaterialTheme.typography.labelMedium,
                color = theme.mutedText,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ThemePickerSection(
    selectedThemeId: LudoThemeId,
    onSelectTheme: (LudoThemeId) -> Unit
) {
    val activeTheme = LocalLudoTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_theme_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Palette,
                contentDescription = null,
                tint = activeTheme.accessibleAccentOn(activeTheme.blue, activeTheme.bg),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleLarge,
                color = activeTheme.text,
                fontWeight = FontWeight.Bold
            )
        }

        val rows = AllLudoThemes.chunked(2)
        rows.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { candidate ->
                    ThemePreviewCard(
                        previewTheme = candidate,
                        selected = candidate.id == selectedThemeId,
                        activeTheme = activeTheme,
                        onClick = { onSelectTheme(candidate.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    previewTheme: LudoTheme,
    selected: Boolean,
    activeTheme: LudoTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) activeTheme.blue else activeTheme.chipLine,
        animationSpec = tween(250),
        label = "themeCardBorder"
    )
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier
            .ludoShadow(activeTheme.cardShadow, 18.dp)
            .clip(shape)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(onClick = onClick)
            .testTag("theme_card_${previewTheme.id.name.lowercase()}"),
        shape = shape,
        color = activeTheme.surface
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mini board preview rendered on the candidate theme's own background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.22f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(previewTheme.bg),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    val boardSide = size.minDimension
                    val left = (size.width - boardSide) / 2f
                    val top = (size.height - boardSide) / 2f
                    val framePad = if (previewTheme.frame != null) boardSide * 0.06f else 0f

                    // Optional frame
                    previewTheme.frame?.let { frameColor ->
                        drawRoundRect(
                            color = frameColor,
                            topLeft = Offset(left, top),
                            size = Size(boardSide, boardSide),
                            cornerRadius = CornerRadius(boardSide * 0.14f)
                        )
                    }

                    val innerLeft = left + framePad
                    val innerTop = top + framePad
                    val innerSide = boardSide - framePad * 2f

                    // Board backdrop
                    drawRoundRect(
                        color = previewTheme.board,
                        topLeft = Offset(innerLeft, innerTop),
                        size = Size(innerSide, innerSide),
                        cornerRadius = CornerRadius(innerSide * 0.11f)
                    )

                    val cell = innerSide / 7f

                    // Mini track squares (cross)
                    for (r in 0..6) {
                        for (c in 0..6) {
                            val inCross = r in 2..4 || c in 2..4
                            if (!inCross) continue
                            val cx = innerLeft + c * cell
                            val cy = innerTop + r * cell
                            val fill = when {
                                r == 3 && c in 1..2 -> previewTheme.red
                                c == 3 && r in 1..2 -> previewTheme.green
                                r == 3 && c in 4..5 -> previewTheme.yellow
                                c == 3 && r in 4..5 -> previewTheme.blue
                                r == 3 && c == 3 -> previewTheme.crownIcon
                                else -> previewTheme.cell
                            }
                            drawRect(
                                color = fill,
                                topLeft = Offset(cx, cy),
                                size = Size(cell, cell)
                            )
                            drawRect(
                                color = previewTheme.gridLine,
                                topLeft = Offset(cx, cy),
                                size = Size(cell, cell),
                                style = Stroke(width = 0.75.dp.toPx())
                            )
                        }
                    }

                    // 4 mini corner bases (2x2 each)
                    val corners = listOf(
                        Triple(0f, 0f, previewTheme.red),
                        Triple(5f * cell, 0f, previewTheme.green),
                        Triple(5f * cell, 5f * cell, previewTheme.yellow),
                        Triple(0f, 5f * cell, previewTheme.blue)
                    )
                    val baseSize = cell * 2f
                    corners.forEach { (dx, dy, color) ->
                        val baseFill = if (previewTheme.baseStyle == BaseCornerStyle.TINT) {
                            color.copy(alpha = 0.18f)
                        } else {
                            color
                        }
                        drawRoundRect(
                            color = baseFill,
                            topLeft = Offset(innerLeft + dx, innerTop + dy),
                            size = Size(baseSize, baseSize),
                            cornerRadius = CornerRadius(baseSize * 0.18f)
                        )
                        val pad = baseSize * 0.22f
                        drawRoundRect(
                            color = previewTheme.baseInner,
                            topLeft = Offset(innerLeft + dx + pad, innerTop + dy + pad),
                            size = Size(baseSize - pad * 2f, baseSize - pad * 2f),
                            cornerRadius = CornerRadius(baseSize * 0.14f)
                        )
                    }

                    // 2 tokens in theme colors on the track
                    val tokenR = cell * 0.38f
                    val t1 = Offset(innerLeft + 2.5f * cell, innerTop + 1.5f * cell)
                    val t2 = Offset(innerLeft + 4.5f * cell, innerTop + 4.5f * cell)
                    listOf(t1 to previewTheme.red, t2 to previewTheme.blue).forEach { (center, col) ->
                        drawCircle(color = previewTheme.tokenEdge, radius = tokenR, center = center)
                        drawCircle(color = col, radius = tokenR * 0.78f, center = center)
                        drawCircle(color = Color.White.copy(alpha = 0.5f), radius = tokenR * 0.28f, center = center)
                    }
                }

                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(activeTheme.blue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected theme",
                            tint = activeTheme.contrastingTextOn(activeTheme.blue),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Text(
                text = previewTheme.displayName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = previewTheme.font,
                    fontSize = 14.sp
                ),
                color = activeTheme.surfaceText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun SettingAudioCard(
    iconForLevel: (Boolean, Float) -> ImageVector,
    iconTint: Color,
    sliderAccent: Color,
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
    val theme = LocalLudoTheme.current
    val displayedTarget = if (checked) volume.coerceIn(0f, 1f) else 0f
    var sliderPosition by remember { mutableFloatStateOf(displayedTarget) }
    var isDragging by remember { mutableStateOf(false) }

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

    CartoonCard(modifier = Modifier.fillMaxWidth()) {
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
                        tint = if (effectiveChecked && percent > 0) iconTint else theme.surfaceMutedText,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = theme.surfaceText
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.surfaceMutedText
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
                        checkedThumbColor = theme.contrastingTextOn(theme.green),
                        checkedTrackColor = theme.green,
                        uncheckedThumbColor = theme.surface,
                        uncheckedTrackColor = theme.timerTrack
                    )
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = theme.surfaceVariant,
                border = BorderStroke(1.dp, theme.chipLine)
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
                            color = theme.surfaceMutedText,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (percent > 0) sliderAccent.copy(alpha = 0.14f) else theme.timerTrack
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (percent > 0) sliderAccent else theme.surfaceMutedText,
                                fontWeight = FontWeight.Bold,
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
                            tint = theme.surfaceMutedText,
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
                                activeTrackColor = sliderAccent,
                                inactiveTrackColor = theme.timerTrack
                            )
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = if (percent > 0) sliderAccent else theme.surfaceMutedText,
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
    val theme = LocalLudoTheme.current
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
                    color = theme.surfaceText
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.surfaceMutedText
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(tag),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = theme.contrastingTextOn(theme.green),
                    checkedTrackColor = theme.green,
                    uncheckedThumbColor = theme.surface,
                    uncheckedTrackColor = theme.timerTrack
                )
            )
        }
    }
}
