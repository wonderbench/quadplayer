package com.quadplayer.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlin.coroutines.coroutineContext

/** Coalesces a burst, then repeats once if a request arrived while scanning. */
internal class RefreshCoordinator(private val scope: CoroutineScope, private val scan: suspend () -> Unit) {
    private val lock = Any()
    private var active: Deferred<Unit>? = null
    private var requestedAgain = false

    suspend fun refresh() {
        val task = synchronized(lock) {
            active?.also { requestedAgain = true } ?: scope.async(start = CoroutineStart.LAZY) {
                try {
                    while (true) {
                        scan()
                        val repeat = synchronized(lock) {
                            if (requestedAgain) { requestedAgain = false; true }
                            else { active = null; false }
                        }
                        if (!repeat) break
                    }
                } finally {
                    val self = coroutineContext[Job]
                    synchronized(lock) {
                        if (active === self) { active = null; requestedAgain = false }
                    }
                }
            }.also { active = it; it.start() }
        }
        task.await()
    }
}
