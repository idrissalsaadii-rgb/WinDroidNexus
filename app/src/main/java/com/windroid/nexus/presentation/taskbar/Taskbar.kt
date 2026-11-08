package com.windroid.nexus.presentation.taskbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.windroid.nexus.domain.model.NetworkType
import com.windroid.nexus.domain.model.SystemStats
import com.windroid.nexus.domain.model.WindowInstance
import com.windroid.nexus.presentation.icons.WinIcons
import com.windroid.nexus.presentation.theme.NexusColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The bottom taskbar: a translucent "acrylic" bar with CENTER-aligned icons
 * (Start button + running/pinned apps) and a right-aligned system tray showing
 * network, battery and a live clock. Layout mirrors a modern desktop shell
 * without reproducing any proprietary icon artwork.
 */
@Composable
fun Taskbar(
    runningWindows: List<WindowInstance>,
    focusedWindowId: Long?,
    stats: SystemStats,
    now: LocalDateTime,
    cursorMode: Boolean,
    onStartClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onToggleCursor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(NexusColors.TaskbarScrim),
    ) {
        // --- Center cluster: Start + running apps ---
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StartButton(onClick = onStartClick)
            runningWindows.forEach { window ->
                TaskbarItem(
                    icon = WinIcons.forKey(window.app.iconKey),
                    label = window.title,
                    isActive = window.windowId == focusedWindowId && !window.isMinimized,
                    isOpen = true,
                    onClick = { onItemClick(window.windowId) },
                )
            }
        }

        // --- Right cluster: system tray ---
        SystemTray(
            stats = stats,
            now = now,
            cursorMode = cursorMode,
            onToggleCursor = onToggleCursor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 8.dp),
        )
    }
}

@Composable
private fun StartButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = WinIcons.Start,
            contentDescription = "Start",
            tint = NexusColors.Accent,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun TaskbarItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isOpen: Boolean,
    onClick: () -> Unit,
) {
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        label = "indicator",
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) Color.White.copy(alpha = 0.16f) else Color.Transparent
            )
            .pointerInput(label) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = NexusColors.OnDark,
            modifier = Modifier.size(22.dp),
        )
        // Running-app underline indicator.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 3.dp)
                .height(3.dp)
                .width(if (isActive) 16.dp else 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NexusColors.Accent.copy(alpha = indicatorAlpha))
        )
    }
}

@Composable
private fun SystemTray(
    stats: SystemStats,
    now: LocalDateTime,
    cursorMode: Boolean,
    onToggleCursor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Touch-to-mouse toggle lives in the tray for quick access.
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (cursorMode) NexusColors.Accent.copy(alpha = 0.25f) else Color.Transparent)
                .pointerInput(Unit) { detectTapGestures { onToggleCursor() } }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = if (cursorMode) "Cursor" else "Touch",
                style = MaterialTheme.typography.labelSmall,
                color = NexusColors.OnDark,
            )
        }

        Icon(
            imageVector = networkIcon(stats.networkType),
            contentDescription = "Network",
            tint = NexusColors.OnDark,
            modifier = Modifier.size(16.dp),
        )
        Icon(
            imageVector = if (stats.isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
            contentDescription = "Battery ${stats.batteryPercent}%",
            tint = if (stats.batteryPercent <= 15 && !stats.isCharging) NexusColors.Warning else NexusColors.OnDark,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "${stats.batteryPercent}%",
            style = MaterialTheme.typography.labelSmall,
            color = NexusColors.OnDark,
        )

        Clock(now)
    }
}

@Composable
private fun Clock(now: LocalDateTime) {
    val time = now.format(TIME_FORMAT)
    val date = now.format(DATE_FORMAT)
    Box(contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
            Text(time, style = MaterialTheme.typography.labelSmall, color = NexusColors.OnDark, textAlign = TextAlign.End)
            Text(date, style = MaterialTheme.typography.labelSmall, color = NexusColors.OnDarkMuted, textAlign = TextAlign.End)
        }
    }
}

private fun networkIcon(type: NetworkType): ImageVector = when (type) {
    NetworkType.WIFI, NetworkType.ETHERNET -> Icons.Filled.Wifi
    NetworkType.CELLULAR -> Icons.Filled.SignalCellularAlt
    NetworkType.NONE -> Icons.Filled.SignalWifiOff
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
