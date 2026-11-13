package com.quadplayer.feature.home

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
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
        val action = context.getString(R.string.import_remove_action)
        compose.onAllNodesWithContentDescription(action).assertCountEquals(1)
        compose.onNodeWithContentDescription(action).performClick()
        compose.runOnIdle { assertEquals(1L, removed) }
    }
}
