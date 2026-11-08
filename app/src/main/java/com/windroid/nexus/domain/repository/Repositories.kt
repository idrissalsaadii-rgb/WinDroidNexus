package com.windroid.nexus.domain.repository

import com.windroid.nexus.domain.model.AppDescriptor
import com.windroid.nexus.domain.model.SystemStats
import com.windroid.nexus.domain.model.VfsNode
import kotlinx.coroutines.flow.Flow

/** Supplies the catalogue of launchable apps (system + user binaries). */
interface AppRepository {
    fun observeApps(): Flow<List<AppDescriptor>>
    suspend fun registerBinary(path: String, label: String): AppDescriptor
    suspend fun setPinnedToStart(id: String, pinned: Boolean)
}

/** Streams live device resource statistics for the Task Manager. */
interface SystemRepository {
    /** @param intervalMillis polling cadence; the flow emits on each tick. */
    fun observeStats(intervalMillis: Long = 1_000L): Flow<SystemStats>
}

/** Bridges Android storage to the emulated Windows drive tree. */
interface VfsRepository {
    /** Lists children of a Windows-style directory (defaults to C:\). */
    suspend fun list(windowsPath: String = "C:\\"): List<VfsNode>
    suspend fun createDirectory(windowsPath: String): VfsNode
    suspend fun delete(windowsPath: String): Boolean
    suspend fun readText(windowsPath: String): String
    suspend fun writeText(windowsPath: String, content: String): VfsNode
    /** Maps an external Android document tree in as a new drive letter. */
    suspend fun mountExternal(driveLetter: Char, hostTreePath: String)
}
