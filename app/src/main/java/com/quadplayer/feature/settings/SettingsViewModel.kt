package com.quadplayer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quadplayer.core.data.SettingsRepository
import com.quadplayer.core.model.AppSettings
import com.quadplayer.core.model.AppThemeMode
import com.quadplayer.core.model.DecoderMode
import com.quadplayer.core.model.PlaybackSpeed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDecoderMode(mode: DecoderMode) {
        viewModelScope.launch { settingsRepository.setDecoderMode(mode) }
    }

    fun setSpeed(speed: PlaybackSpeed) {
        viewModelScope.launch { settingsRepository.setDefaultPlaybackSpeed(speed) }
    }

    fun setDoubleTapSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setDoubleTapSeekSeconds(seconds) }
    }

    fun setReverseDoubleTap(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReverseDoubleTapSeek(enabled) }
    }

    fun setBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBackgroundPlayback(enabled) }
    }

    fun setAutoResume(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoResume(enabled) }
    }

    fun setCrossfade(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCrossfadeAudioSwitching(enabled) }
    }
}
