package com.quadplayer.core.util

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import com.quadplayer.core.model.CapabilityWarning
import com.quadplayer.core.model.DeviceCapability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceCapabilityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun check(): DeviceCapability {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val availableRamMb = memoryInfo.availMem / (1024 * 1024)
        val codecInfos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filterNot(MediaCodecInfo::isEncoder)
            .filter { info -> info.supportedTypes.any { it.startsWith("video/") } }
        val hwDecoders = codecInfos.filterNot { it.name.contains("sw", ignoreCase = true) }
        val maxDecoders = hwDecoders.size.coerceAtMost(4)
        val warning = when {
            totalRamMb < 4096 && maxDecoders < 4 -> CapabilityWarning.BOTH
            totalRamMb < 4096 -> CapabilityWarning.LOW_RAM
            maxDecoders < 4 -> CapabilityWarning.LOW_CODECS
            else -> null
        }
        return DeviceCapability(
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            maxConcurrentDecoders = maxDecoders,
            supportsHevc = codecInfos.any { info -> info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) } },
            supportsAv1 = codecInfos.any { info -> info.supportedTypes.any { it.equals("video/av01", ignoreCase = true) } },
            warning = warning,
        )
    }
}
