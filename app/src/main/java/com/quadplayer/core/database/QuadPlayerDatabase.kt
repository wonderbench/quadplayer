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
    version = 5,
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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            DELETE FROM play_history
            WHERE EXISTS (
                SELECT 1 FROM play_history newer
                WHERE newer.video_id = play_history.video_id
                  AND (newer.last_played_at > play_history.last_played_at
                       OR (newer.last_played_at = play_history.last_played_at AND newer.id > play_history.id))
            )
        """.trimIndent())
        db.execSQL("DROP INDEX IF EXISTS index_play_history_video_id")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_play_history_video_id ON play_history (video_id)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE videos ADD COLUMN is_hidden INTEGER NOT NULL DEFAULT 0")
        // Version 4 used is_accessible for explicit removal of manual entries.
        db.execSQL("UPDATE videos SET is_hidden = 1 WHERE is_manually_added = 1 AND is_accessible = 0")
    }
}
