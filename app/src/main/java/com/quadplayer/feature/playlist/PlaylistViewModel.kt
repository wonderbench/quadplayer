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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistUiState(
    val playlistId: Long = -1L,
    val name: String? = null,
    val videos: List<VideoFile> = emptyList(),
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    private val playlistId = savedStateHandle.getStateFlow("playlistId", -1L)

    val uiState: StateFlow<PlaylistUiState> = playlistId.flatMapLatest { id ->
        combine(playlistRepository.observePlaylistVideos(id), playlistRepository.playlists) { videos, playlists ->
            PlaylistUiState(playlistId = id, name = playlists.firstOrNull { it.id == id }?.name, videos = videos)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaylistUiState())

    fun moveUp(videoId: Long) {
        val state = uiState.value
        val index = state.videos.indexOfFirst { it.id == videoId }
        if (index !in 1..state.videos.lastIndex) return
        val neighborId = state.videos[index - 1].id
        viewModelScope.launch {
            playlistRepository.swapVideos(state.playlistId, videoId, neighborId)
        }
    }

    fun moveDown(videoId: Long) {
        val state = uiState.value
        val index = state.videos.indexOfFirst { it.id == videoId }
        if (index < 0 || index >= state.videos.lastIndex) return
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

    fun rename(name: String) {
        val id = playlistId.value
        val trimmed = name.trim()
        if (id < 0 || trimmed.isEmpty()) return
        viewModelScope.launch { playlistRepository.renamePlaylist(id, trimmed) }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = playlistId.value
        if (id < 0) return
        viewModelScope.launch { playlistRepository.deletePlaylist(id); onDeleted() }
    }
}
