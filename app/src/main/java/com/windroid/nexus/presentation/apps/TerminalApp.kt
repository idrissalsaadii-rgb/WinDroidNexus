package com.windroid.nexus.presentation.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windroid.nexus.domain.model.EngineEvent
import com.windroid.nexus.domain.usecase.LaunchBinaryUseCase
import com.windroid.nexus.presentation.theme.NexusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One line in the terminal scrollback. */
data class ConsoleLine(val text: String, val kind: LineKind)
enum class LineKind { PROMPT, STDOUT, STDERR, SYSTEM }

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val launchBinary: LaunchBinaryUseCase,
) : ViewModel() {

    private val _lines = MutableStateFlow(
        listOf(
            ConsoleLine("WinDroid Nexus Terminal — type a command and press Enter.", LineKind.SYSTEM),
            ConsoleLine("Example: C:\\Windows\\System32\\cmd.exe /c ver", LineKind.SYSTEM),
        )
    )
    val lines: StateFlow<List<ConsoleLine>> = _lines.asStateFlow()

    fun submit(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        append(ConsoleLine("C:\\> $trimmed", LineKind.PROMPT))

        val parts = tokenize(trimmed)
        val exe = parts.first()
        val args = parts.drop(1)

        viewModelScope.launch {
            launchBinary(exe, args)
                .onEach { event -> append(event.toConsoleLine()) }
                .catch { e -> append(ConsoleLine("error: ${e.message}", LineKind.STDERR)) }
                .collect {}
        }
    }

    private fun append(line: ConsoleLine) = _lines.update { it + line }

    /** Splits a command line, honoring simple double-quoted segments. */
    private fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (c in input) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c.isWhitespace() && !inQuotes -> {
                    if (sb.isNotEmpty()) { tokens.add(sb.toString()); sb.clear() }
                }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) tokens.add(sb.toString())
        return tokens.ifEmpty { listOf(input) }
    }

    private fun EngineEvent.toConsoleLine(): ConsoleLine = when (this) {
        is EngineEvent.Starting -> ConsoleLine("[starting] $executablePath", LineKind.SYSTEM)
        is EngineEvent.Started -> ConsoleLine("[pid ${process.pid}] running", LineKind.SYSTEM)
        is EngineEvent.Output -> ConsoleLine(line, if (isError) LineKind.STDERR else LineKind.STDOUT)
        is EngineEvent.Exited -> ConsoleLine("[exit $exitCode]", LineKind.SYSTEM)
        is EngineEvent.Failed -> ConsoleLine("[failed] $reason", LineKind.STDERR)
    }
}

@Composable
fun TerminalApp(viewModel: TerminalViewModel = hiltViewModel()) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to the newest line.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C)),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            itemsIndexed(lines) { _, line ->
                Text(
                    text = line.text,
                    color = when (line.kind) {
                        LineKind.PROMPT -> NexusColors.Accent
                        LineKind.STDERR -> NexusColors.CloseHover
                        LineKind.SYSTEM -> NexusColors.OnDarkMuted
                        LineKind.STDOUT -> Color(0xFFCCCCCC)
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            }
        }
        TextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = NexusColors.OnDark, fontSize = 13.sp),
            singleLine = true,
            placeholder = { Text("C:\\>", fontFamily = FontFamily.Monospace, color = NexusColors.OnDarkMuted) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                viewModel.submit(input); input = ""
            }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF161616),
                unfocusedContainerColor = Color(0xFF161616),
                focusedIndicatorColor = NexusColors.Accent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = NexusColors.Accent,
            ),
        )
    }
}
