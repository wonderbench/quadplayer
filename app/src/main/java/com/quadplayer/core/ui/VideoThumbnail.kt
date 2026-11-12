package com.quadplayer.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quadplayer.core.model.VideoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private object ThumbnailCache : LruCache<String, Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}
private val thumbnailSlots = Semaphore(3)

internal suspend fun loadVideoThumbnail(context: Context, video: VideoFile, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
    val key = "${video.uriString}|${video.dateModified}|$width|$height"
    ThumbnailCache.get(key)?.let { return@withContext it }
    thumbnailSlots.withPermit {
        ThumbnailCache.get(key)?.let { return@withPermit it }
        val result = runCatching {
            if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(video.uri, Size(width, height), null)
            else if (Build.VERSION.SDK_INT >= 27) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, video.uri)
                    retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, height)
                } finally { retriever.release() }
            } else {
                @Suppress("DEPRECATION")
                video.localPath?.let { ThumbnailUtils.createVideoThumbnail(it, MediaStore.Video.Thumbnails.MINI_KIND) }
            }
        }.getOrNull()
        if (result != null) ThumbnailCache.put(key, result)
        result
    }
}

@Composable
fun VideoThumbnail(video: VideoFile, modifier: Modifier = Modifier, width: Dp = 80.dp, height: Dp = 56.dp) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { width.roundToPx().coerceAtLeast(1) }
    val heightPx = with(density) { height.roundToPx().coerceAtLeast(1) }
    val thumbnail by key(video.uriString, video.dateModified, widthPx, heightPx) {
        produceState<Bitmap?>(null) { value = loadVideoThumbnail(context.applicationContext, video, widthPx, heightPx) }
    }
    Box(modifier.size(width, height).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val bitmap = thumbnail
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
