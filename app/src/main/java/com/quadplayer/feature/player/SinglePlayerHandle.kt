package com.quadplayer.feature.player

import android.net.Uri
import androidx.media3.common.Player

/** Additional single-video operations used by the playback session and service. */
interface SinglePlayerHandle : QuadPlayerHandle {
    var onPlayerRebuilt: ((Player) -> Unit)?
    fun togglePlayPause()
    fun seekBy(deltaMs: Long)
    fun cycleAspectMode()
    fun selectAudioTrack(option: TrackOption?)
    fun selectSubtitleTrack(option: TrackOption?)
    suspend fun setExternalSubtitle(uri: Uri?)
    suspend fun setExternalAudio(uri: Uri?)
}
