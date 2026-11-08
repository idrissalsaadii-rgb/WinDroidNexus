package com.windroid.nexus.data.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.SystemClock
import com.windroid.nexus.core.util.DispatcherProvider
import com.windroid.nexus.domain.model.NetworkType
import com.windroid.nexus.domain.model.SystemStats
import com.windroid.nexus.domain.repository.SystemRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls device resource usage for the Task Manager widget.
 *
 * CPU: on most modern Android kernels `/proc/stat` is restricted, so we use it
 * when readable and otherwise fall back to a load-average approximation. Both
 * paths return an aggregate percentage plus best-effort per-core values.
 *
 * RAM: [ActivityManager.MemoryInfo] gives total/available process-wide memory.
 * Battery: a sticky [Intent.ACTION_BATTERY_CHANGED] broadcast.
 */
@Singleton
class SystemMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : SystemRepository {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Previous /proc/stat snapshot per core for delta-based CPU percentages. */
    private var previousCpuTicks: List<CpuTicks> = emptyList()

    override fun observeStats(intervalMillis: Long): Flow<SystemStats> = flow {
        while (true) {
            emit(sample())
            delay(intervalMillis)
        }
    }.flowOn(dispatchers.io)

    private fun sample(): SystemStats {
        val (cpuAggregate, perCore) = readCpu()
        val memInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val battery = readBattery()
        return SystemStats(
            cpuPercent = cpuAggregate,
            perCorePercent = perCore,
            ramUsedBytes = (memInfo.totalMem - memInfo.availMem).coerceAtLeast(0),
            ramTotalBytes = memInfo.totalMem,
            batteryPercent = battery.first,
            isCharging = battery.second,
            networkType = readNetworkType(),
            uptimeMillis = SystemClock.elapsedRealtime(),
        )
    }

    // ---------------------------------------------------------------------
    // CPU
    // ---------------------------------------------------------------------

    private data class CpuTicks(val name: String, val total: Long, val idle: Long)

    private fun readCpu(): Pair<Float, List<Float>> {
        val lines = runCatching {
            File("/proc/stat").readLines().filter { it.startsWith("cpu") }
        }.getOrNull()

        if (lines.isNullOrEmpty()) return loadAverageFallback()

        val current = lines.mapNotNull { parseCpuLine(it) }
        if (previousCpuTicks.isEmpty()) {
            previousCpuTicks = current
            return 0f to List((current.size - 1).coerceAtLeast(0)) { 0f }
        }

        val perCore = mutableListOf<Float>()
        var aggregate = 0f
        current.forEach { now ->
            val before = previousCpuTicks.firstOrNull { it.name == now.name } ?: return@forEach
            val totalDelta = (now.total - before.total).toFloat()
            val idleDelta = (now.idle - before.idle).toFloat()
            val usage = if (totalDelta <= 0f) 0f
            else ((totalDelta - idleDelta) / totalDelta * 100f).coerceIn(0f, 100f)
            if (now.name == "cpu") aggregate = usage else perCore += usage
        }
        previousCpuTicks = current
        return aggregate to perCore
    }

    private fun parseCpuLine(line: String): CpuTicks? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 5) return null
        val name = parts[0]
        val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return null
        val idle = values[3] + (values.getOrNull(4) ?: 0L) // idle + iowait
        val total = values.sum()
        return CpuTicks(name, total, idle)
    }

    /** When /proc/stat is unreadable, approximate from the 1-min load average. */
    private fun loadAverageFallback(): Pair<Float, List<Float>> {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val load1 = runCatching {
            File("/proc/loadavg").readText().trim().split(" ").first().toFloat()
        }.getOrDefault(0f)
        val pct = (load1 / cores * 100f).coerceIn(0f, 100f)
        return pct to List(cores) { pct }
    }

    // ---------------------------------------------------------------------
    // Battery + network
    // ---------------------------------------------------------------------

    private fun readBattery(): Pair<Int, Boolean> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return 0 to false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return percent to charging
    }

    private fun readNetworkType(): NetworkType {
        val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.NONE
        }
    }
}
