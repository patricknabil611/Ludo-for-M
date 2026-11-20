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
 * Manages distinct game sound effects (dice roll, token movement, opponent capture,
 * power-ups, fanfares) and background music playback using Android's [MediaPlayer].
 *
 * Each [SoundEffect] is materialized once as a standard RIFF/WAVE file inside the app's
 * cache directory and played via properly configured [MediaPlayer] instances with automatic
 * completion/error cleanup and a bounded concurrency pool so overlapping effects never leak.
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

    private val musicLock = Any()
    private var musicPlayer: MediaPlayer? = null

    @Volatile
    private var sfxEnabled: Boolean = true

    @Volatile
    private var musicEnabled: Boolean = true

    @Volatile
    private var appInForeground: Boolean = true

    @Volatile
    private var released: Boolean = false

    private val sfxAudioAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private val musicAudioAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    init {
        preloadCoreEffects()
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

    fun updateSettings(soundEffects: Boolean, music: Boolean) {
        sfxEnabled = soundEffects
        val musicChanged = musicEnabled != music
        musicEnabled = music
        if (!soundEffects) {
            stopAllOneShotPlayers()
        }
        if (musicChanged || (musicEnabled && appInForeground)) {
            syncMusicPlayback()
        }
    }

    fun setForeground(inForeground: Boolean) {
        appInForeground = inForeground
        syncMusicPlayback()
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
        if (released || !sfxEnabled || effect.isMusic) return
        val clampedVolume = volume.coerceIn(0f, 1f)
        scope.launch {
            if (released || !sfxEnabled) return@launch
            playOneShotInternal(effect, clampedVolume)
        }
    }

    /**
     * Releases all active [MediaPlayer] instances and background resources.
     */
    fun release() {
        if (released) return
        released = true
        stopAndReleaseMusicPlayer()
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

    private fun syncMusicPlayback() {
        if (released) return
        val shouldPlay = musicEnabled && appInForeground
        if (shouldPlay) {
            scope.launch {
                if (released || !musicEnabled || !appInForeground) return@launch
                startOrResumeMusicPlayer()
            }
        } else {
            synchronized(musicLock) {
                val mp = musicPlayer ?: return
                if (!musicEnabled) {
                    musicPlayer = null
                    safeReleasePlayer(mp)
                } else {
                    runCatching {
                        if (mp.isPlaying) mp.pause()
                    }
                }
            }
        }
    }

    private fun startOrResumeMusicPlayer() {
        val wavFile = runCatching { ensureWavFile(SoundEffect.BACKGROUND_MUSIC) }.getOrNull() ?: return
        synchronized(musicLock) {
            if (released || !musicEnabled || !appInForeground) return
            val existing = musicPlayer
            if (existing != null) {
                runCatching {
                    if (!existing.isPlaying) existing.start()
                }
                return
            }

            var created: MediaPlayer? = null
            try {
                created = mediaPlayerFactory()
                created.setAudioAttributes(musicAudioAttributes)
                FileInputStream(wavFile).use { fis ->
                    created.setDataSource(fis.fd)
                }
                created.isLooping = true
                val vol = SoundEffect.BACKGROUND_MUSIC.defaultVolume
                created.setVolume(vol, vol)
                created.setOnErrorListener { errored, _, _ ->
                    synchronized(musicLock) {
                        if (musicPlayer === errored) musicPlayer = null
                    }
                    safeReleasePlayer(errored)
                    true
                }
                created.prepare()
                if (released || !musicEnabled || !appInForeground) {
                    safeReleasePlayer(created)
                    return
                }
                created.start()
                musicPlayer = created
            } catch (_: Exception) {
                created?.let { safeReleasePlayer(it) }
            }
        }
    }

    private fun stopAndReleaseMusicPlayer() {
        val mp = synchronized(musicLock) {
            val current = musicPlayer
            musicPlayer = null
            current
        }
        mp?.let { safeReleasePlayer(it) }
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
    }
}
