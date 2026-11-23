package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

private const val THEME_TRANSITION_MS = 250

private val LudoShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun LudoForMTheme(
    themeId: LudoThemeId = LudoThemeId.DEFAULT,
    content: @Composable () -> Unit
) {
    val target = themeId.toLudoTheme()
    val spec = tween<Color>(durationMillis = THEME_TRANSITION_MS)

    val bg by animateColorAsState(target.bg, spec, label = "theme_bg")
    val text by animateColorAsState(target.text, spec, label = "theme_text")
    val mutedText by animateColorAsState(target.mutedText, spec, label = "theme_muted")
    val surface by animateColorAsState(target.surface, spec, label = "theme_surface")
    val surfaceText by animateColorAsState(target.surfaceText, spec, label = "theme_surface_text")
    val surfaceMutedText by animateColorAsState(target.surfaceMutedText, spec, label = "theme_surface_muted")
    val board by animateColorAsState(target.board, spec, label = "theme_board")
    val cell by animateColorAsState(target.cell, spec, label = "theme_cell")
    val gridLine by animateColorAsState(target.gridLine, spec, label = "theme_grid")
    val baseInner by animateColorAsState(target.baseInner, spec, label = "theme_base_inner")
    val starIcon by animateColorAsState(target.starIcon, spec, label = "theme_star")
    val crownIcon by animateColorAsState(target.crownIcon, spec, label = "theme_crown")
    val red by animateColorAsState(target.red, spec, label = "theme_red")
    val green by animateColorAsState(target.green, spec, label = "theme_green")
    val yellow by animateColorAsState(target.yellow, spec, label = "theme_yellow")
    val blue by animateColorAsState(target.blue, spec, label = "theme_blue")
    val primaryActionBg by animateColorAsState(target.primaryActionBg, spec, label = "theme_primary_bg")
    val onPrimaryAction by animateColorAsState(target.onPrimaryAction, spec, label = "theme_on_primary")
    val tokenEdge by animateColorAsState(target.tokenEdge, spec, label = "theme_token_edge")
    val dieFace by animateColorAsState(target.dieFace, spec, label = "theme_die_face")
    val pip by animateColorAsState(target.pip, spec, label = "theme_pip")
    val timerTrack by animateColorAsState(target.timerTrack, spec, label = "theme_timer_track")
    val chipLine by animateColorAsState(target.chipLine, spec, label = "theme_chip_line")

    val animatedTheme = target.copy(
        bg = bg,
        text = text,
        mutedText = mutedText,
        surface = surface,
        surfaceText = surfaceText,
        surfaceMutedText = surfaceMutedText,
        board = board,
        cell = cell,
        gridLine = gridLine,
        baseInner = baseInner,
        starIcon = starIcon,
        crownIcon = crownIcon,
        red = red,
        green = green,
        yellow = yellow,
        blue = blue,
        primaryActionBg = primaryActionBg,
        onPrimaryAction = onPrimaryAction,
        tokenEdge = tokenEdge,
        dieFace = dieFace,
        pip = pip,
        timerTrack = timerTrack,
        chipLine = chipLine
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findComponentActivity()
            if (activity != null) {
                val barColorArgb = target.bg.toArgb()
                val style = if (target.isLightSystemBars) {
                    SystemBarStyle.light(scrim = barColorArgb, darkScrim = barColorArgb)
                } else {
                    SystemBarStyle.dark(scrim = barColorArgb)
                }
                activity.enableEdgeToEdge(
                    statusBarStyle = style,
                    navigationBarStyle = style
                )
            }
        }
    }

    val colorScheme = if (animatedTheme.isDarkSurface) {
        darkColorScheme(
            primary = animatedTheme.primaryActionBg,
            onPrimary = animatedTheme.onPrimaryAction,
            primaryContainer = animatedTheme.surfaceVariant,
            onPrimaryContainer = animatedTheme.surfaceText,
            secondary = animatedTheme.yellow,
            onSecondary = animatedTheme.contrastingTextOn(animatedTheme.yellow),
            tertiary = animatedTheme.green,
            onTertiary = animatedTheme.contrastingTextOn(animatedTheme.green),
            background = animatedTheme.bg,
            onBackground = animatedTheme.text,
            surface = animatedTheme.surface,
            onSurface = animatedTheme.surfaceText,
            surfaceVariant = animatedTheme.surfaceVariant,
            onSurfaceVariant = animatedTheme.surfaceMutedText,
            outline = animatedTheme.gridLine,
            error = animatedTheme.red,
            onError = animatedTheme.contrastingTextOn(animatedTheme.red)
        )
    } else {
        lightColorScheme(
            primary = animatedTheme.primaryActionBg,
            onPrimary = animatedTheme.onPrimaryAction,
            primaryContainer = animatedTheme.surfaceVariant,
            onPrimaryContainer = animatedTheme.surfaceText,
            secondary = animatedTheme.yellow,
            onSecondary = animatedTheme.contrastingTextOn(animatedTheme.yellow),
            tertiary = animatedTheme.green,
            onTertiary = animatedTheme.contrastingTextOn(animatedTheme.green),
            background = animatedTheme.bg,
            onBackground = animatedTheme.text,
            surface = animatedTheme.surface,
            onSurface = animatedTheme.surfaceText,
            surfaceVariant = animatedTheme.surfaceVariant,
            onSurfaceVariant = animatedTheme.surfaceMutedText,
            outline = animatedTheme.gridLine,
            error = animatedTheme.red,
            onError = animatedTheme.contrastingTextOn(animatedTheme.red)
        )
    }

    CompositionLocalProvider(LocalLudoTheme provides animatedTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildLudoTypography(animatedTheme.font),
            shapes = LudoShapes,
            content = content
        )
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is Activity -> null
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
