package com.yunx.app.tools.music

import android.Manifest
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.yunx.app.shell.NieveGlassChip
import com.yunx.app.shell.NieveGlassDialog
import com.yunx.app.shell.NieveGlassIconButton
import com.yunx.app.tools.ToolActionButton
import com.yunx.app.tools.ToolsCard
import com.yunx.app.tools.ToolsMessage
import com.yunx.app.tools.ToolsPage
import com.yunx.app.tools.ToolsSearchField
import com.yunx.app.tools.ToolsSectionTitle
import com.yunx.app.ui.SnackbarController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun MusicTab(initialSource: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val api = remember { MusicApi() }
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(if (initialSource == "local") "local" else "online") }
    var onlineFilter by rememberSaveable { mutableStateOf("all") }
    var page by rememberSaveable { mutableStateOf("list") }
    var keyword by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf("") }
    val localSongs = remember { mutableStateListOf<Song>() }
    val favorites = remember { mutableStateListOf<Song>().apply { addAll(MusicStore.favorites(context)) } }
    val history = remember { mutableStateListOf<Song>().apply { addAll(MusicStore.history(context)) } }
    var scanning by remember { mutableStateOf(false) }
    var resolvingId by remember { mutableStateOf("") }
    var downloadingId by remember { mutableStateOf("") }
    var playError by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) {
            scanning = true
            scope.launch {
                val songs = withContext(Dispatchers.IO) { MusicLibrary.scan(context) }
                localSongs.clear()
                localSongs.addAll(songs)
                scanning = false
            }
        }
    }

    fun scanLocal() {
        if (!hasPermission) {
            permissionLauncher.launch(permission)
            return
        }
        scanning = true
        scope.launch {
            val songs = withContext(Dispatchers.IO) { MusicLibrary.scan(context) }
            localSongs.clear()
            localSongs.addAll(songs)
            scanning = false
        }
    }

    fun isFavorite(song: Song) = favorites.any { it.id == song.id && it.source == song.source }

    fun toggleFavorite(song: Song) {
        if (isFavorite(song)) favorites.removeAll { it.id == song.id && it.source == song.source }
        else favorites.add(0, song)
        MusicStore.saveFavorites(context, favorites)
    }

    fun resolveAndPlay(list: List<Song>, at: Int) {
        val song = list.getOrNull(at) ?: return
        MusicPlayer.setQueue(list, at)
        MusicStore.pushHistory(context, song)
        if (history.none { it.id == song.id && it.source == song.source }) {
            history.add(0, song)
        } else {
            history.removeAll { it.id == song.id && it.source == song.source }
            history.add(0, song)
        }
        resolvingId = song.id
        playError = ""
        scope.launch {
            runCatching { api.resolvePlayable(song) }
                .onSuccess { (actualSong, url) ->
                    MusicPlayer.playUrl(context, url, actualSong)
                    if (actualSong.source != song.source) {
                        val label = MusicSource.entries.firstOrNull { it.key == song.source }?.label ?: "该平台"
                        SnackbarController.show("$label 版权受限，已切换免费音源播放")
                    }
                    resolvingId = ""
                }
                .onFailure { error ->
                    playError = error.message ?: "播放失败"
                    showError = true
                    resolvingId = ""
                }
        }
    }

    fun downloadSong(song: Song) {
        if (song.source == MusicSource.LOCAL.key) {
            SnackbarController.show("本地歌曲无需重复下载")
            return
        }
        if (downloadingId.isNotBlank()) return
        downloadingId = "${song.source}:${song.id}"
        scope.launch {
            runCatching { api.resolvePlayable(song) }
                .onSuccess { (actual, url) ->
                    val safeName = buildString {
                        if (actual.artist.isNotBlank()) append(actual.artist).append(" - ")
                        append(actual.name)
                    }.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(90)
                        .ifBlank { "未命名歌曲" } + ".mp3"
                    val request = DownloadManager.Request(Uri.parse(url))
                        .setTitle(actual.name)
                        .setDescription("${actual.artist} · Nieve 音乐")
                        .setMimeType("audio/mpeg")
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(false)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Nieve/$safeName")
                    val manager = context.getSystemService(DownloadManager::class.java)
                    manager.enqueue(request)
                    SnackbarController.show("已开始下载：${actual.name}")
                }
                .onFailure { SnackbarController.show("下载失败：${it.message ?: "没有可用音源"}") }
            downloadingId = ""
        }
    }

    LaunchedEffect(MusicPlayer.endedTick) {
        if (MusicPlayer.endedTick > 0 && MusicPlayer.queue.isNotEmpty()) {
            val nextIndex = MusicPlayer.index + 1
            if (nextIndex <= MusicPlayer.queue.lastIndex) {
                resolveAndPlay(MusicPlayer.queue.toList(), nextIndex)
            }
        }
    }

    LaunchedEffect(mode, hasPermission) {
        if (mode == "local" && localSongs.isEmpty() && !scanning) {
            if (hasPermission) scanLocal() else permissionLauncher.launch(permission)
        }
    }

    fun doSearch() {
        if (keyword.isBlank()) return
        searching = true
        searchError = ""
        results = emptyList()
        scope.launch {
            runCatching { api.searchAll(keyword.trim()) }
                .onSuccess { results = it; searching = false }
                .onFailure { searchError = it.message ?: "搜索失败"; searching = false }
        }
    }

    val visibleResults = remember(results, onlineFilter) {
        if (onlineFilter == "all") results else results.filter { it.source == onlineFilter }
    }

    BackHandler(enabled = page == "player") { page = "list" }

    if (page == "player") {
        MusicPlayerScreen(
            api = api,
            onBack = { page = "list" },
            onPlayIndex = { idx -> resolveAndPlay(MusicPlayer.queue.toList(), idx) },
        )
    } else {
        ToolsPage(title = "音乐", onBack = onExit) {
            Column(Modifier.fillMaxSize()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("online" to "在线搜索", "local" to "本地音乐", "favorites" to "收藏", "history" to "最近播放")) { (key, label) ->
                        NieveGlassChip(label, key == mode, onClick = { mode = key })
                    }
                }
                Spacer(Modifier.height(10.dp))

                when (mode) {
                    "local" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "本地音乐",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            NieveGlassIconButton(Icons.Rounded.Refresh, "重新扫描") { scanLocal() }
                        }
                        Spacer(Modifier.height(6.dp))
                        when {
                            scanning -> ToolsMessage("正在扫描本地音乐…")
                            !hasPermission -> ToolsMessage("需要音频读取权限才能扫描本地歌曲")
                            localSongs.isEmpty() -> ToolsMessage("本地没有找到音乐文件")
                            else -> SongList(
                                songs = localSongs,
                                favoriteCheck = ::isFavorite,
                                onFavorite = ::toggleFavorite,
                                onPlay = { index -> resolveAndPlay(localSongs.toList(), index) },
                                onDownload = ::downloadSong,
                                downloadingId = downloadingId,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    "favorites" -> {
                        if (favorites.isEmpty()) ToolsMessage("还没有收藏歌曲")
                        else SongList(
                            songs = favorites,
                            favoriteCheck = ::isFavorite,
                            onFavorite = ::toggleFavorite,
                            onPlay = { index -> resolveAndPlay(favorites.toList(), index) },
                            onDownload = ::downloadSong,
                            downloadingId = downloadingId,
                            showSource = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    "history" -> {
                        if (history.isEmpty()) ToolsMessage("还没有播放记录")
                        else SongList(
                            songs = history,
                            favoriteCheck = ::isFavorite,
                            onFavorite = ::toggleFavorite,
                            onPlay = { index -> resolveAndPlay(history.toList(), index) },
                            onDownload = ::downloadSong,
                            downloadingId = downloadingId,
                            showSource = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    else -> {
                        ToolsSearchField(
                            value = keyword,
                            onValueChange = { keyword = it },
                            placeholder = "搜索歌曲 / 歌手，酷狗 + 网易云 + QQ 聚合",
                            onSearch = { doSearch() },
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("all" to "全部", "kg" to "酷狗", "wy" to "网易云", "tx" to "QQ")) { (key, label) ->
                                NieveGlassChip(label, onlineFilter == key, onClick = { onlineFilter = key })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        when {
                            searching -> ToolsMessage("正在三平台聚合搜索…")
                            searchError.isNotBlank() -> ToolsMessage(searchError, MaterialTheme.colorScheme.error)
                            visibleResults.isEmpty() -> ToolsMessage(
                                if (results.isEmpty()) "输入关键词，一次搜完酷狗、网易云、QQ" else "该分类下没有结果"
                            )
                            else -> Column(Modifier.weight(1f)) {
                                if (resolvingId.isNotBlank()) {
                                    Text(
                                        "正在解析播放地址…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                                    )
                                }
                                SongList(
                                    songs = visibleResults,
                                    favoriteCheck = ::isFavorite,
                                    onFavorite = ::toggleFavorite,
                                    onPlay = { index -> resolveAndPlay(visibleResults, index) },
                                    onDownload = ::downloadSong,
                                    downloadingId = downloadingId,
                                    showSource = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            MiniPlayerBar(
                onOpen = { page = "player" },
                onNext = {
                    val next = MusicPlayer.index + 1
                    if (next <= MusicPlayer.queue.lastIndex) {
                        resolveAndPlay(MusicPlayer.queue.toList(), next)
                    }
                },
            )
        }
    }

    if (showError) {
        NieveGlassDialog(
            onDismissRequest = { showError = false },
            title = "播放失败",
            confirmText = "知道了",
            onConfirm = { showError = false },
        ) {
            Text(playError, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    favoriteCheck: (Song) -> Boolean,
    onFavorite: (Song) -> Unit,
    onPlay: (Int) -> Unit,
    onDownload: (Song) -> Unit,
    downloadingId: String,
    showSource: Boolean = false,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 140.dp),
    ) {
        items(songs, key = { it.source + it.id }) { song ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        val index = songs.indexOfFirst { it.id == song.id && it.source == song.source }
                        if (index >= 0) onPlay(index)
                    }
                    .padding(vertical = 9.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (song.cover.isNotBlank()) {
                        AsyncImage(
                            model = song.cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(
                            if (showSource) MusicSource.entries.firstOrNull { it.key == song.source }?.label.orEmpty() else "",
                            song.artist,
                            song.album,
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (song.source != MusicSource.LOCAL.key) {
                    val key = "${song.source}:${song.id}"
                    if (downloadingId == key) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "免费下载",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(26.dp)
                                .clickable { onDownload(song) },
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Icon(
                    if (favoriteCheck(song)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (favoriteCheck(song)) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onFavorite(song) },
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(onOpen: () -> Unit, onNext: () -> Unit) {
    val song = MusicPlayer.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (song != null) {
            ToolsCard(
                modifier = Modifier.padding(bottom = 94.dp),
                onClick = onOpen,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (song.cover.isNotBlank()) {
                            AsyncImage(
                                model = song.cover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (MusicPlayer.preparing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (MusicPlayer.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "播放/暂停",
                            modifier = Modifier.size(28.dp).clickable { MusicPlayer.toggle() },
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(26.dp).clickable(onClick = onNext),
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicPlayerScreen(api: MusicApi, onBack: () -> Unit, onPlayIndex: (Int) -> Unit) {
    val context = LocalContext.current
    val song = MusicPlayer.current
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(MusicPlayer.durationMs) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    var lyric by remember { mutableStateOf("") }
    var lyricLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) position = MusicPlayer.position()
            duration = MusicPlayer.durationMs
            delay(500)
        }
    }

    LaunchedEffect(song?.source, song?.id) {
        val current = song ?: return@LaunchedEffect
        lyricLoading = true
        lyric = runCatching { api.lyric(current) }.getOrDefault("")
        lyricLoading = false
    }

    ToolsPage(title = "正在播放", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!song?.cover.isNullOrBlank()) {
                    AsyncImage(
                        model = song?.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                song?.name ?: "未播放",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(song?.artist.orEmpty(), song?.album.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(18.dp))
            val max = if (duration > 0) duration.toFloat() else 1f
            Slider(
                value = if (dragging) dragValue else position.toFloat().coerceIn(0f, max),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    MusicPlayer.seekTo(dragValue.toLong())
                    dragging = false
                },
                valueRange = 0f..max,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(if (dragging) dragValue.toLong() else position), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(36.dp).clickable {
                        if (MusicPlayer.index > 0) onPlayIndex(MusicPlayer.index - 1)
                    },
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(26.dp))
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (MusicPlayer.preparing) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (MusicPlayer.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "播放/暂停",
                            modifier = Modifier.size(36.dp).clickable { MusicPlayer.toggle() },
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(26.dp))
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(36.dp).clickable {
                        if (MusicPlayer.index < MusicPlayer.queue.lastIndex) onPlayIndex(MusicPlayer.index + 1)
                    },
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(26.dp))
            ToolsSectionTitle("歌词")
            Text(
                when {
                    lyricLoading -> "歌词加载中…"
                    lyric.isBlank() -> "暂无歌词（酷狗不提供歌词，可在网易云 / QQ 音乐查看）"
                    else -> lyric
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 120.dp),
                lineHeight = 22.sp,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}
