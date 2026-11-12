package com.quadplayer.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quadplayer.core.data.PlaylistRepository
import com.quadplayer.core.model.VideoFile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistUiState(
    val playlistId: Long = -1L,
    val videos: List<VideoFile> = emptyList(),
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    private val playlistId = savedStateHandle.getStateFlow("playlistId", -1L)

    val uiState: StateFlow<PlaylistUiState> = playlistId.flatMapLatest { id ->
        playlistRepository.observePlaylistVideos(id).map { videos ->
            PlaylistUiState(playlistId = id, videos = videos)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaylistUiState())

    fun moveUp(index: Int) {
        val state = uiState.value
        if (index !in 1..state.videos.lastIndex) return
        val videoId = state.videos[index].id
        val neighborId = state.videos[index - 1].id
        viewModelScope.launch {
            playlistRepository.swapVideos(state.playlistId, videoId, neighborId)
        }
    }

    fun moveDown(index: Int) {
        val state = uiState.value
        if (index < 0 || index >= state.videos.lastIndex) return
        val videoId = state.videos[index].id
        val neighborId = state.videos[index + 1].id
        viewModelScope.launch {
            playlistRepository.swapVideos(state.playlistId, videoId, neighborId)
        }
    }

    fun remove(videoId: Long) {
        viewModelScope.launch {
            playlistRepository.removeVideo(uiState.value.playlistId, videoId)
        }
    }
}
