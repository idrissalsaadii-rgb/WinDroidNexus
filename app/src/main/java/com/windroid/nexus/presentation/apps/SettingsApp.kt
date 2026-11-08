package com.windroid.nexus.presentation.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windroid.nexus.domain.engine.ExecutionEngine
import com.windroid.nexus.domain.model.RuntimeProfile
import com.windroid.nexus.presentation.theme.NexusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val engine: ExecutionEngine,
) : ViewModel() {

    val runtimes: StateFlow<List<RuntimeProfile>> = engine.runtimes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() = viewModelScope.launch { engine.prepareEnvironment() }

    init { refresh() }
}

@Composable
fun SettingsApp(viewModel: SettingsViewModel = hiltViewModel()) {
    val runtimes by viewModel.runtimes.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, color = NexusColors.OnDark)

        Section("Execution engine") {
            Text(
                "WinDroid Nexus runs Windows/Linux binaries through a pluggable native " +
                    "runtime. Install one of the backends below into the engine rootfs to " +
                    "enable .exe execution.",
                color = NexusColors.OnDarkMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (runtimes.isEmpty()) {
                Text(
                    "No runtimes detected. Provision a backend, then tap Re-scan.",
                    color = NexusColors.Warning,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                runtimes.forEach { RuntimeRow(it) }
            }
            Button(onClick = { viewModel.refresh() }) { Text("Re-scan runtimes") }
        }

        Section("About") {
            InfoLine("Product", "WinDroid Nexus")
            InfoLine("Version", "1.0.0")
            InfoLine("Architecture", "Clean Architecture · MVVM · Jetpack Compose")
            InfoLine("Shell", "Original desktop environment (no proprietary assets bundled)")
        }
    }
}

@Composable
private fun RuntimeRow(profile: RuntimeProfile) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (profile.isAvailable) NexusColors.Success else NexusColors.OnDarkMuted),
        )
        Column(Modifier.weight(1f)) {
            Text(profile.displayName, color = NexusColors.OnDark, style = MaterialTheme.typography.titleMedium)
            Text(
                profile.version ?: "not provisioned",
                color = NexusColors.OnDarkMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            if (profile.isAvailable) "Ready" else "Missing",
            color = if (profile.isAvailable) NexusColors.Success else NexusColors.Warning,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = NexusColors.OnDark)
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = NexusColors.OnDarkMuted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = NexusColors.OnDark, style = MaterialTheme.typography.bodyMedium)
    }
}
