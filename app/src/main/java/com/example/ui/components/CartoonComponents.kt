package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CobaltDark
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.InkDark
import com.example.ui.theme.OutlineSoft
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tactile, bubbly 3D cartoon button with bottom lip shadow and spring bounce.
 */
@Composable
fun CartoonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    icon: ImageVector? = null,
    containerColor: Color = CobaltPrimary,
    lipColor: Color = CobaltDark,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    minHeight: Dp = 56.dp,
    cornerRadius: Dp = 24.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "buttonScale"
    )
    val lipHeight = if (pressed && enabled) 2.dp else 6.dp
    val shape = RoundedCornerShape(cornerRadius)

    val actualModifier = modifier
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (enabled) 1f else 0.55f
        }

    Box(
        modifier = actualModifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(lipColor)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.35f)),
                onClick = onClick
            )
            .padding(bottom = lipHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight - lipHeight)
                .clip(shape)
                .background(containerColor)
                .border(2.dp, Color.White.copy(alpha = 0.35f), shape)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun CartoonCard(
    modifier: Modifier = Modifier,
    borderColor: Color = OutlineSoft,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.shadow(
            elevation = 6.dp,
            shape = RoundedCornerShape(cornerRadius),
            ambientColor = InkDark.copy(alpha = 0.10f),
            spotColor = InkDark.copy(alpha = 0.14f)
        ),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun CartoonIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    iconTint: Color = InkDark,
    borderColor: Color = OutlineSoft
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(2.dp, borderColor),
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

data class ConfettiParticle(
    val startX: Float,
    val startY: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val color: Color,
    val rotationSpeed: Float
)

/**
 * Playful burst or shower confetti overlay for token home celebrations and Winner podium.
 */
@Composable
fun ConfettiOverlay(
    triggerKey: Int,
    burstFromCenter: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (triggerKey <= 0) return

    val particles = remember(triggerKey) {
        val rng = Random(triggerKey * 997 + 13)
        val palette = listOf(
            Color(0xFFFF3B4E),
            Color(0xFF18C964),
            Color(0xFFFFBE0B),
            Color(0xFF257CFF),
            Color(0xFFFF6EC7),
            Color(0xFF00D2FF)
        )
        List(if (burstFromCenter) 48 else 72) {
            if (burstFromCenter) {
                val angle = rng.nextDouble(0.0, Math.PI * 2)
                val speed = rng.nextFloat() * 0.65f + 0.2f
                ConfettiParticle(
                    startX = 0.5f,
                    startY = 0.5f,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat() - 0.15f,
                    size = rng.nextFloat() * 14f + 8f,
                    color = palette[rng.nextInt(palette.size)],
                    rotationSpeed = rng.nextFloat() * 360f - 180f
                )
            } else {
                ConfettiParticle(
                    startX = rng.nextFloat(),
                    startY = -rng.nextFloat() * 0.25f,
                    vx = rng.nextFloat() * 0.24f - 0.12f,
                    vy = rng.nextFloat() * 0.45f + 0.35f,
                    size = rng.nextFloat() * 14f + 9f,
                    color = palette[rng.nextInt(palette.size)],
                    rotationSpeed = rng.nextFloat() * 320f - 160f
                )
            }
        }
    }

    var elapsedSeconds by remember(triggerKey) { mutableFloatStateOf(0f) }

    LaunchedEffect(triggerKey) {
        val startNs = withFrameNanos { it }
        val maxDuration = if (burstFromCenter) 1.5f else 4.0f
        while (elapsedSeconds < maxDuration) {
            withFrameNanos { now ->
                elapsedSeconds = (now - startNs) / 1_000_000_000f
            }
        }
    }

    val maxDuration = if (burstFromCenter) 1.5f else 4.0f
    if (elapsedSeconds >= maxDuration) return

    val fade = (1f - (elapsedSeconds / maxDuration)).coerceIn(0f, 1f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val gravity = if (burstFromCenter) 0.55f else 0.16f
        for (p in particles) {
            val px = (p.startX + p.vx * elapsedSeconds) * size.width
            val py = (p.startY + p.vy * elapsedSeconds + 0.5f * gravity * elapsedSeconds * elapsedSeconds) * size.height
            if (py < -40f || py > size.height + 40f) continue
            rotate(degrees = p.rotationSpeed * elapsedSeconds, pivot = Offset(px, py)) {
                drawRect(
                    color = p.color.copy(alpha = fade),
                    topLeft = Offset(px - p.size / 2f, py - p.size * 0.35f),
                    size = Size(p.size, p.size * 0.7f)
                )
            }
        }
    }
}
