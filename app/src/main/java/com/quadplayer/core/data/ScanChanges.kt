package com.quadplayer.core.data

import com.quadplayer.core.database.VideoEntity

internal data class ScanChanges(val upserts: List<VideoEntity>, val hideIds: List<Long>)

internal fun calculateScanChanges(existing: List<VideoEntity>, scanned: List<VideoEntity>): ScanChanges {
    val byUri = existing.associateBy { it.uriString }
    val seen = scanned.mapTo(hashSetOf()) { it.uriString }
    val upserts = scanned.mapNotNull { fresh ->
        val old = byUri[fresh.uriString]
        val merged = if (old == null) fresh else fresh.copy(id = old.id, isFavorite = old.isFavorite,
            isManuallyAdded = old.isManuallyAdded, isAccessible = true)
        merged.takeIf { it != old }
    }
    val hideIds = existing.filter { !it.isManuallyAdded && it.isAccessible && it.uriString !in seen }.map { it.id }
    return ScanChanges(upserts, hideIds)
}
