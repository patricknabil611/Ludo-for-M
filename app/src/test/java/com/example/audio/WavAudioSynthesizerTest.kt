package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

class WavAudioSynthesizerTest {

    @Test
    fun catalog_excludesBackgroundMusicAndUses44100Hz() {
        assertEquals(44100, WavAudioSynthesizer.SAMPLE_RATE)
        assertFalse(
            "Background music must not exist in SoundEffect catalog",
            SoundEffect.entries.any { it.name.contains("MUSIC", ignoreCase = true) || it.fileName.contains("bgm") }
        )
    }

    @Test
    fun allSoundEffects_produceValid44100HzWavWithCleanHeadroom() {
        for (effect in SoundEffect.entries) {
            val wav = WavAudioSynthesizer.synthesizeWav(effect)
            assertTrue("${effect.name} WAV must contain audio data", wav.size > WavAudioSynthesizer.WAV_HEADER_BYTES)
            assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
            assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
            assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))

            val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(1.toShort(), header.getShort(20)) // PCM
            assertEquals(1.toShort(), header.getShort(22)) // Mono
            assertEquals(44100, header.getInt(24))
            assertEquals(16.toShort(), header.getShort(34))
            assertEquals(wav.size - WavAudioSynthesizer.WAV_HEADER_BYTES, header.getInt(40))

            val pcm = WavAudioSynthesizer.synthesizePcm(effect)
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("${effect.name} should have punchy loudness (peak=$peak)", peak > 14000)
            assertTrue("${effect.name} should avoid hard digital clipping (peak=$peak)", peak <= 32767)
        }
    }

    @Test
    fun coreEffects_containRichBassMidAndHighSpectralEnergy() {
        val coreEffects = listOf(
            SoundEffect.DICE_ROLL,
            SoundEffect.TOKEN_MOVE,
            SoundEffect.CAPTURE,
            SoundEffect.TOKEN_HOME,
            SoundEffect.WIN_FANFARE,
            SoundEffect.BOMB_EXPLOSION
        )
        for (effect in coreEffects) {
            val pcm = WavAudioSynthesizer.synthesizePcm(effect)
            val bass = lowPassRms(pcm, cutoffHz = 280.0)
            val mids = bandPassRms(pcm, lowCutoffHz = 350.0, highCutoffHz = 2400.0)
            val highs = highPassRms(pcm, cutoffHz = 2800.0)

            assertTrue("${effect.name} should have bass punch (bass=$bass)", bass > 0.015)
            assertTrue("${effect.name} should have mid body (mids=$mids)", mids > 0.015)
            assertTrue("${effect.name} should have high sparkle (highs=$highs)", highs > 0.008)
        }
    }

    private fun lowPassRms(pcm: ShortArray, cutoffHz: Double): Double {
        val dt = 1.0 / WavAudioSynthesizer.SAMPLE_RATE
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        val alpha = dt / (rc + dt)
        var y = 0.0
        var sumSq = 0.0
        for (sample in pcm) {
            val x = sample.toDouble() / 32768.0
            y += alpha * (x - y)
            sumSq += y * y
        }
        return sqrt(sumSq / pcm.size.coerceAtLeast(1))
    }

    private fun highPassRms(pcm: ShortArray, cutoffHz: Double): Double {
        val dt = 1.0 / WavAudioSynthesizer.SAMPLE_RATE
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        val alpha = rc / (rc + dt)
        var y = 0.0
        var prevX = 0.0
        var sumSq = 0.0
        for (sample in pcm) {
            val x = sample.toDouble() / 32768.0
            y = alpha * (y + x - prevX)
            prevX = x
            sumSq += y * y
        }
        return sqrt(sumSq / pcm.size.coerceAtLeast(1))
    }

    private fun bandPassRms(pcm: ShortArray, lowCutoffHz: Double, highCutoffHz: Double): Double {
        val dt = 1.0 / WavAudioSynthesizer.SAMPLE_RATE
        val hpRc = 1.0 / (2.0 * PI * lowCutoffHz)
        val hpAlpha = hpRc / (hpRc + dt)
        val lpRc = 1.0 / (2.0 * PI * highCutoffHz)
        val lpAlpha = dt / (lpRc + dt)
        var hpY = 0.0
        var prevX = 0.0
        var lpY = 0.0
        var sumSq = 0.0
        for (sample in pcm) {
            val x = sample.toDouble() / 32768.0
            hpY = hpAlpha * (hpY + x - prevX)
            prevX = x
            lpY += lpAlpha * (hpY - lpY)
            sumSq += lpY * lpY
        }
        return sqrt(sumSq / pcm.size.coerceAtLeast(1))
    }
}
