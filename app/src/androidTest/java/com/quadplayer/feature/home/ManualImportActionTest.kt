package com.quadplayer.feature.home

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.R
import com.quadplayer.core.model.VideoFile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualImportActionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyManualImportsOfferRemoval() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manual = VideoFile(1, "content://test/manual", "manual", "imported", "imported", 0, 0, 0, 0, "video/mp4", 0, false, true)
        val scanned = manual.copy(id = 2, uriString = "content://test/scanned", displayName = "scanned", isManuallyAdded = false)
        var removed = -1L
        compose.setContent { VideoList(listOf(manual, scanned), "empty", {}, {}, {}, { removed = it.id }) }
        compose.onNodeWithContentDescription(context.getString(R.string.video_more_actions, "manual")).performClick()
        val action = context.getString(R.string.import_remove_action)
        compose.onNodeWithText(action).performClick()
        compose.runOnIdle { assertEquals(1L, removed) }
        compose.onNodeWithContentDescription(context.getString(R.string.video_more_actions, "scanned")).performClick()
        compose.onAllNodesWithText(action).assertCountEquals(0)
    }

    @Test fun compactCardKeepsPlaybackFavoriteAndPlaylistActionsSeparate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val video = VideoFile(1, "content://test/video", "example.mp4", "folder", "folder", 1000, 0, 0, 0, "video/mp4", 0, false, false)
        var opened = 0
        var favorited = 0
        var added = 0
        compose.setContent { VideoList(listOf(video), "empty", { opened++ }, { favorited++ }, { added++ }, {}) }
        compose.onNodeWithContentDescription(context.getString(R.string.favorite_add)).performClick()
        compose.runOnIdle { assertEquals(0, opened); assertEquals(1, favorited) }
        compose.onNodeWithContentDescription(context.getString(R.string.video_more_actions, "example.mp4")).performClick()
        compose.onNodeWithText(context.getString(R.string.home_add_to_playlist)).performClick()
        compose.runOnIdle { assertEquals(0, opened); assertEquals(1, added) }
        compose.onNodeWithText("example.mp4").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
