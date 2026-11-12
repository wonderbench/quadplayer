package com.quadplayer.feature.player

data class PlaybackQueueState(
    val ids: List<Long> = emptyList(),
    val index: Int = 0,
    val repeatOne: Boolean = false,
) {
    val hasPrevious: Boolean get() = index > 0 && index < ids.size
    val hasNext: Boolean get() = index >= 0 && index < ids.lastIndex

    companion object {
        fun from(playlistIds: List<Long>, selectedId: Long): PlaybackQueueState {
            val index = playlistIds.indexOf(selectedId)
            return if (index >= 0) PlaybackQueueState(playlistIds.toList(), index)
            else PlaybackQueueState(listOf(selectedId), 0)
        }
    }
}
