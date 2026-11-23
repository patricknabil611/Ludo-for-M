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

    private class ManagedPlayer(val player: MediaPlayer) {
        val lock = Any()

        @Volatile
        var isReleased: Boolean = false
            private set

        fun releaseOnce() {
            synchronized(lock) {
                if (isReleased) return
                isReleased = true
                runCatching { player.setOnCompletionListener(null) }
                runCatching { player.setOnErrorListener(null) }
                runCatching { player.release() }
            }
        }
    }

    private val activePlayersLock = Any()
    private val activeOneShotPlayers = ArrayDeque<ManagedPlayer>()

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
            PowerUpType.ROCKET -> SoundEffect.DOUBLE_DICE_ACTIVATE
            PowerUpType.GOLDEN_DICE -> SoundEffect.DOUBLE_DICE_PICKUP
            PowerUpType.SHIELD -> SoundEffect.SHIELD_PICKUP
            PowerUpType.BOMB_TRAP -> SoundEffect.BOMB_PICKUP
        }
        playEffect(effect)
    }

    fun playPowerUpActivate(type: PowerUpType) {
        val effect = when (type) {
            PowerUpType.ROCKET -> SoundEffect.DOUBLE_DICE_ACTIVATE
            PowerUpType.GOLDEN_DICE -> SoundEffect.DOUBLE_DICE_ACTIVATE
            PowerUpType.SHIELD -> SoundEffect.SHIELD_ACTIVATE
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
        var managed: ManagedPlayer? = null
        try {
            val wavFile = ensureWavFile(effect)
            if (released || !sfxEnabled) return

            val candidate = ManagedPlayer(mediaPlayerFactory())
            managed = candidate

            synchronized(candidate.lock) {
                if (released || !sfxEnabled || candidate.isReleased) {
                    candidate.releaseOnce()
                    return
                }
                val player = candidate.player
                player.setAudioAttributes(sfxAudioAttributes)
                FileInputStream(wavFile).use { fis ->
                    player.setDataSource(fis.fd)
                }
                player.setOnCompletionListener {
                    releaseOneShotPlayer(candidate)
                }
                player.setOnErrorListener { _, _, _ ->
                    releaseOneShotPlayer(candidate)
                    true
                }
                player.prepare()
                player.setVolume(volume, volume)
                if (!registerOneShotPlayer(candidate)) {
                    candidate.releaseOnce()
                    return
                }
                player.start()
            }
        } catch (_: Exception) {
            managed?.let { releaseOneShotPlayer(it) }
        }
    }

    private fun registerOneShotPlayer(managed: ManagedPlayer): Boolean {
        var evicted: ManagedPlayer? = null
        synchronized(activePlayersLock) {
            if (released || !sfxEnabled || managed.isReleased) {
                return false
            }
            if (activeOneShotPlayers.size >= MAX_CONCURRENT_SFX_PLAYERS) {
                evicted = activeOneShotPlayers.removeFirst()
            }
            activeOneShotPlayers.addLast(managed)
        }
        evicted?.releaseOnce()
        return true
    }

    private fun releaseOneShotPlayer(managed: ManagedPlayer) {
        synchronized(activePlayersLock) {
            activeOneShotPlayers.remove(managed)
        }
        managed.releaseOnce()
    }

    private fun stopAllOneShotPlayers() {
        val snapshot = synchronized(activePlayersLock) {
            val copy = activeOneShotPlayers.toList()
            activeOneShotPlayers.clear()
            copy
        }
        snapshot.forEach { it.releaseOnce() }
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
