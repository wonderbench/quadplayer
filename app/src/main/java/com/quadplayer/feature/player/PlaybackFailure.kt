package com.quadplayer.feature.player

import androidx.media3.common.PlaybackException
import java.io.FileNotFoundException

enum class PlaybackFailure { PERMISSION, MISSING, UNSUPPORTED, OTHER }

internal fun classifyPlaybackFailure(code: Int, cause: Throwable?): PlaybackFailure {
    val causes = generateSequence(cause) { current -> current.cause?.takeUnless { it === current } }.take(20).toList()
    return when {
        code == PlaybackException.ERROR_CODE_IO_NO_PERMISSION || causes.any { it is SecurityException } -> PlaybackFailure.PERMISSION
        code == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND || causes.any { it is FileNotFoundException } -> PlaybackFailure.MISSING
        code == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
            code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> PlaybackFailure.UNSUPPORTED
        else -> PlaybackFailure.OTHER
    }
}
