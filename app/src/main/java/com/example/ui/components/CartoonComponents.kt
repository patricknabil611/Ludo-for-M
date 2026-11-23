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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.ludoShadow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tactile theme-aware primary action button with gentle press spring.
 */
@Composable
fun CartoonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    icon: ImageVector? = null,
    containerColor: Color? = null,
    lipColor: Color? = null,
    contentColor: Color? = null,
    enabled: Boolean = true,
    minHeight: Dp = 56.dp,
    cornerRadius: Dp = 18.dp
) {
    val theme = LocalLudoTheme.current
    val resolvedBg = containerColor ?: theme.primaryActionBg
    val resolvedLip = lipColor ?: lerp(resolvedBg, Color.Black, 0.28f)
    val resolvedContent = contentColor ?: if (containerColor == null) {
        theme.onPrimaryAction
    } else {
        theme.contrastingTextOn(resolvedBg)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 520f),
        label = "buttonScale"
    )
    val lipHeight = if (pressed && enabled) 1.dp else 4.dp
    val shape = RoundedCornerShape(cornerRadius)

    val actualModifier = modifier
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (enabled) 1f else 0.50f
        }

    Box(
        modifier = actualModifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(resolvedLip)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = resolvedContent.copy(alpha = 0.25f)),
                onClick = onClick
            )
            .padding(bottom = lipHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight - lipHeight)
                .clip(shape)
                .background(resolvedBg)
                .border(1.5.dp, Color.White.copy(alpha = 0.22f), shape)
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
                        tint = resolvedContent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleLarge,
                    color = resolvedContent
                )
            }
        }
    }
}

@Composable
fun CartoonCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    containerColor: Color? = null,
    cornerRadius: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalLudoTheme.current
    val resolvedContainer = containerColor ?: theme.surface
    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier.ludoShadow(theme.cardShadow, cornerRadius),
        shape = shape,
        color = resolvedContainer,
        border = borderColor?.let { BorderStroke(1.5.dp, it) }
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
    containerColor: Color? = null,
    iconTint: Color? = null,
    borderColor: Color? = null,
    size: Dp = 44.dp,
    cornerRadius: Dp = 14.dp
) {
    val theme = LocalLudoTheme.current
    val resolvedBg = containerColor ?: theme.surface
    val resolvedTint = iconTint ?: theme.surfaceText
    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .ludoShadow(theme.cardShadow, cornerRadius),
        shape = shape,
        color = resolvedBg,
        border = borderColor?.let { BorderStroke(1.5.dp, it) }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = resolvedTint,
                modifier = Modifier.size(22.dp)
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
    accentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    if (triggerKey <= 0) return
    val theme = LocalLudoTheme.current

    val particles = remember(triggerKey, accentColor, theme.id) {
        val rng = Random(triggerKey * 997 + 13)
        val palette = if (accentColor != null) {
            listOf(
                accentColor,
                accentColor,
                lerp(accentColor, Color.White, 0.35f),
                lerp(accentColor, Color.Black, 0.18f),
                theme.yellow,
                Color.White
            )
        } else {
            listOf(
                theme.red,
                theme.green,
                theme.yellow,
                theme.blue,
                lerp(theme.red, theme.yellow, 0.5f),
                lerp(theme.blue, theme.green, 0.5f)
            )
        }
        List(if (burstFromCenter) 40 else 72) {
            if (burstFromCenter) {
                val angle = rng.nextDouble(0.0, Math.PI * 2)
                val speed = rng.nextFloat() * 0.58f + 0.18f
                ConfettiParticle(
                    startX = 0.5f,
                    startY = 0.5f,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat() - 0.12f,
                    size = rng.nextFloat() * 12f + 7f,
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
