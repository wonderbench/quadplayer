package com.quadplayer.feature.settings

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutSharedContentTest {
    @Test fun sharedGroupsResolveInChineseAndEnglish() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun localized(locale: Locale): Context {
            val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
            return context.createConfigurationContext(configuration)
        }
        val chinese = localized(Locale.CHINESE)
        val english = localized(Locale.ENGLISH)
        val zh = aboutSoftwareContent(chinese)
        val en = aboutSoftwareContent(english)
        assertEquals(listOf("应用简介", "核心功能", "系统要求", "使用指南"), aboutVisualSections(chinese).map { it.title })
        assertEquals(listOf("Introduction", "Features", "Requirements", "Guide"), aboutVisualSections(english).map { it.title })
        assertEquals(listOf(7, 6, 4, 4), zh.featureGroups.map { it.items.size })
        assertEquals(zh.featureGroups.map { it.items.size }, en.featureGroups.map { it.items.size })
        assertEquals(3, zh.requirements.size)
        assertEquals(listOf(4, 5), zh.guideGroups.map { it.steps.size })
        assertTrue(zh.featureGroups[0].items.any { it.contains("A–B") })
        assertTrue(en.featureGroups[1].items.any { it.contains("remember audio") })
    }
}
