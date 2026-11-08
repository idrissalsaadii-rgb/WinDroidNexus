package com.windroid.nexus

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.windroid.nexus.data.engine.EngineForegroundService
import com.windroid.nexus.presentation.desktop.DesktopScreen
import com.windroid.nexus.presentation.theme.WinDroidTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. Responsibilities:
 *  - Draw edge-to-edge and hide the system bars for an immersive desktop.
 *  - Keep the screen on while the shell is foreground.
 *  - Start the engine's foreground service so guest processes survive
 *    backgrounding.
 *  - Host the Compose desktop.
 *
 * [AndroidEntryPoint] enables Hilt field/constructor injection into the
 * activity-scoped ViewModels used by [DesktopScreen].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: Compose paints behind the (now hidden) system bars.
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enterImmersiveMode()

        // Keep the compatibility runtime alive during execution.
        EngineForegroundService.start(this)

        setContent {
            WinDroidTheme(darkTheme = true) {
                DesktopScreen()
            }
        }
    }

    /**
     * Hides the status and navigation bars and enables "sticky" immersive
     * behavior so a swipe reveals them transiently without ending the mode.
     */
    private fun enterImmersiveMode() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-assert immersive mode after dialogs/keyboards relinquish focus.
        if (hasFocus) enterImmersiveMode()
    }

    override fun onDestroy() {
        // Only tear the engine down if the shell is truly finishing, not on a
        // configuration change.
        if (isFinishing) EngineForegroundService.stop(this)
        super.onDestroy()
    }
}
