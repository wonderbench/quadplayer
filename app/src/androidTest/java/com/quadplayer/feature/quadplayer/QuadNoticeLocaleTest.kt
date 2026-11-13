package com.quadplayer.feature.quadplayer

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
class QuadNoticeLocaleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun retainedNoticeUsesCurrentLanguageAndPreservesArguments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locale = mutableStateOf(Locale.CHINESE)
        val notice = QuadNotice(R.string.quad_import_failed, listOf(3))
        compose.setContent {
            val configuration = Configuration(context.resources.configuration).apply { setLocale(locale.value) }
            val localized = context.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration) {
                Text(quadNoticeText(notice))
            }
        }
        compose.onNodeWithText("无法读取第 3 格选择的文件，请重新选择。").assertIsDisplayed()
        compose.runOnIdle { locale.value = Locale.ENGLISH }
        compose.onNodeWithText("Could not read the file for slot 3. Select it again.").assertIsDisplayed()
    }
}
