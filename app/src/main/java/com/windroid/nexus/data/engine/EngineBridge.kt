package com.windroid.nexus.data.engine

import android.content.Context
import com.windroid.nexus.core.util.DispatcherProvider
import com.windroid.nexus.data.engine.runtime.RuntimeFactory
import com.windroid.nexus.domain.engine.ExecutionEngine
import com.windroid.nexus.domain.engine.NativeRuntime
import com.windroid.nexus.domain.model.EngineEvent
import com.windroid.nexus.domain.model.EngineProcess
import com.windroid.nexus.domain.model.ProcessStatus
import com.windroid.nexus.domain.model.RuntimeProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EngineBridge — the Process Manager / emulation abstraction layer.
 *
 * Responsibilities:
 *  1. Lay out and verify the rootfs skeleton (the simulated Linux/Windows FS).
 *  2. Discover which [NativeRuntime] backends are provisioned and pick one.
 *  3. Launch guest binaries via [ProcessBuilder], streaming stdout/stderr and
 *     lifecycle as a cold [Flow] of [EngineEvent].
 *  4. Track live processes and expose graceful termination + shutdown.
 *
 * It owns no UI concerns and depends only on domain contracts, so it can be
 * unit-tested by substituting a fake [RuntimeFactory].
 */
@Singleton
class EngineBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeFactory: RuntimeFactory,
    private val dispatchers: DispatcherProvider,
) : ExecutionEngine {

    /** Long-lived scope for the stdout/stderr pump of each child process. */
    private val engineScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    /** Root of the emulated filesystem, private to the app sandbox. */
    private val rootfs: File by lazy {
        File(context.filesDir, ROOTFS_DIR).apply { mkdirs() }
    }

    /** Monotonic ids for UI windows that host a console; distinct from OS pid. */
    private val pidCounter = AtomicInteger(1000)

    /** pid -> live OS Process, for stdin/terminate. */
    private val liveProcesses = ConcurrentHashMap<Int, Process>()

    private val _runtimes = MutableStateFlow<List<RuntimeProfile>>(emptyList())
    override val runtimes: Flow<List<RuntimeProfile>> = _runtimes.asStateFlow()

    private val _processes = MutableStateFlow<List<EngineProcess>>(emptyList())
    override val processes: Flow<List<EngineProcess>> = _processes.asStateFlow()

    // ---------------------------------------------------------------------
    // Environment preparation
    // ---------------------------------------------------------------------

    override suspend fun prepareEnvironment(): RuntimeProfile? = withContext(dispatchers.io) {
        runCatching { scaffoldRootfs() }
            .onFailure { return@withContext null }

        // Rebuild the runtime catalogue from disk every time we prepare, so a
        // freshly provisioned backend becomes selectable without a restart.
        val discovered = runtimeFactory.discover(rootfs)
        _runtimes.value = discovered.map { it.profile }

        // Prefer the lightest backend that is actually provisioned.
        val chosen = discovered.firstOrNull { it.isProvisioned() }
        chosen?.profile
    }

    /**
     * Creates the canonical directory skeleton if missing. This is the "C:\"
     * tree plus the Linux dirs a user-space runtime expects. Idempotent.
     */
    private fun scaffoldRootfs() {
        val skeleton = listOf(
            "drive_c/Windows/System32",
            "drive_c/Program Files",
            "drive_c/Users/nexus/Desktop",
            "drive_c/Users/nexus/Documents",
            "drive_c/Users/nexus/Downloads",
            "usr/bin", "bin", "lib", "tmp", "root",
            "opt/wine/bin", "images",
        )
        skeleton.forEach { File(rootfs, it).mkdirs() }

        // A readable marker so File Explorer shows something on first run.
        File(rootfs, "drive_c/Users/nexus/Desktop/Welcome.txt").let {
            if (!it.exists()) it.writeText(
                "Welcome to WinDroid Nexus.\r\n" +
                    "Provision a runtime in Settings to run .exe files.\r\n"
            )
        }
    }

    // ---------------------------------------------------------------------
    // Launch
    // ---------------------------------------------------------------------

    override fun launch(
        executablePath: String,
        args: List<String>,
        env: Map<String, String>,
    ): Flow<EngineEvent> = callbackFlow {
        trySend(EngineEvent.Starting(executablePath))

        // Resolve a usable backend up front; fail fast with a clear message.
        val runtime = runtimeFactory.discover(rootfs).firstOrNull { it.isProvisioned() }
        if (runtime == null) {
            trySend(
                EngineEvent.Failed(
                    "No compatible runtime is provisioned. " +
                        "Install PRoot, Box64+Wine, or QEMU in Settings → Engine."
                )
            )
            close(); return@callbackFlow
        }

        val command = runtime.buildCommand(executablePath, args)
        val pid = pidCounter.getAndIncrement()

        val process: Process = try {
            startProcess(runtime, command, env)
        } catch (io: IOException) {
            // Typically ENOEXEC / EACCES when the backend binary is present but
            // not actually runnable on this ABI — surface it, don't crash.
            trySend(EngineEvent.Failed("Failed to start process: ${io.message}", io))
            close(); return@callbackFlow
        }

        liveProcesses[pid] = process
        val record = EngineProcess(
            pid = pid,
            label = File(executablePath).name.ifBlank { executablePath },
            executablePath = executablePath,
            startedAtMillis = System.currentTimeMillis(),
            status = ProcessStatus.RUNNING,
        )
        updateProcessList { it + record }
        trySend(EngineEvent.Started(record))

        // Pump stdout and stderr on the engine scope; forward each line.
        val stdoutJob = engineScope.launch {
            pumpStream(process.inputStream.bufferedReader()) { line ->
                trySend(EngineEvent.Output(pid, line, isError = false))
            }
        }
        val stderrJob = engineScope.launch {
            pumpStream(process.errorStream.bufferedReader()) { line ->
                trySend(EngineEvent.Output(pid, line, isError = true))
            }
        }

        // Wait for exit on a dedicated thread-backed coroutine.
        val waiterJob = engineScope.launch {
            val exitCode = runCatching { process.waitFor() }.getOrDefault(-1)
            stdoutJob.join(); stderrJob.join()
            liveProcesses.remove(pid)
            updateProcessList { list ->
                list.map { if (it.pid == pid) it.copy(status = ProcessStatus.EXITED) else it }
            }
            trySend(EngineEvent.Exited(pid, exitCode))
            close()
        }

        // Cancelling collection (e.g. the console window closed) terminates
        // the child and tears down the pumps — no leaked processes.
        awaitClose {
            waiterJob.cancel(); stdoutJob.cancel(); stderrJob.cancel()
            liveProcesses.remove(pid)?.destroyForciblySafely()
            updateProcessList { list -> list.filterNot { it.pid == pid && it.status != ProcessStatus.EXITED } }
        }
    }.flowOn(dispatchers.io)

    private fun startProcess(
        runtime: NativeRuntime,
        command: List<String>,
        extraEnv: Map<String, String>,
    ): Process {
        val builder = ProcessBuilder(command)
            .directory(rootfs)
            .redirectErrorStream(false)
        builder.environment().apply {
            putAll(runtime.environment())
            putAll(extraEnv)
            // Ensure the runtime's own libs are discoverable.
            merge("LD_LIBRARY_PATH", File(rootfs, "lib").absolutePath) { a, b -> "$b:$a" }
        }
        return builder.start()
    }

    /** Reads a stream line-by-line until EOF, invoking [onLine] per line. */
    private suspend fun pumpStream(reader: BufferedReader, onLine: (String) -> Unit) {
        reader.use { r ->
            try {
                var line = r.readLine()
                while (line != null) {
                    onLine(line)
                    line = r.readLine()
                }
            } catch (_: IOException) {
                // Stream closed because the process exited — expected.
            }
        }
    }

    // ---------------------------------------------------------------------
    // Interaction / teardown
    // ---------------------------------------------------------------------

    override suspend fun sendStdin(pid: Int, line: String): Boolean =
        withContext(dispatchers.io) {
            val process = liveProcesses[pid] ?: return@withContext false
            runCatching {
                process.outputStream.apply {
                    write((line + "\n").toByteArray())
                    flush()
                }
            }.isSuccess
        }

    override suspend fun terminate(pid: Int): Boolean = withContext(dispatchers.io) {
        val process = liveProcesses[pid] ?: return@withContext false
        process.destroy() // SIGTERM — give the guest a chance to clean up.
        val exited = process.waitForSafely(GRACE_SECONDS)
        if (!exited) process.destroyForciblySafely()
        liveProcesses.remove(pid)
        updateProcessList { list ->
            list.map { if (it.pid == pid) it.copy(status = ProcessStatus.EXITED) else it }
        }
        true
    }

    override suspend fun shutdown() = withContext(dispatchers.io) {
        liveProcesses.values.forEach { it.destroyForciblySafely() }
        liveProcesses.clear()
        _processes.value = emptyList()
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private inline fun updateProcessList(transform: (List<EngineProcess>) -> List<EngineProcess>) {
        _processes.update(transform)
    }

    private fun Process.waitForSafely(seconds: Long): Boolean =
        runCatching { waitFor(seconds, TimeUnit.SECONDS) }.getOrDefault(false)

    private fun Process.destroyForciblySafely() {
        runCatching { if (isAlive) destroyForcibly() }
    }

    companion object {
        private const val ROOTFS_DIR = "rootfs"
        private const val GRACE_SECONDS = 5L
    }
}
