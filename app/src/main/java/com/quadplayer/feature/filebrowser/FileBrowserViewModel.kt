package com.quadplayer.feature.filebrowser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quadplayer.R
import com.quadplayer.core.data.PlaylistRepository
import com.quadplayer.core.data.VideoRepository
import com.quadplayer.core.model.FolderInfo
import com.quadplayer.core.model.PlaylistSummary
import com.quadplayer.core.model.VideoFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FileBrowserSortMode {
    NAME,
    DATE,
    DURATION,
    SIZE,
}

data class FileBrowserUiState(
    val folders: List<FolderInfo> = emptyList(),
    val selectedFolder: FolderInfo? = null,
    val videos: List<VideoFile> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val folderQuery: String = "",
    val videoQuery: String = "",
    val sortMode: FileBrowserSortMode = FileBrowserSortMode.NAME,
    val totalVideoCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val totalSizeBytes: Long = 0L,
)

private data class FileBrowserInputs(
    val folders: List<FolderInfo>,
    val allVideos: List<VideoFile>,
    val playlists: List<PlaylistSummary>,
)

private data class FileBrowserQueryInputs(
    val currentPath: String?,
    val currentFolderQuery: String,
    val currentVideoQuery: String,
)

internal fun filterFolders(
    folders: List<FolderInfo>,
    query: String,
): List<FolderInfo> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return folders
    return folders.filter { folder ->
        folder.name.contains(normalizedQuery, ignoreCase = true) ||
            folder.path.contains(normalizedQuery, ignoreCase = true)
    }
}

internal fun filterAndSortVideos(
    videos: List<VideoFile>,
    selectedFolderPath: String?,
    query: String,
    sortMode: FileBrowserSortMode,
    locale: Locale = Locale.getDefault(),
): List<VideoFile> {
    val inScope = if (selectedFolderPath == null) {
        videos
    } else {
        videos.filter { it.folderPath == selectedFolderPath }
    }
    val normalizedQuery = query.trim()
    val filtered = if (normalizedQuery.isEmpty()) {
        inScope
    } else {
        inScope.filter { video ->
            video.displayName.contains(normalizedQuery, ignoreCase = true) ||
                video.folderName.contains(normalizedQuery, ignoreCase = true) ||
                video.folderPath.contains(normalizedQuery, ignoreCase = true)
        }
    }
    if (sortMode == FileBrowserSortMode.DATE) return filtered.sortedByDescending { it.dateModified }
    val order = NaturalNameOrder(locale)
    return filtered.map { it to order.key(it.displayName) }.sortedWith { a, b ->
        val primary = when (sortMode) {
            FileBrowserSortMode.DURATION -> b.first.durationMs.compareTo(a.first.durationMs)
            FileBrowserSortMode.SIZE -> b.first.sizeBytes.compareTo(a.first.sizeBytes)
            else -> 0
        }
        primary.takeIf { it != 0 }
            ?: order.compare(a.second, b.second).takeIf { it != 0 }
            ?: b.first.dateModified.compareTo(a.first.dateModified)
    }.map { it.first }
}

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val playlistRepository: PlaylistRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val selectedFolderPath = MutableStateFlow<String?>(null)
    private val folderQuery = MutableStateFlow("")
    private val videoQuery = MutableStateFlow("")
    private val sortMode = MutableStateFlow(FileBrowserSortMode.NAME)
    private val openPlaylistRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    private val repositoryInputs = combine(
        videoRepository.folders,
        videoRepository.videos,
        playlistRepository.playlists,
    ) { folders, allVideos, playlists ->
        FileBrowserInputs(
            folders = folders,
            allVideos = allVideos,
            playlists = playlists,
        )
    }

    private val queryInputs = combine(
        selectedFolderPath,
        folderQuery,
        videoQuery,
    ) { currentPath, currentFolderQuery, currentVideoQuery ->
        FileBrowserQueryInputs(
            currentPath = currentPath,
            currentFolderQuery = currentFolderQuery,
            currentVideoQuery = currentVideoQuery,
        )
    }

    val uiState: StateFlow<FileBrowserUiState> = combine(
        repositoryInputs,
        queryInputs,
        sortMode,
    ) { repositoryInputs, queryInputs, currentSortMode ->
        withContext(Dispatchers.Default) {
        val effectiveFolderPath = queryInputs.currentPath?.takeIf { path ->
            repositoryInputs.folders.any { it.path == path }
        }
        val selectedFolder = repositoryInputs.folders.firstOrNull { it.path == effectiveFolderPath }
        val visibleFolders = filterFolders(repositoryInputs.folders, queryInputs.currentFolderQuery)
        val visibleVideos = filterAndSortVideos(
            videos = repositoryInputs.allVideos,
            selectedFolderPath = effectiveFolderPath,
            query = queryInputs.currentVideoQuery,
            sortMode = currentSortMode,
            locale = context.resources.configuration.locales[0] ?: Locale.getDefault(),
        )
        FileBrowserUiState(
            folders = visibleFolders,
            selectedFolder = selectedFolder,
            videos = visibleVideos,
            playlists = repositoryInputs.playlists,
            folderQuery = queryInputs.currentFolderQuery,
            videoQuery = queryInputs.currentVideoQuery,
            sortMode = currentSortMode,
            totalVideoCount = repositoryInputs.allVideos.size,
            totalDurationMs = visibleVideos.sumOf { it.durationMs },
            totalSizeBytes = visibleVideos.sumOf { it.sizeBytes },
        )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FileBrowserUiState())

    val openPlaylistEvents = openPlaylistRequests.asSharedFlow()

    fun refresh() {
        viewModelScope.launch {
            videoRepository.refreshVideos()
        }
    }

    fun selectFolder(path: String?) {
        selectedFolderPath.value = path
        videoQuery.value = ""
    }

    fun updateFolderQuery(query: String) {
        folderQuery.value = query
    }

    fun updateVideoQuery(query: String) {
        videoQuery.value = query
    }

    fun setSortMode(mode: FileBrowserSortMode) {
        sortMode.value = mode
    }

    fun toggleFavorite(videoId: Long) {
        viewModelScope.launch {
            videoRepository.toggleFavorite(videoId)
        }
    }

    fun addVideoToPlaylist(playlistId: Long, videoId: Long) {
        viewModelScope.launch {
            playlistRepository.addVideo(playlistId, videoId)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun createPlaylistAndAdd(name: String, videoId: Long) {
        viewModelScope.launch {
            val playlistId = playlistRepository.createPlaylist(name)
            playlistRepository.addVideo(playlistId, videoId)
        }
    }

    fun createPlaylistFromCurrentView() {
        viewModelScope.launch {
            val currentState = uiState.value
            if (currentState.videos.isEmpty()) return@launch

            val playlistName = context.getString(
                R.string.folders_playlist_name_template,
                currentState.selectedFolder?.name ?: context.getString(R.string.folders_all_videos),
            )
            val playlistId = playlistRepository.createPlaylistWithVideos(playlistName, currentState.videos.map { it.id })
            openPlaylistRequests.emit(playlistId)
        }
    }
}
