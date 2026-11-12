package com.quadplayer.feature.filebrowser

import com.quadplayer.core.model.FolderInfo
import com.quadplayer.core.model.VideoFile
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBrowserViewModelTest {

    @Test
    fun nameSortUsesNaturalNumbersWithoutIntegerOverflow() {
        val names = listOf("第10集", "第2集", "第100000000000000000000集", "第02集", "第1集")
        val result = filterAndSortVideos(
            videos = names.mapIndexed { index, name -> testVideo(index.toLong(), "/a", name) },
            selectedFolderPath = "/a", query = "", sortMode = FileBrowserSortMode.NAME, locale = Locale.CHINA,
        )
        assertEquals(listOf("第1集", "第2集", "第02集", "第10集", "第100000000000000000000集"), result.map { it.displayName })
    }

    @Test
    fun nameSortUsesChineseCollationAndIgnoresCase() {
        val names = listOf("张三", "阿明", "李四", "zoo", "Alpha")
        val result = filterAndSortVideos(
            videos = names.mapIndexed { index, name -> testVideo(index.toLong(), "/a", name) },
            selectedFolderPath = "/a", query = "", sortMode = FileBrowserSortMode.NAME, locale = Locale.CHINA,
        ).map { it.displayName }
        assertTrue(result.indexOf("阿明") < result.indexOf("李四"))
        assertTrue(result.indexOf("李四") < result.indexOf("张三"))
        assertTrue(result.indexOf("Alpha") < result.indexOf("zoo"))
    }

    @Test
    fun filterFolders_matchesFolderNameAndPathIgnoringCase() {
        val folders = listOf(
            FolderInfo(path = "/storage/emulated/0/Movies/Anime", name = "Anime", videoCount = 5),
            FolderInfo(path = "/storage/emulated/0/Movies/Sports", name = "Sports", videoCount = 3),
        )

        val result = filterFolders(folders, "movie")

        assertEquals(2, result.size)
    }

    @Test
    fun filterAndSortVideos_limitsToSelectedFolderThenSortsBySize() {
        val result = filterAndSortVideos(
            videos = listOf(
                testVideo(id = 1, folderPath = "/a", displayName = "A", sizeBytes = 100, durationMs = 50),
                testVideo(id = 2, folderPath = "/b", displayName = "B", sizeBytes = 500, durationMs = 40),
                testVideo(id = 3, folderPath = "/b", displayName = "C", sizeBytes = 300, durationMs = 30),
            ),
            selectedFolderPath = "/b",
            query = "",
            sortMode = FileBrowserSortMode.SIZE,
        )

        assertEquals(listOf(2L, 3L), result.map { it.id })
    }

    @Test
    fun filterAndSortVideos_appliesSearchBeforeNameSort() {
        val result = filterAndSortVideos(
            videos = listOf(
                testVideo(id = 1, folderPath = "/a", displayName = "Zoo"),
                testVideo(id = 2, folderPath = "/a", displayName = "Alpha"),
                testVideo(id = 3, folderPath = "/a", displayName = "Beta Clip"),
            ),
            selectedFolderPath = "/a",
            query = "clip",
            sortMode = FileBrowserSortMode.NAME,
        )

        assertEquals(listOf("Beta Clip"), result.map { it.displayName })
        assertTrue(result.none { it.displayName == "Zoo" })
    }

    private fun testVideo(
        id: Long,
        folderPath: String,
        displayName: String,
        sizeBytes: Long = 0L,
        durationMs: Long = 0L,
    ) = VideoFile(
        id = id,
        uriString = "content://video/$id",
        displayName = displayName,
        folderPath = folderPath,
        folderName = folderPath.substringAfterLast('/'),
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        width = 1920,
        height = 1080,
        mimeType = "video/mp4",
        dateModified = id,
        isFavorite = false,
        isManuallyAdded = false,
        localPath = null,
    )
}
