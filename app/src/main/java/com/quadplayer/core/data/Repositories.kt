package com.quadplayer.core.data

import android.net.Uri
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.quadplayer.R
import android.util.Log
import androidx.core.net.toUri
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

interface VideoRepository {
    val videos: Flow<List<VideoFile>>
    val favorites: Flow<List<VideoFile>>
    val folders: Flow<List<FolderInfo>>

    suspend fun refreshVideos()
    suspend fun importVideo(uri: Uri): VideoFile?
    suspend fun getVideo(videoId: Long): VideoFile?
    suspend fun toggleFavorite(videoId: Long)
    suspend fun hideManualVideo(videoId: Long): Boolean
}

interface HistoryRepository {
    fun observeRecent(limit: Int = 100): Flow<List<RecentPlayback>>
    suspend fun getResumePosition(videoId: Long): Long
    suspend fun recordPlayback(videoId: Long, positionMs: Long)
    suspend fun deleteHistory(videoId: Long)
    suspend fun clearHistory()
}

interface PlaylistRepository {
    val playlists: Flow<List<PlaylistSummary>>

    fun observePlaylistVideos(playlistId: Long): Flow<List<VideoFile>>
    suspend fun createPlaylist(name: String): Long
    suspend fun createPlaylistWithVideos(name: String, videoIds: List<Long>): Long {
        val id = createPlaylist(name)
        videoIds.distinct().forEach { addVideo(id, it) }
        return id
    }
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
    private val manualAccessLock = Mutex()
    override val videos: Flow<List<VideoFile>> = videoDao.observeAll().map { rows -> rows.map { it.toModel() } }
    override val favorites: Flow<List<VideoFile>> = videoDao.observeFavorites().map { rows -> rows.map { it.toModel() } }
    override val folders: Flow<List<FolderInfo>> = videoDao.observeFolders()

    override suspend fun refreshVideos() = refreshCoordinator.refresh()

    private suspend fun scanAndUpdate() {
        val scanned = try {
            scanner.scanVideos()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("VideoRepository", "MediaStore scan failed; retaining cached videos", failure)
            checkManualAvailability()
            return
        }
        val authorizedUris = withContext(Dispatchers.IO) { scanner.persistedMediaUris() }
        database.withTransaction {
            val changes = calculateScanChanges(videoDao.getAll(), scanned, authorizedUris)
            changes.hideIds.chunked(500).forEach { videoDao.markUnavailable(it) }
            if (changes.upserts.isNotEmpty()) videoDao.upsertAll(changes.upserts)
        }
        checkManualAvailability()
    }

    private suspend fun checkManualAvailability() = manualAccessLock.withLock {
        val missing = mutableListOf<Long>()
        val restored = mutableListOf<Long>()
        videoDao.getAll().filter { it.isManuallyAdded && !it.isHidden }.forEach { video ->
            val access = try {
                scanner.manualVideoAccess(video.uriString.toUri())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Log.w("VideoRepository", "Could not check imported video availability", failure)
                ManualVideoAccess.UNKNOWN
            }
            when {
                access == ManualVideoAccess.UNAVAILABLE && video.isAccessible -> missing.add(video.id)
                access == ManualVideoAccess.AVAILABLE && !video.isAccessible -> restored.add(video.id)
            }
        }
        missing.chunked(500).forEach { videoDao.markManualUnavailable(it) }
        restored.chunked(500).forEach { videoDao.markManualAvailable(it) }
    }

    override suspend fun importVideo(uri: Uri): VideoFile? = withContext(Dispatchers.IO) { manualAccessLock.withLock {
        val imported = scanner.inspectImportedVideo(uri) ?: run {
            // Re-establish the MediaStore equivalent of a retained document grant after restart.
            if (uri.authority != android.provider.MediaStore.AUTHORITY || uri.toString() !in scanner.persistedMediaUris()) return@withLock null
            scanner.inspectImportedVideo(uri) ?: return@withLock null
        }
        val mediaUri = scanner.persistedMediaUri(uri)
        val normalized = if (mediaUri != null) imported.copy(uriString = mediaUri.toString(), isManuallyAdded = false) else imported
        database.withTransaction {
            val existing = videoDao.getByUriString(normalized.uriString)
            if (existing != null) {
                if (!existing.isAccessible || existing.isHidden) videoDao.markAvailable(existing.id)
                existing.copy(isAccessible = true, isHidden = false).toModel()
            } else {
                val id = videoDao.upsert(normalized)
                (videoDao.getById(id) ?: normalized.copy(id = id)).toModel()
            }
        }
    } }

    override suspend fun getVideo(videoId: Long): VideoFile? = withContext(Dispatchers.IO) {
        videoDao.getById(videoId)?.takeIf { it.isAccessible && !it.isHidden }?.toModel()
    }

    override suspend fun toggleFavorite(videoId: Long) = withContext(Dispatchers.IO) {
        val current = videoDao.getById(videoId) ?: return@withContext
        videoDao.setFavorite(videoId, !current.isFavorite)
    }

    override suspend fun hideManualVideo(videoId: Long): Boolean = withContext(Dispatchers.IO) {
        manualAccessLock.withLock { videoDao.hideManualVideo(videoId) == 1 }
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

    override suspend fun deleteHistory(videoId: Long) = withContext(Dispatchers.IO) { playHistoryDao.deleteByVideoId(videoId) }
    override suspend fun clearHistory() = withContext(Dispatchers.IO) { playHistoryDao.clear() }

    override suspend fun recordPlayback(videoId: Long, positionMs: Long) = withContext(Dispatchers.IO) {
        playHistoryDao.upsert(
            PlayHistoryEntity(
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
    @ApplicationContext private val context: Context,
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
                name = name.trim().ifBlank { context.getString(R.string.playlist_default_name) },
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun createPlaylistWithVideos(name: String, videoIds: List<Long>): Long = withContext(Dispatchers.IO) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            val id = playlistDao.insertPlaylist(PlaylistEntity(name = name.trim().ifBlank { context.getString(R.string.playlist_default_name) }, createdAt = now, updatedAt = now))
            playlistDao.upsertEntries(videoIds.distinct().mapIndexed { index, videoId -> PlaylistVideoEntity(id, videoId, index) })
            id
        }
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
        database.withTransaction {
            val entries = playlistDao.getPlaylistEntries(playlistId)
            if (entries.any { it.videoId == videoId }) return@withTransaction
            val nextOrder = (entries.maxOfOrNull { it.sortOrder } ?: -1) + 1
            playlistDao.upsertEntries(listOf(PlaylistVideoEntity(playlistId, videoId, nextOrder)))
            touchPlaylist(playlistId)
        }
    }

    override suspend fun removeVideo(playlistId: Long, videoId: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            playlistDao.removeVideo(playlistId, videoId)
            reindexPlaylist(playlistId)
            touchPlaylist(playlistId)
        }
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
