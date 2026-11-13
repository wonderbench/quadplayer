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
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.quadplayer.R
import com.quadplayer.core.data.DocumentAccessGrants
import com.quadplayer.core.data.DocumentGrantToken
import com.quadplayer.core.data.GrantReleaseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,
    private val historyRepository: HistoryRepository,
    private val historyQueue: HistoryWriteQueue,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    val historyError = MutableStateFlow(false)
    val removalError = MutableStateFlow<Int?>(null)
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
    fun dismissRemovalError() { removalError.value = null }

    internal fun hideManualVideo(videoId: Long, uriString: String, revoke: DocumentGrantToken? = null) {
        viewModelScope.launch {
            var hidden = false
            try {
                hidden = videoRepository.hideManualVideo(videoId, uriString, revoke?.revision)
                if (!hidden) removalError.value = R.string.import_remove_failed
                else if (revoke != null && (revoke.uri != uriString || withContext(Dispatchers.IO) {
                        DocumentAccessGrants.releaseRead(context.contentResolver, revoke)
                    } != GrantReleaseResult.RELEASED)) {
                    removalError.value = R.string.import_remove_grant_retained
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Log.w("HomeViewModel", "Unable to remove imported video", failure)
                removalError.value = if (hidden && revoke != null) R.string.import_remove_grant_retained else R.string.import_remove_failed
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
