package com.quadplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quadplayer.core.data.SettingsRepository
import com.quadplayer.core.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
}
