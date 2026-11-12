package com.quadplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.quadplayer.core.data.SettingsRepository
import com.quadplayer.core.data.VideoRepository
import com.quadplayer.core.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class ExternalVideoResult(val request: Long, val videoId: Long?)

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val videoRepository: VideoRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val externalVideo = MutableStateFlow<ExternalVideoResult?>(null)
    private var request = 0L

    fun openExternalVideo(uri: Uri) {
        val current = ++request
        externalVideo.value = null
        viewModelScope.launch {
            val video = try { videoRepository.importVideo(uri) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (request == current) externalVideo.value = ExternalVideoResult(current, video?.id)
        }
    }

    fun consumeExternalVideo(result: ExternalVideoResult) {
        if (externalVideo.value == result) externalVideo.value = null
    }
}
