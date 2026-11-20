package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.engine.PlayerColor

// App Cartoon Palette
val CreamBackground = Color(0xFFFFF8EC)
val CreamSurface = Color(0xFFFFFFFF)
val CreamSurfaceVariant = Color(0xFFF4ECDA)
val SkyTopTint = Color(0xFFEAF3FF)

val CobaltPrimary = Color(0xFF2B4EFF)
val CobaltDark = Color(0xFF1932B8)
val SunnyGold = Color(0xFFFFB800)
val SunnyGoldDark = Color(0xFFD48900)
val MintGreen = Color(0xFF18C964)
val MintGreenDark = Color(0xFF0B8E44)
val CoralPink = Color(0xFFFF4D6D)
val CoralPinkDark = Color(0xFFC9184A)

val InkDark = Color(0xFF1E2238)
val InkMuted = Color(0xFF5D6380)
val OutlineSoft = Color(0xFFD8CEB8)
val BoardWoodRim = Color(0xFFE6D5B8)
val BoardCellBorder = Color(0xFFC9BEA8)
val SafeStarGold = Color(0xFFFFB300)

// Player Color Palettes
data class PlayerPalette(
    val primary: Color,
    val dark: Color,
    val light: Color,
    val highlight: Color,
    val onPrimary: Color = Color.White
)

val RedPalette = PlayerPalette(
    primary = Color(0xFFFF3B4E),
    dark = Color(0xFFB81124),
    light = Color(0xFFFFE5E8),
    highlight = Color(0xFFFF7583)
)

val GreenPalette = PlayerPalette(
    primary = Color(0xFF18C964),
    dark = Color(0xFF0A843F),
    light = Color(0xFFDCFCE9),
    highlight = Color(0xFF5EE896)
)

val YellowPalette = PlayerPalette(
    primary = Color(0xFFFFBE0B),
    dark = Color(0xFFC48200),
    light = Color(0xFFFFF5D1),
    highlight = Color(0xFFFFD859),
    onPrimary = Color(0xFF2C1E00)
)

val BluePalette = PlayerPalette(
    primary = Color(0xFF257CFF),
    dark = Color(0xFF0D4BB3),
    light = Color(0xFFDCEBFF),
    highlight = Color(0xFF6DA8FF)
)

fun PlayerColor.palette(): PlayerPalette = when (this) {
    PlayerColor.RED -> RedPalette
    PlayerColor.GREEN -> GreenPalette
    PlayerColor.YELLOW -> YellowPalette
    PlayerColor.BLUE -> BluePalette
}
