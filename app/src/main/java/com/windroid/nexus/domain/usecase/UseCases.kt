package com.windroid.nexus.domain.usecase

import com.windroid.nexus.domain.engine.ExecutionEngine
import com.windroid.nexus.domain.model.EngineEvent
import com.windroid.nexus.domain.model.SystemStats
import com.windroid.nexus.domain.repository.SystemRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Thin use-cases that encode application rules and keep ViewModels free of
 * repository/engine wiring. Each is a single-responsibility, injectable unit.
 */

class ObserveSystemStatsUseCase @Inject constructor(
    private val systemRepository: SystemRepository,
) {
    operator fun invoke(intervalMillis: Long = 1_000L): Flow<SystemStats> =
        systemRepository.observeStats(intervalMillis)
}

class LaunchBinaryUseCase @Inject constructor(
    private val engine: ExecutionEngine,
) {
    /**
     * Streams the lifecycle of a launched executable. Guards against empty
     * paths before touching the engine so the console shows a clean error.
     */
    operator fun invoke(
        executablePath: String,
        args: List<String> = emptyList(),
    ): Flow<EngineEvent> {
        require(executablePath.isNotBlank()) { "Executable path must not be blank" }
        return engine.launch(executablePath.trim(), args)
    }
}

class PrepareEngineUseCase @Inject constructor(
    private val engine: ExecutionEngine,
) {
    suspend operator fun invoke() = engine.prepareEnvironment()
}

class TerminateProcessUseCase @Inject constructor(
    private val engine: ExecutionEngine,
) {
    suspend operator fun invoke(pid: Int): Boolean = engine.terminate(pid)
}
