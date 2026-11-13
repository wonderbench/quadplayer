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
        data class Delete(val videoId: Long, val result: CompletableDeferred<Unit>) : Operation
        data class Clear(val result: CompletableDeferred<Unit>) : Operation
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
                        is Operation.Delete -> {
                            repository.deleteHistory(operation.videoId)
                            latest.remove(operation.videoId)
                            operation.result.complete(Unit)
                        }
                        is Operation.Clear -> {
                            repository.clearHistory()
                            latest.clear()
                            operation.result.complete(Unit)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    if (operation is Operation.Read) operation.result.cancel(cancelled)
                    if (operation is Operation.Delete) operation.result.cancel(cancelled)
                    if (operation is Operation.Clear) operation.result.cancel(cancelled)
                    throw cancelled
                } catch (failure: Exception) {
                    when (operation) {
                        is Operation.Read -> operation.result.completeExceptionally(failure)
                        is Operation.Delete -> operation.result.completeExceptionally(failure)
                        is Operation.Clear -> operation.result.completeExceptionally(failure)
                        is Operation.Save -> Log.w("HistoryWriteQueue", "Unable to save playback position", failure)
                    }
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

    suspend fun delete(videoId: Long) {
        val result = CompletableDeferred<Unit>()
        operations.send(Operation.Delete(videoId, result))
        result.await()
    }

    suspend fun clear() {
        val result = CompletableDeferred<Unit>()
        operations.send(Operation.Clear(result))
        result.await()
    }
}

/** A screen can record only after its media was successfully loaded. */
internal class PlaybackHistorySession(private val queue: HistoryWriteQueue) {
    private var loadedVideoId: Long? = null
    fun markLoaded(videoId: Long) { loadedVideoId = videoId }
    fun clear() { loadedVideoId = null }
    fun capture(positionMs: Long) { loadedVideoId?.let { queue.enqueue(it, positionMs) } }
}
