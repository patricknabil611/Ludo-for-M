package com.example.audio

import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SoundManagerTest {

    private class RecordingMediaPlayer : MediaPlayer() {
        var dataSourceSet = false
        var prepared = false
        var started = false
        var stopped = false
        var released = false
        var volumeLeft = -1f
        var volumeRight = -1f
        var loopingFlag = false
        var completionListener: OnCompletionListener? = null
        var errorListener: OnErrorListener? = null

        override fun setDataSource(fd: FileDescriptor) {
            dataSourceSet = true
        }

        override fun setVolume(leftVolume: Float, rightVolume: Float) {
            volumeLeft = leftVolume
            volumeRight = rightVolume
        }

        override fun setLooping(looping: Boolean) {
            loopingFlag = looping
        }

        override fun setOnCompletionListener(listener: OnCompletionListener?) {
            completionListener = listener
        }

        override fun setOnErrorListener(listener: OnErrorListener?) {
            errorListener = listener
        }

        override fun prepare() {
            prepared = true
        }

        override fun start() {
            started = true
        }

        override fun isPlaying(): Boolean = started && !stopped && !released

        override fun stop() {
            stopped = true
            started = false
        }

        override fun reset() = Unit

        override fun release() {
            released = true
            started = false
        }
    }

    @Test
    fun synthesizesValidDistinctWavFilesForDiceMoveAndCapture() {
        val diceWav = WavAudioSynthesizer.synthesizeWav(SoundEffect.DICE_ROLL)
        val moveWav = WavAudioSynthesizer.synthesizeWav(SoundEffect.TOKEN_MOVE)
        val captureWav = WavAudioSynthesizer.synthesizeWav(SoundEffect.CAPTURE)

        for (wav in listOf(diceWav, moveWav, captureWav)) {
            assertTrue("WAV must exceed 44-byte header", wav.size > WavAudioSynthesizer.WAV_HEADER_BYTES)
            assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
            assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
            assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))

            val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(1.toShort(), header.getShort(20)) // PCM format
            assertEquals(1.toShort(), header.getShort(22)) // Mono
            assertEquals(WavAudioSynthesizer.SAMPLE_RATE, header.getInt(24))
            assertEquals(16.toShort(), header.getShort(34)) // 16-bit
            assertEquals(wav.size - WavAudioSynthesizer.WAV_HEADER_BYTES, header.getInt(40))
        }

        // Verify all three core effects have distinct durations & non-silent waveforms
        assertNotEquals(diceWav.size, moveWav.size)
        assertNotEquals(moveWav.size, captureWav.size)
        assertNotEquals(diceWav.size, captureWav.size)

        val dicePeak = WavAudioSynthesizer.synthesizePcm(SoundEffect.DICE_ROLL).maxOf { abs(it.toInt()) }
        val movePeak = WavAudioSynthesizer.synthesizePcm(SoundEffect.TOKEN_MOVE).maxOf { abs(it.toInt()) }
        val capturePeak = WavAudioSynthesizer.synthesizePcm(SoundEffect.CAPTURE).maxOf { abs(it.toInt()) }
        assertTrue("Dice roll should be audible", dicePeak > 2000)
        assertTrue("Token move should be audible", movePeak > 2000)
        assertTrue("Capture should be audible", capturePeak > 2000)
    }

    @Test
    fun playsDiceMoveAndCaptureViaMediaPlayerAndReleasesOnCompletion() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val createdPlayers = mutableListOf<RecordingMediaPlayer>()
        val manager = SoundManager(
            context = context,
            ioDispatcher = UnconfinedTestDispatcher(),
            mediaPlayerFactory = {
                RecordingMediaPlayer().also { createdPlayers += it }
            }
        )

        manager.playDiceRoll()
        manager.playTokenMove()
        manager.playCapture()

        assertEquals(3, createdPlayers.size)
        for (mp in createdPlayers) {
            assertTrue(mp.dataSourceSet)
            assertTrue(mp.prepared)
            assertTrue(mp.started)
            assertFalse(mp.released)
        }

        // Distinct volumes per effect
        assertEquals(SoundEffect.DICE_ROLL.defaultVolume, createdPlayers[0].volumeLeft, 0.001f)
        assertEquals(SoundEffect.TOKEN_MOVE.defaultVolume, createdPlayers[1].volumeLeft, 0.001f)
        assertEquals(SoundEffect.CAPTURE.defaultVolume, createdPlayers[2].volumeLeft, 0.001f)

        // Completion callback releases MediaPlayer cleanly
        createdPlayers[0].completionListener?.onCompletion(createdPlayers[0])
        assertTrue(createdPlayers[0].released)

        // Releasing SoundManager stops & releases all remaining active MediaPlayers
        manager.release()
        assertTrue(createdPlayers[1].released)
        assertTrue(createdPlayers[2].released)
    }

    @Test
    fun respectsSoundEffectsToggleWhenMuted() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val createdPlayers = mutableListOf<RecordingMediaPlayer>()
        val manager = SoundManager(
            context = context,
            ioDispatcher = UnconfinedTestDispatcher(),
            mediaPlayerFactory = {
                RecordingMediaPlayer().also { createdPlayers += it }
            }
        )

        manager.updateSettings(soundEffects = false)
        manager.playDiceRoll()
        manager.playTokenMove()
        manager.playCapture()

        assertEquals(0, createdPlayers.size)
        manager.release()
    }

    @Test
    fun cachesValidWavFilesOnDiskForCoreEffects() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = SoundManager(
            context = context,
            ioDispatcher = UnconfinedTestDispatcher(),
            mediaPlayerFactory = { RecordingMediaPlayer() }
        )

        val diceFile = manager.ensureWavFile(SoundEffect.DICE_ROLL)
        val moveFile = manager.ensureWavFile(SoundEffect.TOKEN_MOVE)
        val captureFile = manager.ensureWavFile(SoundEffect.CAPTURE)

        assertTrue(diceFile.exists() && diceFile.length() > 44)
        assertTrue(moveFile.exists() && moveFile.length() > 44)
        assertTrue(captureFile.exists() && captureFile.length() > 44)
        assertNotEquals(diceFile.absolutePath, moveFile.absolutePath)
        assertNotEquals(moveFile.absolutePath, captureFile.absolutePath)

        manager.release()
    }
}
