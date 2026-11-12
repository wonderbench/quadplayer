package com.quadplayer.core.util

import kotlin.math.ln
import kotlin.math.pow
import java.util.concurrent.TimeUnit

fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs.coerceAtLeast(0L))
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroup = (ln(sizeBytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = sizeBytes / 1024.0.pow(digitGroup.toDouble())
    return if (value >= 100 || digitGroup == 0) {
        "%.0f %s".format(value, units[digitGroup])
    } else {
        "%.1f %s".format(value, units[digitGroup])
    }
}
