package com.quadplayer.feature.player

import androidx.media3.common.Player
import com.quadplayer.core.model.AppSettings
import com.quadplayer.core.model.PlaybackSpeed
import com.quadplayer.core.model.VideoFile
import kotlinx.coroutines.flow.StateFlow

/** The operations the quad engine needs, independent of a particular decoder. */
interface QuadPlayerHandle {
    val player: Player
    val uiState: StateFlow<PlayerUiState>
    suspend fun applySettings(settings: AppSettings)
    suspend fun load(video: VideoFile, startPositionMs: Long = 0L, playWhenReady: Boolean = true): Boolean
    fun setProgressUpdatesEnabled(enabled: Boolean)
    fun setPlaybackSpeed(speed: PlaybackSpeed)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setLoopStart(): Boolean
    fun setLoopEnd(): Boolean
    fun clearLoop()
    fun retry(playWhenReady: Boolean = true)
    fun release()
}
