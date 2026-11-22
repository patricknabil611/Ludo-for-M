package com.example.audio

import com.example.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioVolumeSettingsTest {

    @Test
    fun appSettings_defaultsToFullAudibleSfxAndMusicVolume() {
        val defaults = AppSettings()
        assertTrue(defaults.soundEffectsEnabled)
        assertTrue(defaults.musicEnabled)
        assertEquals(1f, defaults.soundEffectsVolume, 0.0001f)
        assertEquals(1f, defaults.musicVolume, 0.0001f)
    }

    @Test
    fun computeEffectiveVolume_scalesByMasterAndZeroesWhenDisabled() {
        val base = SoundEffect.DICE_ROLL.defaultVolume
        assertEquals(base, SoundManager.computeEffectiveVolume(base, 1f, enabled = true), 0.0001f)
        assertEquals(base * 0.5f, SoundManager.computeEffectiveVolume(base, 0.5f, enabled = true), 0.0001f)
        assertEquals(0f, SoundManager.computeEffectiveVolume(base, 0f, enabled = true), 0.0001f)
        assertEquals(0f, SoundManager.computeEffectiveVolume(base, 1f, enabled = false), 0.0001f)
    }

    @Test
    fun computeEffectiveVolume_clampsOutOfBoundsInputs() {
        val musicBase = SoundEffect.BACKGROUND_MUSIC.defaultVolume
        assertEquals(musicBase, SoundManager.computeEffectiveVolume(musicBase, 2.5f, enabled = true), 0.0001f)
        assertEquals(0f, SoundManager.computeEffectiveVolume(musicBase, -0.4f, enabled = true), 0.0001f)
        assertEquals(1f, SoundManager.computeEffectiveVolume(1.8f, 1.4f, enabled = true), 0.0001f)
    }
}
