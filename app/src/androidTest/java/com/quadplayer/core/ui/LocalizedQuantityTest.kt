package com.quadplayer.core.ui

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quadplayer.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizedQuantityTest {
    private fun resources(language: String): android.content.res.Resources {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        return context.createConfigurationContext(configuration).resources
    }

    @Test fun englishUsesSingularOnlyForOne() {
        val res = resources("en")
        assertEquals("0 videos", res.getQuantityString(R.plurals.common_video_count, 0, 0))
        assertEquals("1 video", res.getQuantityString(R.plurals.common_video_count, 1, 1))
        assertEquals("2 videos", res.getQuantityString(R.plurals.common_video_count, 2, 2))
        assertEquals("1 video · 5:00 total · 10 MB", res.getQuantityString(R.plurals.folders_current_view_summary, 1, 1, "5:00", "10 MB"))
        assertEquals("1 second", res.getQuantityString(R.plurals.settings_seek_seconds, 1, 1))
        assertEquals("Forward 2 seconds", res.getQuantityString(R.plurals.player_seek_forward, 2, 2))
    }

    @Test fun chineseStillFormatsNumbersAndSummary() {
        val res = resources("zh")
        assertEquals("1 个视频", res.getQuantityString(R.plurals.common_video_count, 1, 1))
        assertEquals("当前列表：2 个视频 · 总时长 5:00 · 总大小 10 MB", res.getQuantityString(R.plurals.folders_current_view_summary, 2, 2, "5:00", "10 MB"))
        assertEquals("快进 1 秒", res.getQuantityString(R.plurals.player_seek_forward, 1, 1))
    }
}
