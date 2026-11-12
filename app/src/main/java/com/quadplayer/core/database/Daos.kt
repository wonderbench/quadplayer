package com.quadplayer.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.quadplayer.core.model.FolderInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos WHERE is_accessible = 1 ORDER BY date_modified DESC")
    fun observeAll(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE is_favorite = 1 AND is_accessible = 1 ORDER BY display_name COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT folder_path AS path, folder_name AS name, COUNT(*) AS videoCount
        FROM videos
        WHERE is_accessible = 1
        GROUP BY folder_path, folder_name
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeFolders(): Flow<List<FolderInfo>>

    @Query("SELECT * FROM videos WHERE folder_path = :folderPath AND is_accessible = 1 ORDER BY display_name COLLATE NOCASE ASC")
    fun observeByFolder(folderPath: String): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT * FROM videos
        WHERE is_accessible = 1 AND display_name LIKE '%' || :query || '%'
        ORDER BY display_name COLLATE NOCASE ASC
        """,
    )
    fun search(query: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :videoId LIMIT 1")
    suspend fun getById(videoId: Long): VideoEntity?

    @Query("SELECT * FROM videos WHERE uri_string = :uriString LIMIT 1")
    suspend fun getByUriString(uriString: String): VideoEntity?

    @Upsert
    suspend fun upsertAll(videos: List<VideoEntity>)

    @Upsert
    suspend fun upsert(video: VideoEntity): Long

    @Query("UPDATE videos SET is_favorite = :isFavorite WHERE id = :videoId")
    suspend fun setFavorite(videoId: Long, isFavorite: Boolean): Int

    @Query("UPDATE videos SET is_accessible = 0 WHERE is_manually_added = 0")
    suspend fun markScannedUnavailable()
}

@Dao
interface PlayHistoryDao {
    @Query(
        """
        SELECT v.*, ph.last_played_at, ph.last_position_ms
        FROM play_history ph
        INNER JOIN videos v ON ph.video_id = v.id
        WHERE v.is_accessible = 1
        ORDER BY ph.last_played_at DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<RecentPlaybackRow>>

    @Query("SELECT * FROM play_history WHERE video_id = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: Long): PlayHistoryEntity?

    @Upsert
    suspend fun upsert(history: PlayHistoryEntity)
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, p.updated_at, COUNT(v.id) AS video_count
        FROM playlists p
        LEFT JOIN playlist_videos pv ON p.id = pv.playlist_id
        LEFT JOIN videos v ON v.id = pv.video_id AND v.is_accessible = 1
        GROUP BY p.id, p.name, p.updated_at
        ORDER BY p.updated_at DESC
        """,
    )
    fun observePlaylists(): Flow<List<PlaylistSummaryRow>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylist(playlistId: Long): PlaylistEntity?

    @Query(
        """
        SELECT v.* FROM playlist_videos pv
        INNER JOIN videos v ON pv.video_id = v.id
        WHERE pv.playlist_id = :playlistId AND v.is_accessible = 1
        ORDER BY pv.sort_order ASC
        """,
    )
    fun observePlaylistVideos(playlistId: Long): Flow<List<VideoEntity>>

    @Query("SELECT v.id FROM playlist_videos pv JOIN videos v ON v.id = pv.video_id WHERE pv.playlist_id = :playlistId AND v.is_accessible = 1")
    suspend fun getVisibleVideoIds(playlistId: Long): List<Long>

    @Query("SELECT * FROM playlist_videos WHERE playlist_id = :playlistId ORDER BY sort_order ASC")
    suspend fun getPlaylistEntries(playlistId: Long): List<PlaylistVideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Upsert
    suspend fun upsertEntries(entries: List<PlaylistVideoEntity>)

    @Query("DELETE FROM playlist_videos WHERE playlist_id = :playlistId AND video_id = :videoId")
    suspend fun removeVideo(playlistId: Long, videoId: Long): Int

    @Query("DELETE FROM playlist_videos WHERE playlist_id = :playlistId")
    suspend fun clearPlaylistEntries(playlistId: Long): Int

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long): Int
}
