package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.engine.PowerUpType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages distinct in-game sound effects (dice roll, token movement, opponent capture,
 * power-ups, fanfares) using Android's [MediaPlayer].
 *
 * Each [SoundEffect] is materialized once as a studio-mastered 44.1 kHz RIFF/WAVE file
 * inside the app's cache directory and played via properly configured [MediaPlayer] instances
 * with automatic completion/error cleanup and a bounded concurrency pool.
 */
open class SoundManager(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mediaPlayerFactory: () -> MediaPlayer = { MediaPlayer() }
) {
    private val cacheDirectory: File = File(context.applicationContext.cacheDir, SFX_CACHE_DIR)
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val fileLocks = ConcurrentHashMap<SoundEffect, Any>()
    private val cachedFiles = ConcurrentHashMap<SoundEffect, File>()

    private val activePlayersLock = Any()
    private val activeOneShotPlayers = ArrayDeque<MediaPlayer>()

    @Volatile
    private var sfxEnabled: Boolean = true

    @Volatile
    private var sfxVolume: Float = 1f

    @Volatile
    private var released: Boolean = false

    private val sfxAudioAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    init {
        cleanupLegacyAudioCache()
        preloadCoreEffects()
    }

    /**
     * Removes any old v2 or deprecated background music WAV files from cache.
     */
    private fun cleanupLegacyAudioCache() {
        if (released) return
        scope.launch {
            runCatching {
                val activeNames = SoundEffect.entries.map { it.fileName }.toSet()
                cacheDirectory.listFiles()?.forEach { file ->
                    if (file.isFile && file.name !in activeNames) {
                        file.delete()
                    }
                }
            }
        }
    }

    /**
     * Pre-generates the core gameplay sound effect WAV files asynchronously so the first
     * dice roll, token move, and capture play with minimal latency.
     */
    fun preloadCoreEffects() {
        if (released) return
        scope.launch {
            runCatching { ensureWavFile(SoundEffect.DICE_ROLL) }
            runCatching { ensureWavFile(SoundEffect.TOKEN_MOVE) }
            runCatching { ensureWavFile(SoundEffect.CAPTURE) }
        }
    }

    fun updateSettings(
        soundEffects: Boolean,
        soundEffectsVolume: Float = sfxVolume
    ) {
        sfxEnabled = soundEffects
        sfxVolume = soundEffectsVolume.coerceIn(0f, 1f)

        if (!soundEffects || sfxVolume <= 0f) {
            stopAllOneShotPlayers()
        }
    }

    fun setForeground(inForeground: Boolean) {
        if (!inForeground) {
            stopAllOneShotPlayers()
        }
    }

    /** Plays the distinct wooden dice rattle sound effect via [MediaPlayer]. */
    fun playDiceRoll() {
        playEffect(SoundEffect.DICE_ROLL)
    }

    /** Plays the distinct token hop/move sound effect via [MediaPlayer]. */
    fun playTokenMove() {
        playEffect(SoundEffect.TOKEN_MOVE)
    }

    /** Alias for [playTokenMove] used during step-by-step board hop animations. */
    fun playTokenHop() {
        playTokenMove()
    }

    /** Plays the distinct opponent capture knockout sound effect via [MediaPlayer]. */
    fun playCapture() {
        playEffect(SoundEffect.CAPTURE)
    }

    fun playTokenHome() {
        playEffect(SoundEffect.TOKEN_HOME)
    }

    fun playWin() {
        playEffect(SoundEffect.WIN_FANFARE)
    }

    fun playTimerWarning() {
        playEffect(SoundEffect.TIMER_WARNING)
    }

    fun playPowerUpPickup(type: PowerUpType) {
        val effect = when (type) {
            PowerUpType.SHIELD -> SoundEffect.SHIELD_PICKUP
            PowerUpType.DOUBLE_DICE -> SoundEffect.DOUBLE_DICE_PICKUP
            PowerUpType.BOMB_TRAP -> SoundEffect.BOMB_PICKUP
        }
        playEffect(effect)
    }

    fun playPowerUpActivate(type: PowerUpType) {
        val effect = when (type) {
            PowerUpType.SHIELD -> SoundEffect.SHIELD_ACTIVATE
            PowerUpType.DOUBLE_DICE -> SoundEffect.DOUBLE_DICE_ACTIVATE
            PowerUpType.BOMB_TRAP -> SoundEffect.BOMB_ARM
        }
        playEffect(effect)
    }

    fun playBombExplosion() {
        playEffect(SoundEffect.BOMB_EXPLOSION)
    }

    /**
     * Plays a specific [SoundEffect] using an Android [MediaPlayer] instance.
     * Automatically cleans up and releases the player on completion or error.
     */
    fun playEffect(effect: SoundEffect, volume: Float = effect.defaultVolume) {
        if (released || !sfxEnabled) return
        val effectiveVolume = computeEffectiveVolume(
            baseVolume = volume,
            masterVolume = sfxVolume,
            enabled = sfxEnabled
        )
        if (effectiveVolume <= 0f) return
        scope.launch {
            if (released || !sfxEnabled) return@launch
            playOneShotInternal(effect, effectiveVolume)
        }
    }

    /**
     * Releases all active [MediaPlayer] instances and background resources.
     */
    fun release() {
        if (released) return
        released = true
        stopAllOneShotPlayers()
        scope.cancel()
    }

    internal fun ensureWavFile(effect: SoundEffect): File {
        cachedFiles[effect]?.let { existing ->
            if (existing.exists() && existing.length() > WavAudioSynthesizer.WAV_HEADER_BYTES) {
                return existing
            }
        }
        val lock = fileLocks.computeIfAbsent(effect) { Any() }
        synchronized(lock) {
            if (!cacheDirectory.exists()) {
                cacheDirectory.mkdirs()
            }
            val target = File(cacheDirectory, effect.fileName)
            if (!target.exists() || target.length() <= WavAudioSynthesizer.WAV_HEADER_BYTES) {
                val wavBytes = WavAudioSynthesizer.synthesizeWav(effect)
                val temp = File(cacheDirectory, "${effect.fileName}.tmp")
                temp.writeBytes(wavBytes)
                if (!temp.renameTo(target)) {
                    target.writeBytes(wavBytes)
                    temp.delete()
                }
            }
            cachedFiles[effect] = target
            return target
        }
    }

    private fun playOneShotInternal(effect: SoundEffect, volume: Float) {
        var player: MediaPlayer? = null
        try {
            val wavFile = ensureWavFile(effect)
            if (released || !sfxEnabled) return

            player = mediaPlayerFactory()
            registerOneShotPlayer(player)

            player.setAudioAttributes(sfxAudioAttributes)
            FileInputStream(wavFile).use { fis ->
                player.setDataSource(fis.fd)
            }
            player.setVolume(volume, volume)
            player.setOnCompletionListener { completed ->
                releaseOneShotPlayer(completed)
            }
            player.setOnErrorListener { errored, _, _ ->
                releaseOneShotPlayer(errored)
                true
            }
            player.prepare()
            if (released || !sfxEnabled) {
                releaseOneShotPlayer(player)
                return
            }
            player.start()
        } catch (_: Exception) {
            player?.let { releaseOneShotPlayer(it) }
        }
    }

    private fun registerOneShotPlayer(player: MediaPlayer) {
        var evicted: MediaPlayer? = null
        synchronized(activePlayersLock) {
            if (activeOneShotPlayers.size >= MAX_CONCURRENT_SFX_PLAYERS) {
                evicted = activeOneShotPlayers.removeFirst()
            }
            activeOneShotPlayers.addLast(player)
        }
        evicted?.let { safeReleasePlayer(it) }
    }

    private fun releaseOneShotPlayer(player: MediaPlayer) {
        synchronized(activePlayersLock) {
            activeOneShotPlayers.remove(player)
        }
        safeReleasePlayer(player)
    }

    private fun stopAllOneShotPlayers() {
        val snapshot = synchronized(activePlayersLock) {
            val copy = activeOneShotPlayers.toList()
            activeOneShotPlayers.clear()
            copy
        }
        snapshot.forEach { safeReleasePlayer(it) }
    }

    private fun safeReleasePlayer(player: MediaPlayer) {
        runCatching {
            if (player.isPlaying) {
                player.stop()
            }
        }
        runCatching { player.reset() }
        runCatching { player.release() }
    }

    companion object {
        private const val SFX_CACHE_DIR = "ludo_sfx"
        private const val MAX_CONCURRENT_SFX_PLAYERS = 6

        fun computeEffectiveVolume(
            baseVolume: Float,
            masterVolume: Float,
            enabled: Boolean
        ): Float {
            if (!enabled) return 0f
            return (baseVolume.coerceIn(0f, 1f) * masterVolume.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        }
    }
}
