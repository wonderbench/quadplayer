package com.quadplayer.feature.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentHistoryEmptyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyVisibleHistoryStillAllowsClearingHiddenRecords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cleared = mutableStateOf(false)
        compose.setContent { RecentList(emptyList(), "No visible history", {}, {}, { cleared.value = true }) }
        compose.onNodeWithText("No visible history").assertIsDisplayed()
        val clear = context.getString(R.string.history_clear)
        compose.onNodeWithText(clear).performClick()
        compose.onAllNodes(hasText(clear) and hasClickAction())[1].performClick()
        compose.runOnIdle { assertTrue(cleared.value) }
    }
}
