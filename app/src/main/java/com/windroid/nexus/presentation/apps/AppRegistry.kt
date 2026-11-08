package com.windroid.nexus.presentation.apps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windroid.nexus.domain.engine.ExecutionEngine
import com.windroid.nexus.domain.model.AppId
import com.windroid.nexus.domain.model.EngineProcess
import com.windroid.nexus.domain.model.SystemStats
import com.windroid.nexus.domain.usecase.ObserveSystemStatsUseCase
import com.windroid.nexus.domain.usecase.TerminateProcessUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Central map from an [AppId] to the composable that renders its window
 * content. Window chrome (title bar, resize) is applied by WindowFrame; this
 * registry only supplies the interior. A binary launched via the engine (an
 * `exe:` id) opens in an embedded Terminal/console view.
 */
object AppRegistry {
    @Composable
    fun Content(appId: AppId) {
        when (appId) {
            AppId.FILE_EXPLORER -> FileExplorerApp()
            AppId.SETTINGS -> SettingsApp()
            AppId.TERMINAL -> TerminalApp()
            AppId.BROWSER -> BrowserApp()
            AppId.TASK_MANAGER -> TaskManagerHost()
            else -> TerminalApp() // exe:* binaries stream into a console window
        }
    }
}

/** Self-contained Task Manager data holder so the window needs no external wiring. */
@HiltViewModel
class TaskManagerViewModel @Inject constructor(
    observeSystemStats: ObserveSystemStatsUseCase,
    engine: ExecutionEngine,
    private val terminateProcess: TerminateProcessUseCase,
) : ViewModel() {

    val stats: StateFlow<SystemStats> = observeSystemStats(1_000L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SystemStats.EMPTY)

    val processes: StateFlow<List<EngineProcess>> = engine.processes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun endProcess(pid: Int) = viewModelScope.launch { terminateProcess(pid) }
}

@Composable
private fun TaskManagerHost(viewModel: TaskManagerViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val processes by viewModel.processes.collectAsStateWithLifecycle()
    TaskManagerApp(
        stats = stats,
        engineProcesses = processes,
        onEndProcess = viewModel::endProcess,
    )
}
