package com.windroid.nexus.presentation.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windroid.nexus.domain.model.AppDescriptor
import com.windroid.nexus.presentation.apps.AppRegistry
import com.windroid.nexus.presentation.icons.WinIcons
import com.windroid.nexus.presentation.input.CursorOverlay
import com.windroid.nexus.presentation.startmenu.StartMenu
import com.windroid.nexus.presentation.taskbar.Taskbar
import com.windroid.nexus.presentation.theme.NexusColors
import com.windroid.nexus.presentation.window.WindowFrame
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * The desktop composition root. Layers, back-to-front:
 *   1. Wallpaper
 *   2. Desktop shortcut icons
 *   3. Floating app windows (z-ordered)
 *   4. Start menu popup (when open)
 *   5. Taskbar
 *   6. Touch-to-mouse cursor overlay (when enabled)
 */
@Composable
fun DesktopScreen(viewModel: DesktopViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val stats by viewModel.systemStats.collectAsStateWithLifecycle()
    val now = rememberTicker()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidthDp = maxWidth.value
        val taskbarHeightDp = 52f
        val workAreaHeightDp = maxHeight.value - taskbarHeightDp

        // 1. Wallpaper — also the click target that dismisses the Start menu.
        NexusWallpaper(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { if (state.isStartMenuOpen) viewModel.dismissStartMenu() }
                }
        )

        // 2. Desktop shortcuts (double-tap to launch).
        DesktopShortcuts(
            apps = apps.filter { it.pinnedToTaskbar || it.id.value.startsWith("exe:") },
            onOpen = viewModel::openApp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        )

        // 3. Floating windows.
        state.visibleWindows.forEach { window ->
            WindowFrame(
                window = window,
                isFocused = state.focusedWindowId == window.windowId,
                screenWidthDp = screenWidthDp,
                screenHeightDp = workAreaHeightDp,
                onFocus = { viewModel.focusWindow(window.windowId) },
                onMove = { dx, dy -> viewModel.moveWindow(window.windowId, dx, dy) },
                onResize = { dw, dh -> viewModel.resizeWindow(window.windowId, dw, dh) },
                onMinimize = { viewModel.toggleMinimize(window.windowId) },
                onMaximize = { viewModel.toggleMaximize(window.windowId) },
                onClose = { viewModel.closeWindow(window.windowId) },
            ) {
                AppRegistry.Content(window.app.id)
            }
        }

        // 4. Start menu popup, anchored bottom-center above the taskbar.
        if (state.isStartMenuOpen) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (taskbarHeightDp + 10).dp),
            ) {
                StartMenu(apps = apps, onLaunch = viewModel::openApp)
            }
        }

        // 5. Taskbar.
        Taskbar(
            runningWindows = state.taskbarWindows,
            focusedWindowId = state.focusedWindowId,
            stats = stats,
            now = now,
            cursorMode = state.cursorMode,
            onStartClick = viewModel::toggleStartMenu,
            onItemClick = viewModel::onTaskbarItemClick,
            onToggleCursor = viewModel::toggleCursorMode,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 6. Cursor overlay (relative-movement touch-to-mouse).
        CursorOverlay(
            enabled = state.cursorMode,
            onClick = { _, _ -> /* click routed to the focused window surface */ },
        )
    }
}

@Composable
private fun DesktopShortcuts(
    apps: List<AppDescriptor>,
    onOpen: (AppDescriptor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        apps.take(10).forEach { app ->
            Column(
                modifier = Modifier
                    .size(width = 84.dp, height = 88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .pointerInput(app.id.value) {
                        detectTapGestures(onDoubleTap = { onOpen(app) })
                    }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NexusColors.Accent.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = WinIcons.forKey(app.iconKey),
                        contentDescription = app.label,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = app.label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Emits the current time once per second for the taskbar clock. */
@Composable
private fun rememberTicker(): LocalDateTime {
    val time by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1_000)
        }
    }
    return time
}
