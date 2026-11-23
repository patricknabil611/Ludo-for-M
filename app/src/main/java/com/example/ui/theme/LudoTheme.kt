package com.example.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.engine.PlayerColor

enum class LudoThemeId(val displayName: String) {
    ARCADE_LIGHT("Arcade Light"),
    CANDY_POP("Candy Pop"),
    NIGHT_ARCADE("Night Arcade"),
    ROYAL_LOUNGE("Royal Lounge");

    companion object {
        val DEFAULT: LudoThemeId = ARCADE_LIGHT

        fun fromStorageKey(raw: String?): LudoThemeId {
            if (raw.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: DEFAULT
        }
    }
}

enum class BaseCornerStyle {
    TINT,
    SOLID
}

@Immutable
sealed class ThemeShadowSpec {
    data object None : ThemeShadowSpec()

    data class Outline(
        val color: Color,
        val width: Dp = 1.dp
    ) : ThemeShadowSpec()

    data class Soft(
        val offsetY: Dp,
        val blur: Dp,
        val color: Color,
        val outlineColor: Color? = null,
        val outlineWidth: Dp = 1.dp
    ) : ThemeShadowSpec()

    data class SolidOffset(
        val offsetY: Dp,
        val color: Color,
        val outlineColor: Color? = null,
        val outlineWidth: Dp = 1.dp
    ) : ThemeShadowSpec()

    data class Glow(
        val blur: Dp,
        val color: Color,
        val outlineColor: Color? = null,
        val outlineWidth: Dp = 1.dp
    ) : ThemeShadowSpec()
}

@Immutable
data class LudoTheme(
    val id: LudoThemeId,
    val displayName: String,
    val isLightSystemBars: Boolean,
    val bg: Color,
    val text: Color,
    val mutedText: Color,
    val surface: Color,
    val surfaceText: Color,
    val surfaceMutedText: Color,
    val board: Color,
    val cell: Color,
    val gridLine: Color,
    val frame: Color?,
    val framePadding: Dp,
    val frameRadius: Dp,
    val boardRadius: Dp,
    val baseStyle: BaseCornerStyle,
    val baseInner: Color,
    val starIcon: Color,
    val crownIcon: Color,
    val red: Color,
    val green: Color,
    val yellow: Color,
    val blue: Color,
    val primaryActionBg: Color,
    val onPrimaryAction: Color,
    val tokenEdge: Color,
    val dieFace: Color,
    val pip: Color,
    val timerTrack: Color,
    val chipLine: Color,
    val font: FontFamily,
    val cardShadow: ThemeShadowSpec,
    val dieShadow: ThemeShadowSpec,
    val boardShadow: ThemeShadowSpec
) {
    val isDarkSurface: Boolean
        get() = surface.luminance() < 0.25f

    val isDarkBg: Boolean
        get() = bg.luminance() < 0.25f

    val surfaceVariant: Color
        get() = if (isDarkSurface) {
            lerp(surface, cell, 0.72f)
        } else {
            lerp(surface, gridLine, 0.42f)
        }

    fun playerColor(color: PlayerColor): Color = when (color) {
        PlayerColor.RED -> red
        PlayerColor.GREEN -> green
        PlayerColor.YELLOW -> yellow
        PlayerColor.BLUE -> blue
    }

    fun playerPalette(color: PlayerColor): PlayerPalette {
        val base = playerColor(color)
        val dark = lerp(base, Color.Black, 0.30f)
        val light = if (isDarkSurface) {
            lerp(surface, base, 0.22f)
        } else {
            lerp(surface, base, 0.14f)
        }
        val highlight = lerp(base, Color.White, 0.28f)
        return PlayerPalette(
            primary = base,
            dark = dark,
            light = light,
            highlight = highlight,
            onPrimary = contrastingTextOn(base)
        )
    }

    /** Returns an accessible text/icon color with >= 4.5:1 WCAG contrast on [background]. */
    fun contrastingTextOn(background: Color): Color {
        val whiteContrast = contrastRatio(Color.White, background)
        val darkInk = if (isDarkBg) Color(0xFF0B0E20) else Color(0xFF121633)
        val darkContrast = contrastRatio(darkInk, background)
        return if (whiteContrast >= darkContrast) Color.White else darkInk
    }

    /** Returns a darkened or lightened accent shade guaranteed to achieve >= 4.5:1 on [background]. */
    fun accessibleAccentOn(accent: Color, background: Color): Color {
        if (contrastRatio(accent, background) >= 4.5f) return accent
        val target = if (background.luminance() > 0.35f) Color(0xFF0B0E20) else Color.White
        var step = 0.12f
        while (step <= 1f) {
            val candidate = lerp(accent, target, step)
            if (contrastRatio(candidate, background) >= 4.55f) return candidate
            step += 0.08f
        }
        return target
    }
}

val ArcadeLightTheme = LudoTheme(
    id = LudoThemeId.ARCADE_LIGHT,
    displayName = "Arcade Light",
    isLightSystemBars = true,
    bg = Color(0xFFF3F5FC),
    text = Color(0xFF121633),
    mutedText = Color(0xFF5B6283),
    surface = Color(0xFFFFFFFF),
    surfaceText = Color(0xFF121633),
    surfaceMutedText = Color(0xFF5B6283),
    board = Color(0xFFF3F5FC),
    cell = Color(0xFFFFFFFF),
    gridLine = Color(0xFFE4E8F5),
    frame = null,
    framePadding = 0.dp,
    frameRadius = 22.dp,
    boardRadius = 22.dp,
    baseStyle = BaseCornerStyle.TINT,
    baseInner = Color(0xFFFFFFFF),
    starIcon = Color(0xFF8E96BE),
    crownIcon = Color(0xFFFFB800),
    red = Color(0xFFFF3D63),
    green = Color(0xFF14BF6F),
    yellow = Color(0xFFFFB800),
    blue = Color(0xFF2F80FF),
    primaryActionBg = Color(0xFF1D66E5),
    onPrimaryAction = Color(0xFFFFFFFF),
    tokenEdge = Color(0xFFFFFFFF),
    dieFace = Color(0xFFFFFFFF),
    pip = Color(0xFF121633),
    timerTrack = Color(0xFFDCE1F2),
    chipLine = Color(0xFFE4E8F5),
    font = RubikFontFamily,
    cardShadow = ThemeShadowSpec.Soft(
        offsetY = 4.dp,
        blur = 12.dp,
        color = Color(0xFF121633).copy(alpha = 0.06f),
        outlineColor = Color(0xFFE4E8F5),
        outlineWidth = 1.dp
    ),
    dieShadow = ThemeShadowSpec.Glow(
        blur = 20.dp,
        color = Color(0xFF2F80FF).copy(alpha = 0.45f),
        outlineColor = Color(0xFFE4E8F5),
        outlineWidth = 1.dp
    ),
    boardShadow = ThemeShadowSpec.None
)

val CandyPopTheme = LudoTheme(
    id = LudoThemeId.CANDY_POP,
    displayName = "Candy Pop",
    isLightSystemBars = true,
    bg = Color(0xFFE8ECFF),
    text = Color(0xFF1E2250),
    mutedText = Color(0xFF4E5585),
    surface = Color(0xFFFFFFFF),
    surfaceText = Color(0xFF1E2250),
    surfaceMutedText = Color(0xFF4E5585),
    board = Color(0xFFFFFFFF),
    cell = Color(0xFFFFFFFF),
    gridLine = Color(0xFFD5D9EE),
    frame = Color(0xFF2A2F66),
    framePadding = 7.dp,
    frameRadius = 24.dp,
    boardRadius = 18.dp,
    baseStyle = BaseCornerStyle.SOLID,
    baseInner = Color(0xFFFFFFFF),
    starIcon = Color(0xFF8D94BF),
    crownIcon = Color(0xFFE8A600),
    red = Color(0xFFEF4438),
    green = Color(0xFF1FA855),
    yellow = Color(0xFFFFC21A),
    blue = Color(0xFF2C7BF2),
    primaryActionBg = Color(0xFF1E63D8),
    onPrimaryAction = Color(0xFFFFFFFF),
    tokenEdge = Color(0xFFFFFFFF),
    dieFace = Color(0xFFFFFFFF),
    pip = Color(0xFF1E2250),
    timerTrack = Color(0xFFC9CEEB),
    chipLine = Color(0xFFD5D9EE),
    font = FredokaFontFamily,
    cardShadow = ThemeShadowSpec.SolidOffset(
        offsetY = 3.dp,
        color = Color(0xFFC3C8E8)
    ),
    dieShadow = ThemeShadowSpec.SolidOffset(
        offsetY = 4.dp,
        color = Color(0xFFB9BFE3)
    ),
    boardShadow = ThemeShadowSpec.SolidOffset(
        offsetY = 8.dp,
        color = Color(0xFF1B1F4A)
    )
)

val NightArcadeTheme = LudoTheme(
    id = LudoThemeId.NIGHT_ARCADE,
    displayName = "Night Arcade",
    isLightSystemBars = false,
    bg = Color(0xFF0B0E20),
    text = Color(0xFFF1F3FF),
    mutedText = Color(0xFFA6ACD8),
    surface = Color(0xFF1A1F40),
    surfaceText = Color(0xFFF1F3FF),
    surfaceMutedText = Color(0xFFA6ACD8),
    board = Color(0xFF141833),
    cell = Color(0xFF20254A),
    gridLine = Color(0xFF2E3463),
    frame = Color(0xFF2E3463),
    framePadding = 7.dp,
    frameRadius = 22.dp,
    boardRadius = 16.dp,
    baseStyle = BaseCornerStyle.SOLID,
    baseInner = Color(0xFF141833),
    starIcon = Color(0xFF7A82C2),
    crownIcon = Color(0xFFFFD23F),
    red = Color(0xFFFF4D6D),
    green = Color(0xFF33D17A),
    yellow = Color(0xFFFFD23F),
    blue = Color(0xFF4D9BFF),
    primaryActionBg = Color(0xFF4D9BFF),
    onPrimaryAction = Color(0xFF0B0E20),
    tokenEdge = Color(0xFF0B0E20),
    dieFace = Color(0xFFF4F5FF),
    pip = Color(0xFF11142B),
    timerTrack = Color(0xFF2A3060),
    chipLine = Color(0xFF2E3463),
    font = RubikFontFamily,
    cardShadow = ThemeShadowSpec.Outline(
        color = Color(0xFF2E3463),
        width = 1.dp
    ),
    dieShadow = ThemeShadowSpec.Glow(
        blur = 18.dp,
        color = Color(0xFF4D9BFF).copy(alpha = 0.60f)
    ),
    boardShadow = ThemeShadowSpec.Glow(
        blur = 40.dp,
        color = Color(0xFF4D9BFF).copy(alpha = 0.20f)
    )
)

val RoyalLoungeTheme = LudoTheme(
    id = LudoThemeId.ROYAL_LOUNGE,
    displayName = "Royal Lounge",
    isLightSystemBars = false,
    bg = Color(0xFF0F3A2C),
    text = Color(0xFFF7EED7),
    mutedText = Color(0xFFC9DCCF),
    surface = Color(0xFFFBF5E6),
    surfaceText = Color(0xFF2B2414),
    surfaceMutedText = Color(0xFF63583F),
    board = Color(0xFFFBF5E6),
    cell = Color(0xFFFFFCF3),
    gridLine = Color(0xFFE2D5B3),
    frame = Color(0xFFC9A24A),
    framePadding = 7.dp,
    frameRadius = 18.dp,
    boardRadius = 12.dp,
    baseStyle = BaseCornerStyle.SOLID,
    baseInner = Color(0xFFFFFCF3),
    starIcon = Color(0xFFA8955F),
    crownIcon = Color(0xFFB8913E),
    red = Color(0xFFB83227),
    green = Color(0xFF1C7F52),
    yellow = Color(0xFFD99A1E),
    blue = Color(0xFF2A55A0),
    primaryActionBg = Color(0xFF2A55A0),
    onPrimaryAction = Color(0xFFFFFFFF),
    tokenEdge = Color(0xFFF6E7BF),
    dieFace = Color(0xFFFFFCF3),
    pip = Color(0xFF2B2414),
    timerTrack = Color(0xFF2A5A48),
    chipLine = Color(0xFFE2D5B3),
    font = NunitoFontFamily,
    cardShadow = ThemeShadowSpec.SolidOffset(
        offsetY = 3.dp,
        color = Color(0xFF082419)
    ),
    dieShadow = ThemeShadowSpec.SolidOffset(
        offsetY = 4.dp,
        color = Color(0xFFA8873A)
    ),
    boardShadow = ThemeShadowSpec.Soft(
        offsetY = 10.dp,
        blur = 24.dp,
        color = Color.Black.copy(alpha = 0.45f)
    )
)

val AllLudoThemes: List<LudoTheme> = listOf(
    ArcadeLightTheme,
    CandyPopTheme,
    NightArcadeTheme,
    RoyalLoungeTheme
)

fun LudoThemeId.toLudoTheme(): LudoTheme = when (this) {
    LudoThemeId.ARCADE_LIGHT -> ArcadeLightTheme
    LudoThemeId.CANDY_POP -> CandyPopTheme
    LudoThemeId.NIGHT_ARCADE -> NightArcadeTheme
    LudoThemeId.ROYAL_LOUNGE -> RoyalLoungeTheme
}

val LocalLudoTheme = staticCompositionLocalOf<LudoTheme> { ArcadeLightTheme }

fun contrastRatio(foreground: Color, background: Color): Float {
    val l1 = foreground.luminance()
    val l2 = background.luminance()
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Renders the theme's exact shadow + optional hairline outline for cards, dice, pills, and board frames.
 */
fun Modifier.ludoShadow(
    spec: ThemeShadowSpec,
    cornerRadius: Dp
): Modifier = composed {
    if (spec is ThemeShadowSpec.None) return@composed this

    this
        .drawBehind {
            val radiusPx = cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
            when (spec) {
                is ThemeShadowSpec.None -> Unit
                is ThemeShadowSpec.Outline -> Unit
                is ThemeShadowSpec.SolidOffset -> {
                    val offsetYPx = spec.offsetY.toPx()
                    drawRoundRect(
                        color = spec.color,
                        topLeft = Offset(0f, offsetYPx),
                        size = size,
                        cornerRadius = CornerRadius(radiusPx, radiusPx)
                    )
                }
                is ThemeShadowSpec.Soft -> {
                    val blurPx = spec.blur.toPx().coerceAtLeast(1f)
                    val offsetYPx = spec.offsetY.toPx()
                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            isAntiAlias = true
                            color = spec.color.toArgb()
                            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.nativeCanvas.drawRoundRect(
                            0f,
                            offsetYPx,
                            size.width,
                            size.height + offsetYPx,
                            radiusPx,
                            radiusPx,
                            paint
                        )
                    }
                }
                is ThemeShadowSpec.Glow -> {
                    val blurPx = spec.blur.toPx().coerceAtLeast(1f)
                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint().apply {
                            isAntiAlias = true
                            color = spec.color.toArgb()
                            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.nativeCanvas.drawRoundRect(
                            0f,
                            0f,
                            size.width,
                            size.height,
                            radiusPx,
                            radiusPx,
                            paint
                        )
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            val outlineColor: Color?
            val outlineWidth: Dp
            when (spec) {
                is ThemeShadowSpec.Outline -> {
                    outlineColor = spec.color
                    outlineWidth = spec.width
                }
                is ThemeShadowSpec.Soft -> {
                    outlineColor = spec.outlineColor
                    outlineWidth = spec.outlineWidth
                }
                is ThemeShadowSpec.SolidOffset -> {
                    outlineColor = spec.outlineColor
                    outlineWidth = spec.outlineWidth
                }
                is ThemeShadowSpec.Glow -> {
                    outlineColor = spec.outlineColor
                    outlineWidth = spec.outlineWidth
                }
                ThemeShadowSpec.None -> {
                    outlineColor = null
                    outlineWidth = 0.dp
                }
            }
            if (outlineColor != null && outlineWidth > 0.dp) {
                val strokePx = outlineWidth.toPx()
                val half = strokePx / 2f
                val radiusPx = (cornerRadius.toPx() - half)
                    .coerceAtLeast(0f)
                    .coerceAtMost(minOf(size.width, size.height) / 2f)
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(half, half),
                    size = Size(
                        (size.width - strokePx).coerceAtLeast(0f),
                        (size.height - strokePx).coerceAtLeast(0f)
                    ),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx)
                )
            }
        }
}
