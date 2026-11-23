package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.AvatarAccessory
import com.example.data.AvatarBackground
import com.example.data.AvatarCharacter
import com.example.data.AvatarFrame
import com.example.data.PlayerAvatarCodec
import com.example.data.PlayerAvatarConfig
import com.example.ui.theme.AvatarIllustrationColors
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.backdropPalette
import com.example.ui.theme.ludoShadow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Playful cartoon avatar badge rendered completely via Jetpack Compose Canvas.
 * Crisp at every size from 26dp HUD pills up to 112dp studio previews.
 */
@Composable
fun PlayerAvatarBadge(
    avatar: PlayerAvatarConfig,
    size: Dp = 44.dp,
    contentDescription: String? = null,
    showEditBadge: Boolean = false,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current
    val description = contentDescription ?: avatar.accessibilityDescription()
    Box(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCartoonAvatar(avatar = avatar, dimmed = dimmed)
        }

        if (showEditBadge) {
            val badgeSize = (size * 0.36f).coerceIn(16.dp, 26.dp)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(badgeSize),
                shape = CircleShape,
                color = theme.yellow,
                border = BorderStroke(1.5.dp, theme.surface),
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = theme.contrastingTextOn(theme.yellow),
                        modifier = Modifier.size(badgeSize * 0.58f)
                    )
                }
            }
        }
    }
}

private enum class CustomizerTab(
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String
) {
    PRESETS(R.string.avatar_tab_presets, Icons.Filled.Star, "avatar_tab_presets"),
    HERO(R.string.avatar_tab_character, Icons.Filled.Face, "avatar_tab_character"),
    STYLE(R.string.avatar_tab_style, Icons.Filled.Palette, "avatar_tab_style"),
    FLAIR(R.string.avatar_tab_flair, Icons.Filled.AutoAwesome, "avatar_tab_flair")
}

@Composable
fun AvatarCustomizerDialog(
    initialAvatar: PlayerAvatarConfig,
    title: String = stringResource(R.string.avatar_studio_title),
    subtitle: String? = null,
    onDismiss: () -> Unit,
    onSave: (PlayerAvatarConfig) -> Unit
) {
    val theme = LocalLudoTheme.current
    var draft by remember(initialAvatar) { mutableStateOf(initialAvatar) }
    var selectedTab by remember { mutableStateOf(CustomizerTab.PRESETS) }
    var bounceTick by remember { mutableIntStateOf(0) }
    var bounced by remember { mutableStateOf(false) }

    LaunchedEffect(bounceTick) {
        if (bounceTick > 0) {
            bounced = true
            kotlinx.coroutines.delay(130L)
            bounced = false
        }
    }
    val previewScale by animateFloatAsState(
        targetValue = if (bounced) 1.09f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "avatar_preview_bounce"
    )

    fun updateDraft(next: PlayerAvatarConfig) {
        if (next != draft) {
            draft = next
            bounceTick += 1
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .ludoShadow(theme.cardShadow, 26.dp)
                .testTag("avatar_customizer_dialog"),
            shape = RoundedCornerShape(26.dp),
            color = theme.surface,
            border = BorderStroke(2.dp, theme.blue)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.surfaceVariant)
                        .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = theme.surfaceText,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (!subtitle.isNullOrBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = theme.surfaceMutedText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("avatar_dialog_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cd_close_avatar_studio),
                                    tint = theme.surfaceMutedText
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier.scale(previewScale),
                                contentAlignment = Alignment.Center
                            ) {
                                PlayerAvatarBadge(
                                    avatar = draft,
                                    size = 96.dp,
                                    modifier = Modifier.testTag("avatar_live_preview")
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = draft.character.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = theme.surfaceText,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${draft.background.displayName} • ${draft.frame.displayName}" +
                                        if (draft.accessory != AvatarAccessory.NONE) " • ${draft.accessory.displayName}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.surfaceMutedText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    onClick = { updateDraft(PlayerAvatarCodec.randomize()) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = theme.yellow,
                                    modifier = Modifier
                                        .heightIn(min = 42.dp)
                                        .testTag("avatar_randomize_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val onYellow = theme.contrastingTextOn(theme.yellow)
                                        Icon(
                                            imageVector = Icons.Filled.Casino,
                                            contentDescription = null,
                                            tint = onYellow,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.avatar_randomize),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = onYellow,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Segmented Category Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CustomizerTab.entries.forEach { tab ->
                        val active = tab == selectedTab
                        Surface(
                            onClick = { selectedTab = tab },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag(tab.tag),
                            shape = RoundedCornerShape(14.dp),
                            color = if (active) theme.primaryActionBg else theme.surfaceVariant,
                            border = BorderStroke(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) theme.blue else theme.chipLine
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (active) theme.onPrimaryAction else theme.surfaceMutedText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(tab.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) theme.onPrimaryAction else theme.surfaceText,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 330.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        CustomizerTab.PRESETS -> PresetsGrid(current = draft, onSelect = ::updateDraft)
                        CustomizerTab.HERO -> CharactersGrid(current = draft, onSelect = { updateDraft(draft.copy(character = it)) })
                        CustomizerTab.STYLE -> BackdropAndFrameSelector(
                            current = draft,
                            onSelectBackground = { updateDraft(draft.copy(background = it)) },
                            onSelectFrame = { updateDraft(draft.copy(frame = it)) }
                        )
                        CustomizerTab.FLAIR -> AccessoryGrid(current = draft, onSelect = { updateDraft(draft.copy(accessory = it)) })
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("avatar_cancel_button")
                    ) {
                        Text(
                            text = stringResource(R.string.powerup_cancel_targeting),
                            style = MaterialTheme.typography.labelLarge,
                            color = theme.surfaceMutedText,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    CartoonButton(
                        text = stringResource(R.string.avatar_save_cta),
                        onClick = { onSave(draft) },
                        containerColor = theme.primaryActionBg,
                        contentColor = theme.onPrimaryAction,
                        icon = Icons.Filled.Check,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("avatar_save_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetsGrid(
    current: PlayerAvatarConfig,
    onSelect: (PlayerAvatarConfig) -> Unit
) {
    val theme = LocalLudoTheme.current
    val rows = PlayerAvatarCodec.CURATED_PRESETS.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowPresets.forEach { preset ->
                    val selected = current == preset.config
                    Surface(
                        onClick = { onSelect(preset.config) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 66.dp)
                            .testTag("avatar_preset_${preset.id}"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) theme.blue.copy(alpha = 0.14f) else theme.surfaceVariant,
                        border = BorderStroke(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) theme.blue else theme.chipLine
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PlayerAvatarBadge(avatar = preset.config, size = 46.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = theme.surfaceText,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = preset.config.character.tagline,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.surfaceMutedText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharactersGrid(
    current: PlayerAvatarConfig,
    onSelect: (AvatarCharacter) -> Unit
) {
    val theme = LocalLudoTheme.current
    val rows = AvatarCharacter.entries.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowChars ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowChars.forEach { character ->
                    val selected = current.character == character
                    Surface(
                        onClick = { onSelect(character) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("avatar_char_${character.name.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) theme.blue.copy(alpha = 0.14f) else theme.surfaceVariant,
                        border = BorderStroke(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) theme.blue else theme.chipLine
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PlayerAvatarBadge(
                                avatar = current.copy(character = character),
                                size = 50.dp
                            )
                            Text(
                                text = character.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.surfaceText,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackdropAndFrameSelector(
    current: PlayerAvatarConfig,
    onSelectBackground: (AvatarBackground) -> Unit,
    onSelectFrame: (AvatarFrame) -> Unit
) {
    val theme = LocalLudoTheme.current
    Text(
        text = stringResource(R.string.avatar_section_backdrop),
        style = MaterialTheme.typography.labelLarge,
        color = theme.surfaceText,
        fontWeight = FontWeight.Bold
    )
    val bgRows = AvatarBackground.entries.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        bgRows.forEach { rowBgs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowBgs.forEach { bg ->
                    val selected = current.background == bg
                    val palette = bg.backdropPalette()
                    Surface(
                        onClick = { onSelectBackground(bg) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("avatar_bg_${bg.name.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) theme.blue.copy(alpha = 0.14f) else theme.surfaceVariant,
                        border = BorderStroke(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) theme.blue else theme.chipLine
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
                                    .border(
                                        width = if (selected) 2.5.dp else 1.5.dp,
                                        color = if (selected) Color.White else palette.bottom,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = bg.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.surfaceText,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(4.dp))

    Text(
        text = stringResource(R.string.avatar_section_frame),
        style = MaterialTheme.typography.labelLarge,
        color = theme.surfaceText,
        fontWeight = FontWeight.Bold
    )
    val frameRows = AvatarFrame.entries.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        frameRows.forEach { rowFrames ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowFrames.forEach { frame ->
                    val selected = current.frame == frame
                    Surface(
                        onClick = { onSelectFrame(frame) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("avatar_frame_${frame.name.lowercase()}"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) theme.blue.copy(alpha = 0.14f) else theme.surfaceVariant,
                        border = BorderStroke(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) theme.blue else theme.chipLine
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PlayerAvatarBadge(
                                avatar = current.copy(frame = frame),
                                size = 30.dp
                            )
                            Text(
                                text = frame.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.surfaceText,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessoryGrid(
    current: PlayerAvatarConfig,
    onSelect: (AvatarAccessory) -> Unit
) {
    val theme = LocalLudoTheme.current
    val rows = AvatarAccessory.entries.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowAccs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowAccs.forEach { acc ->
                    val selected = current.accessory == acc
                    Surface(
                        onClick = { onSelect(acc) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("avatar_acc_${acc.name.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) theme.blue.copy(alpha = 0.14f) else theme.surfaceVariant,
                        border = BorderStroke(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) theme.blue else theme.chipLine
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PlayerAvatarBadge(
                                avatar = current.copy(accessory = acc),
                                size = 54.dp
                            )
                            Text(
                                text = acc.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.surfaceText,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// Custom Cartoon Canvas Artwork (Backdrop + 12 Characters + Accessories + Frames)
// =========================================================================

private fun DrawScope.drawCartoonAvatar(
    avatar: PlayerAvatarConfig,
    dimmed: Boolean
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension / 2f
    val innerR = r * 0.90f
    val bg = avatar.background.backdropPalette()

    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(bg.top, bg.bottom),
            startY = cy - innerR,
            endY = cy + innerR
        ),
        radius = innerR,
        center = Offset(cx, cy)
    )

    drawCircle(
        color = Color.White.copy(alpha = 0.22f),
        radius = innerR * 0.56f,
        center = Offset(cx - innerR * 0.24f, cy - innerR * 0.34f)
    )
    drawCircle(
        color = bg.accent.copy(alpha = 0.35f),
        radius = innerR * 0.12f,
        center = Offset(cx + innerR * 0.55f, cy - innerR * 0.40f)
    )
    drawCircle(
        color = bg.accent.copy(alpha = 0.28f),
        radius = innerR * 0.08f,
        center = Offset(cx - innerR * 0.56f, cy + innerR * 0.32f)
    )

    when (avatar.character) {
        AvatarCharacter.FOX -> drawFox(cx, cy, innerR)
        AvatarCharacter.LION -> drawLion(cx, cy, innerR)
        AvatarCharacter.PANDA -> drawPanda(cx, cy, innerR)
        AvatarCharacter.FROG -> drawFrog(cx, cy, innerR)
        AvatarCharacter.CAT -> drawCat(cx, cy, innerR)
        AvatarCharacter.OWL -> drawOwl(cx, cy, innerR)
        AvatarCharacter.DRAGON -> drawDragon(cx, cy, innerR)
        AvatarCharacter.ASTRONAUT -> drawAstronaut(cx, cy, innerR)
        AvatarCharacter.KNIGHT -> drawKnight(cx, cy, innerR)
        AvatarCharacter.WIZARD -> drawWizard(cx, cy, innerR)
        AvatarCharacter.NINJA -> drawNinja(cx, cy, innerR)
        AvatarCharacter.ROBOT -> drawRobot(cx, cy, innerR)
    }

    when (avatar.accessory) {
        AvatarAccessory.NONE -> Unit
        AvatarAccessory.CROWN -> drawCrownAccessory(cx, cy, innerR)
        AvatarAccessory.SUNGLASSES -> drawSunglassesAccessory(cx, cy, innerR)
        AvatarAccessory.STAR_PIN -> drawStarPinAccessory(cx, cy, innerR)
        AvatarAccessory.HALO -> drawHaloAccessory(cx, cy, innerR)
        AvatarAccessory.HEADPHONES -> drawHeadphonesAccessory(cx, cy, innerR)
    }

    if (dimmed) {
        drawCircle(
            color = AvatarIllustrationColors.dimmedVeil,
            radius = innerR,
            center = Offset(cx, cy)
        )
    }

    drawAvatarFrame(avatar.frame, cx, cy, r, bg.accent)
}

private fun DrawScope.drawEyes(
    cx: Float,
    ey: Float,
    spacing: Float,
    eyeR: Float,
    irisColor: Color = AvatarIllustrationColors.ink
) {
    val left = Offset(cx - spacing, ey)
    val right = Offset(cx + spacing, ey)
    listOf(left, right).forEach { pos ->
        drawCircle(color = Color.White, radius = eyeR * 1.12f, center = pos)
        drawCircle(color = irisColor, radius = eyeR * 0.78f, center = pos + Offset(0f, eyeR * 0.06f))
        drawCircle(
            color = Color.White,
            radius = eyeR * 0.28f,
            center = pos + Offset(-eyeR * 0.24f, -eyeR * 0.24f)
        )
    }
}

private fun DrawScope.drawRosyCheeks(cx: Float, y: Float, spacing: Float, r: Float) {
    drawCircle(color = AvatarIllustrationColors.rosyCheek, radius = r, center = Offset(cx - spacing, y))
    drawCircle(color = AvatarIllustrationColors.rosyCheek, radius = r, center = Offset(cx + spacing, y))
}

private fun DrawScope.drawCuteSmile(cx: Float, y: Float, halfWidth: Float, strokeW: Float) {
    val smile = Path().apply {
        moveTo(cx - halfWidth, y)
        quadraticTo(cx, y + halfWidth * 0.85f, cx + halfWidth, y)
    }
    drawPath(
        path = smile,
        color = AvatarIllustrationColors.ink,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawFox(cx: Float, cy: Float, r: Float) {
    val fur = AvatarIllustrationColors.foxFur
    val furDark = AvatarIllustrationColors.foxDark
    val cream = AvatarIllustrationColors.foxCream

    val leftEar = Path().apply {
        moveTo(cx - r * 0.50f, cy - r * 0.14f)
        lineTo(cx - r * 0.44f, cy - r * 0.70f)
        lineTo(cx - r * 0.08f, cy - r * 0.42f)
        close()
    }
    val rightEar = Path().apply {
        moveTo(cx + r * 0.50f, cy - r * 0.14f)
        lineTo(cx + r * 0.44f, cy - r * 0.70f)
        lineTo(cx + r * 0.08f, cy - r * 0.42f)
        close()
    }
    drawPath(leftEar, furDark)
    drawPath(rightEar, furDark)
    val leftInner = Path().apply {
        moveTo(cx - r * 0.42f, cy - r * 0.22f)
        lineTo(cx - r * 0.38f, cy - r * 0.56f)
        lineTo(cx - r * 0.16f, cy - r * 0.38f)
        close()
    }
    val rightInner = Path().apply {
        moveTo(cx + r * 0.42f, cy - r * 0.22f)
        lineTo(cx + r * 0.38f, cy - r * 0.56f)
        lineTo(cx + r * 0.16f, cy - r * 0.38f)
        close()
    }
    drawPath(leftInner, cream)
    drawPath(rightInner, cream)

    drawOval(color = fur, topLeft = Offset(cx - r * 0.56f, cy - r * 0.42f), size = Size(r * 1.12f, r * 0.96f))
    drawOval(color = cream, topLeft = Offset(cx - r * 0.48f, cy + r * 0.02f), size = Size(r * 0.96f, r * 0.50f))
    drawEyes(cx, cy - r * 0.02f, r * 0.22f, r * 0.11f)
    drawRosyCheeks(cx, cy + r * 0.16f, r * 0.32f, r * 0.08f)
    drawOval(color = AvatarIllustrationColors.ink, topLeft = Offset(cx - r * 0.09f, cy + r * 0.14f), size = Size(r * 0.18f, r * 0.13f))
    drawCuteSmile(cx, cy + r * 0.30f, r * 0.14f, r * 0.045f)
}

private fun DrawScope.drawLion(cx: Float, cy: Float, r: Float) {
    val mane = AvatarIllustrationColors.lionMane
    val maneLight = AvatarIllustrationColors.lionManeLight
    val goldFur = AvatarIllustrationColors.lionFur
    val snout = AvatarIllustrationColors.lionSnout

    val petals = 12
    for (i in 0 until petals) {
        val a = (i * 2.0 * PI / petals)
        val px = cx + (cos(a) * r * 0.48f).toFloat()
        val py = cy + r * 0.04f + (sin(a) * r * 0.48f).toFloat()
        drawCircle(color = if (i % 2 == 0) mane else maneLight, radius = r * 0.22f, center = Offset(px, py))
    }
    drawCircle(color = goldFur, radius = r * 0.16f, center = Offset(cx - r * 0.38f, cy - r * 0.34f))
    drawCircle(color = goldFur, radius = r * 0.16f, center = Offset(cx + r * 0.38f, cy - r * 0.34f))
    drawCircle(color = goldFur, radius = r * 0.46f, center = Offset(cx, cy + r * 0.05f))
    drawOval(color = snout, topLeft = Offset(cx - r * 0.24f, cy + r * 0.08f), size = Size(r * 0.48f, r * 0.32f))
    drawEyes(cx, cy - r * 0.04f, r * 0.19f, r * 0.10f)
    drawRosyCheeks(cx, cy + r * 0.14f, r * 0.30f, r * 0.075f)
    val nose = Path().apply {
        moveTo(cx - r * 0.09f, cy + r * 0.12f)
        lineTo(cx + r * 0.09f, cy + r * 0.12f)
        lineTo(cx, cy + r * 0.23f)
        close()
    }
    drawPath(nose, AvatarIllustrationColors.lionNose)
    drawCuteSmile(cx, cy + r * 0.27f, r * 0.13f, r * 0.045f)
}

private fun DrawScope.drawPanda(cx: Float, cy: Float, r: Float) {
    val dark = AvatarIllustrationColors.pandaDark
    drawCircle(color = dark, radius = r * 0.21f, center = Offset(cx - r * 0.40f, cy - r * 0.40f))
    drawCircle(color = dark, radius = r * 0.21f, center = Offset(cx + r * 0.40f, cy - r * 0.40f))
    drawOval(color = AvatarIllustrationColors.pandaWhite, topLeft = Offset(cx - r * 0.54f, cy - r * 0.44f), size = Size(r * 1.08f, r * 0.98f))
    drawOval(color = dark, topLeft = Offset(cx - r * 0.40f, cy - r * 0.18f), size = Size(r * 0.32f, r * 0.34f))
    drawOval(color = dark, topLeft = Offset(cx + r * 0.08f, cy - r * 0.18f), size = Size(r * 0.32f, r * 0.34f))
    drawEyes(cx, cy - r * 0.01f, r * 0.23f, r * 0.085f)
    drawRosyCheeks(cx, cy + r * 0.20f, r * 0.32f, r * 0.08f)
    drawOval(color = dark, topLeft = Offset(cx - r * 0.09f, cy + r * 0.14f), size = Size(r * 0.18f, r * 0.12f))
    drawCuteSmile(cx, cy + r * 0.30f, r * 0.13f, r * 0.045f)
}

private fun DrawScope.drawFrog(cx: Float, cy: Float, r: Float) {
    val green = AvatarIllustrationColors.frogGreen
    val belly = AvatarIllustrationColors.frogBelly
    drawCircle(color = green, radius = r * 0.24f, center = Offset(cx - r * 0.30f, cy - r * 0.34f))
    drawCircle(color = green, radius = r * 0.24f, center = Offset(cx + r * 0.30f, cy - r * 0.34f))
    drawOval(color = green, topLeft = Offset(cx - r * 0.58f, cy - r * 0.28f), size = Size(r * 1.16f, r * 0.84f))
    drawOval(color = belly, topLeft = Offset(cx - r * 0.40f, cy + r * 0.16f), size = Size(r * 0.80f, r * 0.34f))
    drawEyes(cx, cy - r * 0.32f, r * 0.30f, r * 0.135f)
    drawRosyCheeks(cx, cy + r * 0.06f, r * 0.36f, r * 0.09f)
    drawCuteSmile(cx, cy + r * 0.10f, r * 0.24f, r * 0.055f)
}

private fun DrawScope.drawCat(cx: Float, cy: Float, r: Float) {
    val fur = AvatarIllustrationColors.catFur
    val innerEar = AvatarIllustrationColors.catInnerEar
    val stripe = AvatarIllustrationColors.catStripe

    val lEar = Path().apply {
        moveTo(cx - r * 0.48f, cy - r * 0.18f)
        lineTo(cx - r * 0.42f, cy - r * 0.66f)
        lineTo(cx - r * 0.08f, cy - r * 0.38f)
        close()
    }
    val rEar = Path().apply {
        moveTo(cx + r * 0.48f, cy - r * 0.18f)
        lineTo(cx + r * 0.42f, cy - r * 0.66f)
        lineTo(cx + r * 0.08f, cy - r * 0.38f)
        close()
    }
    drawPath(lEar, fur)
    drawPath(rEar, fur)
    drawCircle(color = innerEar, radius = r * 0.09f, center = Offset(cx - r * 0.32f, cy - r * 0.44f))
    drawCircle(color = innerEar, radius = r * 0.09f, center = Offset(cx + r * 0.32f, cy - r * 0.44f))

    drawOval(color = fur, topLeft = Offset(cx - r * 0.54f, cy - r * 0.40f), size = Size(r * 1.08f, r * 0.94f))
    for (dx in listOf(-0.12f, 0f, 0.12f)) {
        drawLine(stripe, Offset(cx + r * dx, cy - r * 0.36f), Offset(cx + r * dx, cy - r * 0.20f), r * 0.055f, StrokeCap.Round)
    }
    val whiskerStroke = r * 0.035f
    drawLine(stripe, Offset(cx - r * 0.32f, cy + r * 0.14f), Offset(cx - r * 0.62f, cy + r * 0.10f), whiskerStroke, StrokeCap.Round)
    drawLine(stripe, Offset(cx - r * 0.32f, cy + r * 0.22f), Offset(cx - r * 0.60f, cy + r * 0.26f), whiskerStroke, StrokeCap.Round)
    drawLine(stripe, Offset(cx + r * 0.32f, cy + r * 0.14f), Offset(cx + r * 0.62f, cy + r * 0.10f), whiskerStroke, StrokeCap.Round)
    drawLine(stripe, Offset(cx + r * 0.32f, cy + r * 0.22f), Offset(cx + r * 0.60f, cy + r * 0.26f), whiskerStroke, StrokeCap.Round)

    drawEyes(cx, cy - r * 0.02f, r * 0.21f, r * 0.11f, irisColor = AvatarIllustrationColors.catIris)
    drawRosyCheeks(cx, cy + r * 0.14f, r * 0.30f, r * 0.075f)
    drawCircle(color = innerEar, radius = r * 0.065f, center = Offset(cx, cy + r * 0.14f))
    drawCuteSmile(cx, cy + r * 0.25f, r * 0.13f, r * 0.045f)
}

private fun DrawScope.drawOwl(cx: Float, cy: Float, r: Float) {
    val feather = AvatarIllustrationColors.owlFeather
    val belly = AvatarIllustrationColors.owlBelly
    val ring = AvatarIllustrationColors.owlRing

    val lTuft = Path().apply {
        moveTo(cx - r * 0.44f, cy - r * 0.22f)
        lineTo(cx - r * 0.50f, cy - r * 0.62f)
        lineTo(cx - r * 0.18f, cy - r * 0.40f)
        close()
    }
    val rTuft = Path().apply {
        moveTo(cx + r * 0.44f, cy - r * 0.22f)
        lineTo(cx + r * 0.50f, cy - r * 0.62f)
        lineTo(cx + r * 0.18f, cy - r * 0.40f)
        close()
    }
    drawPath(lTuft, feather)
    drawPath(rTuft, feather)
    drawCircle(color = feather, radius = r * 0.54f, center = Offset(cx, cy + r * 0.04f))
    drawOval(color = belly, topLeft = Offset(cx - r * 0.32f, cy + r * 0.18f), size = Size(r * 0.64f, r * 0.36f))
    drawCircle(color = ring, radius = r * 0.23f, center = Offset(cx - r * 0.21f, cy - r * 0.04f))
    drawCircle(color = ring, radius = r * 0.23f, center = Offset(cx + r * 0.21f, cy - r * 0.04f))
    drawEyes(cx, cy - r * 0.04f, r * 0.21f, r * 0.125f, irisColor = AvatarIllustrationColors.owlIris)
    val beak = Path().apply {
        moveTo(cx - r * 0.09f, cy + r * 0.08f)
        lineTo(cx + r * 0.09f, cy + r * 0.08f)
        lineTo(cx, cy + r * 0.25f)
        close()
    }
    drawPath(beak, AvatarIllustrationColors.owlBeak)
}

private fun DrawScope.drawDragon(cx: Float, cy: Float, r: Float) {
    val scale = AvatarIllustrationColors.dragonScale
    val scaleDark = AvatarIllustrationColors.dragonScaleDark
    val horn = AvatarIllustrationColors.dragonHorn
    val belly = AvatarIllustrationColors.dragonBelly

    val lHorn = Path().apply {
        moveTo(cx - r * 0.36f, cy - r * 0.34f)
        lineTo(cx - r * 0.46f, cy - r * 0.72f)
        lineTo(cx - r * 0.16f, cy - r * 0.44f)
        close()
    }
    val rHorn = Path().apply {
        moveTo(cx + r * 0.36f, cy - r * 0.34f)
        lineTo(cx + r * 0.46f, cy - r * 0.72f)
        lineTo(cx + r * 0.16f, cy - r * 0.44f)
        close()
    }
    drawPath(lHorn, horn)
    drawPath(rHorn, horn)
    drawOval(color = scale, topLeft = Offset(cx - r * 0.52f, cy - r * 0.44f), size = Size(r * 1.04f, r * 0.94f))
    drawOval(color = belly, topLeft = Offset(cx - r * 0.38f, cy + r * 0.08f), size = Size(r * 0.76f, r * 0.42f))
    drawEyes(cx, cy - r * 0.06f, r * 0.21f, r * 0.11f, irisColor = AvatarIllustrationColors.dragonIris)
    drawCircle(color = scaleDark, radius = r * 0.045f, center = Offset(cx - r * 0.10f, cy + r * 0.18f))
    drawCircle(color = scaleDark, radius = r * 0.045f, center = Offset(cx + r * 0.10f, cy + r * 0.18f))
    drawCuteSmile(cx, cy + r * 0.30f, r * 0.16f, r * 0.048f)
}

private fun DrawScope.drawAstronaut(cx: Float, cy: Float, r: Float) {
    val suit = AvatarIllustrationColors.astroSuit
    val rim = AvatarIllustrationColors.astroRim
    val visorTop = AvatarIllustrationColors.astroVisorTop
    val visorBot = AvatarIllustrationColors.astroVisorBottom

    drawRoundRect(AvatarIllustrationColors.coral, Offset(cx - r * 0.64f, cy - r * 0.14f), Size(r * 0.16f, r * 0.34f), CornerRadius(r * 0.08f))
    drawRoundRect(AvatarIllustrationColors.coral, Offset(cx + r * 0.48f, cy - r * 0.14f), Size(r * 0.16f, r * 0.34f), CornerRadius(r * 0.08f))
    drawCircle(color = suit, radius = r * 0.56f, center = Offset(cx, cy + r * 0.02f))
    drawCircle(color = rim, radius = r * 0.56f, center = Offset(cx, cy + r * 0.02f), style = Stroke(width = r * 0.05f))
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(visorTop, visorBot)),
        topLeft = Offset(cx - r * 0.40f, cy - r * 0.26f),
        size = Size(r * 0.80f, r * 0.54f),
        cornerRadius = CornerRadius(r * 0.24f)
    )
    drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(cx - r * 0.32f, cy - r * 0.19f), Size(r * 0.26f, r * 0.12f), CornerRadius(r * 0.06f))
    drawMiniStar(Offset(cx + r * 0.18f, cy - r * 0.08f), r * 0.08f, r * 0.035f, AvatarIllustrationColors.gold)
    drawCircle(color = AvatarIllustrationColors.astroEye, radius = r * 0.065f, center = Offset(cx - r * 0.14f, cy + r * 0.06f))
    drawCircle(color = AvatarIllustrationColors.astroEye, radius = r * 0.065f, center = Offset(cx + r * 0.14f, cy + r * 0.06f))
}

private fun DrawScope.drawKnight(cx: Float, cy: Float, r: Float) {
    val steel = AvatarIllustrationColors.knightSteel
    val steelDark = AvatarIllustrationColors.knightSteelDark
    val plume = AvatarIllustrationColors.knightPlume

    drawOval(color = plume, topLeft = Offset(cx - r * 0.22f, cy - r * 0.72f), size = Size(r * 0.44f, r * 0.34f))
    drawRoundRect(steel, Offset(cx - r * 0.48f, cy - r * 0.46f), Size(r * 0.96f, r * 0.96f), CornerRadius(r * 0.36f))
    drawRect(AvatarIllustrationColors.gold, Offset(cx - r * 0.06f, cy - r * 0.46f), Size(r * 0.12f, r * 0.26f))
    drawRoundRect(AvatarIllustrationColors.ink, Offset(cx - r * 0.36f, cy - r * 0.12f), Size(r * 0.72f, r * 0.24f), CornerRadius(r * 0.10f))
    drawCircle(color = Color.White, radius = r * 0.065f, center = Offset(cx - r * 0.16f, cy))
    drawCircle(color = Color.White, radius = r * 0.065f, center = Offset(cx + r * 0.16f, cy))
    drawLine(steelDark, Offset(cx - r * 0.20f, cy + r * 0.28f), Offset(cx + r * 0.20f, cy + r * 0.28f), r * 0.045f, StrokeCap.Round)
    drawLine(steelDark, Offset(cx - r * 0.14f, cy + r * 0.38f), Offset(cx + r * 0.14f, cy + r * 0.38f), r * 0.045f, StrokeCap.Round)
}

private fun DrawScope.drawWizard(cx: Float, cy: Float, r: Float) {
    val hat = AvatarIllustrationColors.wizardHat
    val skin = AvatarIllustrationColors.wizardSkin
    val beard = AvatarIllustrationColors.wizardBeard

    drawCircle(color = skin, radius = r * 0.40f, center = Offset(cx, cy + r * 0.08f))
    val beardPath = Path().apply {
        moveTo(cx - r * 0.36f, cy + r * 0.18f)
        quadraticTo(cx, cy + r * 0.72f, cx + r * 0.36f, cy + r * 0.18f)
        close()
    }
    drawPath(beardPath, beard)
    drawEyes(cx, cy + r * 0.04f, r * 0.16f, r * 0.085f)
    drawRosyCheeks(cx, cy + r * 0.16f, r * 0.26f, r * 0.065f)
    drawOval(color = hat, topLeft = Offset(cx - r * 0.58f, cy - r * 0.26f), size = Size(r * 1.16f, r * 0.26f))
    val cone = Path().apply {
        moveTo(cx - r * 0.38f, cy - r * 0.16f)
        lineTo(cx + r * 0.08f, cy - r * 0.76f)
        lineTo(cx + r * 0.38f, cy - r * 0.16f)
        close()
    }
    drawPath(cone, hat)
    drawLine(AvatarIllustrationColors.gold, Offset(cx - r * 0.34f, cy - r * 0.18f), Offset(cx + r * 0.34f, cy - r * 0.18f), r * 0.07f, StrokeCap.Round)
    drawMiniStar(Offset(cx, cy - r * 0.40f), r * 0.11f, r * 0.05f, AvatarIllustrationColors.gold)
}

private fun DrawScope.drawNinja(cx: Float, cy: Float, r: Float) {
    val hood = AvatarIllustrationColors.ninjaHood
    val band = AvatarIllustrationColors.ninjaBand
    val skin = AvatarIllustrationColors.ninjaSkin

    drawLine(band, Offset(cx + r * 0.36f, cy - r * 0.22f), Offset(cx + r * 0.68f, cy - r * 0.36f), r * 0.10f, StrokeCap.Round)
    drawLine(band, Offset(cx + r * 0.38f, cy - r * 0.14f), Offset(cx + r * 0.64f, cy - r * 0.16f), r * 0.08f, StrokeCap.Round)
    drawCircle(color = hood, radius = r * 0.54f, center = Offset(cx, cy + r * 0.04f))
    drawRoundRect(band, Offset(cx - r * 0.52f, cy - r * 0.28f), Size(r * 1.04f, r * 0.16f), CornerRadius(r * 0.08f))
    drawRoundRect(skin, Offset(cx - r * 0.36f, cy - r * 0.10f), Size(r * 0.72f, r * 0.26f), CornerRadius(r * 0.12f))
    drawEyes(cx, cy + r * 0.03f, r * 0.18f, r * 0.09f)
}

private fun DrawScope.drawRobot(cx: Float, cy: Float, r: Float) {
    val body = AvatarIllustrationColors.robotBody
    val trim = AvatarIllustrationColors.robotTrim
    val screen = AvatarIllustrationColors.robotScreen
    val glow = AvatarIllustrationColors.robotGlow

    drawLine(trim, Offset(cx, cy - r * 0.42f), Offset(cx, cy - r * 0.64f), r * 0.07f, StrokeCap.Round)
    drawCircle(color = AvatarIllustrationColors.gold, radius = r * 0.10f, center = Offset(cx, cy - r * 0.68f))
    drawRoundRect(trim, Offset(cx - r * 0.62f, cy - r * 0.08f), Size(r * 0.14f, r * 0.26f), CornerRadius(r * 0.06f))
    drawRoundRect(trim, Offset(cx + r * 0.48f, cy - r * 0.08f), Size(r * 0.14f, r * 0.26f), CornerRadius(r * 0.06f))
    drawRoundRect(body, Offset(cx - r * 0.50f, cy - r * 0.42f), Size(r * 1.00f, r * 0.90f), CornerRadius(r * 0.26f))
    drawRoundRect(trim, Offset(cx - r * 0.50f, cy - r * 0.42f), Size(r * 1.00f, r * 0.90f), CornerRadius(r * 0.26f), style = Stroke(width = r * 0.045f))
    drawRoundRect(screen, Offset(cx - r * 0.38f, cy - r * 0.24f), Size(r * 0.76f, r * 0.46f), CornerRadius(r * 0.14f))
    drawCircle(color = glow, radius = r * 0.095f, center = Offset(cx - r * 0.17f, cy - r * 0.02f))
    drawCircle(color = glow, radius = r * 0.095f, center = Offset(cx + r * 0.17f, cy - r * 0.02f))
    val smile = Path().apply {
        moveTo(cx - r * 0.14f, cy + r * 0.12f)
        quadraticTo(cx, cy + r * 0.22f, cx + r * 0.14f, cy + r * 0.12f)
    }
    drawPath(smile, glow, style = Stroke(width = r * 0.045f, cap = StrokeCap.Round))
}

private fun DrawScope.drawCrownAccessory(cx: Float, cy: Float, r: Float) {
    val baseY = cy - r * 0.44f
    val crownPath = Path().apply {
        moveTo(cx - r * 0.30f, baseY)
        lineTo(cx - r * 0.34f, baseY - r * 0.28f)
        lineTo(cx - r * 0.14f, baseY - r * 0.14f)
        lineTo(cx, baseY - r * 0.34f)
        lineTo(cx + r * 0.14f, baseY - r * 0.14f)
        lineTo(cx + r * 0.34f, baseY - r * 0.28f)
        lineTo(cx + r * 0.30f, baseY)
        close()
    }
    drawPath(crownPath, AvatarIllustrationColors.gold)
    drawPath(crownPath, AvatarIllustrationColors.goldDark, style = Stroke(width = r * 0.035f, join = StrokeJoin.Round))
    drawCircle(color = AvatarIllustrationColors.coral, radius = r * 0.045f, center = Offset(cx, baseY - r * 0.10f))
}

private fun DrawScope.drawSunglassesAccessory(cx: Float, cy: Float, r: Float) {
    val ey = cy - r * 0.02f
    val lensW = r * 0.34f
    val lensH = r * 0.22f
    drawLine(AvatarIllustrationColors.ink, Offset(cx - r * 0.06f, ey - r * 0.02f), Offset(cx + r * 0.06f, ey - r * 0.02f), r * 0.055f, StrokeCap.Round)
    listOf(cx - r * 0.38f, cx + r * 0.04f).forEach { lx ->
        drawRoundRect(AvatarIllustrationColors.lensDark, Offset(lx, ey - lensH / 2f), Size(lensW, lensH), CornerRadius(r * 0.08f))
        drawRoundRect(Color.White, Offset(lx, ey - lensH / 2f), Size(lensW, lensH), CornerRadius(r * 0.08f), style = Stroke(width = r * 0.03f))
        drawLine(Color.White.copy(alpha = 0.55f), Offset(lx + lensW * 0.22f, ey - lensH * 0.28f), Offset(lx + lensW * 0.42f, ey + lensH * 0.24f), r * 0.035f, StrokeCap.Round)
    }
}

private fun DrawScope.drawStarPinAccessory(cx: Float, cy: Float, r: Float) {
    val pinCenter = Offset(cx + r * 0.44f, cy - r * 0.44f)
    drawCircle(color = Color.White, radius = r * 0.19f, center = pinCenter)
    drawMiniStar(pinCenter, r * 0.16f, r * 0.075f, AvatarIllustrationColors.gold)
}

private fun DrawScope.drawHaloAccessory(cx: Float, cy: Float, r: Float) {
    drawOval(AvatarIllustrationColors.gold.copy(alpha = 0.40f), Offset(cx - r * 0.40f, cy - r * 0.78f), Size(r * 0.80f, r * 0.24f), style = Stroke(width = r * 0.11f))
    drawOval(AvatarIllustrationColors.haloLight, Offset(cx - r * 0.38f, cy - r * 0.76f), Size(r * 0.76f, r * 0.20f), style = Stroke(width = r * 0.055f))
}

private fun DrawScope.drawHeadphonesAccessory(cx: Float, cy: Float, r: Float) {
    val bandColor = AvatarIllustrationColors.headphoneBand
    val cupColor = AvatarIllustrationColors.headphoneCup
    drawArc(bandColor, 195f, 150f, false, Offset(cx - r * 0.62f, cy - r * 0.64f), Size(r * 1.24f, r * 1.16f), style = Stroke(width = r * 0.09f, cap = StrokeCap.Round))
    drawRoundRect(cupColor, Offset(cx - r * 0.68f, cy - r * 0.16f), Size(r * 0.18f, r * 0.38f), CornerRadius(r * 0.09f))
    drawRoundRect(cupColor, Offset(cx + r * 0.50f, cy - r * 0.16f), Size(r * 0.18f, r * 0.38f), CornerRadius(r * 0.09f))
    drawCircle(color = bandColor, radius = r * 0.05f, center = Offset(cx - r * 0.59f, cy + r * 0.03f))
    drawCircle(color = bandColor, radius = r * 0.05f, center = Offset(cx + r * 0.59f, cy + r * 0.03f))
}

private fun DrawScope.drawAvatarFrame(
    frame: AvatarFrame,
    cx: Float,
    cy: Float,
    r: Float,
    accent: Color
) {
    val strokeW = (r * 0.10f).coerceAtLeast(2f)
    val ringR = r - strokeW / 2f
    when (frame) {
        AvatarFrame.CLASSIC -> {
            drawCircle(color = Color.White, radius = ringR, center = Offset(cx, cy), style = Stroke(width = strokeW))
        }
        AvatarFrame.GOLD_RING -> {
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(AvatarIllustrationColors.goldLight, AvatarIllustrationColors.gold, AvatarIllustrationColors.goldDark, AvatarIllustrationColors.gold),
                    start = Offset(cx - r, cy - r),
                    end = Offset(cx + r, cy + r)
                ),
                radius = ringR,
                center = Offset(cx, cy),
                style = Stroke(width = strokeW * 1.15f)
            )
        }
        AvatarFrame.DASHED -> {
            drawCircle(
                color = Color.White,
                radius = ringR,
                center = Offset(cx, cy),
                style = Stroke(
                    width = strokeW,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.24f, r * 0.16f), 0f)
                )
            )
        }
        AvatarFrame.STAR_HALO -> {
            drawCircle(color = AvatarIllustrationColors.gold, radius = ringR, center = Offset(cx, cy), style = Stroke(width = strokeW * 0.9f))
            for (i in 0 until 4) {
                val angle = (i * 90.0 + 45.0) * PI / 180.0
                val sx = cx + (cos(angle) * ringR).toFloat()
                val sy = cy + (sin(angle) * ringR).toFloat()
                drawMiniStar(Offset(sx, sy), r * 0.12f, r * 0.055f, Color.White)
            }
        }
        AvatarFrame.NEON_RING -> {
            drawCircle(color = AvatarIllustrationColors.neonOuter.copy(alpha = 0.45f), radius = ringR, center = Offset(cx, cy), style = Stroke(width = strokeW * 1.45f))
            drawCircle(color = AvatarIllustrationColors.neonInner, radius = ringR, center = Offset(cx, cy), style = Stroke(width = strokeW * 0.72f))
        }
        AvatarFrame.DOUBLE_RIM -> {
            drawCircle(color = Color.White, radius = ringR, center = Offset(cx, cy), style = Stroke(width = strokeW * 0.65f))
            drawCircle(color = accent, radius = ringR - strokeW * 0.85f, center = Offset(cx, cy), style = Stroke(width = strokeW * 0.45f))
        }
    }
}

private fun DrawScope.drawMiniStar(
    center: Offset,
    outerR: Float,
    innerR: Float,
    color: Color
) {
    val path = Path()
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) outerR else innerR
        val angle = (i * PI / 5.0) - PI / 2.0
        val px = center.x + (rad * cos(angle)).toFloat()
        val py = center.y + (rad * sin(angle)).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color)
}
