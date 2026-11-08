package com.windroid.nexus.presentation.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * An ORIGINAL procedurally-drawn wallpaper — deep indigo gradient with soft
 * diagonal "ribbons". Generated entirely in code so the app ships no copied
 * Microsoft "Bloom"/Windows wallpaper. Swap this composable for an Image if you
 * supply your own licensed artwork.
 */
@Composable
fun NexusWallpaper(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Base vertical gradient.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0B1220),
                    Color(0xFF13203A),
                    Color(0xFF1E2A4A),
                )
            )
        )
        // Layered translucent ribbons for depth.
        drawRibbon(
            start = Offset(size.width * 0.05f, size.height * 0.75f),
            control = Offset(size.width * 0.5f, size.height * 0.2f),
            end = Offset(size.width * 1.05f, size.height * 0.55f),
            color = Color(0x333B82F6),
            thickness = size.height * 0.22f,
        )
        drawRibbon(
            start = Offset(-size.width * 0.05f, size.height * 0.45f),
            control = Offset(size.width * 0.55f, size.height * 0.95f),
            end = Offset(size.width * 1.1f, size.height * 0.35f),
            color = Color(0x222DD4BF),
            thickness = size.height * 0.18f,
        )
        // A soft radial glow toward the upper-left for a subtle light source.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x335B8DEF), Color.Transparent),
                center = Offset(size.width * 0.25f, size.height * 0.2f),
                radius = size.minDimension * 0.6f,
            ),
            radius = size.minDimension * 0.6f,
            center = Offset(size.width * 0.25f, size.height * 0.2f),
        )
    }
}

/** Draws a filled quadratic ribbon by offsetting a Bézier path vertically. */
private fun DrawScope.drawRibbon(
    start: Offset,
    control: Offset,
    end: Offset,
    color: Color,
    thickness: Float,
) {
    val path = Path().apply {
        moveTo(start.x, start.y)
        quadraticBezierTo(control.x, control.y, end.x, end.y)
        lineTo(end.x, end.y + thickness)
        quadraticBezierTo(control.x, control.y + thickness, start.x, start.y + thickness)
        close()
    }
    drawPath(path, color)
}
