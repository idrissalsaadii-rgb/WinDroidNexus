package com.windroid.nexus.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Original "Fluent-inspired" palette — intentionally *not* a copy of any
 * Microsoft brand asset. Accent and acrylic tones are our own values chosen to
 * feel at home in a desktop shell while remaining trademark-safe.
 */
object NexusColors {
    val Accent = Color(0xFF3B82F6)          // primary interactive blue
    val AccentPressed = Color(0xFF2563EB)
    val TaskbarScrim = Color(0xCC1B1D21)     // translucent "acrylic" taskbar
    val WindowChrome = Color(0xFF2A2D34)
    val WindowSurface = Color(0xFF1F2227)
    val WindowSurfaceLight = Color(0xFFF3F4F6)
    val StartSurface = Color(0xF21E2126)
    val OnDark = Color(0xFFE8EAED)
    val OnDarkMuted = Color(0xFF9AA0A6)
    val Divider = Color(0x22FFFFFF)
    val CloseHover = Color(0xFFE11D48)
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
}

private val DarkScheme = darkColorScheme(
    primary = NexusColors.Accent,
    onPrimary = Color.White,
    surface = NexusColors.WindowSurface,
    onSurface = NexusColors.OnDark,
    background = NexusColors.WindowSurface,
    onBackground = NexusColors.OnDark,
    surfaceVariant = NexusColors.WindowChrome,
    outline = NexusColors.OnDarkMuted,
)

private val LightScheme = lightColorScheme(
    primary = NexusColors.Accent,
    onPrimary = Color.White,
    surface = NexusColors.WindowSurfaceLight,
    onSurface = Color(0xFF1A1C1E),
)

private val NexusTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
)

@Composable
fun WinDroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = NexusTypography,
        content = content,
    )
}
