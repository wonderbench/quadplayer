package com.quadplayer.feature.player

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.R
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerNoticeLocaleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun retainedNoticeUsesCurrentLanguage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locale = mutableStateOf(Locale.CHINESE)
        val notice = R.string.player_video_not_found
        compose.setContent {
            val configuration = Configuration(context.resources.configuration).apply { setLocale(locale.value) }
            val localized = context.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration) {
                Text(playerNoticeText(notice))
            }
        }
        compose.onNodeWithText("未找到该视频").assertIsDisplayed()
        compose.runOnIdle { locale.value = Locale.ENGLISH }
        compose.onNodeWithText("Video not found").assertIsDisplayed()
    }
}
