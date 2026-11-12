package com.quadplayer.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.quadplayer.core.model.PlaybackSpeed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.net.Uri

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val host: PlaybackSessionHost,
) : ViewModel() {
    private val session = host.createScreenSession(savedStateHandle)
    val state get() = session.state
    val controller get() = session.controller

    fun onScreenStarted() {
        host.setScreenVisible(session, true)
        host.activate(session)
        runCatching { host.startService() }.onFailure { session.showServiceError() }
    }
    fun onBackgroundStateChanged(allowBackgroundPlayback: Boolean, isInPictureInPicture: Boolean) {
        if (!isInPictureInPicture) host.setScreenVisible(session, false)
        session.onBackgroundStateChanged(allowBackgroundPlayback, isInPictureInPicture)
        if (!allowBackgroundPlayback && !isInPictureInPicture) host.stopService()
    }
    fun previous() = session.previous()
    fun next() = session.next()
    fun toggleRepeatOne() = session.toggleRepeatOne()
    fun togglePlayPause() = session.togglePlayPause()
    fun retry() = session.retry()
    fun seekBy(delta: Long) = session.seekBy(delta)
    fun seekTo(position: Long) = session.seekTo(position)
    fun setSpeed(speed: PlaybackSpeed) = session.setSpeed(speed)
    fun cycleAspectMode() = session.cycleAspectMode()
    fun selectAudioTrack(option: TrackOption?) = session.selectAudioTrack(option)
    fun selectSubtitleTrack(option: TrackOption?) = session.selectSubtitleTrack(option)
    fun setExternalSubtitle(uri: Uri?) = session.setExternalSubtitle(uri)
    fun setExternalAudio(uri: Uri?) = session.setExternalAudio(uri)
    fun clearStatusMessage() = session.clearStatusMessage()
    fun showControls() = session.showControls()
    fun hideControls() = session.hideControls()
    fun toggleControls() = session.toggleControls()

    override fun onCleared() {
        if (!session.backgroundEnabled) {
            session.controller.pause()
            host.stopService()
        }
        host.releaseScreen(session)
        super.onCleared()
    }
}
