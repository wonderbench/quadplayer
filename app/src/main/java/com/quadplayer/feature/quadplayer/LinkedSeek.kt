package com.quadplayer.feature.quadplayer

internal data class SeekRange(val positionMs: Long, val durationMs: Long?)
internal data class LinkedSeekTargets(val positions: List<Long>, val limited: Boolean)

/** Clamp the shared movement to every video's available range to preserve offsets. */
internal fun linkedSeekTargets(ranges: List<SeekRange>, sourceIndex: Int, requestedMs: Long): LinkedSeekTargets {
    require(sourceIndex in ranges.indices)
    val source = ranges[sourceIndex].positionMs
    val requestedDelta = requestedMs.coerceAtLeast(0L) - source
    val minimumDelta = ranges.maxOf { -it.positionMs }
    val maximumDelta = ranges.minOf { (it.durationMs ?: Long.MAX_VALUE) - it.positionMs }
    val delta = requestedDelta.coerceIn(minimumDelta, maximumDelta.coerceAtLeast(minimumDelta))
    return LinkedSeekTargets(ranges.map { it.positionMs + delta }, delta != requestedDelta)
}
