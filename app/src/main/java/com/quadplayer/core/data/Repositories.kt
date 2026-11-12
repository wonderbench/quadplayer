package com.quadplayer.core.data

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
    suspend fun swapVideos(playlistId: Long, firstVideoId: Long, secondVideoId: Long)
}

@Singleton
class DefaultVideoRepository @Inject constructor(
    private val videoDao: VideoDao,
    private val scanner: MediaStoreVideoScanner,
    private val database: QuadPlayerDatabase,
) : VideoRepository {
    private val refreshCoordinator = RefreshCoordinator(CoroutineScope(SupervisorJob() + Dispatchers.IO), ::scanAndUpdate)
    override val videos: Flow<List<VideoFile>> = videoDao.observeAll().map { rows -> rows.map { it.toModel() } }
    override val favorites: Flow<List<VideoFile>> = videoDao.observeFavorites().map { rows -> rows.map { it.toModel() } }
    override val folders: Flow<List<FolderInfo>> = videoDao.observeFolders()

    override fun videosByFolder(folderPath: String): Flow<List<VideoFile>> {
        return videoDao.observeByFolder(folderPath).map { rows -> rows.map { it.toModel() } }
    }

    override fun search(query: String): Flow<List<VideoFile>> {
        return videoDao.search(query).map { rows -> rows.map { it.toModel() } }
    }

    override suspend fun refreshVideos() = refreshCoordinator.refresh()

    private suspend fun scanAndUpdate() {
        val scanned = try {
            scanner.scanVideos()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("VideoRepository", "MediaStore scan failed; retaining cached videos", failure)
            return
        }
        database.withTransaction {
            val changes = calculateScanChanges(videoDao.getAll(), scanned)
            changes.hideIds.chunked(500).forEach { videoDao.markUnavailable(it) }
            if (changes.upserts.isNotEmpty()) videoDao.upsertAll(changes.upserts)
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
        videoDao.getById(videoId)?.takeIf { it.isAccessible }?.toModel()
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
    private val database: QuadPlayerDatabase,
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

    override suspend fun swapVideos(playlistId: Long, firstVideoId: Long, secondVideoId: Long) = withContext(Dispatchers.IO) {
        if (firstVideoId == secondVideoId) return@withContext
        database.withTransaction {
            val visible = playlistDao.getVisibleVideoIds(playlistId).toSet()
            if (firstVideoId !in visible || secondVideoId !in visible) return@withTransaction
            val entries = playlistDao.getPlaylistEntries(playlistId)
            val first = entries.firstOrNull { it.videoId == firstVideoId } ?: return@withTransaction
            val second = entries.firstOrNull { it.videoId == secondVideoId } ?: return@withTransaction
            playlistDao.upsertEntries(listOf(first.copy(sortOrder = second.sortOrder), second.copy(sortOrder = first.sortOrder)))
            touchPlaylist(playlistId)
        }
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
