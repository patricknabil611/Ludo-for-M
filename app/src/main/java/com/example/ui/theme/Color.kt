package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.engine.GameModeId
import com.example.engine.PlayerColor
import com.example.engine.PowerUpType

// Player Color Palettes
data class PlayerPalette(
    val primary: Color,
    val dark: Color,
    val light: Color,
    val highlight: Color,
    val onPrimary: Color = Color.White
)

fun PlayerColor.palette(theme: LudoTheme = ArcadeLightTheme): PlayerPalette =
    theme.playerPalette(this)

fun modeAccentColor(mode: GameModeId, theme: LudoTheme = ArcadeLightTheme): Color = when (mode) {
    GameModeId.CLASSIC -> theme.blue
    GameModeId.BLOCKADES -> lerp(theme.blue, theme.green, 0.35f)
    GameModeId.MASTER -> theme.red
    GameModeId.QUICK -> theme.green
    GameModeId.ARROW -> lerp(theme.red, theme.yellow, 0.50f)
    GameModeId.MAGIC -> lerp(theme.blue, theme.red, 0.50f)
    GameModeId.TIMED -> lerp(theme.blue, theme.green, 0.20f)
    GameModeId.CHAOS -> lerp(theme.red, theme.yellow, 0.25f)
    GameModeId.ELIMINATION -> lerp(theme.red, theme.blue, 0.35f)
}

fun powerUpAccentColor(type: PowerUpType, theme: LudoTheme = ArcadeLightTheme): Color = when (type) {
    PowerUpType.ROCKET -> lerp(theme.red, theme.yellow, 0.45f)
    PowerUpType.GOLDEN_DICE -> theme.yellow
    PowerUpType.SHIELD -> theme.blue
    PowerUpType.BOMB_TRAP -> theme.red
}

fun powerUpSoftBg(type: PowerUpType, theme: LudoTheme = ArcadeLightTheme): Color {
    val accent = powerUpAccentColor(type, theme)
    return if (theme.isDarkSurface) {
        lerp(theme.surface, accent, 0.22f)
    } else {
        lerp(theme.surface, accent, 0.14f)
    }
}

/**
 * Vector illustration palette for customizable player avatars.
 * Kept inside ui/theme so zero hardcoded Color(0x...) literals exist in UI screens or components.
 */
object AvatarArtPalette {
    val white = Color(0xFFFFFFFF)
    val ink = Color(0xFF1B1F3B)
    val darkInk = Color(0xFF14182E)
    val outline = Color(0xFF23284A)

    // Fox
    val foxFur = Color(0xFFFF7A2F)
    val foxDark = Color(0xFFD95816)
    val foxCheek = Color(0xFFFFF3E4)

    // Panda
    val pandaEar = Color(0xFF23284A)
    val pandaBlush = Color(0xFFFF96B4)

    // Bot
    val botMetal = Color(0xFFC4D1E8)
    val botMetalDark = Color(0xFF8EA1C2)
    val botVisor = Color(0xFF1E294A)
    val botGlow = Color(0xFF3EF2C8)

    // Dragon
    val dragonScale = Color(0xFF29C26D)
    val dragonBelly = Color(0xFFFFE57F)
    val dragonHorn = Color(0xFFFF9B3D)

    // Cat
    val catFur = Color(0xFFFFC15E)
    val catStripe = Color(0xFFE08E2B)
    val catInnerEar = Color(0xFFFFA5B8)

    // Owl
    val owlFeather = Color(0xFF8C5CE6)
    val owlBelly = Color(0xFFEFE6FF)
    val owlBeak = Color(0xFFFFB703)

    // Frog
    val frogSkin = Color(0xFF42D158)
    val frogBelly = Color(0xFFCFFFD2)

    // Alien
    val alienSkin = Color(0xFF39D9B8)
    val alienTip = Color(0xFFFFDF5E)

    // Bear
    val bearFur = Color(0xFFB56E43)
    val bearMuzzle = Color(0xFFF5D5B5)

    // Unicorn
    val unicornCoat = Color(0xFFFDF7FF)
    val unicornMane = Color(0xFFFF66A6)
    val unicornHorn = Color(0xFFFFCF33)

    // Accessories
    val crownGold = Color(0xFFFFC833)
    val crownGem = Color(0xFFFF3B6B)
    val visorCyan = Color(0xFF22D3EE)
    val capRed = Color(0xFFEF4444)
    val haloGold = Color(0xFFFFE26A)
    val headphonesCoral = Color(0xFFFF5A7A)
    val flowerPink = Color(0xFFFF6FAE)
    val bowSky = Color(0xFF38BDF8)

    // Preset background chips
    val bgSky = Color(0xFF4CA8FF)
    val bgMint = Color(0xFF2ECC8B)
    val bgPeach = Color(0xFFFF8F66)
    val bgLavender = Color(0xFF9C72FF)
    val bgGold = Color(0xFFFFC43D)
    val bgBerry = Color(0xFFFF5C8A)
    val bgTeal = Color(0xFF1FB5C9)
    val bgIndigo = Color(0xFF4B5CD7)

    // Frame ring accents
    val ringGold = Color(0xFFFFC436)
    val ringMint = Color(0xFF25D38E)
    val ringCoral = Color(0xFFFF5A79)
    val ringSky = Color(0xFF3D8BFF)
    val ringViolet = Color(0xFF9B6BFF)
    val ringObsidian = Color(0xFF23284A)

    // Medal podium accents
    val medalGold = Color(0xFFFFC107)
    val medalSilver = Color(0xFFB0BEC5)
    val medalBronze = Color(0xFFCD7F32)
}
