package com.quadplayer.core.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (Build.VERSION.SDK_INT >= 29 && powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
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

}
