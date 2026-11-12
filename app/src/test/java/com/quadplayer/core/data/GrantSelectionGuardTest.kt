package com.quadplayer.core.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantSelectionGuardTest {
    @Test fun staleDialogCannotRevokeANewerSelection() {
        val guard = GrantSelectionGuard()
        val old = guard.snapshot("video")
        guard.select("video") { }
        var released = false
        assertFalse(guard.releaseIfUnchanged("video", old) { released = true })
        assertFalse(released)
        val current = guard.snapshot("video")
        assertTrue(guard.releaseIfUnchanged("video", current) { released = true })
        assertTrue(released)
    }

    @Test fun selectingAndRevokingSameGrantAreSerialized() {
        val guard = GrantSelectionGuard()
        val old = guard.snapshot("video")
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val released = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val selection = pool.submit {
                guard.select("video") {
                    entered.countDown()
                    check(finish.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val release = pool.submit<Boolean> { guard.releaseIfUnchanged("video", old) { released.set(true) } }
            finish.countDown()
            selection.get(5, TimeUnit.SECONDS)
            assertFalse(release.get(5, TimeUnit.SECONDS))
            assertFalse(released.get())
        } finally { finish.countDown(); pool.shutdownNow() }
    }
}
