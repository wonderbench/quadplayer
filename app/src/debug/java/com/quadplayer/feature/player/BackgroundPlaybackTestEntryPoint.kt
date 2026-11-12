package com.quadplayer.feature.player

import com.quadplayer.core.data.SettingsRepository
import com.quadplayer.core.database.QuadPlayerDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackgroundPlaybackTestEntryPoint {
    fun host(): PlaybackSessionHost
    fun settings(): SettingsRepository
    fun database(): QuadPlayerDatabase
}
