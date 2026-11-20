package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LudoLightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = CobaltDark,
    secondary = SunnyGold,
    onSecondary = Color(0xFF2C1E00),
    secondaryContainer = Color(0xFFFFF0C2),
    onSecondaryContainer = Color(0xFF473000),
    tertiary = MintGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7FCE6),
    onTertiaryContainer = Color(0xFF003919),
    background = CreamBackground,
    onBackground = InkDark,
    surface = CreamSurface,
    onSurface = InkDark,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = OutlineSoft,
    error = CoralPink,
    onError = Color.White
)

private val LudoShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun LudoForMTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LudoLightColorScheme,
        typography = LudoTypography,
        shapes = LudoShapes,
        content = content
    )
}
