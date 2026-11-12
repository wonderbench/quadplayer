package com.quadplayer.feature.quadplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QuadSlotAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptySlotClickAndLoadedSlotActionsAreAccessible() {
        var added = 0
        var selectedAudio = 0
        var openedMenu = 0
        compose.setContent {
            Row {
            Box(Modifier.size(100.dp).testTag("empty").then(quadSlotInteraction(
                true, "audio", "menu", { added++ }, {}, { selectedAudio++ }, { openedMenu++ },
            )))
            Box(Modifier.size(100.dp).testTag("loaded").then(quadSlotInteraction(
                false, "audio", "menu", {}, {}, { selectedAudio++ }, { openedMenu++ },
            )))
            }
        }
        compose.onNodeWithTag("empty").performClick()
        val actions = compose.onNodeWithTag("loaded").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertEquals(1, added)
            assertEquals(listOf("audio", "menu"), actions.map { it.label })
            actions.forEach { it.action() }
            assertEquals(1, selectedAudio)
            assertEquals(1, openedMenu)
        }
    }
}
