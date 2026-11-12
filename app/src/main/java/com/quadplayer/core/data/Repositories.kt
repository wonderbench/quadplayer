package com.quadplayer.core.data

import android.net.Uri
import androidx.room.withTransaction
import com.quadplayer.core.database.PlayHistoryDao
import com.quadplayer.core.database.PlayHistoryEntity
import com.quadplayer.core.database.PlaylistDao
import com.quadplayer.core.database.PlaylistEntity
import com.quadplayer.core.database.PlaylistVideoEntity
import com.quadplayer.core.database.QuadPlayerDatabase
import com.quadplayer.core.database.VideoDao
import com.quadplayer.core.database.toModel
import com.quadplayer.core.model.FolderInfo
import com.quadplayer.core.model.PlaylistSummary
import com.quadplayer.core.model.RecentPlayback
import com.quadplayer.core.model.VideoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface VideoRepository {
    val videos: Flow<List<VideoFile>>
    val favorites: Flow<List<VideoFile>>
    val folders: Flow<List<FolderInfo>>

    fun videosByFolder(folderPath: String): Flow<List<VideoFile>>
    fun search(query: String): Flow<List<VideoFile>>
    suspend fun refreshVideos()
    suspend fun importVideo(uri: Uri): VideoFile?
    suspend fun getVideo(videoId: Long): VideoFile?
    suspend fun toggleFavorite(videoId: Long)
}

interface HistoryRepository {
    fun observeRecent(limit: Int = 100): Flow<List<RecentPlayback>>
    suspend fun getResumePosition(videoId: Long): Long
    suspend fun recordPlayback(videoId: Long, positionMs: Long)
}

interface PlaylistRepository {
    val playlists: Flow<List<PlaylistSummary>>

    fun observePlaylistVideos(playlistId: Long): Flow<List<VideoFile>>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addVideo(playlistId: Long, videoId: Long)
    suspend fun removeVideo(playlistId: Long, videoId: Long)
    suspend fun moveVideo(playlistId: Long, fromIndex: Int, toIndex: Int)
}

@Singleton
class DefaultVideoRepository @Inject constructor(
    private val videoDao: VideoDao,
    private val scanner: MediaStoreVideoScanner,
    private val database: QuadPlayerDatabase,
) : VideoRepository {
    override val videos: Flow<List<VideoFile>> = videoDao.observeAll().map { rows -> rows.map { it.toModel() } }
    override val favorites: Flow<List<VideoFile>> = videoDao.observeFavorites().map { rows -> rows.map { it.toModel() } }
    override val folders: Flow<List<FolderInfo>> = videoDao.observeFolders()

    override fun videosByFolder(folderPath: String): Flow<List<VideoFile>> {
        return videoDao.observeByFolder(folderPath).map { rows -> rows.map { it.toModel() } }
    }

    override fun search(query: String): Flow<List<VideoFile>> {
        return videoDao.search(query).map { rows -> rows.map { it.toModel() } }
    }

    override suspend fun refreshVideos() {
        withContext(Dispatchers.IO) {
        val scanned = scanner.scanVideos()
        database.withTransaction {
            videoDao.upsertAll(scanned)
            val uris = scanned.map { it.uriString }
            if (uris.isEmpty()) videoDao.clearScannedEntries() else videoDao.removeStaleScannedEntries(uris)
        }
        }
    }

    override suspend fun importVideo(uri: Uri): VideoFile? = withContext(Dispatchers.IO) {
        val existing = videoDao.getByUriString(uri.toString())
        if (existing != null) return@withContext existing.toModel()

        val imported = scanner.inspectImportedVideo(uri) ?: return@withContext null
        val id = videoDao.upsert(imported)
        (videoDao.getById(id) ?: imported.copy(id = id)).toModel()
    }

    override suspend fun getVideo(videoId: Long): VideoFile? = withContext(Dispatchers.IO) {
        videoDao.getById(videoId)?.toModel()
    }

    override suspend fun toggleFavorite(videoId: Long) = withContext(Dispatchers.IO) {
        val current = videoDao.getById(videoId) ?: return@withContext
        videoDao.setFavorite(videoId, !current.isFavorite)
    }
}

@Singleton
class DefaultHistoryRepository @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
) : HistoryRepository {
    override fun observeRecent(limit: Int): Flow<List<RecentPlayback>> {
        return playHistoryDao.observeRecent(limit).map { rows -> rows.map { it.toModel() } }
    }

    override suspend fun getResumePosition(videoId: Long): Long = withContext(Dispatchers.IO) {
        playHistoryDao.getByVideoId(videoId)?.lastPositionMs ?: 0L
    }

    override suspend fun recordPlayback(videoId: Long, positionMs: Long) = withContext(Dispatchers.IO) {
        val existing = playHistoryDao.getByVideoId(videoId)
        playHistoryDao.upsert(
            PlayHistoryEntity(
                id = existing?.id ?: 0,
                videoId = videoId,
                lastPositionMs = positionMs,
                lastPlayedAt = System.currentTimeMillis(),
            ),
        )
    }
}

@Singleton
class DefaultPlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
) : PlaylistRepository {
    override val playlists: Flow<List<PlaylistSummary>> = playlistDao.observePlaylists().map { rows ->
        rows.map { it.toModel() }
    }

    override fun observePlaylistVideos(playlistId: Long): Flow<List<VideoFile>> {
        return playlistDao.observePlaylistVideos(playlistId).map { rows -> rows.map { it.toModel() } }
    }

    override suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        playlistDao.insertPlaylist(
            PlaylistEntity(
                name = name.trim().ifBlank { "新播放列表" },
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        val playlist = playlistDao.getPlaylist(playlistId) ?: return@withContext
        playlistDao.upsertPlaylist(playlist.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        withContext(Dispatchers.IO) {
            playlistDao.deletePlaylistById(playlistId)
        }
    }

    override suspend fun addVideo(playlistId: Long, videoId: Long) = withContext(Dispatchers.IO) {
        val entries = playlistDao.getPlaylistEntries(playlistId)
        if (entries.any { it.videoId == videoId }) return@withContext
        val nextOrder = (entries.maxOfOrNull { it.sortOrder } ?: -1) + 1
        playlistDao.upsertEntries(listOf(PlaylistVideoEntity(playlistId, videoId, nextOrder)))
        touchPlaylist(playlistId)
    }

    override suspend fun removeVideo(playlistId: Long, videoId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeVideo(playlistId, videoId)
        reindexPlaylist(playlistId)
        touchPlaylist(playlistId)
    }

    override suspend fun moveVideo(playlistId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val entries = playlistDao.getPlaylistEntries(playlistId).toMutableList()
        if (fromIndex !in entries.indices || toIndex !in entries.indices) return@withContext
        val moved = entries.removeAt(fromIndex)
        entries.add(toIndex, moved)
        playlistDao.clearPlaylistEntries(playlistId)
        playlistDao.upsertEntries(entries.mapIndexed { index, entry -> entry.copy(sortOrder = index) })
        touchPlaylist(playlistId)
    }

    private suspend fun reindexPlaylist(playlistId: Long) {
        val entries = playlistDao.getPlaylistEntries(playlistId)
        playlistDao.clearPlaylistEntries(playlistId)
        playlistDao.upsertEntries(entries.mapIndexed { index, entry -> entry.copy(sortOrder = index) })
    }

    private suspend fun touchPlaylist(playlistId: Long) {
        val playlist = playlistDao.getPlaylist(playlistId) ?: return
        playlistDao.upsertPlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
    }
}
