package com.quadplayer.feature.player

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.Format
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject

/** Private to the device: persisted document grants cannot be transferred in backups. */
internal class TrackChoices(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("video_track_choices", Context.MODE_PRIVATE)

    private fun key(video: String, type: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(video.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) } + ":" + type
    }

    fun get(video: String, type: String): JSONObject? = runCatching {
        preferences.getString(key(video, type), null)?.let(::JSONObject)
    }.getOrNull()

    fun put(video: String, type: String, choice: JSONObject) {
        // apply() updates the in-process view synchronously, even on a quick reopen.
        preferences.edit { putString(key(video, type), choice.toString()) }
    }
}

internal fun trackFingerprint(format: Format): String = JSONObject().apply {
    put("id", format.id ?: JSONObject.NULL)
    put("language", format.language ?: JSONObject.NULL)
    put("label", format.label ?: JSONObject.NULL)
    put("mime", format.sampleMimeType ?: JSONObject.NULL)
    put("codecs", format.codecs ?: JSONObject.NULL)
    put("channels", format.channelCount)
    put("sampleRate", format.sampleRate)
    put("bitrate", format.bitrate)
    put("roles", format.roleFlags)
}.toString()
