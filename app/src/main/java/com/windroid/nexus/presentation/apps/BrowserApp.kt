package com.windroid.nexus.presentation.apps

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.windroid.nexus.presentation.theme.NexusColors

/**
 * A minimal in-shell web browser backed by the system [WebView]. Includes an
 * address bar with naive URL/search handling and basic navigation hardening
 * (JS enabled, file access disabled). Rendered via [AndroidView] interop.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserApp(homeUrl: String = "https://www.wikipedia.org") {
    var address by remember { mutableStateOf(homeUrl) }
    var webView: WebView? by remember { mutableStateOf(null) }
    var status by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Color(0xFF17181C))) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(NexusColors.WindowChrome)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search or enter address", color = NexusColors.OnDarkMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    webView?.loadUrl(normalizeUrl(address))
                }),
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.06f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                    focusedTextColor = NexusColors.OnDark,
                    unfocusedTextColor = NexusColors.OnDark,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = NexusColors.Accent,
                ),
            )
        }

        if (status.isNotEmpty()) {
            Text(
                status,
                color = NexusColors.CloseHover,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            request?.url?.toString()?.let { address = it }
                            return false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) {
                                status = "Could not load page."
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            status = ""
                            url?.let { address = it }
                        }
                    }
                    loadUrl(normalizeUrl(homeUrl))
                    webView = this
                }
            },
        )
    }
}

/** Turns bare terms into a search query and adds a scheme when missing. */
private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
        else -> "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
    }
}
