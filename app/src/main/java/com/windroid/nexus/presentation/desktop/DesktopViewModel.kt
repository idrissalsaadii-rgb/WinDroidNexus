package com.windroid.nexus.presentation.desktop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windroid.nexus.domain.model.AppDescriptor
import com.windroid.nexus.domain.model.AppId
import com.windroid.nexus.domain.model.SystemStats
import com.windroid.nexus.domain.model.WindowBounds
import com.windroid.nexus.domain.model.WindowInstance
import com.windroid.nexus.domain.model.WindowVisualState
import com.windroid.nexus.domain.repository.AppRepository
import com.windroid.nexus.domain.usecase.ObserveSystemStatsUseCase
import com.windroid.nexus.domain.usecase.PrepareEngineUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable snapshot of the whole desktop, rendered by [DesktopScreen].
 * Keeping everything in one state object makes recomposition predictable and
 * the window stack easy to reason about.
 */
data class DesktopUiState(
    val windows: List<WindowInstance> = emptyList(),
    val isStartMenuOpen: Boolean = false,
    val cursorMode: Boolean = false,
    val focusedWindowId: Long? = null,
) {
    val visibleWindows: List<WindowInstance>
        get() = windows.filterNot { it.visualState == WindowVisualState.MINIMIZED }
            .sortedBy { it.zIndex }

    /** Taskbar shows every open window regardless of minimized state. */
    val taskbarWindows: List<WindowInstance> get() = windows.sortedBy { it.windowId }
}

/**
 * Owns window-management logic (open/close/focus/move/resize/min/max), start
 * menu visibility, input mode, and the live system-stats stream. Pure state
 * transitions here keep the composables dumb and testable.
 */
@HiltViewModel
class DesktopViewModel @Inject constructor(
    private val appRepository: AppRepository,
    observeSystemStats: ObserveSystemStatsUseCase,
    private val prepareEngine: PrepareEngineUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DesktopUiState())
    val uiState: StateFlow<DesktopUiState> = _uiState.asStateFlow()

    val apps: StateFlow<List<AppDescriptor>> = appRepository.observeApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 1 Hz resource stream consumed by the Task Manager window. */
    val systemStats: StateFlow<SystemStats> = observeSystemStats(1_000L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SystemStats.EMPTY)

    private var nextWindowId = 1L
    private var topZIndex = 0

    init {
        // Warm up the engine (scaffold rootfs, discover runtimes) off the UI.
        viewModelScope.launch { prepareEngine() }
    }

    // ---------------------------------------------------------------------
    // Window lifecycle
    // ---------------------------------------------------------------------

    fun openApp(app: AppDescriptor) {
        _uiState.update { state ->
            // If already open, just focus/restore it instead of duplicating.
            val existing = state.windows.firstOrNull { it.app.id == app.id }
            if (existing != null) {
                return@update focusInternal(state, existing.windowId)
                    .let { restoreIfMinimized(it, existing.windowId) }
            }
            val id = nextWindowId++
            val window = WindowInstance(
                windowId = id,
                app = app,
                bounds = WindowBounds.cascade(state.windows.size),
                zIndex = ++topZIndex,
            )
            state.copy(
                windows = state.windows + window,
                focusedWindowId = id,
                isStartMenuOpen = false,
            )
        }
    }

    fun openById(appId: AppId) {
        apps.value.firstOrNull { it.id == appId }?.let(::openApp)
    }

    fun closeWindow(windowId: Long) = _uiState.update { state ->
        state.copy(
            windows = state.windows.filterNot { it.windowId == windowId },
            focusedWindowId = state.focusedWindowId.takeIf { it != windowId },
        )
    }

    fun focusWindow(windowId: Long) = _uiState.update { focusInternal(it, windowId) }

    fun toggleMinimize(windowId: Long) = _uiState.update { state ->
        state.copy(windows = state.windows.map {
            if (it.windowId != windowId) it
            else it.copy(
                visualState = if (it.isMinimized) WindowVisualState.NORMAL
                else WindowVisualState.MINIMIZED
            )
        })
    }

    fun toggleMaximize(windowId: Long) = _uiState.update { state ->
        state.copy(windows = state.windows.map { w ->
            if (w.windowId != windowId) w
            else if (w.isMaximized) {
                w.copy(visualState = WindowVisualState.NORMAL, bounds = w.restoreBounds)
            } else {
                w.copy(visualState = WindowVisualState.MAXIMIZED, restoreBounds = w.bounds)
            }
        })
    }

    /** Taskbar click: focus if unfocused, else minimize (classic toggle). */
    fun onTaskbarItemClick(windowId: Long) = _uiState.update { state ->
        val w = state.windows.firstOrNull { it.windowId == windowId } ?: return@update state
        when {
            w.isMinimized -> restoreIfMinimized(focusInternal(state, windowId), windowId)
            state.focusedWindowId == windowId ->
                state.copy(windows = state.windows.map {
                    if (it.windowId == windowId) it.copy(visualState = WindowVisualState.MINIMIZED) else it
                })
            else -> focusInternal(state, windowId)
        }
    }

    // ---------------------------------------------------------------------
    // Drag / resize (dp deltas forwarded from gesture detectors)
    // ---------------------------------------------------------------------

    fun moveWindow(windowId: Long, dxDp: Float, dyDp: Float) = _uiState.update { state ->
        state.copy(windows = state.windows.map {
            if (it.windowId == windowId && !it.isMaximized)
                it.copy(bounds = it.bounds.movedBy(dxDp, dyDp)) else it
        })
    }

    fun resizeWindow(windowId: Long, dwDp: Float, dhDp: Float) = _uiState.update { state ->
        state.copy(windows = state.windows.map {
            if (it.windowId == windowId && !it.isMaximized)
                it.copy(bounds = it.bounds.resizedBy(dwDp, dhDp, MIN_W, MIN_H)) else it
        })
    }

    // ---------------------------------------------------------------------
    // Shell chrome
    // ---------------------------------------------------------------------

    fun toggleStartMenu() = _uiState.update { it.copy(isStartMenuOpen = !it.isStartMenuOpen) }
    fun dismissStartMenu() = _uiState.update { it.copy(isStartMenuOpen = false) }
    fun toggleCursorMode() = _uiState.update { it.copy(cursorMode = !it.cursorMode) }

    fun registerAndOpenBinary(path: String, label: String) = viewModelScope.launch {
        val descriptor = appRepository.registerBinary(path, label)
        openApp(descriptor)
    }

    // ---------------------------------------------------------------------
    // Internal state helpers (pure)
    // ---------------------------------------------------------------------

    private fun focusInternal(state: DesktopUiState, windowId: Long): DesktopUiState {
        if (state.windows.none { it.windowId == windowId }) return state
        val newTop = ++topZIndex
        return state.copy(
            focusedWindowId = windowId,
            windows = state.windows.map {
                if (it.windowId == windowId) it.copy(zIndex = newTop) else it
            },
        )
    }

    private fun restoreIfMinimized(state: DesktopUiState, windowId: Long): DesktopUiState =
        state.copy(windows = state.windows.map {
            if (it.windowId == windowId && it.isMinimized)
                it.copy(visualState = WindowVisualState.NORMAL) else it
        })

    private companion object {
        const val MIN_W = 320f
        const val MIN_H = 220f
    }
}
