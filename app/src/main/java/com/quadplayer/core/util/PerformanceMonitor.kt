package com.quadplayer.core.util

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class PerformanceWarning {
    data object LowMemory : PerformanceWarning()
    data object ThermalThrottle : PerformanceWarning()
}

@Singleton
class PerformanceMonitor @Inject constructor() {
    private val _warnings = MutableSharedFlow<PerformanceWarning>(extraBufferCapacity = 4)
    val warnings: SharedFlow<PerformanceWarning> = _warnings.asSharedFlow()
    private var monitorJob: Job? = null

    fun start(context: Context, scope: CoroutineScope) {
        if (monitorJob != null) return
        monitorJob = scope.launch(Dispatchers.Default) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            while (isActive) {
                val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
                if (memoryInfo.availMem / (1024 * 1024) < 200) {
                    _warnings.tryEmit(PerformanceWarning.LowMemory)
                }
                val thermal = readCpuTemperature()
                if (thermal != null && thermal > 42f) {
                    _warnings.tryEmit(PerformanceWarning.ThermalThrottle)
                }
                delay(2000)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun readCpuTemperature(): Float? {
        return runCatching {
            val file = File("/sys/class/thermal/thermal_zone0/temp")
            if (!file.exists()) return null
            file.readText().trim().toFloatOrNull()?.div(1000f)
        }.getOrNull()
    }
}
