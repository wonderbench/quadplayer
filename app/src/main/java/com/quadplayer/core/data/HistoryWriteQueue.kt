package com.quadplayer.core.data

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Orders reads and writes across playback screens, including their destruction. */
@Singleton
class HistoryWriteQueue internal constructor(
    private val repository: HistoryRepository,
    scope: CoroutineScope,
) {
    @Inject constructor(repository: HistoryRepository) : this(repository, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private sealed interface Operation {
        data class Save(val videoId: Long, val position: Long) : Operation
        data class Read(val videoId: Long, val result: CompletableDeferred<Long>) : Operation
    }
    private val operations = Channel<Operation>(Channel.UNLIMITED)

    init {
        scope.launch {
            val latest = mutableMapOf<Long, Long>()
            for (operation in operations) {
                try {
                    when (operation) {
                        is Operation.Save -> {
                            latest[operation.videoId] = operation.position
                            repository.recordPlayback(operation.videoId, operation.position)
                        }
                        is Operation.Read -> operation.result.complete(latest[operation.videoId] ?: repository.getResumePosition(operation.videoId))
                    }
                } catch (cancelled: CancellationException) {
                    if (operation is Operation.Read) operation.result.cancel(cancelled)
                    throw cancelled
                } catch (failure: Exception) {
                    if (operation is Operation.Read) operation.result.completeExceptionally(failure)
                    else Log.w("HistoryWriteQueue", "Unable to save playback position", failure)
                }
            }
        }
    }

    fun enqueue(videoId: Long, positionMs: Long) {
        check(operations.trySend(Operation.Save(videoId, positionMs.coerceAtLeast(0L))).isSuccess)
    }

    suspend fun getResumePosition(videoId: Long): Long {
        val result = CompletableDeferred<Long>()
        operations.send(Operation.Read(videoId, result))
        return result.await()
    }
}

/** A screen can record only after its media was successfully loaded. */
internal class PlaybackHistorySession(private val queue: HistoryWriteQueue) {
    private var loadedVideoId: Long? = null
    fun markLoaded(videoId: Long) { loadedVideoId = videoId }
    fun clear() { loadedVideoId = null }
    fun capture(positionMs: Long) { loadedVideoId?.let { queue.enqueue(it, positionMs) } }
}
