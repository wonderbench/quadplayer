package com.quadplayer.core.data

import android.content.ContentResolver
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.provider.OpenableColumns
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
internal data class DocumentGrantItem(val uri: Uri, val label: String)
internal enum class GrantReleaseResult { RELEASED, CHANGED, FAILED }

internal object DocumentAccessGrants {
    private val guard = GrantSelectionGuard()

    fun capture(uri: Uri) = DocumentGrantToken(uri.toString(), guard.snapshot(uri.toString()))

    fun isCurrent(uri: String, revision: Long): Boolean = guard.snapshot(uri) == revision

    fun noteSelection(uri: Uri) { guard.select(uri.toString()) { } }

    /** Call on an IO dispatcher: a document provider may need to resolve its display name. */
    fun listReadGrants(resolver: ContentResolver): List<DocumentGrantItem> =
        resolver.persistedUriPermissions.filter { it.isReadPermission }.map { permission ->
            val uri = permission.uri
            val name = runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
            }.getOrNull()?.filterNot { it.isISOControl() }?.trim()?.take(160)?.takeIf { it.isNotEmpty() }
            val fallback = uri.lastPathSegment?.substringAfterLast('/')?.filterNot { it.isISOControl() }
                ?.trim()?.take(160)?.takeIf { it.isNotEmpty() }
            DocumentGrantItem(uri, name ?: fallback ?: uri.authority.orEmpty().ifBlank { "—" })
        }.sortedBy { it.label.lowercase(java.util.Locale.ROOT) }

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
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        GrantReleaseResult.FAILED
    }
}
