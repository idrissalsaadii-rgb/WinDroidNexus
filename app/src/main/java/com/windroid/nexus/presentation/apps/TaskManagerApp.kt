package com.windroid.nexus.presentation.apps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.windroid.nexus.domain.model.EngineProcess
import com.windroid.nexus.domain.model.SystemStats
import com.windroid.nexus.presentation.theme.NexusColors
import kotlin.math.roundToInt

/**
 * Task Manager window content: live CPU (with a scrolling history graph),
 * RAM, and battery meters, plus the list of processes running inside the
 * emulation engine. [stats] updates at the ViewModel's 1 Hz cadence.
 */
@Composable
fun TaskManagerApp(
    stats: SystemStats,
    engineProcesses: List<EngineProcess>,
    onEndProcess: (Int) -> Unit,
) {
    // Keep a bounded rolling history of CPU% for the graph. Appending in a
    // LaunchedEffect (not during composition) avoids snapshot write warnings
    // and runs exactly once per new stats tick.
    val cpuHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(stats.uptimeMillis) {
        cpuHistory.add(stats.cpuPercent)
        if (cpuHistory.size > MAX_HISTORY) cpuHistory.removeAt(0)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MeterCard(
                title = "CPU",
                valueLabel = "${stats.cpuPercent.roundToInt()}%",
                fraction = stats.cpuPercent / 100f,
                accent = NexusColors.Accent,
                modifier = Modifier.weight(1f),
            )
            MeterCard(
                title = "Memory",
                valueLabel = "${formatBytes(stats.ramUsedBytes)} / ${formatBytes(stats.ramTotalBytes)}",
                fraction = stats.ramUsedPercent / 100f,
                accent = NexusColors.Success,
                modifier = Modifier.weight(1f),
            )
            MeterCard(
                title = if (stats.isCharging) "Battery (charging)" else "Battery",
                valueLabel = "${stats.batteryPercent}%",
                fraction = stats.batteryPercent / 100f,
                accent = if (stats.batteryPercent <= 15) NexusColors.Warning else NexusColors.Success,
                modifier = Modifier.weight(1f),
            )
        }

        // CPU history graph.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .padding(12.dp),
        ) {
            androidx.compose.material3.Text(
                "% Utilization over 60s",
                color = NexusColors.OnDarkMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
            CpuGraph(
                history = cpuHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(top = 8.dp),
            )
            if (stats.perCorePercent.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    stats.perCorePercent.forEachIndexed { i, pct ->
                        CoreBar(index = i, percent = pct, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Engine process table.
        androidx.compose.material3.Text(
            "Engine processes (${engineProcesses.size})",
            color = NexusColors.OnDark,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        if (engineProcesses.isEmpty()) {
            androidx.compose.material3.Text(
                "No guest processes running.",
                color = NexusColors.OnDarkMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(engineProcesses, key = { it.pid }) { proc ->
                    ProcessRow(proc, onEndProcess)
                }
            }
        }
    }
}

@Composable
private fun MeterCard(
    title: String,
    valueLabel: String,
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.Text(
            title, color = NexusColors.OnDarkMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
        androidx.compose.material3.Text(
            valueLabel, color = NexusColors.OnDark,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent),
            )
        }
    }
}

@Composable
private fun CpuGraph(history: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Gridlines.
        val rows = 4
        repeat(rows + 1) { r ->
            val y = size.height * r / rows
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
        }
        if (history.size < 2) return@Canvas
        val stepX = size.width / (MAX_HISTORY - 1)
        val path = Path()
        history.forEachIndexed { i, pct ->
            val x = stepX * i
            val y = size.height * (1f - pct / 100f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, NexusColors.Accent, style = Stroke(width = 3f))
    }
}

@Composable
private fun CoreBar(index: Int, percent: Float, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(percent.coerceIn(0f, 100f) / 100f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(NexusColors.Accent),
            )
        }
        androidx.compose.material3.Text(
            "$index",
            color = NexusColors.OnDarkMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ProcessRow(proc: EngineProcess, onEndProcess: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            text = proc.label,
            color = NexusColors.OnDark,
            fontFamily = FontFamily.Monospace,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Text(
            text = "pid ${proc.pid}",
            color = NexusColors.OnDarkMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(80.dp),
        )
        androidx.compose.material3.Text(
            text = proc.status.name,
            color = when (proc.status.name) {
                "RUNNING" -> NexusColors.Success
                "FAILED" -> NexusColors.CloseHover
                else -> NexusColors.OnDarkMuted
            },
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(90.dp),
        )
        androidx.compose.material3.TextButton(onClick = { onEndProcess(proc.pid) }) {
            androidx.compose.material3.Text("End task", color = NexusColors.CloseHover)
        }
    }
}

private const val MAX_HISTORY = 60

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024; unit++
    }
    return "%.1f %s".format(value, units[unit])
}
