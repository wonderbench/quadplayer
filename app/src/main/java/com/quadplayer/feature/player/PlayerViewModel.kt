package com.quadplayer.feature.player

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quadplayer.R
import com.quadplayer.core.data.HistoryRepository
import com.quadplayer.core.data.SettingsRepository
import com.quadplayer.core.data.VideoRepository
import com.quadplayer.core.model.AppSettings
import com.quadplayer.core.model.PlaybackSpeed
import com.quadplayer.core.model.VideoFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerScreenState(
    val video: VideoFile? = null,
    val settings: AppSettings = AppSettings(),
    val playerState: PlayerUiState = PlayerUiState(),
    val controlsVisible: Boolean = true,
    val statusMessage: String? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: android.content.Context,
    savedStateHandle: SavedStateHandle,
    private val videoRepository: VideoRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val playerController = PlayerController(context)
    private val appContext = context
    private val video = MutableStateFlow<VideoFile?>(null)
    private val controlsVisible = MutableStateFlow(true)
    private val statusMessage = MutableStateFlow<String?>(null)
    private var historyJob: Job? = null
    private val videoId: Long = savedStateHandle["videoId"] ?: -1L

    val state: StateFlow<PlayerScreenState> = combine(
        video,
        settingsRepository.settings,
        playerController.uiState,
        controlsVisible,
        statusMessage,
    ) { currentVideo, settings, playerState, isControlsVisible, screenMessage ->
        PlayerScreenState(
            video = currentVideo,
            settings = settings,
            playerState = playerState,
            controlsVisible = isControlsVisible,
            statusMessage = screenMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerScreenState())

    val controller: PlayerController
        get() = playerController

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                playerController.applySettings(settings)
            }
        }
        if (videoId < 0) {
            statusMessage.value = appContext.getString(R.string.player_video_not_found)
        } else {
            viewModelScope.launch {
                val currentVideo = videoRepository.getVideo(videoId)
                if (currentVideo == null) {
                    statusMessage.value = appContext.getString(R.string.player_video_not_found)
                    return@launch
                }
                video.value = currentVideo
                statusMessage.value = null
                val settings = settingsRepository.settings.first()
                val resumePosition = if (settings.autoResume) historyRepository.getResumePosition(videoId) else 0L
                playerController.load(currentVideo, resumePosition)
                startHistorySaver(currentVideo.id)
            }
        }
    }

    fun togglePlayPause() = playerController.togglePlayPause()

    fun seekBy(deltaMs: Long) = playerController.seekBy(deltaMs)

    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    fun setSpeed(speed: PlaybackSpeed) = playerController.setPlaybackSpeed(speed)

    fun cycleAspectMode() = playerController.cycleAspectMode()

    fun selectAudioTrack(option: TrackOption?) = playerController.selectAudioTrack(option)

    fun selectSubtitleTrack(option: TrackOption?) = playerController.selectSubtitleTrack(option)

    fun setExternalSubtitle(uri: Uri?) {
        viewModelScope.launch {
            runCatching {
                playerController.setExternalSubtitle(uri)
            }.onSuccess {
                statusMessage.value = null
            }.onFailure {
                statusMessage.value = appContext.getString(R.string.player_external_subtitle_failed)
            }
        }
    }

    fun setExternalAudio(uri: Uri?) {
        viewModelScope.launch {
            runCatching {
                playerController.setExternalAudio(uri)
            }.onSuccess {
                statusMessage.value = null
            }.onFailure {
                statusMessage.value = appContext.getString(R.string.player_external_audio_failed)
            }
        }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    fun showControls() {
        controlsVisible.value = true
    }

    fun hideControls() {
        controlsVisible.value = false
    }

    fun toggleControls() {
        controlsVisible.value = !controlsVisible.value
    }

    fun onBackgroundStateChanged(allowBackgroundPlayback: Boolean, isInPictureInPicture: Boolean) {
        if (!allowBackgroundPlayback && !isInPictureInPicture) {
            playerController.pause()
        }
    }

    private fun startHistorySaver(videoId: Long) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(5000)
                historyRepository.recordPlayback(videoId, playerController.uiState.value.currentPositionMs)
            }
        }
    }

    override fun onCleared() {
        val currentVideo = video.value
        val finalPositionMs = playerController.uiState.value.currentPositionMs
        if (currentVideo != null) {
            viewModelScope.launch {
                historyRepository.recordPlayback(currentVideo.id, finalPositionMs)
            }
        }
        historyJob?.cancel()
        playerController.release()
        super.onCleared()
    }
}
