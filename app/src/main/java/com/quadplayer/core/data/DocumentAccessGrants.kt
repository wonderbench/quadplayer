package com.quadplayer.core.data

import android.content.ContentResolver
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import androidx.core.net.toUri

/** Serializes a selection with revocation, so an old dialog cannot revoke a newer grant. */
internal class GrantSelectionGuard {
    private val revisions = mutableMapOf<String, Long>()

    @Synchronized fun snapshot(key: String): Long = revisions[key] ?: 0L

    @Synchronized fun <T> select(key: String, operation: () -> T): T {
        revisions[key] = (revisions[key] ?: 0L) + 1L
        return operation()
    }

    @Synchronized fun releaseIfUnchanged(key: String, revision: Long, operation: () -> Unit): Boolean {
        if ((revisions[key] ?: 0L) != revision) return false
        operation()
        return true
    }
}

internal data class DocumentGrantToken(val uri: String, val revision: Long)
internal enum class GrantReleaseResult { RELEASED, CHANGED, FAILED }

internal object DocumentAccessGrants {
    private val guard = GrantSelectionGuard()

    fun capture(uri: Uri) = DocumentGrantToken(uri.toString(), guard.snapshot(uri.toString()))

    fun isCurrent(uri: String, revision: Long): Boolean = guard.snapshot(uri) == revision

    fun noteSelection(uri: Uri) { guard.select(uri.toString()) { } }

    fun retainRead(resolver: ContentResolver, uri: Uri): Boolean = guard.select(uri.toString()) {
        runCatching { resolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess
    }

    fun releaseRead(resolver: ContentResolver, token: DocumentGrantToken): GrantReleaseResult = try {
        val unchanged = guard.releaseIfUnchanged(token.uri, token.revision) {
            val uri = token.uri.toUri()
            if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
                resolver.releasePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (unchanged) GrantReleaseResult.RELEASED else GrantReleaseResult.CHANGED
    } catch (_: SecurityException) {
        GrantReleaseResult.FAILED
    } catch (_: IllegalArgumentException) {
        GrantReleaseResult.FAILED
    }
}
