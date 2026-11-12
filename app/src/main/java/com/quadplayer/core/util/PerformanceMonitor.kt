package com.quadplayer.core.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed class PerformanceWarning {
    data object LowMemory : PerformanceWarning()
    data object ThermalThrottle : PerformanceWarning()
}

internal class WarningTransitions {
    private var active: Set<PerformanceWarning> = emptySet()
    fun update(lowMemory: Boolean, thermalThrottle: Boolean): List<PerformanceWarning> {
        val current = buildSet {
            if (lowMemory) add(PerformanceWarning.LowMemory)
            if (thermalThrottle) add(PerformanceWarning.ThermalThrottle)
        }
        val newlyActive = current - active
        active = current
        return newlyActive.toList()
    }
}

internal fun enqueueWarning(queue: List<PerformanceWarning>, warning: PerformanceWarning): List<PerformanceWarning> =
    if (warning in queue) queue else queue + warning

@Singleton
class PerformanceMonitor @Inject constructor() {
    private val warningEvents = Channel<PerformanceWarning>(Channel.BUFFERED)
    val warnings = warningEvents.receiveAsFlow()
    private var monitorJob: Job? = null

    fun start(context: Context, scope: CoroutineScope) {
        if (monitorJob != null) return
        monitorJob = scope.launch(Dispatchers.Default) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val transitions = WarningTransitions()
            while (isActive) {
                val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
                val hot = Build.VERSION.SDK_INT >= 29 && powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
                transitions.update(memoryInfo.lowMemory, hot).forEach { warningEvents.trySend(it) }
                delay(2000)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        while (warningEvents.tryReceive().isSuccess) { /* discard warnings from the old session */ }
    }

}
