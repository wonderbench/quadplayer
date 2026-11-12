package com.quadplayer.core.data

import android.util.Log
import com.quadplayer.core.database.QuadSessionDao
import com.quadplayer.core.database.QuadSessionEntity
import com.quadplayer.core.model.LayoutMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class SavedQuadSlot(val uri: String, val positionMs: Long, val speed: Float, val playing: Boolean)
data class SavedQuadSession(
    val slots: List<SavedQuadSlot?>,
    val layout: LayoutMode,
    val focusedSlot: Int,
    val audioSource: Int,
)

internal object QuadSessionCodec {
    fun encode(session: SavedQuadSession): String {
        val slots = JSONArray()
        session.slots.forEach { slot ->
            slots.put(slot?.let { JSONObject().put("uri", it.uri).put("position", it.positionMs).put("speed", it.speed.toDouble()).put("playing", it.playing) } ?: JSONObject.NULL)
        }
        return JSONObject().put("version", 1).put("slots", slots).put("layout", session.layout.name)
            .put("focus", session.focusedSlot).put("audio", session.audioSource).toString()
    }
    fun decode(payload: String): SavedQuadSession? = runCatching {
        val root = JSONObject(payload)
        require(root.optInt("version") == 1)
        val entries = root.getJSONArray("slots")
        val slots = (0..3).map { index ->
            entries.optJSONObject(index)?.let { item ->
                val uri = item.optString("uri")
                if (uri.isBlank()) null else SavedQuadSlot(uri, item.optLong("position").coerceAtLeast(0),
                    item.optDouble("speed", 1.0).toFloat().takeIf { it.isFinite() && it > 0 } ?: 1f,
                    item.optBoolean("playing"))
            }
        }
        val layout = runCatching { LayoutMode.valueOf(root.optString("layout")) }.getOrDefault(LayoutMode.EQUAL_GRID)
        SavedQuadSession(slots, layout, root.optInt("focus", 0).coerceIn(0, 3), root.optInt("audio", -1).coerceIn(-1, 3))
    }.getOrNull()
}

@Singleton
class QuadSessionRepository @Inject constructor(private val dao: QuadSessionDao) {
    private sealed interface Operation {
        data class Save(val snapshot: SavedQuadSession) : Operation
        data class Read(val result: CompletableDeferred<SavedQuadSession?>) : Operation
    }
    private val operations = Channel<Operation>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        scope.launch {
            for (operation in operations) {
                try {
                    when (operation) {
                        is Operation.Save -> dao.save(QuadSessionEntity(payload = QuadSessionCodec.encode(operation.snapshot)))
                        is Operation.Read -> operation.result.complete(dao.get()?.payload?.let(QuadSessionCodec::decode))
                    }
                } catch (cancelled: CancellationException) {
                    if (operation is Operation.Read) operation.result.cancel(cancelled)
                    throw cancelled
                } catch (failure: Exception) {
                    if (operation is Operation.Read) operation.result.completeExceptionally(failure)
                    else Log.w("QuadSessionRepository", "Unable to save session", failure)
                }
            }
        }
    }
    fun save(snapshot: SavedQuadSession) { check(operations.trySend(Operation.Save(snapshot)).isSuccess) }
    suspend fun read(): SavedQuadSession? {
        val result = CompletableDeferred<SavedQuadSession?>()
        operations.send(Operation.Read(result))
        return result.await()
    }
}
