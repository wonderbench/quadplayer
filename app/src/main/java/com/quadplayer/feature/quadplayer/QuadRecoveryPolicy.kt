package com.quadplayer.feature.quadplayer

import android.content.Context
import android.net.Uri
import com.quadplayer.core.data.SavedQuadSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class RecoveryPrompt(val session: SavedQuadSession? = null, val visible: Boolean = false, val busy: Boolean = false) {
    fun offer(value: SavedQuadSession) = RecoveryPrompt(value, true)
    fun hide() = copy(visible = false)
    fun reopen() = if (session != null) copy(visible = true) else this
    fun discard() = RecoveryPrompt()
    val maySave: Boolean get() = session == null
}

internal fun recoveredAudioSource(requested: Int, available: List<Int>): Int = when {
    requested < 0 -> -1
    requested in available -> requested
    else -> available.firstOrNull() ?: -1
}

internal suspend fun canReadMedia(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }
    catch (_: Exception) { false }
}
