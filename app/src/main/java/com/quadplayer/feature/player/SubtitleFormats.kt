package com.quadplayer.feature.player

import androidx.media3.common.MimeTypes
import java.util.Locale
import java.io.File

internal fun findLocalSubtitle(videoPath: String?): File? = runCatching {
    val video = videoPath?.let(::File) ?: return@runCatching null
    val parent = video.parentFile ?: return@runCatching null
    listOf("srt", "ass", "ssa", "vtt").asSequence()
        .map { File(parent, "${video.nameWithoutExtension}.$it") }
        .firstOrNull { it.isFile && it.canRead() }
}.getOrNull()

internal fun subtitleMimeType(displayName: String?, providerMime: String?): String {
    return when (displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) {
        "srt" -> MimeTypes.APPLICATION_SUBRIP
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "vtt" -> MimeTypes.TEXT_VTT
        else -> when (providerMime?.lowercase(Locale.ROOT)) {
            "application/x-subrip", "application/srt" -> MimeTypes.APPLICATION_SUBRIP
            "text/x-ssa", "text/ssa", "text/x-ass" -> MimeTypes.TEXT_SSA
            "text/vtt" -> MimeTypes.TEXT_VTT
            else -> MimeTypes.TEXT_VTT
        }
    }
}
