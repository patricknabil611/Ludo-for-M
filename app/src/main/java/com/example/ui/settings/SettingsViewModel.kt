package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppSettings
import com.example.data.SettingsRepository
import com.example.engine.GameModeId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AppSettings()
    )

    fun setSoundEffects(enabled: Boolean) {
        viewModelScope.launch { repository.setSoundEffectsEnabled(enabled) }
    }

    fun setSoundEffectsVolume(volume: Float) {
        viewModelScope.launch { repository.setSoundEffectsVolume(volume) }
    }

    fun setMusic(enabled: Boolean) {
        viewModelScope.launch { repository.setMusicEnabled(enabled) }
    }

    fun setMusicVolume(volume: Float) {
        viewModelScope.launch { repository.setMusicVolume(volume) }
    }

    fun setVibration(enabled: Boolean) {
        viewModelScope.launch { repository.setVibrationEnabled(enabled) }
    }

    fun markTutorialSeen() {
        viewModelScope.launch { repository.markTutorialSeen() }
    }

    fun markModeGuideSeen(mode: GameModeId) {
        viewModelScope.launch { repository.markModeGuideSeen(mode) }
    }

    fun markPowerUpGuideSeen() {
        viewModelScope.launch { repository.markPowerUpGuideSeen() }
    }

    fun setActiveOnlineRoomCode(roomCode: String) {
        viewModelScope.launch { repository.setActiveOnlineRoomCode(roomCode) }
    }

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository) as T
        }
    }
}
