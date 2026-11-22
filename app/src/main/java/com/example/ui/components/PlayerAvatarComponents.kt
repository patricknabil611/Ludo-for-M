package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.example.ui.theme.CobaltDark
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralPink
import com.example.ui.theme.InkDark
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenDark
import com.example.ui.theme.OutlineSoft
import com.example.ui.theme.SkyTopTint
import com.example.ui.theme.SunnyGold
import com.example.ui.theme.SunnyGoldDark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class BackdropColors(
    val top: Color,
    val bottom: Color,
    val accent: Color
)

private fun AvatarBackground.colors(): BackdropColors = when (this) {
    AvatarBackground.COBALT -> BackdropColors(Color(0xFF42A5F5), Color(0xFF1565C0), Color(0xFFBBDEFB))
    AvatarBackground.SUNSET -> BackdropColors(Color(0xFFFFB74D), Color(0xFFE65100), Color(0xFFFFE0B2))
    AvatarBackground.MINT -> BackdropColors(Color(0xFF4DD0E1), Color(0xFF00897B), Color(0xFFB2DFDB))
    AvatarBackground.BERRY -> BackdropColors(Color(0xFFEC407A), Color(0xFF880E4F), Color(0xFFF8BBD0))
    AvatarBackground.ROYAL -> BackdropColors(Color(0xFF7E57C2), Color(0xFF311B92), Color(0xFFD1C4E9))
    AvatarBackground.OCEAN -> BackdropColors(Color(0xFF26C6DA), Color(0xFF006064), Color(0xFFB2EBF2))
    AvatarBackground.GOLD -> BackdropColors(Color(0xFFFFEE58), Color(0xFFF57F17), Color(0xFFFFF9C4))
    AvatarBackground.CORAL -> BackdropColors(Color(0xFFFF7043), Color(0xFFC62828), Color(0xFFFFCCBC))
}

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
                color = SunnyGold,
                border = BorderStroke(1.5.dp, Color.White),
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = InkDark,
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

/**
 * Full-featured, playful Avatar Studio dialog allowing users to pick a curated preset
 * or mix & match Character, Backdrop, Frame, and Accessory with live preview + randomize.
 */
@Composable
fun AvatarCustomizerDialog(
    initialAvatar: PlayerAvatarConfig,
    title: String = stringResource(R.string.avatar_studio_title),
    subtitle: String? = null,
    onDismiss: () -> Unit,
    onSave: (PlayerAvatarConfig) -> Unit
) {
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
                .testTag("avatar_customizer_dialog"),
            shape = RoundedCornerShape(30.dp),
            color = Color.White,
            border = BorderStroke(3.dp, CobaltPrimary),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header & Live Spotlight Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(SkyTopTint, Color(0xFFF3F8FF), Color.White)
                            )
                        )
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
                                    color = InkDark,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (!subtitle.isNullOrBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkMuted,
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
                                    tint = InkMuted
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
                                    color = InkDark,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${draft.background.displayName} • ${draft.frame.displayName}" +
                                        if (draft.accessory != AvatarAccessory.NONE) " • ${draft.accessory.displayName}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    onClick = { updateDraft(PlayerAvatarCodec.randomize()) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = SunnyGold,
                                    border = BorderStroke(1.5.dp, SunnyGoldDark),
                                    modifier = Modifier
                                        .heightIn(min = 42.dp)
                                        .testTag("avatar_randomize_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Casino,
                                            contentDescription = null,
                                            tint = InkDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.avatar_randomize),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = InkDark,
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
                        .padding(horizontal = 14.dp, vertical = 4.dp),
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
                            color = if (active) CobaltPrimary else Color(0xFFF3F6FB),
                            border = BorderStroke(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) CobaltDark else OutlineSoft
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
                                    tint = if (active) Color.White else InkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(tab.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) Color.White else InkDark,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Scrollable Customization Content Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 330.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        CustomizerTab.PRESETS -> {
                            PresetsGrid(
                                current = draft,
                                onSelect = ::updateDraft
                            )
                        }
                        CustomizerTab.HERO -> {
                            CharactersGrid(
                                current = draft,
                                onSelect = { updateDraft(draft.copy(character = it)) }
                            )
                        }
                        CustomizerTab.STYLE -> {
                            BackdropAndFrameSelector(
                                current = draft,
                                onSelectBackground = { updateDraft(draft.copy(background = it)) },
                                onSelectFrame = { updateDraft(draft.copy(frame = it)) }
                            )
                        }
                        CustomizerTab.FLAIR -> {
                            AccessoryGrid(
                                current = draft,
                                onSelect = { updateDraft(draft.copy(accessory = it)) }
                            )
                        }
                    }
                }

                // Footer Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFE))
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
                            color = InkMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    CartoonButton(
                        text = stringResource(R.string.avatar_save_cta),
                        onClick = { onSave(draft) },
                        containerColor = MintGreen,
                        lipColor = MintGreenDark,
                        contentColor = Color.White,
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
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) CobaltPrimary.copy(alpha = 0.14f) else Color(0xFFF8FAFE),
                        border = BorderStroke(
                            width = if (selected) 2.5.dp else 1.5.dp,
                            color = if (selected) CobaltPrimary else OutlineSoft
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
                                    color = InkDark,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = preset.config.character.tagline,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted,
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
                        color = if (selected) CobaltPrimary.copy(alpha = 0.14f) else Color(0xFFF8FAFE),
                        border = BorderStroke(
                            width = if (selected) 2.5.dp else 1.2.dp,
                            color = if (selected) CobaltPrimary else OutlineSoft
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
                                color = if (selected) CobaltDark else InkDark,
                                fontWeight = FontWeight.ExtraBold,
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
    Text(
        text = stringResource(R.string.avatar_section_backdrop),
        style = MaterialTheme.typography.labelLarge,
        color = InkDark,
        fontWeight = FontWeight.ExtraBold
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
                    val palette = bg.colors()
                    Surface(
                        onClick = { onSelectBackground(bg) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("avatar_bg_${bg.name.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) CobaltPrimary.copy(alpha = 0.14f) else Color(0xFFF8FAFE),
                        border = BorderStroke(
                            width = if (selected) 2.5.dp else 1.2.dp,
                            color = if (selected) CobaltPrimary else OutlineSoft
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
                                color = InkDark,
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
        color = InkDark,
        fontWeight = FontWeight.ExtraBold
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
                        color = if (selected) CobaltPrimary.copy(alpha = 0.14f) else Color(0xFFF8FAFE),
                        border = BorderStroke(
                            width = if (selected) 2.5.dp else 1.2.dp,
                            color = if (selected) CobaltPrimary else OutlineSoft
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
                                color = InkDark,
                                fontWeight = FontWeight.ExtraBold,
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
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) CobaltPrimary.copy(alpha = 0.14f) else Color(0xFFF8FAFE),
                        border = BorderStroke(
                            width = if (selected) 2.5.dp else 1.2.dp,
                            color = if (selected) CobaltPrimary else OutlineSoft
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
                                color = if (selected) CobaltDark else InkDark,
                                fontWeight = FontWeight.ExtraBold,
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
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val r = size.minDimension / 2f
    val innerR = r * 0.90f
    val bg = avatar.background.colors()

    // 1. Vibrant gradient backdrop disc
    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(bg.top, bg.bottom),
            startY = cy - innerR,
            endY = cy + innerR
        ),
        radius = innerR,
        center = Offset(cx, cy)
    )

    // Soft glossy highlight bubble & decorative dots on backdrop
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

    // 2. Character portrait
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

    // 3. Optional Accessory / Flair
    when (avatar.accessory) {
        AvatarAccessory.NONE -> Unit
        AvatarAccessory.CROWN -> drawCrownAccessory(cx, cy, innerR)
        AvatarAccessory.SUNGLASSES -> drawSunglassesAccessory(cx, cy, innerR)
        AvatarAccessory.STAR_PIN -> drawStarPinAccessory(cx, cy, innerR)
        AvatarAccessory.HALO -> drawHaloAccessory(cx, cy, innerR)
        AvatarAccessory.HEADPHONES -> drawHeadphonesAccessory(cx, cy, innerR)
    }

    // 4. Optional dimmed veil (for eliminated players in Elimination mode)
    if (dimmed) {
        drawCircle(
            color = Color(0x99202635),
            radius = innerR,
            center = Offset(cx, cy)
        )
    }

    // 5. Outer decorative frame ring
    drawAvatarFrame(avatar.frame, cx, cy, r, bg.accent)
}

private fun DrawScope.drawEyes(
    cx: Float,
    ey: Float,
    spacing: Float,
    eyeR: Float,
    irisColor: Color = InkDark
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
    drawCircle(color = Color(0x55FF5252), radius = r, center = Offset(cx - spacing, y))
    drawCircle(color = Color(0x55FF5252), radius = r, center = Offset(cx + spacing, y))
}

private fun DrawScope.drawCuteSmile(cx: Float, y: Float, halfWidth: Float, strokeW: Float) {
    val smile = Path().apply {
        moveTo(cx - halfWidth, y)
        quadraticTo(cx, y + halfWidth * 0.85f, cx + halfWidth, y)
    }
    drawPath(
        path = smile,
        color = InkDark,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawFox(cx: Float, cy: Float, r: Float) {
    val fur = Color(0xFFFF7043)
    val furDark = Color(0xFFD84315)
    val cream = Color(0xFFFFF3E0)

    // Pointy ears
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
    // Inner ear cream
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

    // Head
    drawOval(
        color = fur,
        topLeft = Offset(cx - r * 0.56f, cy - r * 0.42f),
        size = Size(r * 1.12f, r * 0.96f)
    )
    // Cream cheeks & snout
    drawOval(
        color = cream,
        topLeft = Offset(cx - r * 0.48f, cy + r * 0.02f),
        size = Size(r * 0.96f, r * 0.50f)
    )
    // Eyes & nose
    drawEyes(cx, cy - r * 0.02f, r * 0.22f, r * 0.11f)
    drawRosyCheeks(cx, cy + r * 0.16f, r * 0.32f, r * 0.08f)
    drawOval(
        color = InkDark,
        topLeft = Offset(cx - r * 0.09f, cy + r * 0.14f),
        size = Size(r * 0.18f, r * 0.13f)
    )
    drawCuteSmile(cx, cy + r * 0.30f, r * 0.14f, r * 0.045f)
}

private fun DrawScope.drawLion(cx: Float, cy: Float, r: Float) {
    val mane = Color(0xFFD84315)
    val maneLight = Color(0xFFEF6C00)
    val goldFur = Color(0xFFFFCA28)
    val snout = Color(0xFFFFF8E1)

    // Fluffy scalloped mane
    val petals = 12
    for (i in 0 until petals) {
        val a = (i * 2.0 * PI / petals)
        val px = cx + (cos(a) * r * 0.48f).toFloat()
        val py = cy + r * 0.04f + (sin(a) * r * 0.48f).toFloat()
        drawCircle(color = if (i % 2 == 0) mane else maneLight, radius = r * 0.22f, center = Offset(px, py))
    }
    // Round ears
    drawCircle(color = goldFur, radius = r * 0.16f, center = Offset(cx - r * 0.38f, cy - r * 0.34f))
    drawCircle(color = goldFur, radius = r * 0.16f, center = Offset(cx + r * 0.38f, cy - r * 0.34f))
    // Face
    drawCircle(color = goldFur, radius = r * 0.46f, center = Offset(cx, cy + r * 0.05f))
    // Snout
    drawOval(
        color = snout,
        topLeft = Offset(cx - r * 0.24f, cy + r * 0.08f),
        size = Size(r * 0.48f, r * 0.32f)
    )
    drawEyes(cx, cy - r * 0.04f, r * 0.19f, r * 0.10f)
    drawRosyCheeks(cx, cy + r * 0.14f, r * 0.30f, r * 0.075f)
    // Triangular nose
    val nose = Path().apply {
        moveTo(cx - r * 0.09f, cy + r * 0.12f)
        lineTo(cx + r * 0.09f, cy + r * 0.12f)
        lineTo(cx, cy + r * 0.23f)
        close()
    }
    drawPath(nose, Color(0xFF5D4037))
    drawCuteSmile(cx, cy + r * 0.27f, r * 0.13f, r * 0.045f)
}

private fun DrawScope.drawPanda(cx: Float, cy: Float, r: Float) {
    val dark = Color(0xFF263238)
    // Round dark ears
    drawCircle(color = dark, radius = r * 0.21f, center = Offset(cx - r * 0.40f, cy - r * 0.40f))
    drawCircle(color = dark, radius = r * 0.21f, center = Offset(cx + r * 0.40f, cy - r * 0.40f))
    // White face
    drawOval(
        color = Color(0xFFFAFAFA),
        topLeft = Offset(cx - r * 0.54f, cy - r * 0.44f),
        size = Size(r * 1.08f, r * 0.98f)
    )
    // Signature eye patches
    drawOval(
        color = dark,
        topLeft = Offset(cx - r * 0.40f, cy - r * 0.18f),
        size = Size(r * 0.32f, r * 0.34f)
    )
    drawOval(
        color = dark,
        topLeft = Offset(cx + r * 0.08f, cy - r * 0.18f),
        size = Size(r * 0.32f, r * 0.34f)
    )
    drawEyes(cx, cy - r * 0.01f, r * 0.23f, r * 0.085f)
    drawRosyCheeks(cx, cy + r * 0.20f, r * 0.32f, r * 0.08f)
    drawOval(
        color = dark,
        topLeft = Offset(cx - r * 0.09f, cy + r * 0.14f),
        size = Size(r * 0.18f, r * 0.12f)
    )
    drawCuteSmile(cx, cy + r * 0.30f, r * 0.13f, r * 0.045f)
}

private fun DrawScope.drawFrog(cx: Float, cy: Float, r: Float) {
    val green = Color(0xFF43A047)
    val belly = Color(0xFFC8E6C9)
    // Big perched frog eye domes
    drawCircle(color = green, radius = r * 0.24f, center = Offset(cx - r * 0.30f, cy - r * 0.34f))
    drawCircle(color = green, radius = r * 0.24f, center = Offset(cx + r * 0.30f, cy - r * 0.34f))
    // Wide smiling head
    drawOval(
        color = green,
        topLeft = Offset(cx - r * 0.58f, cy - r * 0.28f),
        size = Size(r * 1.16f, r * 0.84f)
    )
    // Light chin
    drawOval(
        color = belly,
        topLeft = Offset(cx - r * 0.40f, cy + r * 0.16f),
        size = Size(r * 0.80f, r * 0.34f)
    )
    drawEyes(cx, cy - r * 0.32f, r * 0.30f, r * 0.135f)
    drawRosyCheeks(cx, cy + r * 0.06f, r * 0.36f, r * 0.09f)
    drawCuteSmile(cx, cy + r * 0.10f, r * 0.24f, r * 0.055f)
}

private fun DrawScope.drawCat(cx: Float, cy: Float, r: Float) {
    val fur = Color(0xFF90A4AE)
    val innerEar = Color(0xFFFFAB91)
    val stripe = Color(0xFF546E7A)

    // Ears
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

    // Round face
    drawOval(
        color = fur,
        topLeft = Offset(cx - r * 0.54f, cy - r * 0.40f),
        size = Size(r * 1.08f, r * 0.94f)
    )
    // Forehead tabby stripes
    for (dx in listOf(-0.12f, 0f, 0.12f)) {
        drawLine(
            color = stripe,
            start = Offset(cx + r * dx, cy - r * 0.36f),
            end = Offset(cx + r * dx, cy - r * 0.20f),
            strokeWidth = r * 0.055f,
            cap = StrokeCap.Round
        )
    }
    // Whiskers
    val whiskerStroke = r * 0.035f
    drawLine(stripe, Offset(cx - r * 0.32f, cy + r * 0.14f), Offset(cx - r * 0.62f, cy + r * 0.10f), whiskerStroke, StrokeCap.Round)
    drawLine(stripe, Offset(cx - r * 0.32f, cy + r * 0.22f), Offset(cx - r * 0.60f, cy + r * 0.26f), whiskerStroke, StrokeCap.Round)
    drawLine(stripe, Offset(cx + r * 0.32f, cy + r * 0.14f), Offset(cx + r * 0.62f, cy + r * 0.10f), whiskerStroke, StrokeCap.Round)
    drawLine(stripe, Offset(cx + r * 0.32f, cy + r * 0.22f), Offset(cx + r * 0.60f, cy + r * 0.26f), whiskerStroke, StrokeCap.Round)

    drawEyes(cx, cy - r * 0.02f, r * 0.21f, r * 0.11f, irisColor = Color(0xFF2E7D32))
    drawRosyCheeks(cx, cy + r * 0.14f, r * 0.30f, r * 0.075f)
    drawCircle(color = innerEar, radius = r * 0.065f, center = Offset(cx, cy + r * 0.14f))
    drawCuteSmile(cx, cy + r * 0.25f, r * 0.13f, r * 0.045f)
}

private fun DrawScope.drawOwl(cx: Float, cy: Float, r: Float) {
    val feather = Color(0xFF6D4C41)
    val belly = Color(0xFFFFECB3)
    val ring = Color(0xFFD7CCC8)

    // Ear tufts
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

    // Round body-head
    drawCircle(color = feather, radius = r * 0.54f, center = Offset(cx, cy + r * 0.04f))
    // Belly feather patch
    drawOval(
        color = belly,
        topLeft = Offset(cx - r * 0.32f, cy + r * 0.18f),
        size = Size(r * 0.64f, r * 0.36f)
    )
    // Large owl spectacle discs
    drawCircle(color = ring, radius = r * 0.23f, center = Offset(cx - r * 0.21f, cy - r * 0.04f))
    drawCircle(color = ring, radius = r * 0.23f, center = Offset(cx + r * 0.21f, cy - r * 0.04f))
    drawEyes(cx, cy - r * 0.04f, r * 0.21f, r * 0.125f, irisColor = Color(0xFFF57F17))
    // Beak
    val beak = Path().apply {
        moveTo(cx - r * 0.09f, cy + r * 0.08f)
        lineTo(cx + r * 0.09f, cy + r * 0.08f)
        lineTo(cx, cy + r * 0.25f)
        close()
    }
    drawPath(beak, Color(0xFFFFB300))
}

private fun DrawScope.drawDragon(cx: Float, cy: Float, r: Float) {
    val scale = Color(0xFF00ACC1)
    val scaleDark = Color(0xFF006064)
    val horn = Color(0xFFFFCA28)
    val belly = Color(0xFFB2EBF2)

    // Golden dragon horns
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

    // Head + jaw
    drawOval(
        color = scale,
        topLeft = Offset(cx - r * 0.52f, cy - r * 0.44f),
        size = Size(r * 1.04f, r * 0.94f)
    )
    drawOval(
        color = belly,
        topLeft = Offset(cx - r * 0.38f, cy + r * 0.08f),
        size = Size(r * 0.76f, r * 0.42f)
    )
    // Cute nostrils & smile
    drawEyes(cx, cy - r * 0.06f, r * 0.21f, r * 0.11f, irisColor = Color(0xFFD84315))
    drawCircle(color = scaleDark, radius = r * 0.045f, center = Offset(cx - r * 0.10f, cy + r * 0.18f))
    drawCircle(color = scaleDark, radius = r * 0.045f, center = Offset(cx + r * 0.10f, cy + r * 0.18f))
    drawCuteSmile(cx, cy + r * 0.30f, r * 0.16f, r * 0.048f)
}

private fun DrawScope.drawAstronaut(cx: Float, cy: Float, r: Float) {
    val suit = Color(0xFFF5F7FA)
    val rim = Color(0xFFB0BEC5)
    val visorTop = Color(0xFF1A237E)
    val visorBot = Color(0xFF3949AB)

    // Ear pods
    drawRoundRect(
        color = CoralPink,
        topLeft = Offset(cx - r * 0.64f, cy - r * 0.14f),
        size = Size(r * 0.16f, r * 0.34f),
        cornerRadius = CornerRadius(r * 0.08f, r * 0.08f)
    )
    drawRoundRect(
        color = CoralPink,
        topLeft = Offset(cx + r * 0.48f, cy - r * 0.14f),
        size = Size(r * 0.16f, r * 0.34f),
        cornerRadius = CornerRadius(r * 0.08f, r * 0.08f)
    )
    // Helmet shell
    drawCircle(color = suit, radius = r * 0.56f, center = Offset(cx, cy + r * 0.02f))
    drawCircle(
        color = rim,
        radius = r * 0.56f,
        center = Offset(cx, cy + r * 0.02f),
        style = Stroke(width = r * 0.05f)
    )
    // Reflective space visor
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(visorTop, visorBot)),
        topLeft = Offset(cx - r * 0.40f, cy - r * 0.26f),
        size = Size(r * 0.80f, r * 0.54f),
        cornerRadius = CornerRadius(r * 0.24f, r * 0.24f)
    )
    // Glossy visor gleam & tiny reflected star
    drawRoundRect(
        color = Color.White.copy(alpha = 0.28f),
        topLeft = Offset(cx - r * 0.32f, cy - r * 0.19f),
        size = Size(r * 0.26f, r * 0.12f),
        cornerRadius = CornerRadius(r * 0.06f, r * 0.06f)
    )
    drawMiniStar(Offset(cx + r * 0.18f, cy - r * 0.08f), r * 0.08f, r * 0.035f, SunnyGold)
    // Friendly glowing eyes inside visor
    drawCircle(color = Color(0xFF80DEEA), radius = r * 0.065f, center = Offset(cx - r * 0.14f, cy + r * 0.06f))
    drawCircle(color = Color(0xFF80DEEA), radius = r * 0.065f, center = Offset(cx + r * 0.14f, cy + r * 0.06f))
}

private fun DrawScope.drawKnight(cx: Float, cy: Float, r: Float) {
    val steel = Color(0xFFCFD8DC)
    val steelDark = Color(0xFF78909C)
    val plume = Color(0xFFE53935)

    // Red helmet plume
    drawOval(
        color = plume,
        topLeft = Offset(cx - r * 0.22f, cy - r * 0.72f),
        size = Size(r * 0.44f, r * 0.34f)
    )
    // Steel helmet
    drawRoundRect(
        color = steel,
        topLeft = Offset(cx - r * 0.48f, cy - r * 0.46f),
        size = Size(r * 0.96f, r * 0.96f),
        cornerRadius = CornerRadius(r * 0.36f, r * 0.36f)
    )
    // Gold center crest
    drawRect(
        color = SunnyGold,
        topLeft = Offset(cx - r * 0.06f, cy - r * 0.46f),
        size = Size(r * 0.12f, r * 0.26f)
    )
    // Visor slit
    drawRoundRect(
        color = InkDark,
        topLeft = Offset(cx - r * 0.36f, cy - r * 0.12f),
        size = Size(r * 0.72f, r * 0.24f),
        cornerRadius = CornerRadius(r * 0.10f, r * 0.10f)
    )
    // Determined eyes inside visor
    drawCircle(color = Color.White, radius = r * 0.065f, center = Offset(cx - r * 0.16f, cy))
    drawCircle(color = Color.White, radius = r * 0.065f, center = Offset(cx + r * 0.16f, cy))
    // Lower face guard vents
    drawLine(steelDark, Offset(cx - r * 0.20f, cy + r * 0.28f), Offset(cx + r * 0.20f, cy + r * 0.28f), r * 0.045f, StrokeCap.Round)
    drawLine(steelDark, Offset(cx - r * 0.14f, cy + r * 0.38f), Offset(cx + r * 0.14f, cy + r * 0.38f), r * 0.045f, StrokeCap.Round)
}

private fun DrawScope.drawWizard(cx: Float, cy: Float, r: Float) {
    val hat = Color(0xFF5E35B1)
    val skin = Color(0xFFFFCCBC)
    val beard = Color(0xFFF5F5F5)

    // Face
    drawCircle(color = skin, radius = r * 0.40f, center = Offset(cx, cy + r * 0.08f))
    // Fluffy white beard
    val beardPath = Path().apply {
        moveTo(cx - r * 0.36f, cy + r * 0.18f)
        quadraticTo(cx, cy + r * 0.72f, cx + r * 0.36f, cy + r * 0.18f)
        close()
    }
    drawPath(beardPath, beard)
    drawEyes(cx, cy + r * 0.04f, r * 0.16f, r * 0.085f)
    drawRosyCheeks(cx, cy + r * 0.16f, r * 0.26f, r * 0.065f)

    // Wizard hat brim & pointed cone
    drawOval(
        color = hat,
        topLeft = Offset(cx - r * 0.58f, cy - r * 0.26f),
        size = Size(r * 1.16f, r * 0.26f)
    )
    val cone = Path().apply {
        moveTo(cx - r * 0.38f, cy - r * 0.16f)
        lineTo(cx + r * 0.08f, cy - r * 0.76f)
        lineTo(cx + r * 0.38f, cy - r * 0.16f)
        close()
    }
    drawPath(cone, hat)
    // Gold hat band & star
    drawLine(
        color = SunnyGold,
        start = Offset(cx - r * 0.34f, cy - r * 0.18f),
        end = Offset(cx + r * 0.34f, cy - r * 0.18f),
        strokeWidth = r * 0.07f,
        cap = StrokeCap.Round
    )
    drawMiniStar(Offset(cx, cy - r * 0.40f), r * 0.11f, r * 0.05f, SunnyGold)
}

private fun DrawScope.drawNinja(cx: Float, cy: Float, r: Float) {
    val hood = Color(0xFF263238)
    val band = Color(0xFFE53935)
    val skin = Color(0xFFFFD180)

    // Trailing headband tails
    drawLine(band, Offset(cx + r * 0.36f, cy - r * 0.22f), Offset(cx + r * 0.68f, cy - r * 0.36f), r * 0.10f, StrokeCap.Round)
    drawLine(band, Offset(cx + r * 0.38f, cy - r * 0.14f), Offset(cx + r * 0.64f, cy - r * 0.16f), r * 0.08f, StrokeCap.Round)

    // Ninja hood
    drawCircle(color = hood, radius = r * 0.54f, center = Offset(cx, cy + r * 0.04f))
    // Red headband
    drawRoundRect(
        color = band,
        topLeft = Offset(cx - r * 0.52f, cy - r * 0.28f),
        size = Size(r * 1.04f, r * 0.16f),
        cornerRadius = CornerRadius(r * 0.08f, r * 0.08f)
    )
    // Face opening
    drawRoundRect(
        color = skin,
        topLeft = Offset(cx - r * 0.36f, cy - r * 0.10f),
        size = Size(r * 0.72f, r * 0.26f),
        cornerRadius = CornerRadius(r * 0.12f, r * 0.12f)
    )
    drawEyes(cx, cy + r * 0.03f, r * 0.18f, r * 0.09f)
}

private fun DrawScope.drawRobot(cx: Float, cy: Float, r: Float) {
    val body = Color(0xFFECEFF1)
    val trim = Color(0xFF607D8B)
    val screen = Color(0xFF1A237E)
    val glow = Color(0xFF00E5FF)

    // Antenna
    drawLine(
        color = trim,
        start = Offset(cx, cy - r * 0.42f),
        end = Offset(cx, cy - r * 0.64f),
        strokeWidth = r * 0.07f,
        cap = StrokeCap.Round
    )
    drawCircle(color = SunnyGold, radius = r * 0.10f, center = Offset(cx, cy - r * 0.68f))
    // Side ear bolts
    drawRoundRect(
        color = trim,
        topLeft = Offset(cx - r * 0.62f, cy - r * 0.08f),
        size = Size(r * 0.14f, r * 0.26f),
        cornerRadius = CornerRadius(r * 0.06f, r * 0.06f)
    )
    drawRoundRect(
        color = trim,
        topLeft = Offset(cx + r * 0.48f, cy - r * 0.08f),
        size = Size(r * 0.14f, r * 0.26f),
        cornerRadius = CornerRadius(r * 0.06f, r * 0.06f)
    )
    // Rounded robot head
    drawRoundRect(
        color = body,
        topLeft = Offset(cx - r * 0.50f, cy - r * 0.42f),
        size = Size(r * 1.00f, r * 0.90f),
        cornerRadius = CornerRadius(r * 0.26f, r * 0.26f)
    )
    drawRoundRect(
        color = trim,
        topLeft = Offset(cx - r * 0.50f, cy - r * 0.42f),
        size = Size(r * 1.00f, r * 0.90f),
        cornerRadius = CornerRadius(r * 0.26f, r * 0.26f),
        style = Stroke(width = r * 0.045f)
    )
    // LED face screen
    drawRoundRect(
        color = screen,
        topLeft = Offset(cx - r * 0.38f, cy - r * 0.24f),
        size = Size(r * 0.76f, r * 0.46f),
        cornerRadius = CornerRadius(r * 0.14f, r * 0.14f)
    )
    // Glowing cyan eyes & digital smile
    drawCircle(color = glow, radius = r * 0.095f, center = Offset(cx - r * 0.17f, cy - r * 0.02f))
    drawCircle(color = glow, radius = r * 0.095f, center = Offset(cx + r * 0.17f, cy - r * 0.02f))
    val smile = Path().apply {
        moveTo(cx - r * 0.14f, cy + r * 0.12f)
        quadraticTo(cx, cy + r * 0.22f, cx + r * 0.14f, cy + r * 0.12f)
    }
    drawPath(smile, glow, style = Stroke(width = r * 0.045f, cap = StrokeCap.Round))
}

// =========================================================================
// Accessories
// =========================================================================

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
    drawPath(crownPath, SunnyGold)
    drawPath(crownPath, SunnyGoldDark, style = Stroke(width = r * 0.035f, join = StrokeJoin.Round))
    drawCircle(color = CoralPink, radius = r * 0.045f, center = Offset(cx, baseY - r * 0.10f))
}

private fun DrawScope.drawSunglassesAccessory(cx: Float, cy: Float, r: Float) {
    val ey = cy - r * 0.02f
    val lensW = r * 0.34f
    val lensH = r * 0.22f
    // Bridge
    drawLine(
        color = InkDark,
        start = Offset(cx - r * 0.06f, ey - r * 0.02f),
        end = Offset(cx + r * 0.06f, ey - r * 0.02f),
        strokeWidth = r * 0.055f,
        cap = StrokeCap.Round
    )
    // Left & right lenses
    listOf(cx - r * 0.38f, cx + r * 0.04f).forEach { lx ->
        drawRoundRect(
            color = Color(0xFF1E2430),
            topLeft = Offset(lx, ey - lensH / 2f),
            size = Size(lensW, lensH),
            cornerRadius = CornerRadius(r * 0.08f, r * 0.08f)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(lx, ey - lensH / 2f),
            size = Size(lensW, lensH),
            cornerRadius = CornerRadius(r * 0.08f, r * 0.08f),
            style = Stroke(width = r * 0.03f)
        )
        // Diagonal lens shine
        drawLine(
            color = Color.White.copy(alpha = 0.55f),
            start = Offset(lx + lensW * 0.22f, ey - lensH * 0.28f),
            end = Offset(lx + lensW * 0.42f, ey + lensH * 0.24f),
            strokeWidth = r * 0.035f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawStarPinAccessory(cx: Float, cy: Float, r: Float) {
    val pinCenter = Offset(cx + r * 0.44f, cy - r * 0.44f)
    drawCircle(color = Color.White, radius = r * 0.19f, center = pinCenter)
    drawMiniStar(pinCenter, r * 0.16f, r * 0.075f, SunnyGold)
}

private fun DrawScope.drawHaloAccessory(cx: Float, cy: Float, r: Float) {
    drawOval(
        color = SunnyGold.copy(alpha = 0.40f),
        topLeft = Offset(cx - r * 0.40f, cy - r * 0.78f),
        size = Size(r * 0.80f, r * 0.24f),
        style = Stroke(width = r * 0.11f)
    )
    drawOval(
        color = Color(0xFFFFF59D),
        topLeft = Offset(cx - r * 0.38f, cy - r * 0.76f),
        size = Size(r * 0.76f, r * 0.20f),
        style = Stroke(width = r * 0.055f)
    )
}

private fun DrawScope.drawHeadphonesAccessory(cx: Float, cy: Float, r: Float) {
    val bandColor = Color(0xFFFF4081)
    val cupColor = Color(0xFF263238)
    // Arch over top of head
    drawArc(
        color = bandColor,
        startAngle = 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(cx - r * 0.62f, cy - r * 0.64f),
        size = Size(r * 1.24f, r * 1.16f),
        style = Stroke(width = r * 0.09f, cap = StrokeCap.Round)
    )
    // Ear cups
    drawRoundRect(
        color = cupColor,
        topLeft = Offset(cx - r * 0.68f, cy - r * 0.16f),
        size = Size(r * 0.18f, r * 0.38f),
        cornerRadius = CornerRadius(r * 0.09f, r * 0.09f)
    )
    drawRoundRect(
        color = cupColor,
        topLeft = Offset(cx + r * 0.50f, cy - r * 0.16f),
        size = Size(r * 0.18f, r * 0.38f),
        cornerRadius = CornerRadius(r * 0.09f, r * 0.09f)
    )
    drawCircle(color = bandColor, radius = r * 0.05f, center = Offset(cx - r * 0.59f, cy + r * 0.03f))
    drawCircle(color = bandColor, radius = r * 0.05f, center = Offset(cx + r * 0.59f, cy + r * 0.03f))
}

// =========================================================================
// Outer Frame Styles
// =========================================================================

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
            drawCircle(
                color = Color.White,
                radius = ringR,
                center = Offset(cx, cy),
                style = Stroke(width = strokeW)
            )
        }
        AvatarFrame.GOLD_RING -> {
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFF176), SunnyGold, SunnyGoldDark, SunnyGold),
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
            drawCircle(
                color = SunnyGold,
                radius = ringR,
                center = Offset(cx, cy),
                style = Stroke(width = strokeW * 0.9f)
            )
            for (i in 0 until 4) {
                val angle = (i * 90.0 + 45.0) * PI / 180.0
                val sx = cx + (cos(angle) * ringR).toFloat()
                val sy = cy + (sin(angle) * ringR).toFloat()
                drawMiniStar(Offset(sx, sy), r * 0.12f, r * 0.055f, Color.White)
            }
        }
        AvatarFrame.NEON_RING -> {
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.45f),
                radius = ringR,
                center = Offset(cx, cy),
                style = Stroke(width = strokeW * 1.45f)
            )
            drawCircle(
                color = Color(0xFF18FFFF),
                radius = ringR,
                center = Offset(cx, cy),
                style = Stroke(width = strokeW * 0.72f)
            )
        }
        AvatarFrame.DOUBLE_RIM -> {
            drawCircle(
                color = Color.White,
                radius = ringR,
                center = Offset(cx, cy),
                style = Stroke(width = strokeW * 0.65f)
            )
            drawCircle(
                color = accent,
                radius = ringR - strokeW * 0.85f,
                center = Offset(cx, cy),
                style = Stroke(width = strokeW * 0.45f)
            )
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
