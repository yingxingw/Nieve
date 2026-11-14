package com.yunx.app.tools.movie

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material.icons.rounded.Refresh
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.yunx.app.shell.NieveGlassChip
import com.yunx.app.tools.store.LibraryStore
import com.yunx.app.tools.store.MovieHistoryEntry
import com.yunx.app.tools.ToolActionButton
import com.yunx.app.tools.ToolsLibraryShortcut
import com.yunx.app.tools.ToolsCard
import com.yunx.app.tools.ToolsMessage
import com.yunx.app.tools.ToolsPage
import com.yunx.app.tools.ToolsSectionTitle
import com.yunx.app.tools.ToolsSearchField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MovieTab(
    onExit: () -> Unit,
    onImmersiveChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val api = remember { MovieApi() }
    val store = remember { LibraryStore(context) }
    var page by rememberSaveable { mutableStateOf("home") }
    var siteIndex by rememberSaveable { mutableIntStateOf(0) }
    var movie by remember { mutableStateOf<Movie?>(null) }
    var lines by remember { mutableStateOf<List<MoviePlayLine>>(emptyList()) }
    var playerLine by remember { mutableStateOf<MoviePlayLine?>(null) }
    var playerEpisodeIndex by rememberSaveable { mutableIntStateOf(0) }
    var libraryMode by rememberSaveable { mutableStateOf("fav") }
    var detailFromLibrary by remember { mutableStateOf(false) }
    var resumeLineName by remember { mutableStateOf<String?>(null) }
    var resumeEpisodeIndex by remember { mutableIntStateOf(-1) }

    BackHandler(enabled = page != "home") {
        when (page) {
            "player" -> page = "detail"
            "library" -> page = "home"
            else -> page = if (detailFromLibrary) "library" else "home"
        }
    }

    when (page) {
        "home" -> MovieHome(
            api = api,
            siteIndex = siteIndex,
            onSiteChange = {
                siteIndex = it
                lines = emptyList()
            },
            onOpenMovie = {
                movie = it
                detailFromLibrary = false
                resumeLineName = null
                resumeEpisodeIndex = -1
                page = "detail"
            },
            onOpenLibrary = {
                libraryMode = it
                page = "library"
            },
            onExit = onExit,
        )
        "library" -> MovieLibrary(
            mode = libraryMode,
            onBack = { page = "home" },
            onOpenMovie = { item, source ->
                movie = item
                siteIndex = source
                detailFromLibrary = true
                resumeLineName = null
                resumeEpisodeIndex = -1
                page = "detail"
            },
            onOpenHistory = { entry ->
                movie = entry.movie
                siteIndex = entry.siteIndex
                detailFromLibrary = true
                resumeLineName = entry.lineName
                resumeEpisodeIndex = entry.episodeIndex
                page = "detail"
            },
        )
        "detail" -> {
            val current = movie
            if (current == null) page = "home" else MovieDetailScreen(
                api = api,
                site = movieSites[siteIndex],
                movie = current,
                onBack = { page = if (detailFromLibrary) "library" else "home" },
                onLinesLoaded = { lines = it },
                resumeLineName = resumeLineName,
                resumeEpisodeIndex = resumeEpisodeIndex,
                onPlay = { line, index ->
                    resumeLineName = null
                    resumeEpisodeIndex = -1
                    val episode = line.episodes.getOrNull(index)
                    if (episode != null) {
                        store.recordMovie(current, siteIndex, line.name, index, episode.name)
                    }
                    playerLine = line
                    playerEpisodeIndex = index
                    page = "player"
                },
            )
        }
        "player" -> {
            val current = movie
            val line = playerLine
            if (current == null || line == null || line.episodes.isEmpty()) {
                page = "detail"
            } else {
                MoviePlayerScreen(
                    api = api,
                    title = current.name,
                    line = line,
                    startIndex = playerEpisodeIndex.coerceIn(0, line.episodes.lastIndex),
                    onSelectEpisode = { index ->
                        playerEpisodeIndex = index
                        line.episodes.getOrNull(index)?.let {
                            store.recordMovie(current, siteIndex, line.name, index, it.name)
                        }
                    },
                    onBack = { page = "detail" },
                    onImmersiveChanged = onImmersiveChanged,
                )
            }
        }
    }
}

@Composable
private fun MovieHome(
    api: MovieApi,
    siteIndex: Int,
    onSiteChange: (Int) -> Unit,
    onOpenMovie: (Movie) -> Unit,
    onOpenLibrary: (String) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val favoriteCount = remember { store.movieFavorites().size }
    val historyCount = remember { store.movieHistory().size }
    val scope = rememberCoroutineScope()
    var keyword by rememberSaveable { mutableStateOf("") }
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(siteIndex, reload, searchMode) {
        loading = true
        error = ""
        runCatching { api.search(movieSites[siteIndex], if (searchMode) keyword.trim() else "", 1) }
            .onSuccess { movies = it; loading = false }
            .onFailure { error = it.message ?: "加载失败"; loading = false }
    }

    ToolsPage(title = "影视大全", onBack = onExit) {
        Column(Modifier.fillMaxSize()) {
            ToolsSearchField(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = "电影 / 电视剧 / 动漫",
                onSearch = {
                    if (keyword.isNotBlank()) {
                        searchMode = true
                        reload++
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolsLibraryShortcut(
                    title = "收藏",
                    count = favoriteCount,
                    subtitle = "已收藏的影片",
                    icon = Icons.Rounded.Bookmark,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenLibrary("fav") },
                )
                Spacer(Modifier.width(8.dp))
                ToolsLibraryShortcut(
                    title = "历史",
                    count = historyCount,
                    subtitle = "上次看到哪",
                    icon = Icons.Rounded.History,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenLibrary("history") },
                )
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(movieSites.size) { index ->
                    NieveGlassChip(
                        movieSites[index].name,
                        selected = index == siteIndex,
                        onClick = {
                            if (index != siteIndex) {
                                onSiteChange(index)
                                reload++
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                }
                error.isNotBlank() -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    ToolActionButton("重试", onClick = { reload++ })
                }
                movies.isEmpty() -> ToolsMessage("该线路没有内容，换一个源或关键词")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                ) {
                    items(movies, key = { it.id + it.name }) { item ->
                        MovieRow(item) { onOpenMovie(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieRow(movie: Movie, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 98.dp, height = 136.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            AsyncImage(
                model = movie.pic,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (movie.remarks.isNotBlank()) {
                Text(
                    movie.remarks,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                movie.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                listOf(movie.year, movie.area, movie.type).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (movie.actor.isNotBlank()) {
                Text(
                    "主演：${movie.actor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (movie.content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    movie.content.replace(Regex("<[^>]+>"), "").trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MovieLibrary(
    mode: String,
    onBack: () -> Unit,
    onOpenMovie: (Movie, Int) -> Unit,
    onOpenHistory: (MovieHistoryEntry) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val favorites = remember(mode) { store.movieFavorites() }
    val history = remember(mode) { store.movieHistory() }
    val isFavorites = mode == "fav"

    ToolsPage(title = if (isFavorites) "影视收藏" else "观看历史", onBack = onBack) {
        if (isFavorites && favorites.isEmpty()) {
            ToolsMessage("还没有收藏的影片\n在详情页点“收藏”即可添加")
        } else if (!isFavorites && history.isEmpty()) {
            ToolsMessage("还没有观看记录\n播放一集后会自动记录")
        } else if (isFavorites) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
            ) {
                items(favorites, key = { it.movie.id }) { entry ->
                    MovieRow(entry.movie) { onOpenMovie(entry.movie, entry.siteIndex) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
            ) {
                items(history, key = { it.movie.id }) { entry ->
                    MovieHistoryRow(entry) { onOpenHistory(entry) }
                }
            }
        }
    }
}

@Composable
private fun MovieHistoryRow(entry: MovieHistoryEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 58.dp, height = 82.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            AsyncImage(
                model = entry.movie.pic,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.movie.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "${entry.lineName} · ${entry.episodeName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatMovieTime(entry.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "继续",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun formatMovieTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovieDetailScreen(
    api: MovieApi,
    site: MovieSite,
    movie: Movie,
    onBack: () -> Unit,
    onLinesLoaded: (List<MoviePlayLine>) -> Unit,
    onPlay: (MoviePlayLine, Int) -> Unit,
    resumeLineName: String? = null,
    resumeEpisodeIndex: Int = -1,
) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val initialLines = remember(movie.id, movie.playUrl) { api.playLines(movie) }
    var detail by remember(movie.id) { mutableStateOf(movie) }
    var lines by remember(movie.id) { mutableStateOf(initialLines) }
    var loading by remember { mutableStateOf(initialLines.isEmpty()) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var favorite by remember(movie.id) { mutableStateOf(store.isMovieFavorite(movie.id)) }
    var autoResumed by remember(movie.id) { mutableStateOf(false) }
    val lastWatch = remember(movie.id) {
        store.movieHistory().firstOrNull { it.movie.id == movie.id }
    }

    LaunchedEffect(movie.id, site.api, retry) {
        error = ""
        if (retry == 0 && initialLines.isNotEmpty()) {
            onLinesLoaded(initialLines)
            loading = false
            if (!autoResumed && resumeLineName != null && resumeEpisodeIndex >= 0) {
                initialLines.firstOrNull { it.name == resumeLineName }?.let { line ->
                    autoResumed = true
                    onPlay(line, resumeEpisodeIndex.coerceIn(0, line.episodes.lastIndex))
                }
            }
            return@LaunchedEffect
        }
        loading = true
        runCatching { api.detail(site, movie) }
            .onSuccess { (d, l) ->
                detail = d
                lines = l
                onLinesLoaded(l)
                loading = false
                if (!autoResumed && resumeLineName != null && resumeEpisodeIndex >= 0) {
                    l.firstOrNull { it.name == resumeLineName }?.let { line ->
                        autoResumed = true
                        onPlay(line, resumeEpisodeIndex.coerceIn(0, line.episodes.lastIndex))
                    }
                }
            }
            .onFailure {
                error = it.message ?: "详情加载失败"
                loading = false
            }
    }

    ToolsPage(title = detail.name, onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    AsyncImage(
                        model = detail.pic,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 104.dp, height = 148.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            detail.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOf(detail.year, detail.area, detail.type, detail.remarks)
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (detail.director.isNotBlank()) {
                            Text(
                                "导演：${detail.director}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        if (detail.actor.isNotBlank()) {
                            Text(
                                "主演：${detail.actor}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (detail.content.isNotBlank()) {
                    ToolsCard {
                        Text(
                            detail.content.replace(Regex("<[^>]+>"), "").trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val watch = lastWatch
                    val resumeLine = watch?.let { item ->
                        lines.firstOrNull { it.name == item.lineName }
                    }
                    if (watch != null && resumeLine != null) {
                        ToolActionButton(
                            text = "继续观看",
                            primary = true,
                            onClick = {
                                onPlay(
                                    resumeLine,
                                    watch.episodeIndex.coerceIn(0, resumeLine.episodes.lastIndex)
                                )
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    ToolActionButton(
                        text = if (favorite) "已收藏" else "收藏影片",
                        onClick = {
                            favorite = store.toggleMovieFavorite(detail, movieSites.indexOf(site))
                        },
                    )
                }
                if (lastWatch != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "上次看到：${lastWatch.lineName} · ${lastWatch.episodeName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                when {
                    loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在解析播放线路…", style = MaterialTheme.typography.bodySmall)
                    }
                    error.isNotBlank() -> Column {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        ToolActionButton("重新加载", onClick = { retry++ })
                    }
                    lines.isEmpty() -> Text(
                        "该影片暂无播放地址",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (lines.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ToolsSectionTitle("播放线路", modifier = Modifier.weight(1f))
                        Text(
                            if (loading) "刷新中…" else "刷新线路",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(enabled = !loading) { retry++ },
                        )
                    }
                }
            }

            itemsIndexed(lines, key = { index, _ -> index }) { lineIndex, line ->
                ToolsCard(modifier = Modifier.padding(bottom = 10.dp)) {
                    Text(
                        line.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        line.episodes.forEachIndexed { epIndex, episode ->
                            NieveGlassChip(
                                episode.name,
                                selected = false,
                                onClick = { onPlay(line, epIndex) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun MoviePlayerScreen(
    api: MovieApi,
    title: String,
    line: MoviePlayLine,
    startIndex: Int,
    onSelectEpisode: (Int) -> Unit,
    onBack: () -> Unit,
    onImmersiveChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val insetsController = remember(activity) {
        activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
    }
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 50_000, 1_000, 2_000)
                    .build()
            )
            .build()
            .apply {
                playWhenReady = true
            }
    }
    var index by remember { mutableIntStateOf(startIndex) }
    var resolvedUrl by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var autoRetries by remember { mutableIntStateOf(0) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var landscape by rememberSaveable { mutableStateOf(true) }
    var previousOrientation by remember { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    val current = line.episodes.getOrNull(index)

    fun applyOrientation(useLandscape: Boolean) {
        activity?.requestedOrientation = if (useLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    fun enterFullscreen() {
        previousOrientation = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        fullscreen = true
        landscape = true
        applyOrientation(true)
    }

    fun exitFullscreen() {
        fullscreen = false
        activity?.requestedOrientation = previousOrientation
    }

    BackHandler(enabled = fullscreen) { exitFullscreen() }

    LaunchedEffect(fullscreen) {
        onImmersiveChanged(fullscreen)
        if (fullscreen) {
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onImmersiveChanged(false)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(playbackError: PlaybackException) {
                resolving = false
                error = "播放失败，请重试或返回换线路（${playbackError.errorCodeName}）"
                if (autoRetries < 1) {
                    autoRetries++
                    attempt++
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> resolving = true
                    Player.STATE_READY -> {
                        error = ""
                        resolving = false
                    }
                    Player.STATE_ENDED -> resolving = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(current?.url) {
        autoRetries = 0
        attempt = 0
    }

    LaunchedEffect(current?.url, attempt) {
        val raw = current?.url ?: return@LaunchedEffect
        resolving = true
        error = ""
        resolvedUrl = ""
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        runCatching { api.resolvePlayUrl(raw) }
            .onSuccess {
                resolvedUrl = it
            }
            .onFailure {
                error = it.message ?: "解析播放地址失败"
                resolving = false
            }
    }

    LaunchedEffect(resolvedUrl, attempt) {
        if (resolvedUrl.isBlank()) return@LaunchedEffect
        runCatching {
            exoPlayer.setMediaItem(MediaItem.fromUri(resolvedUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }.onFailure {
            error = it.message ?: "播放器加载失败"
            resolving = false
        }
    }

    if (fullscreen) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            MoviePlayerSurface(exoPlayer, Modifier.fillMaxSize())
            if (resolving) {
                CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.dp, color = Color.White)
            }
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerOverlayButton(
                    icon = Icons.Rounded.ScreenRotation,
                    contentDescription = if (landscape) "切换为竖屏全屏" else "切换为横屏全屏",
                    onClick = {
                        landscape = !landscape
                        applyOrientation(landscape)
                    },
                )
                if (error.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    PlayerOverlayButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "重新加载",
                        onClick = { attempt++ },
                    )
                }
                Spacer(Modifier.width(8.dp))
                PlayerOverlayButton(
                    icon = Icons.Rounded.FullscreenExit,
                    contentDescription = "退出全屏",
                    onClick = { exitFullscreen() },
                )
            }
            if (error.isNotBlank()) {
                Text(
                    text = error,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                )
            }
        }
        return
    }

    ToolsPage(title = title, onBack = onBack) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                MoviePlayerSurface(exoPlayer, Modifier.fillMaxSize())
                if (resolving) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                }
                PlayerOverlayButton(
                    icon = Icons.Rounded.Fullscreen,
                    contentDescription = "全屏播放",
                    onClick = { enterFullscreen() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                current?.name ?: "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                line.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    ToolActionButton("重试", onClick = { attempt++ })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                ToolActionButton(
                    "上一集",
                    enabled = index > 0,
                    onClick = {
                        index--
                        onSelectEpisode(index)
                    },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${index + 1} / ${line.episodes.size}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Spacer(Modifier.weight(1f))
                ToolActionButton(
                    "下一集",
                    enabled = index < line.episodes.lastIndex,
                    onClick = {
                        index++
                        onSelectEpisode(index)
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            ToolsSectionTitle("选集")
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        line.episodes.forEachIndexed { epIndex, episode ->
                            NieveGlassChip(
                                episode.name,
                                selected = epIndex == index,
                                onClick = {
                                    index = epIndex
                                    onSelectEpisode(index)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun MoviePlayerSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun PlayerOverlayButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(38.dp)
            .background(Color.Black.copy(alpha = 0.46f), RoundedCornerShape(19.dp)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(21.dp),
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
