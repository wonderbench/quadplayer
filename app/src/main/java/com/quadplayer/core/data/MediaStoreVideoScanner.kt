package com.quadplayer.core.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.quadplayer.core.database.VideoEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreVideoScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Only normalize when the underlying document grant is persisted and the mapped URI remains readable. */
    fun persistedMediaUri(uri: Uri): Uri? {
        if (Build.VERSION.SDK_INT < 29 || !DocumentsContract.isDocumentUri(context, uri)) return null
        val resolver = context.contentResolver
        if (resolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission }) return null
        return mappedReadableVideoUri(uri)
    }

    fun persistedMediaUris(): Set<String> {
        if (Build.VERSION.SDK_INT < 29) return emptySet()
        return context.contentResolver.persistedUriPermissions.asSequence()
            .filter { it.isReadPermission }
            .mapNotNull { mappedReadableVideoUri(it.uri)?.toString() }
            .toSet()
    }

    private fun mappedReadableVideoUri(uri: Uri): Uri? {
        if (Build.VERSION.SDK_INT < 29 || !DocumentsContract.isDocumentUri(context, uri)) return null
        return runCatching {
            val mapped = MediaStore.getMediaUri(context, uri) ?: return@runCatching null
            if (mapped.authority != MediaStore.AUTHORITY || !mapped.path.orEmpty().contains("/video/media/")) return@runCatching null
            val readable = context.contentResolver.openFileDescriptor(mapped, "r")?.use { true } ?: false
            mapped.takeIf { readable }
        }.getOrNull()
    }

    suspend fun scanVideos(): List<VideoEntity> = withContext(Dispatchers.IO) {
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Video.Media.RELATIVE_PATH)
            add(MediaStore.MediaColumns.DATA)
        }.toTypedArray()

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Video.Media.SIZE} > 0",
            null,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val bucketIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val relativeIndex = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

            buildList {
                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idIndex)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
                    val folderPath = if (relativeIndex >= 0) cursor.getString(relativeIndex).orEmpty() else if (dataIndex >= 0) cursor.getString(dataIndex)?.substringBeforeLast('/', "").orEmpty() else ""
                    val folderName = cursor.getString(bucketIndex).orEmpty().ifBlank {
                        folderPath.trimEnd('/').substringAfterLast('/', "本地视频")
                    }
                    add(
                        VideoEntity(
                            uriString = uri.toString(),
                            displayName = cursor.getString(nameIndex) ?: "视频 $mediaId",
                            folderPath = folderPath.ifBlank { "storage" },
                            folderName = folderName.ifBlank { "本地视频" },
                            durationMs = cursor.getLong(durationIndex).coerceAtLeast(0L),
                            sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(0L),
                            width = cursor.getInt(widthIndex),
                            height = cursor.getInt(heightIndex),
                            mimeType = cursor.getString(mimeIndex) ?: "video/*",
                            dateModified = cursor.getLong(modifiedIndex),
                            localPath = if (dataIndex >= 0) cursor.getString(dataIndex) else null,
                        ),
                    )
                }
            }
        } ?: error("MediaStore query returned no cursor")
    }

    suspend fun inspectImportedVideo(uri: Uri): VideoEntity? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val readable = runCatching { resolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)
        if (!readable) return@withContext null
        val metadata = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    (if (nameIndex >= 0) cursor.getString(nameIndex) else null) to
                        (if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L)
                }
        }.getOrNull()
        val dimensions = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                Triple(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                )
            } finally { retriever.release() }
        }.getOrDefault(Triple(0L, 0, 0))
        VideoEntity(
            uriString = uri.toString(),
            displayName = metadata?.first?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "视频",
            folderPath = "imported",
            folderName = "手动导入",
            durationMs = dimensions.first,
            sizeBytes = metadata?.second ?: 0L,
            width = dimensions.second,
            height = dimensions.third,
            mimeType = runCatching { resolver.getType(uri) }.getOrNull() ?: "video/*",
            dateModified = System.currentTimeMillis(),
            isManuallyAdded = true,
        )
    }
}
