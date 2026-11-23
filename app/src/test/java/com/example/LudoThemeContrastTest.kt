package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AllLudoThemes
import com.example.ui.theme.ArcadeLightTheme
import com.example.ui.theme.BaseCornerStyle
import com.example.ui.theme.CandyPopTheme
import com.example.ui.theme.LudoThemeId
import com.example.ui.theme.NightArcadeTheme
import com.example.ui.theme.RoyalLoungeTheme
import com.example.ui.theme.ThemeShadowSpec
import com.example.ui.theme.contrastRatio
import com.example.ui.theme.toLudoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoThemeContrastTest {

    @Test
    fun defaultThemeIsArcadeLightAndBorderless() {
        assertEquals(LudoThemeId.ARCADE_LIGHT, LudoThemeId.DEFAULT)
        assertEquals(LudoThemeId.ARCADE_LIGHT, LudoThemeId.fromStorageKey(null))
        assertEquals(LudoThemeId.ARCADE_LIGHT, LudoThemeId.fromStorageKey("unknown_theme"))
        assertEquals(ArcadeLightTheme, LudoThemeId.ARCADE_LIGHT.toLudoTheme())

        assertNull("Arcade Light must be borderless (frame == null)", ArcadeLightTheme.frame)
        assertEquals(0.dp, ArcadeLightTheme.framePadding)
        assertEquals(ThemeShadowSpec.None, ArcadeLightTheme.boardShadow)
        assertEquals(ArcadeLightTheme.bg, ArcadeLightTheme.board)
        assertEquals(BaseCornerStyle.TINT, ArcadeLightTheme.baseStyle)
    }

    @Test
    fun allFourThemesExistWithExpectedProperties() {
        assertEquals(4, AllLudoThemes.size)
        assertEquals(
            listOf(ArcadeLightTheme, CandyPopTheme, NightArcadeTheme, RoyalLoungeTheme),
            AllLudoThemes
        )
        assertTrue(ArcadeLightTheme.isLightSystemBars)
        assertTrue(CandyPopTheme.isLightSystemBars)
        assertTrue(!NightArcadeTheme.isLightSystemBars)
        assertTrue(!RoyalLoungeTheme.isLightSystemBars)
    }

    @Test
    fun allFourThemesPassWcagContrastForTextAndPrimaryControls() {
        for (theme in AllLudoThemes) {
            val textOnBg = contrastRatio(theme.text, theme.bg)
            assertTrue(
                "${theme.displayName} text/bg contrast $textOnBg < 4.5",
                textOnBg >= 4.5f
            )

            val mutedOnBg = contrastRatio(theme.mutedText, theme.bg)
            assertTrue(
                "${theme.displayName} mutedText/bg contrast $mutedOnBg < 4.5",
                mutedOnBg >= 4.5f
            )

            val surfaceText = contrastRatio(theme.surfaceText, theme.surface)
            assertTrue(
                "${theme.displayName} surfaceText/surface contrast $surfaceText < 4.5",
                surfaceText >= 4.5f
            )

            val surfaceMuted = contrastRatio(theme.surfaceMutedText, theme.surface)
            assertTrue(
                "${theme.displayName} surfaceMutedText/surface contrast $surfaceMuted < 4.5",
                surfaceMuted >= 4.5f
            )

            val primaryBtn = contrastRatio(theme.onPrimaryAction, theme.primaryActionBg)
            assertTrue(
                "${theme.displayName} primaryAction contrast $primaryBtn < 4.5",
                primaryBtn >= 4.5f
            )

            val pipOnDie = contrastRatio(theme.pip, theme.dieFace)
            assertTrue(
                "${theme.displayName} pip/dieFace contrast $pipOnDie < 4.5",
                pipOnDie >= 4.5f
            )

            // Verify dynamic helpers guarantee >= 4.5:1 for all 4 player colors on bg and surface
            listOf(theme.red, theme.green, theme.yellow, theme.blue).forEach { accent ->
                val accentOnBg = theme.accessibleAccentOn(accent, theme.bg)
                assertTrue(
                    "${theme.displayName} accessibleAccentOn(bg) < 4.5",
                    contrastRatio(accentOnBg, theme.bg) >= 4.5f
                )
                val accentOnSurface = theme.accessibleAccentOn(accent, theme.surface)
                assertTrue(
                    "${theme.displayName} accessibleAccentOn(surface) < 4.5",
                    contrastRatio(accentOnSurface, theme.surface) >= 4.5f
                )
                val textOnAccent = theme.contrastingTextOn(accent)
                assertTrue(
                    "${theme.displayName} contrastingTextOn(accent) < 3.0",
                    contrastRatio(textOnAccent, accent) >= 3.0f
                )
            }
        }
    }

    @Test
    fun exactTokenValuesMatchSpecification() {
        assertEquals(Color(0xFFF3F5FC), ArcadeLightTheme.bg)
        assertEquals(Color(0xFFE8ECFF), CandyPopTheme.bg)
        assertEquals(Color(0xFF0B0E20), NightArcadeTheme.bg)
        assertEquals(Color(0xFF0F3A2C), RoyalLoungeTheme.bg)

        assertEquals(Color(0xFF2F80FF), ArcadeLightTheme.blue)
        assertEquals(Color(0xFF2C7BF2), CandyPopTheme.blue)
        assertEquals(Color(0xFF4D9BFF), NightArcadeTheme.blue)
        assertEquals(Color(0xFF2A55A0), RoyalLoungeTheme.blue)
    }
}
