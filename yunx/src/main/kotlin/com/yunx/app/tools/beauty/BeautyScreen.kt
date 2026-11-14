package com.yunx.app.tools.beauty

import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.yunx.app.shell.NieveGlassChip
import com.yunx.app.tools.ToolActionButton
import com.yunx.app.tools.ToolsPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class BeautyCategory(val name: String, val api: String)

// 只保留非露骨、非未成年指向的分类，避免把擦边/软色情内容带进应用。
val beautyCategories = listOf(
    BeautyCategory("汉服", "https://api.yujn.cn/api/hanfu.php?type=video"),
    BeautyCategory("漫展", "https://api.yujn.cn/api/manzhan.php?type=video"),
    BeautyCategory("穿搭", "https://api.yujn.cn/api/chuanda.php?type=video"),
    BeautyCategory("变装", "https://api.yujn.cn/api/bianzhuang.php?type=video"),
    BeautyCategory("COS", "https://api.yujn.cn/api/COS.php?type=video"),
    BeautyCategory("甜美", "https://api.yujn.cn/api/tianmei.php?type=video"),
    BeautyCategory("青春", "https://api.yujn.cn/api/qingchun.php?type=video"),
)

class BeautyException(message: String) : Exception(message)

class BeautyApi {
    private val client = OkHttpClient.Builder().followRedirects(true).build()

    suspend fun randomVideo(apiUrl: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw BeautyException("视频接口返回 ${response.code}")
            val finalUrl = response.request.url.toString()
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.contains("video") || finalUrl.contains(".mp4")) {
                return@withContext finalUrl
            }
            val body = response.body?.string().orEmpty().trim()
            if (body.startsWith("http://") || body.startsWith("https://")) return@withContext body
            val jsonUrl = runCatching { findUrlInJson(JSONObject(body)) }.getOrNull()
            if (!jsonUrl.isNullOrBlank()) return@withContext normalizeUrl(jsonUrl)
            runCatching { findUrlInJson(JSONArray(body)) }.getOrNull()?.let { return@withContext normalizeUrl(it) }
            Regex("""https?://[^"'\s<>]+\.mp4[^"'\s<>]*""").find(body)?.value?.let { return@withContext it }
            if (finalUrl.startsWith("http") && !finalUrl.contains("api.yujn.cn/api/")) return@withContext finalUrl
            throw BeautyException("该分类暂时没有可用视频，换一个分类试试")
        }
    }

    private fun findUrlInJson(json: JSONObject): String? {
        for (key in json.keys()) {
            val value = json.opt(key)
            when (value) {
                is String -> if (value.startsWith("http") && (value.contains(".mp4") || value.contains("video"))) return value
                is JSONObject -> findUrlInJson(value)?.let { return it }
                is JSONArray -> findUrlInJson(value)?.let { return it }
            }
        }
        return null
    }

    private fun findUrlInJson(json: JSONArray): String? {
        for (i in 0 until json.length()) {
            when (val value = json.opt(i)) {
                is String -> if (value.startsWith("http") && (value.contains(".mp4") || value.contains("video"))) return value
                is JSONObject -> findUrlInJson(value)?.let { return it }
                is JSONArray -> findUrlInJson(value)?.let { return it }
            }
        }
        return null
    }

    private fun normalizeUrl(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http") -> url
            else -> "https://$url"
        }
    }
}

@UnstableApi
@Composable
fun BeautyScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val api = remember { BeautyApi() }
    val scope = rememberCoroutineScope()
    var categoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var endedTick by remember { mutableIntStateOf(0) }
    var videoUrl by remember { mutableStateOf("") }
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) endedTick++
            }

            override fun onPlayerError(playbackException: androidx.media3.common.PlaybackException) {
                error = playbackException.message ?: "播放失败"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    fun load() {
        loading = true
        error = ""
        scope.launch {
            runCatching { api.randomVideo(beautyCategories[categoryIndex].api) }
                .onSuccess {
                    videoUrl = it
                    exoPlayer.setMediaItem(MediaItem.fromUri(it))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    loading = false
                }
                .onFailure {
                    error = it.message ?: "加载失败"
                    loading = false
                }
        }
    }

    LaunchedEffect(categoryIndex, endedTick) {
        load()
    }

    ToolsPage(title = "随机美女视频", onBack = onExit) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(beautyCategories) { category ->
                    val index = beautyCategories.indexOf(category)
                    NieveGlassChip(category.name, index == categoryIndex, onClick = {
                        if (index != categoryIndex) {
                            categoryIndex = index
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                        }
                    })
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (loading) {
                    CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.dp)
                }
                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Text(
                    beautyCategories[categoryIndex].name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolActionButton(
                        "换一个",
                        enabled = !loading,
                        primary = true,
                        onClick = {
                            if (videoUrl.isNotBlank()) {
                                exoPlayer.stop()
                                exoPlayer.clearMediaItems()
                            }
                            load()
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "每次请求随机返回一条视频，播放结束后自动换下一条；全部来自公开视频接口，仅保留非露骨分类。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 96.dp),
            )
        }
    }
}
