package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.GameModeId
import com.example.ui.theme.LudoThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ludoSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ludo_for_m_settings"
)

data class AppSettings(
    val soundEffectsEnabled: Boolean = true,
    val soundEffectsVolume: Float = 1f,
    val vibrationEnabled: Boolean = true,
    val themeId: LudoThemeId = LudoThemeId.DEFAULT,
    val hasSeenTutorial: Boolean = false,
    val seenModeGuides: Set<String> = emptySet(),
    val activeOnlineRoomCode: String = "",
    val isLoaded: Boolean = false
) {
    val hasSeenPowerUpGuide: Boolean
        get() = GameModeId.MAGIC.name in seenModeGuides

    fun hasSeenModeGuide(mode: GameModeId): Boolean = mode.name in seenModeGuides
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SOUND_EFFECTS = booleanPreferencesKey("sound_effects_enabled")
        val SOUND_EFFECTS_VOLUME = floatPreferencesKey("sound_effects_volume")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val THEME_ID = stringPreferencesKey("app_theme_id")
        val SEEN_POWER_UP_GUIDE = booleanPreferencesKey("seen_power_up_guide")
        val SEEN_TUTORIAL = booleanPreferencesKey("seen_tutorial")
        val SEEN_MODE_GUIDES = stringSetPreferencesKey("seen_mode_guides")
        val ACTIVE_ONLINE_ROOM = stringPreferencesKey("active_online_room_code")
    }

    val settingsFlow: Flow<AppSettings> = context.ludoSettingsDataStore.data.map { prefs ->
        val legacyPowerUp = prefs[Keys.SEEN_POWER_UP_GUIDE] ?: false
        val seenSet = buildSet {
            for (raw in prefs[Keys.SEEN_MODE_GUIDES].orEmpty()) {
                add(GameModeId.fromLegacyName(raw).first.name)
            }
            if (legacyPowerUp) add(GameModeId.MAGIC.name)
        }
        AppSettings(
            soundEffectsEnabled = prefs[Keys.SOUND_EFFECTS] ?: true,
            soundEffectsVolume = (prefs[Keys.SOUND_EFFECTS_VOLUME] ?: 1f).coerceIn(0f, 1f),
            vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
            themeId = LudoThemeId.fromStorageKey(prefs[Keys.THEME_ID]),
            hasSeenTutorial = prefs[Keys.SEEN_TUTORIAL] ?: false,
            seenModeGuides = seenSet,
            activeOnlineRoomCode = prefs[Keys.ACTIVE_ONLINE_ROOM].orEmpty(),
            isLoaded = true
        )
    }

    suspend fun readInitialThemeId(): LudoThemeId {
        return runCatching {
            val prefs = context.ludoSettingsDataStore.data.first()
            LudoThemeId.fromStorageKey(prefs[Keys.THEME_ID])
        }.getOrDefault(LudoThemeId.DEFAULT)
    }

    suspend fun setThemeId(themeId: LudoThemeId) {
        context.ludoSettingsDataStore.edit { prefs ->
            prefs[Keys.THEME_ID] = themeId.name
        }
    }

    suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        context.ludoSettingsDataStore.edit { prefs ->
            prefs[Keys.SOUND_EFFECTS] = enabled
            if (enabled && (prefs[Keys.SOUND_EFFECTS_VOLUME] ?: 1f) <= MIN_RESTORE_THRESHOLD) {
                prefs[Keys.SOUND_EFFECTS_VOLUME] = RESTORED_VOLUME_DEFAULT
            }
        }
    }

    suspend fun setSoundEffectsVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        context.ludoSettingsDataStore.edit { prefs ->
            prefs[Keys.SOUND_EFFECTS_VOLUME] = clamped
            prefs[Keys.SOUND_EFFECTS] = clamped > 0f
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.ludoSettingsDataStore.edit { it[Keys.VIBRATION] = enabled }
    }

    companion object {
        private const val MIN_RESTORE_THRESHOLD = 0.01f
        private const val RESTORED_VOLUME_DEFAULT = 0.7f
    }

    suspend fun markTutorialSeen() {
        context.ludoSettingsDataStore.edit { it[Keys.SEEN_TUTORIAL] = true }
    }

    suspend fun markModeGuideSeen(mode: GameModeId) {
        context.ludoSettingsDataStore.edit { prefs ->
            val current = prefs[Keys.SEEN_MODE_GUIDES].orEmpty()
                .map { GameModeId.fromLegacyName(it).first.name }
                .toSet()
            prefs[Keys.SEEN_MODE_GUIDES] = current + mode.name
            if (mode == GameModeId.MAGIC) {
                prefs[Keys.SEEN_POWER_UP_GUIDE] = true
            }
        }
    }

    suspend fun markPowerUpGuideSeen() {
        markModeGuideSeen(GameModeId.MAGIC)
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
