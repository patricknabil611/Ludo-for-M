package com.example.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PlayerAvatarCodec
import com.example.engine.GameState
import com.example.engine.PlayerColor
import com.example.ui.components.CartoonButton
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.theme.ArcadeLightTheme
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.LudoTheme
import com.example.ui.theme.ludoShadow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class AllTokensHomeCelebrationState(
    val playerName: String,
    val color: PlayerColor,
    val rank: Int,
    val isMatchOver: Boolean,
    val isTeamsPartnerContinuation: Boolean = false,
    val teamLabel: String? = null,
    val triggerKey: Int = 1,
    val avatarId: String = "",
    val isBot: Boolean = false
)

enum class CelebrationParticleShape {
    RIBBON,
    CIRCLE,
    STAR
}

data class CelebrationParticle(
    val originXFraction: Float,
    val originYFraction: Float,
    val velocityX: Float,
    val velocityY: Float,
    val gravity: Float,
    val sizeDp: Float,
    val color: Color,
    val shape: CelebrationParticleShape,
    val rotationSpeedDeg: Float,
    val initialRotationDeg: Float,
    val flutterAmplitude: Float,
    val flutterFreq: Float,
    val delaySec: Float
)

object CelebrationSequenceHelper {

    const val REQUIRED_HOME_TOKENS = 4
    const val FIRST_TOKEN_REVEAL_DELAY_MS = 260L
    const val TOKEN_REVEAL_STAGGER_MS = 135L
    const val TOKEN_REVEAL_DURATION_MS = 240L
    const val AUTO_DISMISS_MS = 5000L

    fun detectAllFourTokensHome(
        prevState: GameState?,
        nextState: GameState,
        nextTriggerKey: Int
    ): AllTokensHomeCelebrationState? {
        if (prevState == null) return null

        val newlyCompletedPlayer = nextState.players.firstOrNull { nextPlayer ->
            val prevPlayer = prevState.playerByColor(nextPlayer.color)
            val prevCount = prevPlayer?.finishedTokenCount ?: 0
            prevCount < REQUIRED_HOME_TOKENS && nextPlayer.finishedTokenCount >= REQUIRED_HOME_TOKENS
        } ?: return null

        val finishIndex = nextState.finishOrder.indexOf(newlyCompletedPlayer.color)
        val rank = if (finishIndex >= 0) finishIndex + 1 else 1
        val isMatchOver = nextState.isMatchFinished
        val isTeamsPartnerContinuation =
            nextState.isTeamMatch && !isMatchOver

        return AllTokensHomeCelebrationState(
            playerName = newlyCompletedPlayer.nickname,
            color = newlyCompletedPlayer.color,
            rank = rank,
            isMatchOver = isMatchOver,
            isTeamsPartnerContinuation = isTeamsPartnerContinuation,
            teamLabel = nextState.winningTeamLabel,
            triggerKey = nextTriggerKey,
            avatarId = newlyCompletedPlayer.avatarId,
            isBot = newlyCompletedPlayer.setup.isBot
        )
    }

    fun tokenRevealProgress(elapsedMs: Long, tokenIndex: Int): Float {
        val safeIndex = tokenIndex.coerceIn(0, REQUIRED_HOME_TOKENS - 1)
        val startMs = FIRST_TOKEN_REVEAL_DELAY_MS + safeIndex * TOKEN_REVEAL_STAGGER_MS
        if (elapsedMs <= startMs) return 0f
        return ((elapsedMs - startMs).toFloat() / TOKEN_REVEAL_DURATION_MS.toFloat())
            .coerceIn(0f, 1f)
    }

    fun generateCannonParticles(
        seed: Int,
        accentColor: Color,
        count: Int = 96,
        theme: LudoTheme = ArcadeLightTheme
    ): List<CelebrationParticle> {
        val rng = Random(seed * 7919 + 42)
        val palette = listOf(
            accentColor,
            theme.yellow,
            theme.red,
            theme.green,
            theme.blue,
            theme.crownIcon
        )
        val shapes = CelebrationParticleShape.entries

        return List(count) { index ->
            val wave = index % 3
            val fromLeftCannon = index % 2 == 0
            val (originX, originY, vx, vy, delaySec) = when (wave) {
                0, 1 -> {
                    val ox = if (fromLeftCannon) -0.02f else 1.02f
                    val oy = 0.86f + rng.nextFloat() * 0.12f
                    val speedX = (0.34f + rng.nextFloat() * 0.56f) * if (fromLeftCannon) 1f else -1f
                    val speedY = -(0.88f + rng.nextFloat() * 0.58f)
                    val delay = if (wave == 0) rng.nextFloat() * 0.14f else 0.24f + rng.nextFloat() * 0.22f
                    ParticleLaunch(ox, oy, speedX, speedY, delay)
                }
                else -> {
                    val ox = rng.nextFloat()
                    val oy = -0.06f - rng.nextFloat() * 0.10f
                    val speedX = (rng.nextFloat() - 0.5f) * 0.18f
                    val speedY = 0.12f + rng.nextFloat() * 0.20f
                    val delay = 0.15f + rng.nextFloat() * 0.65f
                    ParticleLaunch(ox, oy, speedX, speedY, delay)
                }
            }

            CelebrationParticle(
                originXFraction = originX,
                originYFraction = originY,
                velocityX = vx,
                velocityY = vy,
                gravity = if (wave == 2) 0.34f else 0.92f + rng.nextFloat() * 0.25f,
                sizeDp = 7f + rng.nextFloat() * 7.5f,
                color = palette[rng.nextInt(palette.size)],
                shape = shapes[rng.nextInt(shapes.size)],
                rotationSpeedDeg = (rng.nextFloat() - 0.5f) * 460f,
                initialRotationDeg = rng.nextFloat() * 360f,
                flutterAmplitude = 0.012f + rng.nextFloat() * 0.024f,
                flutterFreq = 4f + rng.nextFloat() * 5f,
                delaySec = delaySec
            )
        }
    }

    private data class ParticleLaunch(
        val originX: Float,
        val originY: Float,
        val vx: Float,
        val vy: Float,
        val delaySec: Float
    )
}

@Composable
fun AllTokensHomeCelebrationOverlay(
    celebration: AllTokensHomeCelebrationState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLudoTheme.current
    val playerPalette = theme.playerPalette(celebration.color)
    val particles = remember(celebration.triggerKey, celebration.color, theme.id) {
        CelebrationSequenceHelper.generateCannonParticles(
            seed = celebration.triggerKey * 31 + celebration.color.ordinal,
            accentColor = playerPalette.primary,
            theme = theme
        )
    }

    var elapsedMs by remember(celebration.triggerKey) { mutableLongStateOf(0L) }
    val cardScale = remember(celebration.triggerKey) { Animatable(0.55f) }
    val trophyScale = remember(celebration.triggerKey) { Animatable(0.25f) }

    LaunchedEffect(celebration.triggerKey) {
        cardScale.snapTo(0.55f)
        cardScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    LaunchedEffect(celebration.triggerKey) {
        trophyScale.snapTo(0.25f)
        trophyScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.48f,
                stiffness = 320f
            )
        )
    }

    LaunchedEffect(celebration.triggerKey) {
        val startNanos = withFrameNanos { it }
        var dismissed = false
        while (!dismissed) {
            val nowNanos = withFrameNanos { it }
            val ms = ((nowNanos - startNanos) / 1_000_000L).coerceAtLeast(0L)
            elapsedMs = ms
            if (ms >= CelebrationSequenceHelper.AUTO_DISMISS_MS) {
                dismissed = true
                onDismiss()
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "celebration_loop")
    val sunburstAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sunburst_rotation"
    )
    val trophyRock by infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trophy_rock"
    )

    val countdownProgress = (1f - elapsedMs.toFloat() / CelebrationSequenceHelper.AUTO_DISMISS_MS.toFloat())
        .coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        playerPalette.dark.copy(alpha = 0.44f),
                        Color.Black.copy(alpha = 0.72f)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .testTag("all_tokens_home_celebration_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val elapsedSec = elapsedMs / 1000f
            drawFireworkRings(
                elapsedSec = elapsedSec,
                accentColor = playerPalette.primary,
                goldColor = theme.yellow
            )
            drawCelebrationParticles(
                particles = particles,
                elapsedSec = elapsedSec
            )
        }

        Box(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .scale(cardScale.value)
                .testTag("all_tokens_home_celebration_modal"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .ludoShadow(theme.cardShadow, 28.dp),
                shape = RoundedCornerShape(28.dp),
                color = theme.surface,
                border = BorderStroke(3.dp, theme.yellow)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(118.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawSunburstHalo(
                                rotationDeg = sunburstAngle,
                                accentColor = playerPalette.primary,
                                goldColor = theme.yellow
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .scale(trophyScale.value)
                                .graphicsLayer { rotationZ = trophyRock },
                            contentAlignment = Alignment.Center
                        ) {
                            PlayerAvatarBadge(
                                avatar = PlayerAvatarCodec.resolve(
                                    raw = celebration.avatarId,
                                    fallbackColor = celebration.color,
                                    isBot = celebration.isBot,
                                    seed = celebration.color.ordinal
                                ),
                                size = 78.dp,
                                contentDescription = celebration.playerName
                            )
                            val medalFg = theme.contrastingTextOn(theme.yellow)
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(30.dp),
                                shape = CircleShape,
                                color = theme.yellow,
                                border = BorderStroke(2.dp, theme.surface)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (celebration.rank == 1 || celebration.teamLabel != null) {
                                            Icons.Filled.EmojiEvents
                                        } else {
                                            Icons.Filled.WorkspacePremium
                                        },
                                        contentDescription = stringResource(R.string.cd_celebration_trophy),
                                        tint = medalFg,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    val rankPillText = when {
                        celebration.teamLabel != null && celebration.isMatchOver ->
                            celebration.teamLabel.uppercase()
                        celebration.rank == 1 -> stringResource(R.string.celebration_rank_first)
                        celebration.rank == 2 -> stringResource(R.string.celebration_rank_second)
                        celebration.rank == 3 -> stringResource(R.string.celebration_rank_third)
                        else -> stringResource(R.string.celebration_rank_generic)
                    }
                    val pillFg = theme.contrastingTextOn(theme.yellow)
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = theme.yellow
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = pillFg,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = rankPillText,
                                style = MaterialTheme.typography.labelLarge,
                                color = pillFg,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = pillFg,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(
                            R.string.celebration_headline_all_home,
                            celebration.playerName
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = theme.surfaceText,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(6.dp))

                    val subtitle = when {
                        celebration.isMatchOver ->
                            stringResource(R.string.celebration_subtitle_match_over)
                        celebration.isTeamsPartnerContinuation ->
                            stringResource(R.string.celebration_subtitle_teams_continue)
                        else ->
                            stringResource(R.string.celebration_subtitle_match_continues, celebration.rank)
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.surfaceMutedText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(18.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = playerPalette.light,
                        border = BorderStroke(1.5.dp, playerPalette.primary.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(CelebrationSequenceHelper.REQUIRED_HOME_TOKENS) { idx ->
                                    val progress = CelebrationSequenceHelper.tokenRevealProgress(
                                        elapsedMs = elapsedMs,
                                        tokenIndex = idx
                                    )
                                    StaggeredHomeTokenBadge(
                                        color = celebration.color,
                                        tokenNumber = idx + 1,
                                        revealProgress = progress
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            val badgeBg = lerp(theme.surface, theme.green, 0.16f)
                            val badgeFg = theme.accessibleAccentOn(theme.green, badgeBg)
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = badgeBg,
                                border = BorderStroke(1.5.dp, theme.green)
                            ) {
                                Text(
                                    text = stringResource(R.string.celebration_tokens_home_badge),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = badgeFg,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    val ctaText = if (celebration.isMatchOver) {
                        stringResource(R.string.celebration_cta_see_podium)
                    } else {
                        stringResource(R.string.celebration_cta_continue)
                    }
                    val ctaBg = if (celebration.isMatchOver) theme.yellow else theme.primaryActionBg
                    val ctaFg = if (celebration.isMatchOver) theme.contrastingTextOn(theme.yellow) else theme.onPrimaryAction
                    CartoonButton(
                        text = ctaText,
                        onClick = onDismiss,
                        containerColor = ctaBg,
                        contentColor = ctaFg,
                        icon = if (celebration.isMatchOver) Icons.Filled.EmojiEvents else Icons.Filled.PlayArrow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("celebration_continue_button")
                    )

                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(theme.timerTrack)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(countdownProgress)
                                .height(5.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(playerPalette.primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaggeredHomeTokenBadge(
    color: PlayerColor,
    tokenNumber: Int,
    revealProgress: Float
) {
    val theme = LocalLudoTheme.current
    val playerColor = theme.playerColor(color)
    val tokenCd = stringResource(R.string.cd_celebration_token_home, tokenNumber)
    val popScale = when {
        revealProgress <= 0f -> 0.25f
        revealProgress < 0.72f -> 0.25f + (revealProgress / 0.72f) * 0.92f
        else -> 1.17f - ((revealProgress - 0.72f) / 0.28f) * 0.17f
    }
    val alpha = (revealProgress * 1.4f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = tokenCd },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = theme.surface,
            border = BorderStroke(
                width = 2.dp,
                color = if (revealProgress > 0.35f) theme.yellow else playerColor.copy(alpha = 0.25f)
            )
        ) {}

        Canvas(
            modifier = Modifier
                .size(40.dp)
                .scale(popScale)
                .graphicsLayer { this.alpha = alpha }
        ) {
            val r = size.minDimension * 0.42f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = Color.Black.copy(alpha = 0.22f),
                radius = r,
                center = center + Offset(0f, 2.dp.toPx())
            )
            drawCircle(color = playerColor, radius = r, center = center)
            drawCircle(
                color = theme.tokenEdge,
                radius = r - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.50f),
                radius = r * 0.38f,
                center = center
            )
        }

        if (revealProgress >= 0.65f) {
            val checkScale = ((revealProgress - 0.65f) / 0.35f).coerceIn(0f, 1f)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .scale(checkScale),
                shape = CircleShape,
                color = theme.green,
                border = BorderStroke(1.5.dp, theme.surface)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = theme.contrastingTextOn(theme.green),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawSunburstHalo(
    rotationDeg: Float,
    accentColor: Color,
    goldColor: Color
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerR = size.minDimension * 0.50f
    val innerR = size.minDimension * 0.28f
    val rayCount = 12

    rotate(degrees = rotationDeg, pivot = center) {
        for (i in 0 until rayCount) {
            val angleDeg = i * (360f / rayCount)
            val halfSpread = 9f
            val a1 = Math.toRadians((angleDeg - halfSpread).toDouble())
            val a2 = Math.toRadians((angleDeg + halfSpread).toDouble())
            val path = Path().apply {
                moveTo(center.x, center.y)
                lineTo(
                    center.x + (outerR * cos(a1)).toFloat(),
                    center.y + (outerR * sin(a1)).toFloat()
                )
                lineTo(
                    center.x + (outerR * cos(a2)).toFloat(),
                    center.y + (outerR * sin(a2)).toFloat()
                )
                close()
            }
            drawPath(
                path = path,
                color = if (i % 2 == 0) goldColor.copy(alpha = 0.52f) else accentColor.copy(alpha = 0.28f)
            )
        }
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(goldColor.copy(alpha = 0.42f), Color.Transparent),
            center = center,
            radius = outerR
        ),
        radius = outerR,
        center = center
    )
    drawCircle(
        color = goldColor.copy(alpha = 0.32f),
        radius = innerR,
        center = center
    )
}

private fun DrawScope.drawFireworkRings(
    elapsedSec: Float,
    accentColor: Color,
    goldColor: Color
) {
    val center = Offset(size.width / 2f, size.height * 0.48f)
    val maxRadius = size.minDimension * 0.62f
    val ringDelays = listOf(0.0f, 0.28f, 0.58f)

    ringDelays.forEachIndexed { idx, delaySec ->
        val localT = ((elapsedSec - delaySec) / 0.95f)
        if (localT in 0f..1f) {
            val eased = 1f - (1f - localT) * (1f - localT)
            val radius = maxRadius * (0.12f + eased * 0.88f)
            val alpha = (1f - localT).coerceIn(0f, 1f) * 0.65f
            val ringColor = if (idx % 2 == 0) goldColor else accentColor
            drawCircle(
                color = ringColor.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = (8.dp.toPx() * (1f - localT * 0.7f)).coerceAtLeast(2f))
            )

            val sparkCount = 12
            for (s in 0 until sparkCount) {
                val theta = (s.toFloat() / sparkCount) * 2f * PI.toFloat() + idx * 0.26f
                val sx = center.x + cos(theta) * radius
                val sy = center.y + sin(theta) * radius
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = (4.dp.toPx() * (1f - localT * 0.6f)).coerceAtLeast(1.5f),
                    center = Offset(sx, sy)
                )
            }
        }
    }
}

private fun DrawScope.drawCelebrationParticles(
    particles: List<CelebrationParticle>,
    elapsedSec: Float
) {
    val w = size.width
    val h = size.height

    particles.forEach { p ->
        val t = elapsedSec - p.delaySec
        if (t <= 0f) return@forEach

        val fade = when {
            t < 0.08f -> t / 0.08f
            t > 3.2f -> (1f - (t - 3.2f) / 1.4f).coerceIn(0f, 1f)
            else -> 1f
        }
        if (fade <= 0.01f) return@forEach

        val drag = 1f / (1f + 0.38f * t)
        val flutter = sin(t * p.flutterFreq + p.initialRotationDeg) * p.flutterAmplitude
        val xFrac = p.originXFraction + p.velocityX * t * drag + flutter
        val yFrac = p.originYFraction + p.velocityY * t + 0.5f * p.gravity * t * t

        val x = xFrac * w
        val y = yFrac * h
        if (y > h + 40f || x < -60f || x > w + 60f) return@forEach

        val pxSize = p.sizeDp.dp.toPx()
        val angle = p.initialRotationDeg + p.rotationSpeedDeg * t
        val drawColor = p.color.copy(alpha = fade)

        rotate(degrees = angle, pivot = Offset(x, y)) {
            when (p.shape) {
                CelebrationParticleShape.RIBBON -> {
                    drawRoundRect(
                        color = drawColor,
                        topLeft = Offset(x - pxSize * 0.65f, y - pxSize * 0.30f),
                        size = Size(pxSize * 1.3f, pxSize * 0.6f),
                        cornerRadius = CornerRadius(pxSize * 0.2f, pxSize * 0.2f)
                    )
                }
                CelebrationParticleShape.CIRCLE -> {
                    drawCircle(color = drawColor, radius = pxSize * 0.42f, center = Offset(x, y))
                }
                CelebrationParticleShape.STAR -> {
                    drawMiniStar(
                        center = Offset(x, y),
                        outerRadius = pxSize * 0.58f,
                        innerRadius = pxSize * 0.26f,
                        color = drawColor
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawMiniStar(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    color: Color
) {
    val path = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val angle = (i * PI / points) - PI / 2.0
        val px = center.x + (r * cos(angle)).toFloat()
        val py = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path = path, color = color)
}
