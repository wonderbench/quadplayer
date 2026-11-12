package com.quadplayer.core.data

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Serializes snapshots in the order they were captured, beyond a screen's lifetime. */
@Singleton
class HistoryWriteQueue internal constructor(
    private val repository: HistoryRepository,
    scope: CoroutineScope,
) {
    @Inject constructor(repository: HistoryRepository) : this(
        repository,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val snapshots = Channel<Pair<Long, Long>>(Channel.UNLIMITED)

    init {
        scope.launch {
            for ((videoId, position) in snapshots) {
                try {
                    repository.recordPlayback(videoId, position)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w("HistoryWriteQueue", "Unable to save playback position", failure)
                }
            }
        }
    }

    fun enqueue(videoId: Long, positionMs: Long) {
        snapshots.trySend(videoId to positionMs.coerceAtLeast(0L))
    }
}
