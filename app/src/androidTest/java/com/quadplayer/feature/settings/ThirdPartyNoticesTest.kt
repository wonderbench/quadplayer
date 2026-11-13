package com.quadplayer.feature.settings

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThirdPartyNoticesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bundledLicensesLoadAndCanBeClosed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var closed = false
        compose.setContent { ThirdPartyNoticesDialog(onDismiss = { closed = true }) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Third-party components", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("androidx.media3:media3-exoplayer", substring = true).assertExists()
        compose.onNodeWithText("SIL OPEN FONT LICENSE", substring = true).assertExists()
        compose.onNodeWithText("Apache License", substring = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.common_close)).performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
}
