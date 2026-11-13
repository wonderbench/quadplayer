package com.quadplayer.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.quadplayer.core.data.DefaultPlaylistRepository
import com.quadplayer.core.data.QuadSessionRepository
import com.quadplayer.core.data.SavedQuadSession
import com.quadplayer.core.data.SavedQuadSlot
import com.quadplayer.core.model.LayoutMode
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), QuadPlayerDatabase::class.java)
    @After fun cleanUp() { context.deleteDatabase(name) }

    @Test fun intermediateSchemasMigrateToCurrentVersion() {
        for (version in 2..3) {
            context.deleteDatabase(name)
            helper.createDatabase(name, version).close()
            helper.runMigrationsAndValidate(
                name, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5
            ).close()
        }
    }

    @Test fun migrationKeepsPreviouslyRemovedManualVideosHidden() = runBlocking {
        context.deleteDatabase(name)
        helper.createDatabase(name, 4).use { db ->
            db.execSQL("INSERT INTO videos VALUES (1,'content://video/manual','v','imported','imported',100,100,1,1,'video/mp4',1,1,1,NULL,0)")
            db.execSQL("INSERT INTO play_history VALUES (1,1,42,1)")
        }
        helper.runMigrationsAndValidate(name, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).close()
        val db = Room.databaseBuilder(context, QuadPlayerDatabase::class.java, name)
            .allowMainThreadQueries().addMigrations(MIGRATION_4_5).build()
        try {
            assertTrue(db.videoDao().getById(1)!!.isHidden)
            assertFalse(db.videoDao().getById(1)!!.isAccessible)
            assertTrue(db.videoDao().observeAll().first().isEmpty())
            assertEquals(42L, db.playHistoryDao().getByVideoId(1)!!.lastPositionMs)
            db.videoDao().markAvailable(1)
            assertFalse(db.videoDao().getById(1)!!.isHidden)
            assertTrue(db.videoDao().getById(1)!!.isAccessible)
        } finally { db.close() }
    }

    @Test fun migrationRetainsReferencesWhenAccessIsRemovedAndRestored() = runBlocking {
        context.deleteDatabase(name)
        helper.createDatabase(name, 1).use { db ->
            db.execSQL("INSERT INTO videos VALUES (1,'content://video/1','v','folder','folder',100,100,1,1,'video/mp4',1,1,0,NULL)")
            db.execSQL("INSERT INTO play_history VALUES (1,1,42,1)")
            db.execSQL("INSERT INTO play_history VALUES (2,1,99,2)")
            db.execSQL("INSERT INTO play_history VALUES (3,1,77,2)")
            db.execSQL("INSERT INTO playlists VALUES (1,'list',1,1)")
            db.execSQL("INSERT INTO playlist_videos VALUES (1,1,0)")
        }
        helper.runMigrationsAndValidate(name, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).close()
        val db = Room.databaseBuilder(context, QuadPlayerDatabase::class.java, name)
            .allowMainThreadQueries().addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
        try {
            val dao = db.videoDao()
            val video = dao.getById(1)!!
            assertTrue(video.isFavorite && video.isAccessible)
            assertFalse(video.isHidden)
            assertEquals(77L, db.playHistoryDao().getByVideoId(1)!!.lastPositionMs)
            assertEquals(1, db.playlistDao().observePlaylistVideos(1).first().size)
            dao.markUnavailable(listOf(1))
            assertTrue(dao.observeAll().first().isEmpty())
            assertTrue(db.playlistDao().observePlaylistVideos(1).first().isEmpty())
            dao.upsert(video)
            assertEquals(1, dao.observeFavorites().first().size)
            assertEquals(1, db.playlistDao().observePlaylistVideos(1).first().size)
            assertEquals(77L, db.playHistoryDao().getByVideoId(1)!!.lastPositionMs)
            dao.upsert(video.copy(id = 2, uriString = "content://video/2", isFavorite = false))
            dao.upsert(video.copy(id = 3, uriString = "content://video/3", isFavorite = false))
            db.playlistDao().upsertEntries(listOf(PlaylistVideoEntity(1, 2, 1), PlaylistVideoEntity(1, 3, 2)))
            dao.upsert(video.copy(isAccessible = false))
            val repository = DefaultPlaylistRepository(db.playlistDao(), db, context)
            repository.swapVideos(1, 3, 2)
            assertEquals(listOf(3L, 2L), db.playlistDao().observePlaylistVideos(1).first().map { it.id })
            dao.upsert(video)
            assertEquals(listOf(1L, 3L, 2L), db.playlistDao().observePlaylistVideos(1).first().map { it.id })
            assertEquals(77L, db.playHistoryDao().getByVideoId(1)!!.lastPositionMs)
            db.playHistoryDao().upsert(PlayHistoryEntity(videoId = 1, lastPositionMs = 88, lastPlayedAt = 3))
            assertEquals(88L, db.playHistoryDao().getByVideoId(1)!!.lastPositionMs)
            assertEquals(1, db.playHistoryDao().observeRecent(100).first().count { it.video.id == 1L })
            val sessions = QuadSessionRepository(db.quadSessionDao())
            val saved = SavedQuadSession(listOf(SavedQuadSlot("content://video/1", 1234, 1.5f, false), null, null, null), LayoutMode.FOCUS_GRID, 0, 0)
            sessions.save(saved)
            assertEquals(saved, sessions.read())
            val history = db.playHistoryDao()
            history.upsert(PlayHistoryEntity(videoId = 2, lastPositionMs = 25, lastPlayedAt = 2))
            history.deleteByVideoId(1)
            assertEquals(null, history.getByVideoId(1))
            assertEquals(25L, history.getByVideoId(2)?.lastPositionMs)
            dao.markUnavailable(listOf(2))
            assertTrue(history.observeRecent(100).first().isEmpty())
            history.clear()
            dao.upsert(dao.getById(2)!!.copy(isAccessible = true))
            assertEquals(null, history.getByVideoId(2))
            assertTrue(dao.getById(1)!!.isFavorite)
        } finally { db.close() }
    }
}
