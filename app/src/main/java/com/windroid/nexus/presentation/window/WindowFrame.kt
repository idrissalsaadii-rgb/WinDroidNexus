package com.windroid.nexus.presentation.window

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.windroid.nexus.domain.model.WindowInstance
import com.windroid.nexus.presentation.icons.WinIcons
import com.windroid.nexus.presentation.theme.NexusColors

/**
 * The reusable chrome around every app window: a draggable title bar with
 * minimize / maximize / close, a 1px accent border, a drop shadow, and a
 * resize grip at the bottom-right corner. Content is injected via [content]
 * so the frame is agnostic to which app it hosts.
 *
 * Gesture deltas are converted from px to dp before reaching the ViewModel so
 * all window math stays in density-independent units.
 */
@Composable
fun WindowFrame(
    window: WindowInstance,
    isFocused: Boolean,
    screenWidthDp: Float,
    screenHeightDp: Float,
    onFocus: () -> Unit,
    onMove: (dxDp: Float, dyDp: Float) -> Unit,
    onResize: (dwDp: Float, dhDp: Float) -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val cornerRadius = if (window.isMaximized) 0.dp else 10.dp
    val shape = RoundedCornerShape(cornerRadius)

    // Maximized windows occupy the full work area (above the taskbar).
    val bounds = if (window.isMaximized) {
        window.bounds.copy(x = 0f, y = 0f, width = screenWidthDp, height = screenHeightDp)
    } else window.bounds

    Box(
        modifier = Modifier
            .zIndex(window.zIndex.toFloat())
            .offset(x = bounds.x.dp, y = bounds.y.dp)
            .size(width = bounds.width.dp, height = bounds.height.dp)
            .shadow(if (isFocused) 18.dp else 8.dp, shape)
            .clip(shape)
            .background(NexusColors.WindowSurface)
            .border(
                width = 1.dp,
                color = if (isFocused) NexusColors.Accent.copy(alpha = 0.6f) else NexusColors.Divider,
                shape = shape,
            )
            // Tapping anywhere on the window raises it to the top of the stack.
            .pointerInput(window.windowId) {
                detectTapGestures(onPress = { onFocus() })
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            TitleBar(
                window = window,
                isFocused = isFocused,
                onDrag = { dxPx, dyPx ->
                    with(density) { onMove(dxPx.toDp().value, dyPx.toDp().value) }
                },
                onDragStart = onFocus,
                onMinimize = onMinimize,
                onMaximize = onMaximize,
                onClose = onClose,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(NexusColors.WindowSurface)
            ) { content() }
        }

        // Bottom-right resize grip (hidden while maximized).
        if (!window.isMaximized) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .pointerInput(window.windowId) {
                        detectDragGestures(
                            onDragStart = { onFocus() },
                            onDrag = { change, drag ->
                                change.consume()
                                with(density) { onResize(drag.x.toDp().value, drag.y.toDp().value) }
                            },
                        )
                    }
            )
        }
    }
}

@Composable
private fun TitleBar(
    window: WindowInstance,
    isFocused: Boolean,
    onDrag: (dxPx: Float, dyPx: Float) -> Unit,
    onDragStart: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(
                if (isFocused) NexusColors.WindowChrome
                else NexusColors.WindowChrome.copy(alpha = 0.7f)
            )
            // Dragging the title bar moves the window; double-tap maximizes.
            .pointerInput(window.windowId) {
                detectTapGestures(
                    onPress = { onDragStart() },
                    onDoubleTap = { onMaximize() },
                )
            }
            .pointerInput(window.windowId) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, drag ->
                        change.consume()
                        onDrag(drag.x, drag.y)
                    },
                )
            }
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = WinIcons.forKey(window.app.iconKey),
            contentDescription = null,
            tint = NexusColors.OnDark,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = window.title,
            style = MaterialTheme.typography.labelSmall,
            color = NexusColors.OnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        CaptionButton(Icons.Filled.Minimize, "Minimize", onMinimize)
        CaptionButton(Icons.Filled.CropSquare, "Maximize", onMaximize)
        CaptionButton(Icons.Filled.Close, "Close", onClose)
    }
}

@Composable
private fun CaptionButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 36.dp)
            .pointerInput(description) {
                detectTapGestures { onClick() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = NexusColors.OnDark,
            modifier = Modifier.size(15.dp),
        )
    }
}
