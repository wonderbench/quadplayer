package com.quadplayer.ui.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DepthChoiceChipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionIsExposedAndChangesWhenClicked() {
        compose.setContent {
            var selected by remember { mutableStateOf(false) }
            DepthChoiceChip(selected = selected, onClick = { selected = !selected }, label = { Text("Choice") })
        }
        compose.onNodeWithText("Choice").assertIsNotSelected().performClick().assertIsSelected()
    }
}
