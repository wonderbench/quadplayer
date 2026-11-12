package com.quadplayer.feature.quadplayer

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuadBottomOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsDispatchTheirCallbacksWithoutViewModel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var presets = 0
        var playback = 0
        var interaction = 0
        compose.setContent {
            BottomOverlay(
                modifier = Modifier, slots = emptyList(), linkedSeek = false,
                onPresets = { presets++ }, presetsReady = true, onInteraction = { interaction++ },
                onTogglePlayAll = { playback++ }, onSynchronize = {}, onToggleLinkedSeek = {},
                onMuteAll = {}, onSetGlobalSpeed = {},
            )
        }
        compose.onNodeWithText(context.getString(R.string.quad_presets_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.quad_play_pause_all)).performClick()
        compose.runOnIdle {
            assertEquals(1, presets)
            assertEquals(1, playback)
            assertEquals(2, interaction)
        }
    }
}
