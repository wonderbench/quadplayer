package com.quadplayer.feature.filebrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NaturalNameOrderTest {
    @Test fun chineseAndEpisodeNumbersSortOnAndroid() {
        val order = NaturalNameOrder(Locale.CHINA)
        fun before(a: String, b: String) = order.compare(order.key(a), order.key(b)) < 0
        assertTrue(before("第2集", "第10集"))
        assertTrue(before("阿明", "李四"))
        assertTrue(before("李四", "张三"))
    }
}
