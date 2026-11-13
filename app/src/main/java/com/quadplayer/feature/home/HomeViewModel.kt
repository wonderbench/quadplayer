package com.quadplayer.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quadplayer.core.data.HistoryRepository
import com.quadplayer.core.data.HistoryWriteQueue
import com.quadplayer.core.data.PlaylistRepository
import com.quadplayer.core.data.VideoRepository
import com.quadplayer.core.model.PlaylistSummary
import com.quadplayer.core.model.RecentPlayback
import com.quadplayer.core.model.VideoFile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.util.Log

data class HomeUiState(
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val videos: List<VideoFile> = emptyList(),
    val recent: List<RecentPlayback> = emptyList(),
    val favorites: List<VideoFile> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val historyRepository: HistoryRepository,
    private val historyQueue: HistoryWriteQueue,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    val historyError = MutableStateFlow(false)
    val removalError = MutableStateFlow(false)
    private var refreshCount = 0

    private val libraryState = combine(
        videoRepository.videos,
        historyRepository.observeRecent(),
        videoRepository.favorites,
        playlistRepository.playlists,
    ) { videos, recent, favorites, playlists ->
        Triple(videos, recent, favorites) to playlists
    }

    val uiState: StateFlow<HomeUiState> = combine(
        refreshing,
        query,
        libraryState,
    ) { isRefreshing, searchQuery, libraryData ->
        val (primary, playlists) = libraryData
        val (videos, recent, favorites) = primary
        val filteredVideos = if (searchQuery.isBlank()) {
            videos
        } else {
            videos.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        }
        HomeUiState(
            isRefreshing = isRefreshing,
            searchQuery = searchQuery,
            videos = filteredVideos,
            recent = recent,
            favorites = favorites.filter {
                searchQuery.isBlank() || it.displayName.contains(searchQuery, ignoreCase = true)
            },
            playlists = playlists,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun refresh() {
        viewModelScope.launch {
            refreshCount++
            refreshing.value = true
            try {
                videoRepository.refreshVideos()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("HomeViewModel", "Media refresh failed", failure)
            } finally {
                refreshCount--
                refreshing.value = refreshCount > 0
            }
        }
    }

    fun updateSearchQuery(value: String) {
        query.value = value
    }

    fun toggleFavorite(videoId: Long) {
        viewModelScope.launch {
            videoRepository.toggleFavorite(videoId)
        }
    }

    fun deleteHistory(videoId: Long) = changeHistory { historyQueue.delete(videoId) }
    fun clearHistory() = changeHistory { historyQueue.clear() }
    fun dismissHistoryError() { historyError.value = false }
    fun dismissRemovalError() { removalError.value = false }

    fun hideManualVideo(videoId: Long) {
        viewModelScope.launch {
            try {
                if (!videoRepository.hideManualVideo(videoId)) removalError.value = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Log.w("HomeViewModel", "Unable to remove imported video", failure)
                removalError.value = true
            }
        }
    }

    private fun changeHistory(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Log.w("HomeViewModel", "Unable to update history", failure)
                historyError.value = true
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun addVideoToPlaylist(playlistId: Long, videoId: Long) {
        viewModelScope.launch {
            playlistRepository.addVideo(playlistId, videoId)
        }
    }
}
