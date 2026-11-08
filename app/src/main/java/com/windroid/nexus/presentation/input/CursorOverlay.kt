package com.windroid.nexus.presentation.input

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.windroid.nexus.presentation.theme.NexusColors
import androidx.compose.foundation.Canvas

/**
 * Touch-to-mouse emulation layer.
 *
 * When cursor mode is enabled, this transparent overlay captures drag gestures
 * anywhere on screen and moves a virtual arrow cursor accordingly (trackpad
 * style — relative movement with an acceleration factor), decoupling the
 * cursor from the raw touch point so the finger never hides the target. A
 * single tap issues a left click at the cursor's position via [onClick].
 *
 * The emitted click coordinate is reported in dp so callers can hit-test
 * against window bounds that are also expressed in dp.
 */
@Composable
fun CursorOverlay(
    enabled: Boolean,
    onClick: (xDp: Float, yDp: Float) -> Unit,
    modifier: Modifier = Modifier,
    acceleration: Float = 1.6f,
) {
    if (!enabled) return
    val density = LocalDensity.current

    // Cursor position stored in px for smooth sub-dp movement.
    var cursor by remember { mutableStateOf(Offset(200f, 200f)) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                detectDragGestures(
                    onDragEnd = {
                        with(density) { onClick(cursor.x.toDp().value, cursor.y.toDp().value) }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        val next = Offset(
                            x = (cursor.x + drag.x * acceleration).coerceIn(0f, size.width.toFloat()),
                            y = (cursor.y + drag.y * acceleration).coerceIn(0f, size.height.toFloat()),
                        )
                        cursor = next
                    },
                )
            },
    ) {
        val xDp: Dp = with(density) { cursor.x.toDp() }
        val yDp: Dp = with(density) { cursor.y.toDp() }
        ArrowCursor(
            modifier = Modifier
                .offset(x = xDp, y = yDp)
                .size(28.dp)
        )
    }
}

/** An original arrow-pointer glyph drawn with a Path (no bitmap asset). */
@Composable
private fun ArrowCursor(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val p = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, size.height * 0.75f)
            lineTo(size.width * 0.24f, size.height * 0.58f)
            lineTo(size.width * 0.40f, size.height * 0.92f)
            lineTo(size.width * 0.56f, size.height * 0.84f)
            lineTo(size.width * 0.40f, size.height * 0.50f)
            lineTo(size.width * 0.66f, size.height * 0.50f)
            close()
        }
        drawPath(p, Color.White)
        drawPath(p, Color.Black, style = Stroke(width = 2f))
    }
}

/** A small floating click-button pad shown when cursor mode is active. */
@Composable
fun ClickPad(
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(NexusColors.Accent.copy(alpha = 0.3f))
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onLeftClick() }, onDrag = { c, _ -> c.consume() })
                }
        )
    }
}
