package com.example.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Catalog of distinct in-game sound effects synthesized as studio-mastered 44.1 kHz
 * 16-bit PCM WAV files for playback via Android's [android.media.MediaPlayer].
 */
enum class SoundEffect(
    val fileName: String,
    val defaultVolume: Float
) {
    DICE_ROLL("sfx_dice_roll_v3.wav", 0.88f),
    TOKEN_MOVE("sfx_token_move_v3.wav", 0.76f),
    CAPTURE("sfx_capture_v3.wav", 0.94f),
    TOKEN_HOME("sfx_token_home_v3.wav", 0.88f),
    WIN_FANFARE("sfx_win_fanfare_v3.wav", 0.92f),
    TIMER_WARNING("sfx_timer_warning_v3.wav", 0.70f),
    SHIELD_PICKUP("sfx_shield_pickup_v3.wav", 0.84f),
    DOUBLE_DICE_PICKUP("sfx_double_dice_pickup_v3.wav", 0.84f),
    BOMB_PICKUP("sfx_bomb_pickup_v3.wav", 0.84f),
    SHIELD_ACTIVATE("sfx_shield_activate_v3.wav", 0.88f),
    DOUBLE_DICE_ACTIVATE("sfx_double_dice_activate_v3.wav", 0.88f),
    BOMB_ARM("sfx_bomb_arm_v3.wav", 0.86f),
    BOMB_EXPLOSION("sfx_bomb_explosion_v3.wav", 0.96f)
}

/**
 * High-fidelity pure Kotlin PCM-to-WAV synthesizer engineered to pop clearly on mobile
 * speakers with layered Bass (sub + warm upper-bass harmonics), articulate Mids, and
 * sparkling Highs, finished with a 3-band mobile EQ and soft-clip mastering limiter.
 */
object WavAudioSynthesizer {

    const val SAMPLE_RATE = 44100
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
     * Dice roll (440 ms): wooden dice tumbling and bouncing across a resonant tabletop.
     * - Bass: warm wooden table thuds (115–190 Hz + upper-bass 2nd harmonic)
     * - Mids: woody hollow body resonance (480–1150 Hz band-passed impacts)
     * - Highs: crisp ivory/hardwood clack transients (3.0–7.5 kHz bursts)
     */
    private fun synthesizeDiceRoll(): ShortArray {
        val durationMs = 440
        val count = samplesForMs(durationMs)
        val buf = DoubleArray(count)
        val rng = Random(4242)

        val bounceTimesMs = listOf(0, 48, 92, 146, 206, 274, 344)
        val bounceAmps = listOf(0.84, 1.00, 0.92, 0.96, 0.80, 0.68, 0.54)

        bounceTimesMs.forEachIndexed { idx, startMs ->
            val startIdx = samplesForMs(startMs)
            val hitLen = samplesForMs(68)
            val amp = bounceAmps[idx]

            val tableFreq = 128.0 + (idx % 3) * 18.0
            val woodMidFreq = 540.0 + rng.nextDouble() * 340.0
            val clackHighFreq = 3200.0 + rng.nextDouble() * 2400.0

            val midFilter = BiquadFilter.bandPass(SAMPLE_RATE, woodMidFreq, q = 3.2)
            val highFilter = BiquadFilter.bandPass(SAMPLE_RATE, clackHighFreq, q = 1.8)

            for (i in 0 until hitLen) {
                val pos = startIdx + i
                if (pos >= count) break
                val t = i.toDouble() / SAMPLE_RATE

                // Bass tabletop thud (fundamental + psychoacoustic 2nd harmonic)
                val bassEnv = exp(-t * 48.0) * (1.0 - exp(-t * 1400.0))
                val bass = (sin(2.0 * PI * tableFreq * t) * 0.72 +
                    sin(2.0 * PI * (tableFreq * 2.0) * t) * 0.36) * bassEnv * 0.62

                // Resonant hardwood body knock (mids)
                val noise = rng.nextDouble() * 2.0 - 1.0
                val midEnv = exp(-t * 74.0) * (1.0 - exp(-t * 2000.0))
                val woodTone = (sin(2.0 * PI * woodMidFreq * t) * 0.55 +
                    sin(2.0 * PI * (woodMidFreq * 1.48) * t) * 0.25 +
                    midFilter.process(noise) * 0.75) * midEnv * 0.65

                // Crisp ivory edge click (highs)
                val highEnv = exp(-t * 210.0) * (1.0 - exp(-t * 3600.0))
                val clack = (highFilter.process(noise) * 0.85 +
                    sin(2.0 * PI * (clackHighFreq * 1.12) * t) * 0.25) * highEnv * 0.58

                buf[pos] += (bass + woodTone + clack) * amp
            }
        }

        return masterForMobileSpeaker(buf, drive = 1.35, targetPeak = 0.90)
    }

    /**
     * Token move (130 ms): tactile glossy marimba-wood hop with warm low thump,
     * bouncy pitch-rising mid body, and crisp top-end click so every step pops.
     */
    private fun synthesizeTokenMove(): ShortArray {
        val count = samplesForMs(130)
        val buf = DoubleArray(count)
        val rng = Random(1301)
        val clickFilter = BiquadFilter.bandPass(SAMPLE_RATE, 4600.0, q = 1.6)

        var bassPhase = 0.0
        var midPhase = 0.0
        var sparklePhase = 0.0

        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val p = i.toDouble() / count

            // Punchy warm bass lift (145 Hz -> 235 Hz + 2nd harmonic for speaker clarity)
            val bassFreq = 145.0 + p * 90.0
            bassPhase += 2.0 * PI * bassFreq / SAMPLE_RATE
            val bassEnv = exp(-t * 34.0) * (1.0 - exp(-t * 1400.0))
            val bass = (sin(bassPhase) * 0.75 + sin(bassPhase * 2.0) * 0.35) * bassEnv * 0.56

            // Rounded wooden marimba mid hop (420 Hz -> 730 Hz)
            val midFreq = 420.0 + p * 310.0
            midPhase += 2.0 * PI * midFreq / SAMPLE_RATE
            val midEnv = exp(-t * 22.0) * (1.0 - exp(-t * 1100.0))
            val mids = (sin(midPhase) * 0.72 +
                sin(midPhase * 2.0) * 0.28 +
                sin(midPhase * 3.0) * 0.12) * midEnv * 0.68

            // Crisp glossy tap transient + airy wooden sparkle (highs)
            val sparkleFreq = 3200.0 + p * 1800.0
            sparklePhase += 2.0 * PI * sparkleFreq / SAMPLE_RATE
            val tapEnv = exp(-t * 130.0) * (1.0 - exp(-t * 3200.0))
            val shimmerEnv = exp(-t * 44.0) * (1.0 - exp(-t * 1800.0))
            val highs = clickFilter.process(rng.nextDouble() * 2.0 - 1.0) * tapEnv * 0.52 +
                (sin(sparklePhase) * 0.65 + sin(sparklePhase * 1.5) * 0.35) * shimmerEnv * 0.24

            buf[i] = bass + mids + highs
        }

        return masterForMobileSpeaker(buf, drive = 1.25, targetPeak = 0.88)
    }

    /**
     * Capture (430 ms): heavyweight knockout bass punch + crunchy mid-band impact +
     * snappy high crack, followed by a glossy descending comic power-slide.
     */
    private fun synthesizeCapture(): ShortArray {
        val count = samplesForMs(430)
        val buf = DoubleArray(count)
        val rng = Random(777)

        val crunchFilter = BiquadFilter.bandPass(SAMPLE_RATE, 1350.0, q = 1.4)
        val slapFilter = BiquadFilter.bandPass(SAMPLE_RATE, 5200.0, q = 1.5)

        var subPhase = 0.0
        var slidePhase = 0.0
        var zingPhase = 0.0

        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val p = i.toDouble() / count

            // 1. Deep punchy bass drop (165 Hz -> 58 Hz) with warm 2nd/3rd harmonics
            val subFreq = 58.0 + 107.0 * exp(-t * 18.0)
            subPhase += 2.0 * PI * subFreq / SAMPLE_RATE
            val subEnv = exp(-t * 9.5) * (1.0 - exp(-t * 1500.0))
            val bass = (sin(subPhase) * 0.78 +
                sin(subPhase * 2.0) * 0.42 +
                sin(subPhase * 3.0) * 0.16) * subEnv * 0.78

            // 2. Impact slap + mid crunch right at hit onset
            val noise = rng.nextDouble() * 2.0 - 1.0
            val crunchEnv = exp(-t * 42.0)
            val slapEnv = exp(-t * 115.0)
            val impactMids = crunchFilter.process(noise) * crunchEnv * 0.58
            val impactHighs = slapFilter.process(noise) * slapEnv * 0.64

            // 3. Glossy descending comic knockout slide (780 Hz -> 180 Hz)
            val slideFreq = 780.0 * (0.23.pow(p))
            slidePhase += 2.0 * PI * slideFreq / SAMPLE_RATE
            val slideEnv = exp(-t * 5.8) * (1.0 - exp(-t * 320.0))
            val slideMids = (sin(slidePhase) * 0.62 +
                sin(slidePhase * 2.0) * 0.26 +
                sin(slidePhase * 3.0) * 0.12) * slideEnv * 0.58

            // 4. High metallic star-zing overtone
            val zingFreq = 3100.0 - p * 1400.0
            zingPhase += 2.0 * PI * zingFreq / SAMPLE_RATE
            val zingEnv = exp(-t * 14.0) * (1.0 - exp(-t * 900.0))
            val zingHighs = sin(zingPhase) * zingEnv * 0.26

            buf[i] = bass + impactMids + slideMids + impactHighs + zingHighs
        }

        return masterForMobileSpeaker(buf, drive = 1.55, targetPeak = 0.93)
    }

    /**
     * Token Home (580 ms): radiant bell-marimba ascent anchored by a warm bass chord
     * and sparkling upper-harmonic shimmer.
     */
    private fun synthesizeTokenHome(): ShortArray =
        synthesizeRichChordArpeggio(
            notes = listOf(
                RichNote(freqHz = 523.25, bassHz = 130.81, durationMs = 105),
                RichNote(freqHz = 659.25, bassHz = 164.81, durationMs = 105),
                RichNote(freqHz = 783.99, bassHz = 196.00, durationMs = 120),
                RichNote(freqHz = 1046.50, bassHz = 130.81, durationMs = 250, sparkleBoost = 1.35)
            ),
            tailMs = 140,
            drive = 1.25
        )

    /**
     * Win Fanfare (1120 ms): triumphant arcade victory brass-chime progression
     * with punchy bass foundation, lush chordal mids, and bright glockenspiel highs.
     */
    private fun synthesizeWinFanfare(): ShortArray =
        synthesizeRichChordArpeggio(
            notes = listOf(
                RichNote(freqHz = 523.25, harmonyHz = 392.00, bassHz = 130.81, durationMs = 125),
                RichNote(freqHz = 659.25, harmonyHz = 523.25, bassHz = 164.81, durationMs = 125),
                RichNote(freqHz = 783.99, harmonyHz = 659.25, bassHz = 196.00, durationMs = 125),
                RichNote(freqHz = 1046.50, harmonyHz = 783.99, bassHz = 130.81, durationMs = 185, sparkleBoost = 1.25),
                RichNote(freqHz = 783.99, harmonyHz = 659.25, bassHz = 196.00, durationMs = 125),
                RichNote(freqHz = 1046.50, harmonyHz = 783.99, bassHz = 130.81, durationMs = 435, sparkleBoost = 1.45)
            ),
            tailMs = 180,
            drive = 1.35
        )

    /**
     * Timer warning (120 ms): crisp double-layer woodblock + high clarity pulse.
     */
    private fun synthesizeTimerWarning(): ShortArray {
        val count = samplesForMs(120)
        val buf = DoubleArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val bassEnv = exp(-t * 44.0) * (1.0 - exp(-t * 1600.0))
            val midEnv = exp(-t * 30.0) * (1.0 - exp(-t * 1800.0))
            val highEnv = exp(-t * 42.0) * (1.0 - exp(-t * 2400.0))

            val bass = (sin(2.0 * PI * 220.0 * t) * 0.55 + sin(2.0 * PI * 440.0 * t) * 0.25) * bassEnv * 0.45
            val mid = sin(2.0 * PI * 980.0 * t) * midEnv * 0.65
            val high = (sin(2.0 * PI * 2350.0 * t) * 0.60 + sin(2.0 * PI * 4700.0 * t) * 0.25) * highEnv * 0.48
            buf[i] = bass + mid + high
        }
        return masterForMobileSpeaker(buf, drive = 1.15, targetPeak = 0.85)
    }

    private fun synthesizeShieldPickup(): ShortArray =
        synthesizeRichChordArpeggio(
            notes = listOf(
                RichNote(freqHz = 587.33, bassHz = 146.83, durationMs = 85),
                RichNote(freqHz = 880.00, bassHz = 220.00, durationMs = 95),
                RichNote(freqHz = 1174.66, harmonyHz = 880.00, bassHz = 146.83, durationMs = 180, sparkleBoost = 1.3)
            ),
            tailMs = 110,
            drive = 1.22
        )

    private fun synthesizeDoubleDicePickup(): ShortArray =
        synthesizeRichChordArpeggio(
            notes = listOf(
                RichNote(freqHz = 659.25, bassHz = 164.81, durationMs = 70),
                RichNote(freqHz = 987.77, bassHz = 196.00, durationMs = 70),
                RichNote(freqHz = 659.25, bassHz = 164.81, durationMs = 70),
                RichNote(freqHz = 1318.51, harmonyHz = 987.77, bassHz = 164.81, durationMs = 175, sparkleBoost = 1.35)
            ),
            tailMs = 110,
            drive = 1.28
        )

    private fun synthesizeBombPickup(): ShortArray =
        synthesizeRichChordArpeggio(
            notes = listOf(
                RichNote(freqHz = 440.00, bassHz = 110.00, durationMs = 80),
                RichNote(freqHz = 349.23, bassHz = 116.54, durationMs = 85),
                RichNote(freqHz = 523.25, bassHz = 130.81, durationMs = 145)
            ),
            tailMs = 95,
            drive = 1.35
        )

    /**
     * Shield activate (340 ms): deep resonant energy swell + crystalline harmonic shimmer.
     */
    private fun synthesizeShieldActivate(): ShortArray {
        val count = samplesForMs(340)
        val buf = DoubleArray(count)
        var bassPhase = 0.0
        var midPhase = 0.0
        var shimmerPhaseA = 0.0
        var shimmerPhaseB = 0.0

        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val p = i.toDouble() / count

            val bassFreq = 120.0 + p * 120.0
            bassPhase += 2.0 * PI * bassFreq / SAMPLE_RATE
            val bassEnv = exp(-t * 6.0) * (1.0 - exp(-t * 160.0))
            val bass = (sin(bassPhase) * 0.72 + sin(bassPhase * 2.0) * 0.34) * bassEnv * 0.62

            val midFreq = 380.0 + p * 560.0
            midPhase += 2.0 * PI * midFreq / SAMPLE_RATE
            val midEnv = exp(-t * 5.8) * (1.0 - exp(-t * 140.0))
            val mids = (sin(midPhase) * 0.65 + sin(midPhase * 1.5) * 0.35) * midEnv * 0.66

            // Crystalline high shimmer chorus
            val shFreqA = 2400.0 + p * 2200.0
            val shFreqB = shFreqA * 1.015
            shimmerPhaseA += 2.0 * PI * shFreqA / SAMPLE_RATE
            shimmerPhaseB += 2.0 * PI * shFreqB / SAMPLE_RATE
            val highEnv = exp(-t * 7.2) * (1.0 - exp(-t * 120.0))
            val highs = (sin(shimmerPhaseA) * 0.5 + sin(shimmerPhaseB) * 0.5) * highEnv * 0.42

            buf[i] = bass + mids + highs
        }
        return masterForMobileSpeaker(buf, drive = 1.28, targetPeak = 0.90)
    }

    private fun synthesizeDoubleDiceActivate(): ShortArray =
        synthesizeRichChordArpeggio(
            notes = listOf(
                RichNote(freqHz = 523.25, bassHz = 130.81, durationMs = 68),
                RichNote(freqHz = 783.99, bassHz = 196.00, durationMs = 68),
                RichNote(freqHz = 1046.50, harmonyHz = 783.99, bassHz = 130.81, durationMs = 80),
                RichNote(freqHz = 1318.51, harmonyHz = 1046.50, bassHz = 164.81, durationMs = 190, sparkleBoost = 1.4)
            ),
            tailMs = 120,
            drive = 1.32
        )

    private fun synthesizeBombArm(): ShortArray =
        synthesizeRichChordArpeggio(
            notes = listOf(
                RichNote(freqHz = 740.00, bassHz = 146.83, durationMs = 65),
                RichNote(freqHz = 0.0, bassHz = 0.0, durationMs = 40),
                RichNote(freqHz = 1108.73, bassHz = 185.00, durationMs = 115, sparkleBoost = 1.3)
            ),
            tailMs = 85,
            drive = 1.30
        )

    /**
     * Bomb explosion (520 ms): cinematic mobile blast with chesty multi-harmonic bass drop,
     * roaring mid-band fire body, and crisp high-frequency crackling debris.
     */
    private fun synthesizeBombExplosion(): ShortArray {
        val count = samplesForMs(520)
        val buf = DoubleArray(count)
        val rng = Random(9901)

        val lowRoarFilter = BiquadFilter.bandPass(SAMPLE_RATE, 260.0, q = 1.1)
        val midRoarFilter = BiquadFilter.bandPass(SAMPLE_RATE, 950.0, q = 0.9)
        val highCrackleFilter = BiquadFilter.highPass(SAMPLE_RATE, 3200.0, q = 0.8)

        var boomPhase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val noise = rng.nextDouble() * 2.0 - 1.0

            // Deep bass shockwave (145 Hz -> 42 Hz) + upper-bass harmonics so phone speakers thump
            val boomFreq = 42.0 + 103.0 * exp(-t * 11.0)
            boomPhase += 2.0 * PI * boomFreq / SAMPLE_RATE
            val bassEnv = exp(-t * 5.8) * (1.0 - exp(-t * 1600.0))
            val bass = (sin(boomPhase) * 0.78 +
                sin(boomPhase * 2.0) * 0.44 +
                lowRoarFilter.process(noise) * 0.45) * bassEnv * 0.88

            // Fiery mid-range roar
            val midEnv = exp(-t * 7.6) * (1.0 - exp(-t * 1100.0))
            val mids = midRoarFilter.process(noise) * midEnv * 0.72

            // Crisp explosion crackle + bright high debris burst
            val highEnv = exp(-t * 13.5) * (1.0 - exp(-t * 2500.0))
            val crackleGate = if (rng.nextDouble() > 0.76) 1.35 else 0.72
            val highs = highCrackleFilter.process(noise) * highEnv * crackleGate * 0.60

            buf[i] = bass + mids + highs
        }
        return masterForMobileSpeaker(buf, drive = 1.65, targetPeak = 0.94)
    }

    private data class RichNote(
        val freqHz: Double,
        val harmonyHz: Double = 0.0,
        val bassHz: Double = 0.0,
        val durationMs: Int,
        val sparkleBoost: Double = 1.0
    )

    /**
     * Synthesizes a polyphonic overlapping chime/marimba arpeggio with:
     * - Warm bass pulse (fundamental + 2nd harmonic)
     * - Lush detuned mid-range voice + optional harmony interval
     * - Sparkling high overtones + crisp mallet attack transient
     */
    private fun synthesizeRichChordArpeggio(
        notes: List<RichNote>,
        tailMs: Int,
        drive: Double
    ): ShortArray {
        val totalMs = notes.sumOf { it.durationMs } + tailMs
        val buf = DoubleArray(samplesForMs(totalMs))
        val rng = Random(31415)
        var cursorSamples = 0

        for (note in notes) {
            val stepSamples = samplesForMs(note.durationMs)
            if (note.freqHz > 0.0) {
                val ringSamples = samplesForMs(note.durationMs + tailMs)
                val attackFilter = BiquadFilter.bandPass(SAMPLE_RATE, (note.freqHz * 4.0).coerceIn(2400.0, 8500.0), q = 1.6)
                for (i in 0 until ringSamples) {
                    val pos = cursorSamples + i
                    if (pos >= buf.size) break
                    val t = i.toDouble() / SAMPLE_RATE

                    // Bass foundation
                    val bass = if (note.bassHz > 0.0) {
                        val bEnv = exp(-t * 9.0) * (1.0 - exp(-t * 480.0))
                        (sin(2.0 * PI * note.bassHz * t) * 0.72 +
                            sin(2.0 * PI * (note.bassHz * 2.0) * t) * 0.34) * bEnv * 0.44
                    } else 0.0

                    // Rich detuned mid bell/marimba body
                    val midEnv = exp(-t * 7.8) * (1.0 - exp(-t * 620.0))
                    val f = note.freqHz
                    val primaryVoice = sin(2.0 * PI * f * t) * 0.56 +
                        sin(2.0 * PI * (f * 1.0035) * t) * 0.24 +
                        sin(2.0 * PI * (f * 2.0) * t) * 0.24
                    val harmonyVoice = if (note.harmonyHz > 0.0) {
                        sin(2.0 * PI * note.harmonyHz * t) * 0.30 +
                            sin(2.0 * PI * (note.harmonyHz * 2.0) * t) * 0.12
                    } else 0.0
                    val mids = (primaryVoice + harmonyVoice) * midEnv * 0.60

                    // Crisp high sparkle harmonics + mallet transient
                    val highEnv = exp(-t * 14.5) * (1.0 - exp(-t * 1400.0))
                    val malletEnv = exp(-t * 150.0)
                    val sparkleFreq = (f * 3.0).coerceAtMost(11500.0)
                    val airFreq = (f * 4.0).coerceAtMost(13500.0)
                    val highs = ((sin(2.0 * PI * sparkleFreq * t) * 0.46 +
                        sin(2.0 * PI * airFreq * t) * 0.28) * highEnv +
                        attackFilter.process(rng.nextDouble() * 2.0 - 1.0) * malletEnv * 0.42) *
                        0.38 * note.sparkleBoost

                    buf[pos] += bass + mids + highs
                }
            }
            cursorSamples += stepSamples
        }

        return masterForMobileSpeaker(buf, drive = drive, targetPeak = 0.91)
    }

    /**
     * 3-Band Mobile Speaker Mastering Chain:
     * 1. DC removal & 48 Hz sub-sonic high-pass protection
     * 2. Low-shelf warmth boost (+3.5 dB @ 190 Hz) for rich punch on phone speakers
     * 3. Mid presence contour (+2.2 dB @ 1850 Hz) for crisp board-game articulation
     * 4. High-shelf air & sparkle (+3.0 dB @ 5400 Hz) for clean top-end pop
     * 5. Warm tanh harmonic saturation + peak normalization + click-free edge fades
     */
    private fun masterForMobileSpeaker(
        raw: DoubleArray,
        drive: Double = 1.25,
        targetPeak: Double = 0.91
    ): ShortArray {
        val n = raw.size
        if (n == 0) return ShortArray(0)

        var sum = 0.0
        for (v in raw) sum += v
        val dc = sum / n

        val hpProtect = BiquadFilter.highPass(SAMPLE_RATE, 48.0, q = 0.707)
        val bassShelf = BiquadFilter.lowShelf(SAMPLE_RATE, 190.0, gainDb = 3.5, q = 0.707)
        val midPresence = BiquadFilter.peaking(SAMPLE_RATE, 1850.0, gainDb = 2.2, q = 1.1)
        val highAir = BiquadFilter.highShelf(SAMPLE_RATE, 5400.0, gainDb = 3.0, q = 0.707)

        val eqOut = DoubleArray(n)
        var maxAbs = 1e-9
        for (i in 0 until n) {
            var x = raw[i] - dc
            x = hpProtect.process(x)
            x = bassShelf.process(x)
            x = midPresence.process(x)
            x = highAir.process(x)
            eqOut[i] = x
            val a = abs(x)
            if (a > maxAbs) maxAbs = a
        }

        // Normalize into saturation stage, apply warm soft-clipping, then scale to targetPeak
        val normDrive = drive / maxAbs
        val tanhNorm = tanh(drive).coerceAtLeast(1e-6)
        val fadeInSamples = samplesForMs(3).coerceAtMost(n / 4)
        val fadeOutSamples = samplesForMs(8).coerceAtMost(n / 3)

        val out = ShortArray(n)
        for (i in 0 until n) {
            var y = tanh(eqOut[i] * normDrive) / tanhNorm
            if (i < fadeInSamples && fadeInSamples > 0) {
                y *= i.toDouble() / fadeInSamples
            }
            val fromEnd = n - 1 - i
            if (fromEnd < fadeOutSamples && fadeOutSamples > 0) {
                y *= fromEnd.toDouble() / fadeOutSamples
            }
            val scaled = (y * targetPeak * 32767.0).toInt().coerceIn(-32767, 32767)
            out[i] = scaled.toShort()
        }
        return out
    }

    private fun samplesForMs(ms: Int): Int = ((SAMPLE_RATE.toLong() * ms) / 1000L).toInt()

    /**
     * Lightweight Direct-Form II Transposed biquad filter (RBJ Audio EQ Cookbook).
     */
    private class BiquadFilter(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double
    ) {
        private var z1 = 0.0
        private var z2 = 0.0

        fun process(input: Double): Double {
            val out = b0 * input + z1
            z1 = b1 * input - a1 * out + z2
            z2 = b2 * input - a2 * out
            return out
        }

        companion object {
            fun highPass(sampleRate: Int, freqHz: Double, q: Double): BiquadFilter {
                val w0 = 2.0 * PI * freqHz / sampleRate
                val cosW = cos(w0)
                val alpha = sin(w0) / (2.0 * q)
                val b0 = (1.0 + cosW) / 2.0
                val b1 = -(1.0 + cosW)
                val b2 = (1.0 + cosW) / 2.0
                val a0 = 1.0 + alpha
                val a1 = -2.0 * cosW
                val a2 = 1.0 - alpha
                return BiquadFilter(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun bandPass(sampleRate: Int, freqHz: Double, q: Double): BiquadFilter {
                val clampedFreq = freqHz.coerceIn(40.0, sampleRate * 0.45)
                val w0 = 2.0 * PI * clampedFreq / sampleRate
                val cosW = cos(w0)
                val alpha = sin(w0) / (2.0 * q)
                val b0 = alpha
                val b1 = 0.0
                val b2 = -alpha
                val a0 = 1.0 + alpha
                val a1 = -2.0 * cosW
                val a2 = 1.0 - alpha
                return BiquadFilter(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun peaking(sampleRate: Int, freqHz: Double, gainDb: Double, q: Double): BiquadFilter {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2.0 * PI * freqHz / sampleRate
                val cosW = cos(w0)
                val alpha = sin(w0) / (2.0 * q)
                val b0 = 1.0 + alpha * a
                val b1 = -2.0 * cosW
                val b2 = 1.0 - alpha * a
                val a0 = 1.0 + alpha / a
                val a1 = -2.0 * cosW
                val a2 = 1.0 - alpha / a
                return BiquadFilter(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun lowShelf(sampleRate: Int, freqHz: Double, gainDb: Double, q: Double): BiquadFilter {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2.0 * PI * freqHz / sampleRate
                val cosW = cos(w0)
                val alpha = sin(w0) / (2.0 * q)
                val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                val b0 = a * ((a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha)
                val b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW)
                val b2 = a * ((a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha)
                val a0 = (a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha
                val a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW)
                val a2 = (a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha
                return BiquadFilter(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun highShelf(sampleRate: Int, freqHz: Double, gainDb: Double, q: Double): BiquadFilter {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2.0 * PI * freqHz / sampleRate
                val cosW = cos(w0)
                val alpha = sin(w0) / (2.0 * q)
                val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                val b0 = a * ((a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha)
                val b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW)
                val b2 = a * ((a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha)
                val a0 = (a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha
                val a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW)
                val a2 = (a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha
                return BiquadFilter(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }
        }
    }
}
