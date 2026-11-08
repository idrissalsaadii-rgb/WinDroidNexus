package com.windroid.nexus.presentation.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps a descriptor's [iconKey] to a generic Material vector. Using neutral,
 * generic glyphs (folder, gear, globe) keeps the shell free of any specific
 * proprietary icon set while still reading as a desktop environment.
 *
 * The Start button deliberately uses a generic "apps" grid glyph rather than
 * any trademarked four-pane logo.
 */
object WinIcons {
    val Start: ImageVector = Icons.Filled.Apps

    fun forKey(key: String): ImageVector = when (key) {
        "folder" -> Icons.Filled.Folder
        "gear" -> Icons.Filled.Settings
        "terminal" -> Icons.Filled.Terminal
        "globe" -> Icons.Filled.Language
        "chart" -> Icons.Filled.BarChart
        "exe" -> Icons.Filled.Memory
        else -> Icons.Filled.Apps
    }
}
