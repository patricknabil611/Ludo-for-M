package com.example.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.engine.PowerUpType

class LudoHapticsManager(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    @Volatile
    private var enabled: Boolean = true

    fun updateEnabled(isEnabled: Boolean) {
        enabled = isEnabled
    }

    fun vibrateDiceRoll() {
        if (!enabled) return
        triggerWaveform(longArrayOf(0, 22, 35, 22), intArrayOf(0, 120, 0, 140))
    }

    fun vibrateCapture() {
        if (!enabled) return
        triggerOneShot(durationMs = 85L, amplitude = 220)
    }

    fun vibrateWin() {
        if (!enabled) return
        triggerWaveform(longArrayOf(0, 70, 70, 120, 60, 160), intArrayOf(0, 180, 0, 220, 0, 255))
    }

    fun vibratePowerUpPickup(type: PowerUpType) {
        if (!enabled) return
        when (type) {
            PowerUpType.SHIELD ->
                triggerWaveform(longArrayOf(0, 30, 40, 45), intArrayOf(0, 140, 0, 190))
            PowerUpType.DOUBLE_DICE ->
                triggerWaveform(longArrayOf(0, 25, 30, 25, 30, 35), intArrayOf(0, 130, 0, 160, 0, 200))
            PowerUpType.BOMB_TRAP ->
                triggerWaveform(longArrayOf(0, 45, 35, 30), intArrayOf(0, 180, 0, 140))
        }
    }

    fun vibratePowerUpActivate(type: PowerUpType) {
        if (!enabled) return
        when (type) {
            PowerUpType.SHIELD ->
                triggerWaveform(longArrayOf(0, 55, 30, 55), intArrayOf(0, 190, 0, 220))
            PowerUpType.DOUBLE_DICE ->
                triggerWaveform(longArrayOf(0, 28, 28, 28, 28, 45), intArrayOf(0, 150, 0, 180, 0, 230))
            PowerUpType.BOMB_TRAP ->
                triggerWaveform(longArrayOf(0, 20, 45, 50), intArrayOf(0, 130, 0, 200))
        }
    }

    fun vibrateBombExplosion() {
        if (!enabled) return
        triggerWaveform(longArrayOf(0, 120, 40, 90), intArrayOf(0, 255, 0, 210))
    }

    private fun triggerOneShot(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        }
    }

    private fun triggerWaveform(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(timings, -1)
            }
        }
    }
}
