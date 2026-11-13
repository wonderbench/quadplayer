package com.quadplayer.feature.player

import androidx.media3.common.PlaybackException
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFailureTest {
    @Test fun classifiesActionableErrorsAndPrefersPermissionCause() {
        assertEquals(PlaybackFailure.PERMISSION, classifyPlaybackFailure(PlaybackException.ERROR_CODE_IO_NO_PERMISSION, null))
        assertEquals(PlaybackFailure.PERMISSION, classifyPlaybackFailure(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, SecurityException()))
        assertEquals(PlaybackFailure.MISSING, classifyPlaybackFailure(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, FileNotFoundException()))
        assertEquals(PlaybackFailure.UNSUPPORTED, classifyPlaybackFailure(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, null))
        assertEquals(PlaybackFailure.OTHER, classifyPlaybackFailure(PlaybackException.ERROR_CODE_UNSPECIFIED, null))
    }
}
