package com.quadplayer.core.util

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import com.quadplayer.core.model.CapabilityWarning
import com.quadplayer.core.model.DeviceCapability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class VideoCapabilityWarning {
    data object NoSupportedDecoder : VideoCapabilityWarning()
    data object SoftwareOnly : VideoCapabilityWarning()
    data class ConcurrentLimit(val count: Int, val reportedLimit: Int) : VideoCapabilityWarning()
}

internal data class DecoderSupport(val hardware: Boolean, val maxInstances: Int?)

internal fun assessDecoderSupport(supported: List<DecoderSupport>, activeCount: Int): VideoCapabilityWarning? {
    if (supported.isEmpty()) return VideoCapabilityWarning.NoSupportedDecoder
    val hardware = supported.filter { it.hardware }
    if (hardware.isEmpty()) return VideoCapabilityWarning.SoftwareOnly
    val limit = hardware.mapNotNull { it.maxInstances?.takeIf { n -> n > 0 } }.maxOrNull()
    return if (limit != null && activeCount > limit) VideoCapabilityWarning.ConcurrentLimit(activeCount, limit) else null
}

@Singleton
class DeviceCapabilityChecker @Inject constructor(@ApplicationContext private val context: Context) {
    private val decoders by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot(MediaCodecInfo::isEncoder)
    }
    private fun isHardware(info: MediaCodecInfo): Boolean = if (Build.VERSION.SDK_INT >= 29) {
        info.isHardwareAccelerated
    } else {
        val name = info.name.lowercase()
        !name.startsWith("omx.google.") && !name.startsWith("c2.android.") && !name.contains("software")
    }
    private fun maxInstances(info: MediaCodecInfo, mime: String): Int? = runCatching {
        info.getCapabilitiesForType(mime).maxSupportedInstances.takeIf { it > 0 }
    }.getOrNull()

    fun check(): DeviceCapability {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val total = memory.totalMem / (1024 * 1024)
        val avcLimit = decoders.filter(::isHardware).mapNotNull { info ->
            val mime = info.supportedTypes.firstOrNull { it.equals("video/avc", true) }
            mime?.let { maxInstances(info, it) }
        }.maxOrNull() ?: 0
        val lowCodec = avcLimit in 1..3
        val warning = when {
            total < 4096 && lowCodec -> CapabilityWarning.BOTH
            total < 4096 -> CapabilityWarning.LOW_RAM
            lowCodec -> CapabilityWarning.LOW_CODECS
            else -> null
        }
        return DeviceCapability(
            totalRamMb = total,
            availableRamMb = memory.availMem / (1024 * 1024),
            maxConcurrentDecoders = avcLimit,
            supportsHevc = decoders.any { it.supportedTypes.any { type -> type.equals("video/hevc", true) } },
            supportsAv1 = decoders.any { it.supportedTypes.any { type -> type.equals("video/av01", true) } },
            warning = warning,
        )
    }

    /** Returns a warning only when the actual video track could be inspected. Call off the main thread. */
    fun inspectVideo(uri: Uri, activeCount: Int): VideoCapabilityWarning? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val format = (0 until extractor.trackCount).map(extractor::getTrackFormat).firstOrNull {
                it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return null
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val supported = decoders.mapNotNull { info ->
                val type = info.supportedTypes.firstOrNull { it.equals(mime, true) } ?: return@mapNotNull null
                val caps = runCatching { info.getCapabilitiesForType(type) }.getOrNull() ?: return@mapNotNull null
                if (runCatching { caps.isFormatSupported(format) }.getOrDefault(false)) info to caps else null
            }
            assessDecoderSupport(supported.map { (info, caps) ->
                DecoderSupport(isHardware(info), runCatching { caps.maxSupportedInstances }.getOrNull())
            }, activeCount)
        } catch (_: Exception) {
            null
        } finally { extractor.release() }
    }
}
