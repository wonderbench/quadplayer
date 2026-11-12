package com.quadplayer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quadplayer.core.data.SettingsRepository
import com.quadplayer.core.model.AppSettings
import com.quadplayer.core.model.AppThemeMode
import com.quadplayer.core.model.DecoderMode
import com.quadplayer.core.model.PlaybackSpeed
import com.quadplayer.core.model.SubtitleStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val subtitleMutex = Mutex()
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

    fun setAutoPictureInPicture(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoPictureInPicture(enabled) }
    }

    fun setAutoResume(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoResume(enabled) }
    }

    fun setCrossfade(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCrossfadeAudioSwitching(enabled) }
    }

    fun setSubtitleScale(scale: Float) = updateSubtitle { it.copy(fontScale = scale) }
    fun setSubtitleForeground(color: Long) = updateSubtitle { it.copy(foregroundColor = color) }
    fun setSubtitleBackground(color: Long) = updateSubtitle { it.copy(backgroundColor = color) }

    private fun updateSubtitle(update: (SubtitleStyle) -> SubtitleStyle) {
        viewModelScope.launch {
            subtitleMutex.withLock {
                settingsRepository.setSubtitleStyle(update(settingsRepository.settings.first().subtitleStyle))
            }
        }
    }
}
