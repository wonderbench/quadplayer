package com.quadplayer.feature.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    @Inject lateinit var host: PlaybackSessionHost
    private var current: PlaybackSession? = null
    private var session: MediaSession? = null
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val playback = current ?: return
            if (playbackState == Player.STATE_IDLE && playback.controller.player.mediaItemCount > 0 && !host.hasVisibleScreen(playback)) {
                playback.recordCurrentPosition()
                releasePlaybackSession()
                stopSelf()
            }
        }
    }
    private val mediaCallback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_SET_MEDIA_ITEM)
                .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .remove(Player.COMMAND_SET_PLAYLIST_METADATA)
                .remove(Player.COMMAND_SET_REPEAT_MODE)
                .remove(Player.COMMAND_SET_SHUFFLE_MODE)
                .remove(Player.COMMAND_SET_AUDIO_ATTRIBUTES)
                .remove(Player.COMMAND_SET_VIDEO_SURFACE)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(commands).build()
        }
    }
    private val sessionListener: (PlaybackSession?) -> Unit = { attachPlayback(it) }

    override fun onCreate() {
        super.onCreate()
        host.attachService(sessionListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        // A bound service can outlive stopService; reconnect on a new explicit start.
        host.attachService(sessionListener)
        return result
    }

    private fun attachPlayback(playback: PlaybackSession?) {
        current?.onBackgroundPolicyChanged = null
        current?.controller?.let { old ->
            old.player.removeListener(playerListener)
            old.onPlayerRebuilt = null
        }
        current = playback
        if (playback == null) {
            releasePlaybackSession()
            stopSelf()
            return
        }
        val controller = playback.controller
        playback.onBackgroundPolicyChanged = { enabled ->
            if (!enabled && !host.hasVisibleScreen(playback)) {
                playback.onBackgroundStateChanged(allowBackgroundPlayback = false, isInPictureInPicture = false)
                releasePlaybackSession()
                stopSelf()
            }
        }
        controller.player.addListener(playerListener)
        if (session == null) {
            val builder = MediaSession.Builder(this, controller.player).setCallback(mediaCallback)
            packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pending = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.setSessionActivity(pending)
            }
            session = builder.build().also { addSession(it) }
        } else {
            session?.player = controller.player
        }
        controller.onPlayerRebuilt = { replacement ->
            session?.player?.removeListener(playerListener)
            session?.player = replacement
            replacement.addListener(playerListener)
        }
    }

    private fun releasePlaybackSession() {
        current?.onBackgroundPolicyChanged = null
        current?.controller?.let { controller ->
            controller.player.removeListener(playerListener)
            controller.onPlayerRebuilt = null
        }
        session?.let { mediaSession ->
            removeSession(mediaSession)
            mediaSession.release()
        }
        session = null
        current = null
        host.detachService(sessionListener)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        releasePlaybackSession()
        super.onDestroy()
    }
}
