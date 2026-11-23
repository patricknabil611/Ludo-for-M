package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.data.AvatarBackground

data class AvatarBackdropPalette(
    val top: Color,
    val bottom: Color,
    val accent: Color
)

fun AvatarBackground.backdropPalette(): AvatarBackdropPalette = when (this) {
    AvatarBackground.COBALT -> AvatarBackdropPalette(Color(0xFF42A5F5), Color(0xFF1565C0), Color(0xFFBBDEFB))
    AvatarBackground.SUNSET -> AvatarBackdropPalette(Color(0xFFFFB74D), Color(0xFFE65100), Color(0xFFFFE0B2))
    AvatarBackground.MINT -> AvatarBackdropPalette(Color(0xFF4DD0E1), Color(0xFF00897B), Color(0xFFB2DFDB))
    AvatarBackground.BERRY -> AvatarBackdropPalette(Color(0xFFEC407A), Color(0xFF880E4F), Color(0xFFF8BBD0))
    AvatarBackground.ROYAL -> AvatarBackdropPalette(Color(0xFF7E57C2), Color(0xFF311B92), Color(0xFFD1C4E9))
    AvatarBackground.OCEAN -> AvatarBackdropPalette(Color(0xFF26C6DA), Color(0xFF006064), Color(0xFFB2EBF2))
    AvatarBackground.GOLD -> AvatarBackdropPalette(Color(0xFFFFEE58), Color(0xFFF57F17), Color(0xFFFFF9C4))
    AvatarBackground.CORAL -> AvatarBackdropPalette(Color(0xFFFF7043), Color(0xFFC62828), Color(0xFFFFCCBC))
}

/**
 * Character illustration colors for vector cartoon avatars.
 * Isolated inside ui/theme so zero Color(0x...) literals appear in screens or components.
 */
object AvatarIllustrationColors {
    val ink = Color(0xFF1E2238)
    val dimmedVeil = Color(0x99202635)
    val rosyCheek = Color(0x55FF5252)

    val gold = Color(0xFFFFB800)
    val goldDark = Color(0xFFD48900)
    val goldLight = Color(0xFFFFF176)
    val haloLight = Color(0xFFFFF59D)
    val coral = Color(0xFFFF4D6D)

    // Fox
    val foxFur = Color(0xFFFF7043)
    val foxDark = Color(0xFFD84315)
    val foxCream = Color(0xFFFFF3E0)

    // Lion
    val lionMane = Color(0xFFD84315)
    val lionManeLight = Color(0xFFEF6C00)
    val lionFur = Color(0xFFFFCA28)
    val lionSnout = Color(0xFFFFF8E1)
    val lionNose = Color(0xFF5D4037)

    // Panda
    val pandaDark = Color(0xFF263238)
    val pandaWhite = Color(0xFFFAFAFA)

    // Frog
    val frogGreen = Color(0xFF43A047)
    val frogBelly = Color(0xFFC8E6C9)

    // Cat
    val catFur = Color(0xFF90A4AE)
    val catInnerEar = Color(0xFFFFAB91)
    val catStripe = Color(0xFF546E7A)
    val catIris = Color(0xFF2E7D32)

    // Owl
    val owlFeather = Color(0xFF6D4C41)
    val owlBelly = Color(0xFFFFECB3)
    val owlRing = Color(0xFFD7CCC8)
    val owlIris = Color(0xFFF57F17)
    val owlBeak = Color(0xFFFFB300)

    // Dragon
    val dragonScale = Color(0xFF00ACC1)
    val dragonScaleDark = Color(0xFF006064)
    val dragonHorn = Color(0xFFFFCA28)
    val dragonBelly = Color(0xFFB2EBF2)
    val dragonIris = Color(0xFFD84315)

    // Astronaut
    val astroSuit = Color(0xFFF5F7FA)
    val astroRim = Color(0xFFB0BEC5)
    val astroVisorTop = Color(0xFF1A237E)
    val astroVisorBottom = Color(0xFF3949AB)
    val astroEye = Color(0xFF80DEEA)

    // Knight
    val knightSteel = Color(0xFFCFD8DC)
    val knightSteelDark = Color(0xFF78909C)
    val knightPlume = Color(0xFFE53935)

    // Wizard
    val wizardHat = Color(0xFF5E35B1)
    val wizardSkin = Color(0xFFFFCCBC)
    val wizardBeard = Color(0xFFF5F5F5)

    // Ninja
    val ninjaHood = Color(0xFF263238)
    val ninjaBand = Color(0xFFE53935)
    val ninjaSkin = Color(0xFFFFD180)

    // Robot
    val robotBody = Color(0xFFECEFF1)
    val robotTrim = Color(0xFF607D8B)
    val robotScreen = Color(0xFF1A237E)
    val robotGlow = Color(0xFF00E5FF)

    // Accessories & Frames
    val lensDark = Color(0xFF1E2430)
    val headphoneBand = Color(0xFFFF4081)
    val headphoneCup = Color(0xFF263238)
    val neonOuter = Color(0xFF00E5FF)
    val neonInner = Color(0xFF18FFFF)
}
