package com.quadplayer.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quadplayer.core.model.AppSettings
import com.quadplayer.core.model.AppThemeMode
import com.quadplayer.core.model.DecoderMode
import com.quadplayer.core.model.PlaybackSpeed
import com.quadplayer.core.model.SubtitleStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "quadplayer_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

internal inline fun <reified T : Enum<T>> savedEnumOrDefault(value: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setDecoderMode(mode: DecoderMode)
    suspend fun setDefaultPlaybackSpeed(speed: PlaybackSpeed)
    suspend fun setDoubleTapSeekSeconds(seconds: Int)
    suspend fun setReverseDoubleTapSeek(enabled: Boolean)
    suspend fun setBackgroundPlayback(enabled: Boolean)
    suspend fun setAutoResume(enabled: Boolean)
    suspend fun setCrossfadeAudioSwitching(enabled: Boolean)
    suspend fun setThemeMode(themeMode: AppThemeMode)
    suspend fun setSubtitleStyle(style: SubtitleStyle)
    suspend fun setAutoPictureInPicture(enabled: Boolean)
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val dataStore = context.dataStore

    override val settings: Flow<AppSettings> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        AppSettings(
            decoderMode = savedEnumOrDefault(preferences[KEY_DECODER_MODE], DecoderMode.AUTO),
            defaultPlaybackSpeed = PlaybackSpeed.fromValue(preferences[KEY_DEFAULT_SPEED] ?: 1f),
            doubleTapSeekSeconds = preferences[KEY_DOUBLE_TAP_SECONDS] ?: 10,
            reverseDoubleTapSeek = preferences[KEY_REVERSE_DOUBLE_TAP] ?: false,
            backgroundPlayback = preferences[KEY_BACKGROUND_PLAYBACK] ?: false,
            autoResume = preferences[KEY_AUTO_RESUME] ?: true,
            crossfadeAudioSwitching = preferences[KEY_CROSSFADE_AUDIO] ?: true,
            themeMode = savedEnumOrDefault(preferences[KEY_THEME_MODE], AppThemeMode.SYSTEM),
            autoPictureInPicture = preferences[KEY_AUTO_PIP] ?: false,
            subtitleStyle = SubtitleStyle(
                fontScale = preferences[KEY_SUBTITLE_FONT_SCALE] ?: 1f,
                foregroundColor = preferences[KEY_SUBTITLE_FOREGROUND] ?: SubtitleStyle().foregroundColor,
                backgroundColor = preferences[KEY_SUBTITLE_BACKGROUND] ?: SubtitleStyle().backgroundColor,
            ),
        )
    }

    override suspend fun setDecoderMode(mode: DecoderMode) {
        dataStore.edit { it[KEY_DECODER_MODE] = mode.name }
    }

    override suspend fun setDefaultPlaybackSpeed(speed: PlaybackSpeed) {
        dataStore.edit { it[KEY_DEFAULT_SPEED] = speed.value }
    }

    override suspend fun setDoubleTapSeekSeconds(seconds: Int) {
        dataStore.edit { it[KEY_DOUBLE_TAP_SECONDS] = seconds }
    }

    override suspend fun setReverseDoubleTapSeek(enabled: Boolean) {
        dataStore.edit { it[KEY_REVERSE_DOUBLE_TAP] = enabled }
    }

    override suspend fun setBackgroundPlayback(enabled: Boolean) {
        dataStore.edit { it[KEY_BACKGROUND_PLAYBACK] = enabled }
    }

    override suspend fun setAutoResume(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_RESUME] = enabled }
    }

    override suspend fun setCrossfadeAudioSwitching(enabled: Boolean) {
        dataStore.edit { it[KEY_CROSSFADE_AUDIO] = enabled }
    }

    override suspend fun setThemeMode(themeMode: AppThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = themeMode.name }
    }

    override suspend fun setAutoPictureInPicture(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_PIP] = enabled }
    }

    override suspend fun setSubtitleStyle(style: SubtitleStyle) {
        dataStore.edit {
            it[KEY_SUBTITLE_FONT_SCALE] = style.fontScale
            it[KEY_SUBTITLE_FOREGROUND] = style.foregroundColor
            it[KEY_SUBTITLE_BACKGROUND] = style.backgroundColor
        }
    }

    private companion object {
        val KEY_DECODER_MODE = stringPreferencesKey("decoder_mode")
        val KEY_DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val KEY_DOUBLE_TAP_SECONDS = intPreferencesKey("double_tap_seconds")
        val KEY_REVERSE_DOUBLE_TAP = booleanPreferencesKey("reverse_double_tap")
        val KEY_BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val KEY_AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val KEY_CROSSFADE_AUDIO = booleanPreferencesKey("crossfade_audio")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_AUTO_PIP = booleanPreferencesKey("auto_picture_in_picture")
        val KEY_SUBTITLE_FONT_SCALE = floatPreferencesKey("subtitle_font_scale")
        val KEY_SUBTITLE_FOREGROUND = longPreferencesKey("subtitle_foreground")
        val KEY_SUBTITLE_BACKGROUND = longPreferencesKey("subtitle_background")
    }
}
