package com.windroid.nexus.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Immutable domain models for WinDroid Nexus.
 *
 * These are pure Kotlin types with no Android framework or Compose-UI
 * dependencies beyond lightweight value classes (Dp), keeping the domain
 * layer testable in isolation.
 */

/** Stable identity for every built-in or user-launched application. */
@JvmInline
value class AppId(val value: String) {
    companion object {
        val FILE_EXPLORER = AppId("sys.file_explorer")
        val SETTINGS = AppId("sys.settings")
        val TERMINAL = AppId("sys.terminal")
        val BROWSER = AppId("sys.browser")
        val TASK_MANAGER = AppId("sys.task_manager")
        /** Dynamically created when the user launches an external .exe. */
        fun forBinary(path: String) = AppId("exe:$path")
    }
}

/** A launchable entry shown on the desktop and in the Start menu. */
@Immutable
data class AppDescriptor(
    val id: AppId,
    val label: String,
    val iconKey: String,
    val pinnedToStart: Boolean = false,
    val pinnedToTaskbar: Boolean = false,
    /** Null for native shell apps; set for binaries run through the engine. */
    val executablePath: String? = null,
)

/** Rectangular bounds of a floating window, expressed in dp. */
@Immutable
data class WindowBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val offset get() = DpOffset(x.dp, y.dp)

    fun movedBy(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)

    fun resizedBy(dw: Float, dh: Float, minW: Float, minH: Float) = copy(
        width = (width + dw).coerceAtLeast(minW),
        height = (height + dh).coerceAtLeast(minH),
    )

    companion object {
        /** Cascading default so freshly opened windows don't stack exactly. */
        fun cascade(index: Int) = WindowBounds(
            x = 120f + (index % 6) * 32f,
            y = 80f + (index % 6) * 32f,
            width = 820f,
            height = 520f,
        )
    }
}

enum class WindowVisualState { NORMAL, MINIMIZED, MAXIMIZED }

/**
 * Live state of a single on-screen window. [zIndex] drives stacking order;
 * the focused window always owns the highest index.
 */
@Immutable
data class WindowInstance(
    val windowId: Long,
    val app: AppDescriptor,
    val bounds: WindowBounds,
    val restoreBounds: WindowBounds = bounds,
    val visualState: WindowVisualState = WindowVisualState.NORMAL,
    val zIndex: Int,
    val title: String = app.label,
) {
    val isMinimized get() = visualState == WindowVisualState.MINIMIZED
    val isMaximized get() = visualState == WindowVisualState.MAXIMIZED
}

/** Snapshot of device resource usage, polled for the Task Manager widget. */
@Immutable
data class SystemStats(
    val cpuPercent: Float,          // 0..100 aggregate across cores
    val perCorePercent: List<Float>,
    val ramUsedBytes: Long,
    val ramTotalBytes: Long,
    val batteryPercent: Int,        // 0..100
    val isCharging: Boolean,
    val networkType: NetworkType,
    val uptimeMillis: Long,
) {
    val ramUsedPercent: Float
        get() = if (ramTotalBytes == 0L) 0f
        else (ramUsedBytes.toDouble() / ramTotalBytes * 100.0).toFloat()

    companion object {
        val EMPTY = SystemStats(
            cpuPercent = 0f,
            perCorePercent = emptyList(),
            ramUsedBytes = 0L,
            ramTotalBytes = 0L,
            batteryPercent = 0,
            isCharging = false,
            networkType = NetworkType.NONE,
            uptimeMillis = 0L,
        )
    }
}

enum class NetworkType { NONE, WIFI, CELLULAR, ETHERNET }

/** A process running inside the emulation engine. */
@Immutable
data class EngineProcess(
    val pid: Int,
    val label: String,
    val executablePath: String,
    val startedAtMillis: Long,
    val status: ProcessStatus,
)

enum class ProcessStatus { STARTING, RUNNING, EXITED, FAILED }

/**
 * Streamed events emitted while the engine launches and runs a binary.
 * Collected as a cold [kotlinx.coroutines.flow.Flow] so the UI can render a
 * live console without the engine knowing anything about the UI.
 */
sealed interface EngineEvent {
    data class Starting(val executablePath: String) : EngineEvent
    data class Started(val process: EngineProcess) : EngineEvent
    data class Output(val pid: Int, val line: String, val isError: Boolean) : EngineEvent
    data class Exited(val pid: Int, val exitCode: Int) : EngineEvent
    data class Failed(val reason: String, val cause: Throwable? = null) : EngineEvent
}

/** Which native backend the engine should drive. */
enum class RuntimeKind {
    /** PRoot user-space chroot — no root, runs ARM/Linux Windows layers. */
    PROOT,
    /** Box64 + Wine — translates x86_64 Windows calls on ARM. */
    BOX64_WINE,
    /** Full-system QEMU — heaviest, boots an actual guest image. */
    QEMU_SYSTEM,
}

/** Describes a runtime the engine discovered on disk and can launch. */
@Immutable
data class RuntimeProfile(
    val kind: RuntimeKind,
    val displayName: String,
    val rootfsPath: String,
    val launcherBinary: String,
    val isAvailable: Boolean,
    val version: String? = null,
)

/** A node in the Virtual File System (used by File Explorer + VFS manager). */
@Immutable
data class VfsNode(
    val windowsPath: String,     // e.g. C:\Users\nexus\Documents
    val hostPath: String,        // absolute POSIX path on the Android device
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)
