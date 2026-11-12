package com.quadplayer.feature.player

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quadplayer.R
import com.quadplayer.core.data.HistoryWriteQueue
import com.quadplayer.core.data.PlaybackHistorySession
import com.quadplayer.core.data.PlaylistRepository
import com.quadplayer.core.data.SettingsRepository
import com.quadplayer.core.data.VideoRepository
import com.quadplayer.core.model.AppSettings
import com.quadplayer.core.model.PlaybackSpeed
import com.quadplayer.core.model.PlaybackState
import androidx.media3.common.Player
import com.quadplayer.core.model.VideoFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerScreenState(
    val video: VideoFile? = null,
    val settings: AppSettings = AppSettings(),
    val playerState: PlayerUiState = PlayerUiState(),
    val controlsVisible: Boolean = true,
    @androidx.annotation.StringRes val statusMessage: Int? = null,
    val queue: PlaybackQueueState = PlaybackQueueState(),
)

class PlaybackSession(
    context: android.content.Context,
    savedStateHandle: SavedStateHandle,
    private val videoRepository: VideoRepository,
    private val playlistRepository: PlaylistRepository,
    private val historyWriteQueue: HistoryWriteQueue,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val playerController = PlayerController(context, handleAudioFocus = true, rememberTrackChoices = true)
    private val historySession = PlaybackHistorySession(historyWriteQueue)
    private val video = MutableStateFlow<VideoFile?>(null)
    private val controlsVisible = MutableStateFlow(true)
    private val statusMessage = MutableStateFlow<Int?>(null)
    private val queue = MutableStateFlow(PlaybackQueueState())
    private var loading = false
    var backgroundEnabled: Boolean = false
        private set
    var onBackgroundPolicyChanged: ((Boolean) -> Unit)? = null
    private val playlistId: Long = savedStateHandle["playlistId"] ?: -1L
    private var historyJob: Job? = null
    private val videoId: Long = savedStateHandle["videoId"] ?: -1L

    private val playbackState = combine(
        video, settingsRepository.settings, playerController.uiState, controlsVisible, statusMessage,
    ) { currentVideo, settings, playerState, visible, message ->
        PlayerScreenState(
            video = currentVideo,
            settings = settings,
            playerState = playerState,
            controlsVisible = visible,
            statusMessage = message,
        )
    }
    val state: StateFlow<PlayerScreenState> = combine(playbackState, queue) { playback, currentQueue ->
        playback.copy(queue = currentQueue)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerScreenState())

    val controller: PlayerController
        get() = playerController

    internal fun currentVideoUri(): String? = video.value?.uriString

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                backgroundEnabled = settings.backgroundPlayback
                playerController.applySettings(settings)
                onBackgroundPolicyChanged?.invoke(settings.backgroundPlayback)
            }
        }
        viewModelScope.launch {
            if (videoId < 0) {
                statusMessage.value = R.string.player_video_not_found
                return@launch
            }
            val ids = if (playlistId >= 0) playlistRepository.observePlaylistVideos(playlistId).first().map { it.id } else emptyList()
            queue.value = PlaybackQueueState.from(ids, videoId)
            openIndex(queue.value.index, resume = true)
        }
        viewModelScope.launch {
            playerController.uiState.map { it.playbackState }.distinctUntilChanged().collect { playback ->
                if (playback == PlaybackState.ENDED && !queue.value.repeatOne) next()
            }
        }
    }

    private fun openIndex(index: Int, resume: Boolean = false, direction: Int = 1) {
        if (loading || index !in queue.value.ids.indices) return
        loading = true
        viewModelScope.launch {
            try {
                var candidate = index
                var nextVideo: VideoFile? = null
                while (candidate in queue.value.ids.indices && nextVideo == null) {
                    nextVideo = videoRepository.getVideo(queue.value.ids[candidate])
                    if (nextVideo == null) candidate += direction
                }
                if (nextVideo == null) {
                    statusMessage.value = R.string.player_video_not_found
                    return@launch
                }
                savePosition()
                historySession.clear()
                playerController.pause()
                val settings = settingsRepository.settings.first()
                val position = if (resume && settings.autoResume) {
                    normalizeResumePosition(historyWriteQueue.getResumePosition(nextVideo.id), nextVideo.durationMs)
                } else 0L
                playerController.load(nextVideo, position)
                historySession.markLoaded(nextVideo.id)
                video.value = nextVideo
                queue.value = queue.value.copy(index = candidate)
                statusMessage.value = null
                startHistorySaver()
            } finally { loading = false }
        }
    }

    fun previous() = openIndex(queue.value.index - 1, direction = -1)
    fun next() = openIndex(queue.value.index + 1)
    fun toggleRepeatOne() {
        val repeat = !queue.value.repeatOne
        queue.value = queue.value.copy(repeatOne = repeat)
        playerController.player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
        if (!playerController.player.playWhenReady) savePosition()
    }

    fun retry() = playerController.retry()

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
                statusMessage.value = R.string.player_external_subtitle_failed
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
                statusMessage.value = R.string.player_external_audio_failed
            }
        }
    }

    fun showServiceError() { statusMessage.value = R.string.player_service_failed }

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
        savePosition()
        if (!allowBackgroundPlayback && !isInPictureInPicture) {
            playerController.pause()
        }
    }

    private fun startHistorySaver() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                savePosition()
            }
        }
    }

    fun recordCurrentPosition() = savePosition()

    private fun savePosition() {
        val player = playerController.player
        val duration = player.duration.takeIf { it > 0 } ?: video.value?.durationMs ?: 0L
        historySession.capture(if (player.playbackState == Player.STATE_ENDED) 0L else normalizeResumePosition(player.currentPosition, duration))
    }

    override fun onCleared() {
        historyJob?.cancel()
        savePosition()
        playerController.release()
        super.onCleared()
    }
}
