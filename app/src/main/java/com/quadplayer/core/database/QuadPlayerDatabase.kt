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
        QuadSessionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class QuadPlayerDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun quadSessionDao(): QuadSessionDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE videos ADD COLUMN is_accessible INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS quad_session (id INTEGER NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
    }
}
