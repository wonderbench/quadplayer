package com.quadplayer.core.model

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class PlaybackState {
    IDLE,
    BUFFERING,
    READY,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
}

enum class LayoutMode {
    EQUAL_GRID,
}

enum class DecoderMode {
    AUTO,
    HARDWARE,
    SOFTWARE,
}

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AspectMode {
    FIT,
    FILL,
    ZOOM,
    SIXTEEN_NINE,
    FOUR_THREE,
    ORIGINAL,
}

enum class PlaybackSpeed(val value: Float, val label: String) {
    X0_5(0.5f, "0.5x"),
    X0_75(0.75f, "0.75x"),
    X1_0(1.0f, "1.0x"),
    X1_25(1.25f, "1.25x"),
    X1_5(1.5f, "1.5x"),
    X2_0(2.0f, "2.0x"),
    X3_0(3.0f, "3.0x"),
    ;

    companion object {
        fun fromValue(value: Float): PlaybackSpeed {
            return entries.minByOrNull { kotlin.math.abs(it.value - value) } ?: X1_0
        }
    }
}

data class SubtitleStyle(
    val fontScale: Float = 1f,
    val foregroundColor: Long = Color.White.toArgb().toLong(),
    val backgroundColor: Long = Color.Black.copy(alpha = 0.45f).toArgb().toLong(),
)

enum class CapabilityWarning {
    LOW_RAM,
    LOW_CODECS,
    BOTH,
}

data class DeviceCapability(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val maxConcurrentDecoders: Int,
    val supportsHevc: Boolean,
    val supportsAv1: Boolean,
    val warning: CapabilityWarning? = null,
)

data class VideoFile(
    val id: Long,
    val uriString: String,
    val displayName: String,
    val folderPath: String,
    val folderName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val dateModified: Long,
    val isFavorite: Boolean,
    val isManuallyAdded: Boolean,
    val localPath: String? = null,
) {
    val uri: Uri
        get() = Uri.parse(uriString)
}

data class FolderInfo(
    val path: String,
    val name: String,
    val videoCount: Int,
)

data class RecentPlayback(
    val video: VideoFile,
    val lastPlayedAt: Long,
    val lastPositionMs: Long,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val videoCount: Int,
)

data class AppSettings(
    val decoderMode: DecoderMode = DecoderMode.AUTO,
    val defaultPlaybackSpeed: PlaybackSpeed = PlaybackSpeed.X1_0,
    val doubleTapSeekSeconds: Int = 10,
    val reverseDoubleTapSeek: Boolean = false,
    val backgroundPlayback: Boolean = false,
    val autoResume: Boolean = true,
    val crossfadeAudioSwitching: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
)
