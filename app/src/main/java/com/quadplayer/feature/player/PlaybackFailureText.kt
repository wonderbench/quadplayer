package com.quadplayer.feature.player

import androidx.annotation.StringRes
import com.quadplayer.R

@StringRes
fun playbackFailureText(failure: PlaybackFailure?): Int = when (failure) {
    PlaybackFailure.PERMISSION -> R.string.player_error_permission
    PlaybackFailure.MISSING -> R.string.player_error_missing
    PlaybackFailure.UNSUPPORTED -> R.string.player_error_unsupported
    PlaybackFailure.OTHER, null -> R.string.player_error_failed
}

@StringRes
fun playbackFailureShortText(failure: PlaybackFailure?): Int = when (failure) {
    PlaybackFailure.PERMISSION -> R.string.player_error_permission_short
    PlaybackFailure.MISSING -> R.string.player_error_missing_short
    PlaybackFailure.UNSUPPORTED -> R.string.player_error_unsupported_short
    PlaybackFailure.OTHER, null -> R.string.player_error_failed
}
