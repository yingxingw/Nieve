package com.yunx.app.tools.web

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.tools.ToolActionButton
import com.yunx.app.tools.ToolsMessage
import com.yunx.app.tools.ToolsPage
import com.yunx.app.tools.ToolsSearchField
import com.yunx.app.ui.SnackbarController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun WebSourceScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val client = remember { OkHttpClient.Builder().followRedirects(true).build() }
    var url by rememberSaveable { mutableStateOf("") }
    var html by remember { mutableStateOf("") }
    var finalUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun fetch() {
        var target = url.trim()
        if (target.isBlank()) return
        if (!target.startsWith("http://") && !target.startsWith("https://")) target = "https://$target"
        loading = true
        error = ""
        html = ""
        finalUrl = ""
        status = ""
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(target)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
                        )
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        Triple(body, response.request.url.toString(), response.code)
                    }
                }
            }.onSuccess { (body, resolved, code) ->
                html = body
                finalUrl = resolved
                status = "HTTP $code · ${body.length} 字符"
                loading = false
            }.onFailure {
                error = it.message ?: "抓取失败"
                loading = false
            }
        }
    }

    ToolsPage(title = "网页源码抓取", onBack = onExit) {
        Column(Modifier.fillMaxSize()) {
            ToolsSearchField(
                value = url,
                onValueChange = { url = it },
                placeholder = "输入网址，例如 https://example.com",
                onSearch = { fetch() },
            )
            Spacer(Modifier.height(8.dp))
            Row {
                ToolActionButton(
                    if (loading) "抓取中…" else "抓取源码",
                    enabled = !loading && url.isNotBlank(),
                    primary = true,
                    onClick = { fetch() },
                )
                Spacer(Modifier.width(8.dp))
                ToolActionButton(
                    "复制",
                    enabled = html.isNotBlank(),
                    onClick = {
                        clipboard.setText(AnnotatedString(html))
                        SnackbarController.show("源码已复制")
                    },
                )
                Spacer(Modifier.width(8.dp))
                ToolActionButton(
                    "分享",
                    enabled = html.isNotBlank(),
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, finalUrl.ifBlank { url })
                            putExtra(Intent.EXTRA_TEXT, html)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享源码"))
                    },
                )
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "$status  ${finalUrl.ifBlank { url }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                loading -> ToolsMessage("正在请求网页…")
                error.isNotBlank() -> ToolsMessage(error, MaterialTheme.colorScheme.error)
                html.isBlank() -> ToolsMessage("抓取结果会显示在这里，可复制或分享")
                else -> SelectionContainer {
                    Text(
                        text = html,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f),
                                androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            )
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
                }
            }
        }
    }
}
