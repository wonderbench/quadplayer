package com.quadplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        VideoEntity::class,
        PlayHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistVideoEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class QuadPlayerDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun playlistDao(): PlaylistDao
}
