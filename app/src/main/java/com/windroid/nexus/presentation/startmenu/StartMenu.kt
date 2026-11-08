package com.windroid.nexus.presentation.startmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.windroid.nexus.domain.model.AppDescriptor
import com.windroid.nexus.presentation.icons.WinIcons
import com.windroid.nexus.presentation.theme.NexusColors

/**
 * The Start menu popup: a rounded, elevated acrylic panel anchored above the
 * taskbar. Contains a search field that filters the app list and a grid of
 * pinned apps. Tapping a tile launches the app; tapping outside dismisses.
 */
@Composable
fun StartMenu(
    apps: List<AppDescriptor>,
    onLaunch: (AppDescriptor) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps.filter { it.pinnedToStart }
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .width(560.dp)
            .heightIn(max = 520.dp)
            .shadow(24.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(NexusColors.StartSurface)
            // Swallow taps so clicks inside the menu don't dismiss it.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = NexusColors.OnDarkMuted)
            },
            placeholder = { Text("Type here to search", color = NexusColors.OnDarkMuted) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                focusedTextColor = NexusColors.OnDark,
                unfocusedTextColor = NexusColors.OnDark,
                focusedIndicatorColor = NexusColors.Accent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        Text(
            text = if (query.isBlank()) "Pinned" else "Results",
            style = MaterialTheme.typography.titleMedium,
            color = NexusColors.OnDark,
        )

        if (filtered.isEmpty()) {
            Text(
                "No apps match \"$query\".",
                style = MaterialTheme.typography.bodyMedium,
                color = NexusColors.OnDarkMuted,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id.value }) { app ->
                    StartTile(app = app, onClick = { onLaunch(app) })
                }
            }
        }
    }
}

@Composable
private fun StartTile(app: AppDescriptor, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(app.id.value) { detectTapGestures { onClick() } }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NexusColors.Accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = WinIcons.forKey(app.iconKey),
                contentDescription = app.label,
                tint = NexusColors.OnDark,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = NexusColors.OnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
