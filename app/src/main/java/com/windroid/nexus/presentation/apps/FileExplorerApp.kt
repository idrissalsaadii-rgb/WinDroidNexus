package com.windroid.nexus.presentation.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windroid.nexus.core.util.Resource
import com.windroid.nexus.domain.model.VfsNode
import com.windroid.nexus.domain.repository.VfsRepository
import com.windroid.nexus.presentation.theme.NexusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State for the File Explorer window. */
data class ExplorerState(
    val currentPath: String = "C:\\",
    val entries: Resource<List<VfsNode>> = Resource.Loading,
) {
    val canGoUp: Boolean get() = currentPath.trimEnd('\\').contains('\\')
}

@HiltViewModel
class FileExplorerViewModel @Inject constructor(
    private val vfs: VfsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExplorerState())
    val state: StateFlow<ExplorerState> = _state.asStateFlow()

    init { navigateTo("C:\\") }

    fun navigateTo(path: String) {
        _state.update { it.copy(currentPath = path, entries = Resource.Loading) }
        viewModelScope.launch {
            val result = runCatching { vfs.list(path) }
            _state.update {
                it.copy(
                    entries = result.fold(
                        onSuccess = { nodes -> Resource.Success(nodes) },
                        onFailure = { e -> Resource.Error(e.message ?: "Unable to read $path") },
                    )
                )
            }
        }
    }

    fun onEntryClick(node: VfsNode) {
        if (node.isDirectory) navigateTo(node.windowsPath)
    }

    fun goUp() {
        val parent = _state.value.currentPath.trimEnd('\\').substringBeforeLast('\\', "C:")
        navigateTo(if (parent.endsWith(":")) "$parent\\" else parent)
    }
}

@Composable
fun FileExplorerApp(viewModel: FileExplorerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // Address bar.
        Row(
            Modifier
                .fillMaxWidth()
                .background(NexusColors.WindowChrome)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Up",
                tint = if (state.canGoUp) NexusColors.OnDark else NexusColors.OnDarkMuted,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(enabled = state.canGoUp) { viewModel.goUp() },
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(state.currentPath, color = NexusColors.OnDark, style = MaterialTheme.typography.bodySmall)
            }
        }

        when (val entries = state.entries) {
            is Resource.Loading -> CenteredNote("Loading…")
            is Resource.Error -> CenteredNote(entries.message, isError = true)
            is Resource.Success -> {
                if (entries.data.isEmpty()) {
                    CenteredNote("This folder is empty.")
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                        items(entries.data, key = { it.windowsPath }) { node ->
                            EntryRow(node) { viewModel.onEntryClick(node) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(node: VfsNode, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (node.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
            contentDescription = null,
            tint = if (node.isDirectory) NexusColors.Accent else NexusColors.OnDarkMuted,
            modifier = Modifier.size(20.dp),
        )
        Text(node.name, color = NexusColors.OnDark, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (!node.isDirectory) {
            Text(formatBytesPublic(node.sizeBytes), color = NexusColors.OnDarkMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CenteredNote(text: String, isError: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = if (isError) NexusColors.CloseHover else NexusColors.OnDarkMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Shared byte formatter (public so other app composables can reuse it). */
fun formatBytesPublic(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble(); var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return "%.1f %s".format(value, units[unit])
}
