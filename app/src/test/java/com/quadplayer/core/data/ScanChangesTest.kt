package com.quadplayer.core.data

import com.quadplayer.core.database.VideoEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanChangesTest {
    private fun video(id: Long, uri: String, favorite: Boolean = false, accessible: Boolean = true, manual: Boolean = false) =
        VideoEntity(id, uri, "name", "folder", "folder", 1, 2, 3, 4, "video/mp4", 5, favorite, manual, null, accessible)

    @Test fun unchangedRowsAreNotRewrittenAndMissingRowsAreHidden() {
        val existing = listOf(video(1, "a", favorite = true), video(2, "b"), video(3, "manual", manual = true))
        val changes = calculateScanChanges(existing, listOf(video(0, "a"), video(0, "new")))
        assertEquals(listOf(video(0, "new")), changes.upserts)
        assertEquals(listOf(2L), changes.hideIds)
    }

    @Test fun restoredRowRetainsIdentityAndFavorite() {
        val old = video(7, "a", favorite = true, accessible = false)
        val changes = calculateScanChanges(listOf(old), listOf(video(0, "a")))
        assertEquals(listOf(old.copy(isAccessible = true)), changes.upserts)
        assertEquals(emptyList<Long>(), changes.hideIds)
    }

    @Test fun persistedAccessKeepsUnscannedMediaVisibleUntilGrantIsLost() {
        val existing = listOf(video(1, "granted"), video(2, "revoked"))
        assertEquals(listOf(2L), calculateScanChanges(existing, emptyList(), setOf("granted")).hideIds)
        assertEquals(listOf(1L, 2L), calculateScanChanges(existing, emptyList()).hideIds)
    }
}
