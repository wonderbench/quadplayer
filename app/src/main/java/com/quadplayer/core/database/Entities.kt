package com.quadplayer.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.quadplayer.core.model.PlaylistSummary
import com.quadplayer.core.model.RecentPlayback
import com.quadplayer.core.model.VideoFile

@Entity(
    tableName = "videos",
    indices = [Index(value = ["uri_string"], unique = true)],
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "uri_string") val uriString: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "folder_path") val folderPath: String,
    @ColumnInfo(name = "folder_name") val folderName: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "width") val width: Int,
    @ColumnInfo(name = "height") val height: Int,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_manually_added") val isManuallyAdded: Boolean = false,
    @ColumnInfo(name = "local_path") val localPath: String? = null,
    @ColumnInfo(name = "is_accessible", defaultValue = "1") val isAccessible: Boolean = true,
)

@Entity(
    tableName = "play_history",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["video_id"], unique = true)],
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "video_id") val videoId: Long,
    @ColumnInfo(name = "last_position_ms") val lastPositionMs: Long,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,
)

@Entity(tableName = "quad_session")
data class QuadSessionEntity(
    @PrimaryKey val id: Int = 0,
    val payload: String,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "playlist_videos",
    primaryKeys = ["playlist_id", "video_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlist_id"), Index("video_id")],
)
data class PlaylistVideoEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "video_id") val videoId: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

data class RecentPlaybackRow(
    @Embedded val video: VideoEntity,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,
    @ColumnInfo(name = "last_position_ms") val lastPositionMs: Long,
)

data class PlaylistSummaryRow(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "video_count") val videoCount: Int,
)

fun VideoEntity.toModel(): VideoFile = VideoFile(
    id = id,
    uriString = uriString,
    displayName = displayName,
    folderPath = folderPath,
    folderName = folderName,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    mimeType = mimeType,
    dateModified = dateModified,
    isFavorite = isFavorite,
    isManuallyAdded = isManuallyAdded,
    localPath = localPath,
)

fun RecentPlaybackRow.toModel(): RecentPlayback = RecentPlayback(
    video = video.toModel(),
    lastPlayedAt = lastPlayedAt,
    lastPositionMs = lastPositionMs,
)

fun PlaylistSummaryRow.toModel(): PlaylistSummary = PlaylistSummary(
    id = id,
    name = name,
    updatedAt = updatedAt,
    videoCount = videoCount,
)
