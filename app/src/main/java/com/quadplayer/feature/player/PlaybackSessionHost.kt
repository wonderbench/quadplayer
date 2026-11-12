package com.quadplayer.feature.player

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.quadplayer.core.data.HistoryWriteQueue
import com.quadplayer.core.data.PlaylistRepository
import com.quadplayer.core.data.SettingsRepository
import com.quadplayer.core.data.VideoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Owns playback while either a screen or the media service still needs it. */
@Singleton
class PlaybackSessionHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videos: VideoRepository,
    private val playlists: PlaylistRepository,
    private val history: HistoryWriteQueue,
    private val settings: SettingsRepository,
) {
    private val screens = linkedMapOf<PlaybackSession, ViewModelStore>()
    private val visibleScreens = mutableSetOf<PlaybackSession>()
    private var active: PlaybackSession? = null
    private var serviceAttached = false
    private var serviceRequested = false
    private var serviceListener: ((PlaybackSession?) -> Unit)? = null

    fun createScreenSession(arguments: SavedStateHandle): PlaybackSession {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PlaybackSession(
                context, arguments, videos, playlists, history, settings,
            ) as T
        }
        val session = ViewModelProvider(store, factory)[PlaybackSession::class.java]
        screens[session] = store
        activate(session)
        return session
    }

    fun activate(session: PlaybackSession) {
        if (active === session) return
        val old = active
        old?.controller?.pause()
        active = session
        serviceListener?.invoke(session)
        if (old != null && old !in screens) dispose(old)
    }

    fun startService() {
        serviceRequested = true
        try {
            checkNotNull(context.startService(Intent(context, PlaybackService::class.java)))
        } catch (failure: Exception) {
            serviceRequested = false
            throw failure
        }
    }

    fun stopService() {
        serviceRequested = false
        context.stopService(Intent(context, PlaybackService::class.java))
        val current = active
        if (!serviceAttached && current != null && current !in screens) {
            active = null
            dispose(current)
        }
    }

    fun attachService(listener: (PlaybackSession?) -> Unit) {
        serviceAttached = true
        serviceRequested = false
        serviceListener = listener
        listener(active)
    }

    fun detachService(listener: (PlaybackSession?) -> Unit) {
        if (serviceListener !== listener) return
        serviceListener = null
        serviceAttached = false
        serviceRequested = false
        val current = active
        if (current != null && current !in screens) {
            active = null
            dispose(current)
        }
    }

    fun setScreenVisible(session: PlaybackSession, visible: Boolean) {
        if (visible) visibleScreens.add(session) else visibleScreens.remove(session)
    }

    fun hasVisibleScreen(session: PlaybackSession): Boolean = session in visibleScreens
    internal fun hasActiveSession(): Boolean = active != null

    fun releaseScreen(session: PlaybackSession) {
        visibleScreens.remove(session)
        val store = screens.remove(session) ?: return
        if (session !== active || (!serviceAttached && !serviceRequested)) {
            if (session === active) active = null
            store.clear()
        } else {
            retained = session to store
        }
    }

    private var retained: Pair<PlaybackSession, ViewModelStore>? = null
    private fun dispose(session: PlaybackSession) {
        visibleScreens.remove(session)
        val retainedForSession = retained?.takeIf { it.first === session }
        val store = screens.remove(session) ?: retainedForSession?.second
        if (retainedForSession != null) retained = null
        store?.clear()
    }
}
