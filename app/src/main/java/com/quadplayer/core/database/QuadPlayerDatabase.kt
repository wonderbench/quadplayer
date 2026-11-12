package com.quadplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VideoEntity::class,
        PlayHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistVideoEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class QuadPlayerDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun playlistDao(): PlaylistDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE videos ADD COLUMN is_accessible INTEGER NOT NULL DEFAULT 1")
    }
}
