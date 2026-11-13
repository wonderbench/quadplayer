package com.quadplayer.feature.settings

import android.content.Context
import androidx.core.net.toUri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.R
import com.quadplayer.core.data.DocumentGrantItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistedGrantManagerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun eachReleaseActionIdentifiesItsOwnDocument() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = DocumentGrantItem("content://example/one".toUri(), "First video")
        val second = DocumentGrantItem("content://example/two".toUri(), "Second video")
        var chosen: DocumentGrantItem? = null
        compose.setContent { GrantList(listOf(first, second), enabled = true, onRelease = { chosen = it }) }
        compose.onNodeWithText("First video").assertExists()
        compose.onNodeWithText("Second video").assertExists()
        val action = context.getString(R.string.settings_grant_revoke_action)
        compose.onAllNodesWithText(action).assertCountEquals(2)
        compose.onAllNodesWithText(action)[1].performClick()
        compose.runOnIdle { assertEquals(second, chosen) }
    }
}
