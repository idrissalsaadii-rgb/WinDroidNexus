package com.windroid.nexus.data.repository

import com.windroid.nexus.domain.model.AppDescriptor
import com.windroid.nexus.domain.model.AppId
import com.windroid.nexus.domain.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory catalogue of launchable apps, seeded with the built-in shell apps
 * and extended at runtime when the user registers an external binary. Exposed
 * as a [Flow] so the desktop/Start menu recompose when the catalogue changes.
 *
 * (Persistence to DataStore is intentionally out of scope of this class; it is
 * wired at the DI layer and can be layered on without changing callers.)
 */
@Singleton
class AppRepositoryImpl @Inject constructor() : AppRepository {

    private val _apps = MutableStateFlow(DEFAULT_APPS)
    override fun observeApps(): Flow<List<AppDescriptor>> = _apps.asStateFlow()

    override suspend fun registerBinary(path: String, label: String): AppDescriptor {
        val descriptor = AppDescriptor(
            id = AppId.forBinary(path),
            label = label,
            iconKey = ICON_EXE,
            executablePath = path,
            pinnedToStart = true,
        )
        _apps.update { current ->
            // De-duplicate by id so re-registering updates in place.
            (current.filterNot { it.id == descriptor.id } + descriptor)
                .sortedBy { it.label.lowercase() }
        }
        return descriptor
    }

    override suspend fun setPinnedToStart(id: String, pinned: Boolean) {
        _apps.update { current ->
            current.map { if (it.id.value == id) it.copy(pinnedToStart = pinned) else it }
        }
    }

    companion object {
        const val ICON_EXE = "exe"

        private val DEFAULT_APPS = listOf(
            AppDescriptor(AppId.FILE_EXPLORER, "File Explorer", "folder", pinnedToStart = true, pinnedToTaskbar = true),
            AppDescriptor(AppId.BROWSER, "Browser", "globe", pinnedToStart = true, pinnedToTaskbar = true),
            AppDescriptor(AppId.TERMINAL, "Terminal", "terminal", pinnedToStart = true, pinnedToTaskbar = true),
            AppDescriptor(AppId.SETTINGS, "Settings", "gear", pinnedToStart = true),
            AppDescriptor(AppId.TASK_MANAGER, "Task Manager", "chart", pinnedToStart = true),
        )
    }
}
