package com.windroid.nexus.domain.engine

import com.windroid.nexus.domain.model.EngineEvent
import com.windroid.nexus.domain.model.EngineProcess
import com.windroid.nexus.domain.model.RuntimeProfile
import kotlinx.coroutines.flow.Flow

/**
 * The abstraction the rest of the app sees. UI and use-cases depend only on
 * this interface — never on PRoot/Wine/QEMU specifics — so backends can be
 * swapped or stubbed (e.g. in unit tests) without touching presentation code.
 */
interface ExecutionEngine {

    /** Runtimes discovered on disk, hot-reloaded when provisioning changes. */
    val runtimes: Flow<List<RuntimeProfile>>

    /** Processes currently alive inside the engine. */
    val processes: Flow<List<EngineProcess>>

    /**
     * One-time (idempotent) preparation: unpacks the rootfs skeleton, fixes
     * permissions, and verifies the selected runtime's launcher is executable.
     * @return the profile that will be used, or null if none is usable.
     */
    suspend fun prepareEnvironment(): RuntimeProfile?

    /**
     * Launches a Windows/Linux binary and streams its lifecycle as events.
     * The returned flow is cold: collection starts the process; cancelling
     * collection requests graceful termination.
     *
     * @param executablePath a Windows-style path (C:\...) or host path.
     * @param args command-line arguments passed through to the guest.
     * @param env extra environment variables merged into the guest env.
     */
    fun launch(
        executablePath: String,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap(),
    ): Flow<EngineEvent>

    /** Writes a line to the stdin of a running interactive process. */
    suspend fun sendStdin(pid: Int, line: String): Boolean

    /** Requests graceful shutdown, escalating to SIGKILL after a timeout. */
    suspend fun terminate(pid: Int): Boolean

    /** Kills every child and releases file handles. Safe to call repeatedly. */
    suspend fun shutdown()
}

/**
 * Strategy contract for a concrete native backend. One implementation exists
 * per [com.windroid.nexus.domain.model.RuntimeKind]. The EngineBridge selects
 * the best available [NativeRuntime] and delegates command construction to it,
 * which is the single seam where backend-specific knowledge lives.
 */
interface NativeRuntime {

    val profile: RuntimeProfile

    /** Cheap check that the launcher binary exists and is executable. */
    fun isProvisioned(): Boolean

    /**
     * Builds the full argv to hand to [ProcessBuilder] for a given guest
     * executable. This is where "run notepad.exe" becomes, for example,
     * `proot -r <rootfs> -b /dev … wine C:/Windows/notepad.exe`.
     */
    fun buildCommand(
        guestExecutable: String,
        guestArgs: List<String>,
    ): List<String>

    /** Environment variables the backend needs (WINEPREFIX, BOX64_PATH, …). */
    fun environment(): Map<String, String>

    /** Translates a Windows path to the host path the backend expects. */
    fun resolveGuestPath(windowsOrHostPath: String): String
}
