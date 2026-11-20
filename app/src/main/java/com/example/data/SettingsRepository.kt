package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.GameModeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ludoSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ludo_for_m_settings"
)

data class AppSettings(
    val soundEffectsEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val hasSeenTutorial: Boolean = false,
    val seenModeGuides: Set<String> = emptySet(),
    val activeOnlineRoomCode: String = "",
    val isLoaded: Boolean = false
) {
    val hasSeenPowerUpGuide: Boolean
        get() = GameModeId.POWER_UP.name in seenModeGuides

    fun hasSeenModeGuide(mode: GameModeId): Boolean = mode.name in seenModeGuides
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SOUND_EFFECTS = booleanPreferencesKey("sound_effects_enabled")
        val MUSIC = booleanPreferencesKey("music_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val SEEN_POWER_UP_GUIDE = booleanPreferencesKey("seen_power_up_guide")
        val SEEN_TUTORIAL = booleanPreferencesKey("seen_tutorial")
        val SEEN_MODE_GUIDES = stringSetPreferencesKey("seen_mode_guides")
        val ACTIVE_ONLINE_ROOM = stringPreferencesKey("active_online_room_code")
    }

    val settingsFlow: Flow<AppSettings> = context.ludoSettingsDataStore.data.map { prefs ->
        val legacyPowerUp = prefs[Keys.SEEN_POWER_UP_GUIDE] ?: false
        val seenSet = buildSet {
            addAll(prefs[Keys.SEEN_MODE_GUIDES].orEmpty())
            if (legacyPowerUp) add(GameModeId.POWER_UP.name)
        }
        AppSettings(
            soundEffectsEnabled = prefs[Keys.SOUND_EFFECTS] ?: true,
            musicEnabled = prefs[Keys.MUSIC] ?: true,
            vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
            hasSeenTutorial = prefs[Keys.SEEN_TUTORIAL] ?: false,
            seenModeGuides = seenSet,
            activeOnlineRoomCode = prefs[Keys.ACTIVE_ONLINE_ROOM].orEmpty(),
            isLoaded = true
        )
    }

    suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        context.ludoSettingsDataStore.edit { it[Keys.SOUND_EFFECTS] = enabled }
    }

    suspend fun setMusicEnabled(enabled: Boolean) {
        context.ludoSettingsDataStore.edit { it[Keys.MUSIC] = enabled }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.ludoSettingsDataStore.edit { it[Keys.VIBRATION] = enabled }
    }

    suspend fun markTutorialSeen() {
        context.ludoSettingsDataStore.edit { it[Keys.SEEN_TUTORIAL] = true }
    }

    suspend fun markModeGuideSeen(mode: GameModeId) {
        context.ludoSettingsDataStore.edit { prefs ->
            val current = prefs[Keys.SEEN_MODE_GUIDES].orEmpty()
            prefs[Keys.SEEN_MODE_GUIDES] = current + mode.name
            if (mode == GameModeId.POWER_UP) {
                prefs[Keys.SEEN_POWER_UP_GUIDE] = true
            }
        }
    }

    suspend fun markPowerUpGuideSeen() {
        markModeGuideSeen(GameModeId.POWER_UP)
    }

    suspend fun setActiveOnlineRoomCode(roomCode: String) {
        val clean = roomCode.trim().uppercase().take(6)
        context.ludoSettingsDataStore.edit { prefs ->
            if (clean.isBlank()) {
                prefs.remove(Keys.ACTIVE_ONLINE_ROOM)
            } else {
                prefs[Keys.ACTIVE_ONLINE_ROOM] = clean
            }
        }
    }
}
