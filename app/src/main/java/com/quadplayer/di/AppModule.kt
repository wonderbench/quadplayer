package com.quadplayer.di

import android.content.Context
import androidx.room.Room
import com.quadplayer.core.data.DataStoreSettingsRepository
import com.quadplayer.core.data.DefaultHistoryRepository
import com.quadplayer.core.data.DefaultPlaylistRepository
import com.quadplayer.core.data.DefaultVideoRepository
import com.quadplayer.core.data.HistoryRepository
import com.quadplayer.core.data.PlaylistRepository
import com.quadplayer.core.data.SettingsRepository
import com.quadplayer.core.data.VideoRepository
import com.quadplayer.core.database.PlayHistoryDao
import com.quadplayer.core.database.PlaylistDao
import com.quadplayer.core.database.QuadPlayerDatabase
import com.quadplayer.core.database.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): QuadPlayerDatabase {
        return Room.databaseBuilder(context, QuadPlayerDatabase::class.java, "quadplayer.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideVideoDao(database: QuadPlayerDatabase): VideoDao = database.videoDao()

    @Provides
    fun providePlayHistoryDao(database: QuadPlayerDatabase): PlayHistoryDao = database.playHistoryDao()

    @Provides
    fun providePlaylistDao(database: QuadPlayerDatabase): PlaylistDao = database.playlistDao()

    @Provides
    @Singleton
    fun provideVideoRepository(impl: DefaultVideoRepository): VideoRepository = impl

    @Provides
    @Singleton
    fun provideHistoryRepository(impl: DefaultHistoryRepository): HistoryRepository = impl

    @Provides
    @Singleton
    fun providePlaylistRepository(impl: DefaultPlaylistRepository): PlaylistRepository = impl

    @Provides
    @Singleton
    fun provideSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository = impl
}
