package com.example.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Catalog of distinct game sound effects and background music synthesized as standard
 * 16-bit mono PCM WAV files for playback via Android's [android.media.MediaPlayer].
 */
enum class SoundEffect(
    val fileName: String,
    val defaultVolume: Float,
    val isMusic: Boolean = false
) {
    DICE_ROLL("sfx_dice_roll_v2.wav", 0.72f),
    TOKEN_MOVE("sfx_token_move_v2.wav", 0.60f),
    CAPTURE("sfx_capture_v2.wav", 0.85f),
    TOKEN_HOME("sfx_token_home_v2.wav", 0.75f),
    WIN_FANFARE("sfx_win_fanfare_v2.wav", 0.82f),
    TIMER_WARNING("sfx_timer_warning_v2.wav", 0.55f),
    SHIELD_PICKUP("sfx_shield_pickup_v2.wav", 0.72f),
    DOUBLE_DICE_PICKUP("sfx_double_dice_pickup_v2.wav", 0.72f),
    BOMB_PICKUP("sfx_bomb_pickup_v2.wav", 0.72f),
    SHIELD_ACTIVATE("sfx_shield_activate_v2.wav", 0.76f),
    DOUBLE_DICE_ACTIVATE("sfx_double_dice_activate_v2.wav", 0.76f),
    BOMB_ARM("sfx_bomb_arm_v2.wav", 0.76f),
    BOMB_EXPLOSION("sfx_bomb_explosion_v2.wav", 0.88f),
    BACKGROUND_MUSIC("bgm_ludo_loop_v2.wav", 0.24f, isMusic = true)
}

/**
 * Pure Kotlin PCM-to-WAV synthesizer that generates distinct, tactile cartoon board-game
 * sound effects with valid 44-byte RIFF/WAVE headers for Android [android.media.MediaPlayer].
 */
object WavAudioSynthesizer {

    const val SAMPLE_RATE = 22050
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val WAV_HEADER_BYTES = 44

    fun synthesizeWav(effect: SoundEffect): ByteArray {
        val pcm = synthesizePcm(effect)
        return encodePcm16ToWav(pcm, SAMPLE_RATE)
    }

    fun synthesizePcm(effect: SoundEffect): ShortArray = when (effect) {
        SoundEffect.DICE_ROLL -> synthesizeDiceRoll()
        SoundEffect.TOKEN_MOVE -> synthesizeTokenMove()
        SoundEffect.CAPTURE -> synthesizeCapture()
        SoundEffect.TOKEN_HOME -> synthesizeTokenHome()
        SoundEffect.WIN_FANFARE -> synthesizeWinFanfare()
        SoundEffect.TIMER_WARNING -> synthesizeTimerWarning()
        SoundEffect.SHIELD_PICKUP -> synthesizeShieldPickup()
        SoundEffect.DOUBLE_DICE_PICKUP -> synthesizeDoubleDicePickup()
        SoundEffect.BOMB_PICKUP -> synthesizeBombPickup()
        SoundEffect.SHIELD_ACTIVATE -> synthesizeShieldActivate()
        SoundEffect.DOUBLE_DICE_ACTIVATE -> synthesizeDoubleDiceActivate()
        SoundEffect.BOMB_ARM -> synthesizeBombArm()
        SoundEffect.BOMB_EXPLOSION -> synthesizeBombExplosion()
        SoundEffect.BACKGROUND_MUSIC -> synthesizeBackgroundMelody()
    }

    /**
     * Encodes mono 16-bit signed PCM samples into a standard little-endian RIFF/WAVE byte array.
     */
    fun encodePcm16ToWav(
        samples: ShortArray,
        sampleRate: Int = SAMPLE_RATE
    ): ByteArray {
        val dataSize = samples.size * 2
        val byteRate = sampleRate * CHANNELS * (BITS_PER_SAMPLE / 8)
        val blockAlign = CHANNELS * (BITS_PER_SAMPLE / 8)
        val buffer = ByteBuffer.allocate(WAV_HEADER_BYTES + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(36 + dataSize)
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // fmt sub-chunk (PCM = 1)
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16)
        buffer.putShort(1.toShort())
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(BITS_PER_SAMPLE.toShort())

        // data sub-chunk
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(dataSize)
        for (sample in samples) {
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    /**
     * Dice roll: staggered wooden rattle & decelerating table bounce clicks (340 ms).
     */
    private fun synthesizeDiceRoll(): ShortArray {
        val durationMs = 340
        val count = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(count)
        val rng = Random(42)
        // Staggered rattle clicks accelerating then settling onto the board
        val clickStartsMs = listOf(0, 42, 88, 142, 206, 274)
        val clickAmplitudes = listOf(0.85, 0.95, 1.0, 0.88, 0.72, 0.56)

        clickStartsMs.forEachIndexed { clickIdx, startMs ->
            val startIdx = (startMs * SAMPLE_RATE) / 1000
            val clickLen = (36 * SAMPLE_RATE) / 1000
            val baseFreq = 310.0 + rng.nextInt(210)
            val overtoneFreq = baseFreq * 2.15
            val amp = clickAmplitudes[clickIdx]
            for (i in 0 until clickLen) {
                val idx = startIdx + i
                if (idx >= count) break
                val t = i.toDouble() / SAMPLE_RATE
                val env = exp(-t * 105.0) * (1.0 - exp(-t * 950.0))
                val noise = (rng.nextDouble() * 2.0 - 1.0) * 0.42 * exp(-t * 160.0)
                val woodKnock = sin(2.0 * PI * baseFreq * t) * 0.62 +
                    sin(2.0 * PI * overtoneFreq * t) * 0.24
                val v = (noise + woodKnock) * env * amp * 22000.0
                out[idx] = (out[idx] + v.toInt()).coerceIn(-32767, 32767).toShort()
            }
        }
        return out
    }

    /**
     * Token move: snappy upward wooden piece hop/pop chirp (105 ms).
     */
    private fun synthesizeTokenMove(): ShortArray {
        val count = (SAMPLE_RATE * 105) / 1000
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / count
            val freq = 410.0 + progress * 310.0
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = exp(-t * 25.0) * (1.0 - exp(-t * 260.0))
            val body = 0.78 * sin(phase) + 0.22 * sin(phase * 2.0)
            val tap = sin(2.0 * PI * 980.0 * t) * exp(-t * 180.0) * 0.25
            out[i] = ((body + tap) * env * 20500.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * Capture: punchy knockout impact + dramatic comic downward slide (320 ms).
     */
    private fun synthesizeCapture(): ShortArray {
        val count = (SAMPLE_RATE * 320) / 1000
        val out = ShortArray(count)
        var slidePhase = 0.0
        var thumpPhase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / count
            val slideFreq = 640.0 - progress * 485.0
            val thumpFreq = 150.0 - progress * 85.0
            slidePhase += 2.0 * PI * slideFreq / SAMPLE_RATE
            thumpPhase += 2.0 * PI * thumpFreq / SAMPLE_RATE

            val slideEnv = exp(-t * 7.8) * (1.0 - exp(-t * 220.0))
            val thumpEnv = exp(-t * 24.0)
            val slideWave = 0.62 * sin(slidePhase) + 0.28 * sin(slidePhase * 1.5)
            val impactThump = 0.55 * sin(thumpPhase) * thumpEnv
            val sample = (slideWave * slideEnv + impactThump) * 24500.0
            out[i] = sample.toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthesizeTokenHome(): ShortArray =
        buildArpeggio(listOf(523.25 to 90, 659.25 to 90, 783.99 to 110, 1046.50 to 210))

    private fun synthesizeWinFanfare(): ShortArray =
        buildArpeggio(
            listOf(
                523.25 to 110,
                659.25 to 110,
                783.99 to 110,
                1046.50 to 160,
                783.99 to 110,
                1046.50 to 360
            )
        )

    private fun synthesizeTimerWarning(): ShortArray {
        val count = (SAMPLE_RATE * 90) / 1000
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 32.0)
            out[i] = (sin(2.0 * PI * 880.0 * t) * env * 15000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthesizeShieldPickup(): ShortArray =
        buildArpeggio(listOf(587.33 to 75, 880.00 to 85, 1174.66 to 150))

    private fun synthesizeDoubleDicePickup(): ShortArray =
        buildArpeggio(listOf(659.25 to 65, 987.77 to 65, 659.25 to 65, 1318.51 to 140))

    private fun synthesizeBombPickup(): ShortArray =
        buildArpeggio(listOf(440.00 to 70, 349.23 to 75, 523.25 to 120))

    private fun synthesizeShieldActivate(): ShortArray {
        val count = (SAMPLE_RATE * 280) / 1000
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val p = i.toDouble() / count
            val freq = 360.0 + p * 540.0
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = exp(-t * 6.5) * (1.0 - exp(-t * 90.0))
            val wave = 0.65 * sin(phase) + 0.35 * sin(phase * 1.5)
            out[i] = (wave * env * 20000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthesizeDoubleDiceActivate(): ShortArray =
        buildArpeggio(listOf(523.25 to 60, 783.99 to 60, 1046.50 to 70, 1318.51 to 150))

    private fun synthesizeBombArm(): ShortArray =
        buildArpeggio(listOf(740.0 to 55, 0.0 to 35, 987.77 to 95))

    private fun synthesizeBombExplosion(): ShortArray {
        val count = (SAMPLE_RATE * 380) / 1000
        val out = ShortArray(count)
        val rng = Random(99)
        var boomPhase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val p = i.toDouble() / count
            val env = exp(-t * 8.5)
            val boomFreq = 160.0 - p * 105.0
            boomPhase += 2.0 * PI * boomFreq / SAMPLE_RATE
            val boom = sin(boomPhase) * 0.72
            val crackle = (rng.nextDouble() * 2.0 - 1.0) * 0.55 * exp(-t * 14.0)
            out[i] = ((boom + crackle) * env * 28000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthesizeBackgroundMelody(): ShortArray {
        val melody = listOf(
            523.25 to 170, 659.25 to 170, 783.99 to 170, 659.25 to 170,
            880.00 to 240, 783.99 to 240, 659.25 to 170, 523.25 to 170,
            587.33 to 170, 698.46 to 170, 880.00 to 170, 698.46 to 170,
            783.99 to 320, 0.0 to 120,
            523.25 to 170, 659.25 to 170, 783.99 to 170, 1046.50 to 240,
            880.00 to 170, 783.99 to 170, 698.46 to 170, 659.25 to 170,
            587.33 to 170, 659.25 to 170, 587.33 to 170, 493.88 to 170,
            523.25 to 360, 0.0 to 200
        )
        val totalSamples = melody.sumOf { (_, ms) -> (SAMPLE_RATE * ms) / 1000 }
        val out = ShortArray(totalSamples)
        var cursor = 0
        for ((freq, ms) in melody) {
            val n = (SAMPLE_RATE * ms) / 1000
            if (freq > 0.0) {
                for (i in 0 until n) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = exp(-t * 9.5) * (1.0 - exp(-t * 140.0))
                    val wave = 0.80 * sin(2.0 * PI * freq * t) + 0.20 * sin(2.0 * PI * (freq * 2.0) * t)
                    out[cursor + i] = (wave * env * 13000.0).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
            cursor += n
        }
        return out
    }

    private fun buildArpeggio(notes: List<Pair<Double, Int>>): ShortArray {
        val total = notes.sumOf { (_, ms) -> (SAMPLE_RATE * ms) / 1000 }
        val out = ShortArray(total)
        var cursor = 0
        for ((freq, ms) in notes) {
            val n = (SAMPLE_RATE * ms) / 1000
            if (freq > 0.0) {
                for (i in 0 until n) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = exp(-t * 11.0) * (1.0 - exp(-t * 180.0))
                    val wave = 0.75 * sin(2.0 * PI * freq * t) + 0.25 * sin(2.0 * PI * (freq * 2.0) * t)
                    out[cursor + i] = (wave * env * 19500.0).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
            cursor += n
        }
        return out
    }
}
