package com.quadplayer.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.json.JSONObject

private val Context.quadPresets: DataStore<Preferences> by preferencesDataStore(name = "quadplayer_presets")
private const val PREFIX = "preset_"

data class QuadPreset(val id: String, val name: String, val savedAt: Long)
enum class SavePresetResult { SAVED, INVALID_NAME, DUPLICATE_NAME, EMPTY_SESSION }

@Singleton
class QuadPresetRepository private constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.quadPresets)

    internal companion object {
        fun forTesting(store: DataStore<Preferences>) = QuadPresetRepository(store)
    }
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val presets = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            preferences.asMap().mapNotNull { (key, value) ->
                if (!key.name.startsWith(PREFIX) || value !is String) null
                else decode(key.name.removePrefix(PREFIX), value)?.first
            }.sortedByDescending { it.savedAt }
        }

    suspend fun save(name: String, snapshot: SavedQuadSession): SavePresetResult = writeScope.async {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 80 || trimmed.any { it.isISOControl() }) return@async SavePresetResult.INVALID_NAME
        if (snapshot.slots.none { it != null }) return@async SavePresetResult.EMPTY_SESSION
        var result = SavePresetResult.SAVED
        store.edit { preferences ->
            val duplicate = preferences.asMap().any { (key, value) ->
                key.name.startsWith(PREFIX) && value is String &&
                    decode(key.name.removePrefix(PREFIX), value)?.first?.name?.equals(trimmed, ignoreCase = true) == true
            }
            if (duplicate) result = SavePresetResult.DUPLICATE_NAME else {
                val id = UUID.randomUUID().toString()
                val payload = JSONObject().put("version", 1).put("name", trimmed).put("savedAt", System.currentTimeMillis())
                    .put("session", JSONObject(QuadSessionCodec.encode(snapshot))).toString()
                preferences[stringPreferencesKey(PREFIX + id)] = payload
            }
        }
        result
    }.await()

    suspend fun read(id: String): SavedQuadSession? {
        val payload = store.data.first()[stringPreferencesKey(PREFIX + id)] ?: return null
        return decode(id, payload)?.second
    }

    suspend fun delete(id: String) { writeScope.async { store.edit { it.remove(stringPreferencesKey(PREFIX + id)) } }.await() }

    private fun decode(id: String, payload: String): Pair<QuadPreset, SavedQuadSession>? = runCatching {
        val root = JSONObject(payload)
        require(root.optInt("version") == 1)
        val name = root.getString("name").trim().also { require(it.isNotEmpty() && it.length <= 80) }
        val session = QuadSessionCodec.decode(root.getJSONObject("session").toString()) ?: error("Invalid session")
        QuadPreset(id, name, root.optLong("savedAt")) to session
    }.getOrNull()
}
