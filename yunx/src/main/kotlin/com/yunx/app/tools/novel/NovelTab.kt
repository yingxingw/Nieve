package com.yunx.app.tools.novel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.yunx.app.shell.NieveGlassChip
import com.yunx.app.shell.NieveGlassIconButton
import com.yunx.app.tools.store.LibraryStore
import com.yunx.app.tools.store.NovelHistoryEntry
import com.yunx.app.tools.ToolActionButton
import com.yunx.app.tools.ToolsLibraryShortcut
import com.yunx.app.tools.ToolsCard
import com.yunx.app.tools.ToolsMessage
import com.yunx.app.tools.ToolsPage
import com.yunx.app.tools.ToolsSearchField
import com.yunx.app.tools.ToolsSectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun NovelTab(
    onExit: () -> Unit,
    onImmersiveChanged: (Boolean) -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf("home") }
    var book by remember { mutableStateOf<QmBook?>(null) }
    var chapters by remember { mutableStateOf<List<QmChapter>>(emptyList()) }
    var readerIndex by rememberSaveable { mutableIntStateOf(0) }
    var libraryMode by rememberSaveable { mutableStateOf("fav") }
    var resumeIndex by remember { mutableIntStateOf(-1) }
    var detailFromLibrary by remember { mutableStateOf(false) }

    BackHandler(enabled = page != "home") {
        when (page) {
            "reader", "download" -> page = "detail"
            "library" -> page = "home"
            else -> page = "home"
        }
    }

    when (page) {
        "home" -> NovelHome(
            onExit = onExit,
            onOpenBook = {
                book = it
                resumeIndex = -1
                detailFromLibrary = false
                page = "detail"
            },
            onOpenLibrary = {
                libraryMode = it
                page = "library"
            },
        )
        "library" -> NovelLibrary(
            mode = libraryMode,
            onBack = { page = "home" },
            onOpenBook = {
                book = it
                resumeIndex = -1
                detailFromLibrary = true
                page = "detail"
            },
            onOpenHistory = { item, index ->
                book = item
                resumeIndex = index
                detailFromLibrary = true
                page = "detail"
            },
        )
        "detail" -> {
            val current = book
            if (current == null) {
                page = "home"
            } else {
                NovelDetail(
                    book = current,
                    resumeIndex = resumeIndex.takeIf { it >= 0 },
                    onBack = { page = if (detailFromLibrary) "library" else "home" },
                    onRead = { list, index ->
                        chapters = list
                        readerIndex = index
                        resumeIndex = -1
                        page = "reader"
                    },
                    onDownload = { list ->
                        chapters = list
                        page = "download"
                    },
                )
            }
        }
        "reader" -> {
            val current = book
            if (current == null || chapters.isEmpty()) {
                page = "detail"
            } else {
                NovelReader(
                    book = current,
                    chapters = chapters,
                    startIndex = readerIndex.coerceIn(0, chapters.lastIndex),
                    onBack = { page = "detail" },
                    onImmersiveChanged = onImmersiveChanged,
                )
            }
        }
        "download" -> {
            val current = book
            if (current == null || chapters.isEmpty()) {
                page = "detail"
            } else {
                NovelDownloadScreen(
                    book = current,
                    chapters = chapters,
                    onBack = { page = "detail" },
                )
            }
        }
    }
}

@Composable
private fun NovelHome(
    onExit: () -> Unit,
    onOpenBook: (QmBook) -> Unit,
    onOpenLibrary: (String) -> Unit,
) {
    val context = LocalContext.current
    val api = remember { QimaoApi(context) }
    val store = remember { LibraryStore(context) }
    val favoriteCount = remember { store.novelFavorites().size }
    val historyCount = remember { store.novelHistory().size }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(1) }
    var categoryId by rememberSaveable { mutableStateOf("0") }
    var categoryType by rememberSaveable { mutableStateOf("0") }
    var categories by remember { mutableStateOf<List<QmCategory>>(emptyList()) }
    var books by remember { mutableStateOf<List<QmBook>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<QmBook>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, categoryId, categoryType, reload) {
        loading = true
        error = ""
        runCatching { api.leaderBoard(tab, categoryId, categoryType) }
            .onSuccess { (cats, list) ->
                if (categories.isEmpty() || categoryId == "0") categories = cats
                books = list
                loading = false
            }
            .onFailure {
                error = it.message ?: "榜单加载失败"
                loading = false
            }
    }

    fun doSearch() {
        val keyword = query.trim()
        if (keyword.isEmpty()) return
        searchMode = true
        searching = true
        searchError = ""
        scope.launch {
            runCatching { api.search(keyword) }
                .onSuccess { searchResults = it; searching = false }
                .onFailure { searchError = it.message ?: "搜索失败"; searching = false }
        }
    }

    ToolsPage(title = "七猫小说", onBack = onExit) {
        Column(Modifier.fillMaxSize()) {
            ToolsSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "书名 / 作者",
                onSearch = { doSearch() },
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolsLibraryShortcut(
                    title = "收藏",
                    count = favoriteCount,
                    subtitle = "已收藏的书",
                    icon = Icons.Rounded.Bookmark,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenLibrary("fav") },
                )
                Spacer(Modifier.width(8.dp))
                ToolsLibraryShortcut(
                    title = "历史",
                    count = historyCount,
                    subtitle = "上次读到哪",
                    icon = Icons.Rounded.History,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenLibrary("history") },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NieveGlassChip("男生", tab == 1, onClick = {
                    if (tab != 1) {
                        tab = 1
                        categoryId = "0"
                        categoryType = "0"
                        categories = emptyList()
                        searchMode = false
                    }
                })
                Spacer(Modifier.width(8.dp))
                NieveGlassChip("女生", tab == 2, onClick = {
                    if (tab != 2) {
                        tab = 2
                        categoryId = "0"
                        categoryType = "0"
                        categories = emptyList()
                        searchMode = false
                    }
                })
                Spacer(Modifier.weight(1f))
                if (searchMode) {
                    ToolActionButton("返回榜单", onClick = {
                        searchMode = false
                        searchError = ""
                    })
                }
            }
            if (!searchMode) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.categoryId + it.categoryType }) { category ->
                        val selected = category.categoryId == categoryId && category.categoryType == categoryType
                        NieveGlassChip(category.title, selected, onClick = {
                            categoryId = category.categoryId
                            categoryType = category.categoryType
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            val currentList = if (searchMode) searchResults else books
            val currentLoading = if (searchMode) searching else loading
            val currentError = if (searchMode) searchError else error
            when {
                currentLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                }
                currentError.isNotBlank() -> ToolsMessage(currentError, MaterialTheme.colorScheme.error)
                currentList.isEmpty() -> ToolsMessage(if (searchMode) "没有搜到相关小说" else "暂无书籍")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                ) {
                    items(currentList, key = { it.id }) { book ->
                        NovelBookRow(book, onClick = { onOpenBook(book) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelBookRow(book: QmBook, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = book.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 62.dp, height = 82.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    if (book.author.isNotBlank()) append(book.author).append("  ")
                    if (book.score.isNotBlank()) append(book.score).append(" 分")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            if (book.subtitle.isNotBlank()) {
                Text(
                    book.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (book.intro.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    book.intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NovelLibrary(
    mode: String,
    onBack: () -> Unit,
    onOpenBook: (QmBook) -> Unit,
    onOpenHistory: (QmBook, Int) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val favorites = remember(mode) { store.novelFavorites() }
    val history = remember(mode) { store.novelHistory() }
    val isFavorites = mode == "fav"

    ToolsPage(title = if (isFavorites) "小说收藏" else "阅读历史", onBack = onBack) {
        if (isFavorites && favorites.isEmpty()) {
            ToolsMessage("还没有收藏的小说\n在详情页点“收藏”即可添加")
        } else if (!isFavorites && history.isEmpty()) {
            ToolsMessage("还没有阅读记录\n打开一本书开始阅读后会自动记录")
        } else if (isFavorites) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
            ) {
                items(favorites, key = { it.id }) { book ->
                    NovelBookRow(book, onClick = { onOpenBook(book) })
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
            ) {
                items(history, key = { it.book.id }) { entry ->
                    NovelHistoryRow(entry) { onOpenHistory(entry.book, entry.chapterIndex) }
                }
            }
        }
    }
}

@Composable
private fun NovelHistoryRow(entry: NovelHistoryEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = entry.book.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 52.dp, height = 70.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "读到 ${entry.chapterTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatLibraryTime(entry.updatedAt),
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

private fun formatLibraryTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun NovelDetail(
    book: QmBook,
    onBack: () -> Unit,
    onRead: (List<QmChapter>, Int) -> Unit,
    onDownload: (List<QmChapter>) -> Unit,
    resumeIndex: Int? = null,
) {
    val context = LocalContext.current
    val api = remember { QimaoApi(context) }
    val store = remember { LibraryStore(context) }
    var detailed by remember(book.id) { mutableStateOf(book) }
    var chapters by remember(book.id) { mutableStateOf<List<QmChapter>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var favorite by remember(book.id) { mutableStateOf(store.isNovelFavorite(book.id)) }
    var autoResumed by remember(book.id) { mutableStateOf(false) }
    val lastRead = remember(book.id) { store.novelHistory().firstOrNull { it.book.id == book.id } }
    val listState = rememberLazyListState()

    LaunchedEffect(book.id) {
        loading = true
        error = ""
        val detailResult = runCatching { api.search(book.title, 1) }
        detailResult.getOrNull()?.firstOrNull { it.id == book.id }?.let { detailed = it }
        runCatching { api.chapters(book.id) }
            .onSuccess {
                chapters = it
                loading = false
                if (!autoResumed && resumeIndex != null && it.isNotEmpty()) {
                    autoResumed = true
                    onRead(it, resumeIndex.coerceIn(0, it.lastIndex))
                }
            }
            .onFailure { error = it.message ?: "章节加载失败"; loading = false }
    }

    ToolsPage(title = book.title, onBack = onBack) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    AsyncImage(
                        model = detailed.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 104.dp, height = 140.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            detailed.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (detailed.author.isNotBlank()) {
                            Text(
                                "作者：${detailed.author}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (detailed.score.isNotBlank()) {
                            Text(
                                "评分 ${detailed.score}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (detailed.subtitle.isNotBlank()) {
                            Text(
                                detailed.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (detailed.intro.isNotBlank()) {
                    ToolsCard {
                        Text(
                            detailed.intro,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    ToolActionButton(
                        text = when {
                            loading -> "加载章节…"
                            lastRead != null -> "继续阅读"
                            else -> "开始阅读"
                        },
                        enabled = chapters.isNotEmpty(),
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val start = lastRead?.chapterIndex
                                ?.coerceIn(0, chapters.lastIndex) ?: 0
                            onRead(chapters, start)
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    ToolActionButton(
                        text = if (favorite) "已收藏" else "收藏",
                        modifier = Modifier.weight(1f),
                        onClick = { favorite = store.toggleNovelFavorite(detailed) },
                    )
                    Spacer(Modifier.width(8.dp))
                    ToolActionButton(
                        text = "下载全本",
                        enabled = chapters.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = { onDownload(chapters) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (loading) {
                        "正在获取章节…"
                    } else {
                        buildString {
                            append("共 ${chapters.size} 章")
                            lastRead?.let { append(" · 上次读到 ${it.chapterTitle}") }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                ToolsSectionTitle("章节列表")
            }
            itemsIndexed(chapters, key = { _, it -> it.id }) { index, chapter ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onRead(chapters, index) }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        chapter.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (chapter.words > 0) {
                        Text(
                            "${chapter.words} 字",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelReader(
    book: QmBook,
    chapters: List<QmChapter>,
    startIndex: Int,
    onBack: () -> Unit,
    onImmersiveChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val api = remember { QimaoApi(context) }
    val store = remember { LibraryStore(context) }
    var index by rememberSaveable { mutableIntStateOf(startIndex) }
    var fontSize by rememberSaveable { mutableIntStateOf(18) }
    var themeIndex by rememberSaveable { mutableIntStateOf(0) }
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val cache = remember { mutableStateMapOf<String, String>() }
    val scrollState = rememberScrollState()

    // 阅读页是沉浸页：隐藏壳层浮动底栏，并让底部按钮避开系统导航栏。
    LaunchedEffect(Unit) { onImmersiveChanged(true) }
    DisposableEffect(Unit) {
        onDispose { onImmersiveChanged(false) }
    }

    LaunchedEffect(index) {
        val chapter = chapters.getOrNull(index) ?: return@LaunchedEffect
        store.recordNovel(book, index, chapter.title)
        loading = true
        error = ""
        content = cache[chapter.id] ?: ""
        scrollState.scrollTo(0)
        if (cache.containsKey(chapter.id)) {
            loading = false
            return@LaunchedEffect
        }
        runCatching { api.chapterContent(book.id, chapter.id) }
            .onSuccess {
                cache[chapter.id] = it
                if (chapters.getOrNull(index)?.id == chapter.id) content = it
                loading = false
            }
            .onFailure {
                error = it.message ?: "正文加载失败"
                loading = false
            }
    }

    val themes = listOf(
        Triple(Color(0xFFF7F3EA), Color(0xFF2B2B2B), "纸白"),
        Triple(Color(0xFFE8F1E6), Color(0xFF263A2A), "护眼"),
        Triple(Color(0xFF0B0B0D), Color(0xFFBFC3CC), "夜间"),
    )
    val theme = themes[themeIndex]
    val chapter = chapters.getOrNull(index)

    ToolsPage(title = book.title, onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.first)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                chapter?.title ?: "",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.second,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f)) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = theme.second)
                    }
                    error.isNotBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(error, color = Color(0xFFCC4444), fontSize = 14.sp)
                    }
                    else -> Text(
                        text = content,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.75f).sp,
                        color = theme.second,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(bottom = 16.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolActionButton("上一章", enabled = index > 0, onClick = { index-- })
                Spacer(Modifier.weight(1f))
                Text(
                    "${index + 1}/${chapters.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.second.copy(alpha = 0.7f),
                )
                Spacer(Modifier.weight(1f))
                ToolActionButton("下一章", enabled = index < chapters.lastIndex, onClick = { index++ })
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolActionButton("A-", enabled = fontSize > 14, onClick = { fontSize -= 2 })
                Spacer(Modifier.width(6.dp))
                ToolActionButton("A+", enabled = fontSize < 26, onClick = { fontSize += 2 })
                Spacer(Modifier.weight(1f))
                themes.forEachIndexed { i, item ->
                    if (i > 0) Spacer(Modifier.width(6.dp))
                    NieveGlassChip(item.third, themeIndex == i, onClick = { themeIndex = i })
                }
            }
        }
    }
}

@Composable
private fun NovelDownloadScreen(
    book: QmBook,
    chapters: List<QmChapter>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val api = remember { QimaoApi(context) }
    val downloader = remember { NovelDownloader(context, api) }
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(chapters.size) }
    var current by remember { mutableStateOf("") }
    var resultPath by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val cancelled = remember { AtomicBoolean(false) }
    var started by remember { mutableStateOf(false) }

    fun start() {
        if (running) return
        running = true
        error = ""
        resultPath = ""
        done = 0
        cancelled.set(false)
        scope.launch {
            runCatching {
                downloader.download(
                    book = book,
                    chapters = chapters,
                    isCancelled = { cancelled.get() },
                    onProgress = { d, t, title ->
                        withContext(Dispatchers.Main) {
                            done = d
                            total = t
                            current = title
                        }
                    },
                )
            }.onSuccess {
                running = false
                resultPath = it
            }.onFailure {
                running = false
                error = it.message ?: "下载失败"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            start()
        }
    }

    ToolsPage(title = "下载小说", onBack = onBack) {
        Column(Modifier.fillMaxSize()) {
            ToolsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (book.author.isBlank()) "${chapters.size} 章" else "${book.author} · ${chapters.size} 章",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { if (total > 0) done.toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (running) "已下载 $done / $total 章" else if (resultPath.isNotBlank()) "下载完成" else "准备中",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (current.isNotBlank() && running) {
                    Text(
                        current,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    ToolActionButton(
                        text = if (running) "取消下载" else "重新下载",
                        enabled = resultPath.isBlank() || !running,
                        primary = running,
                        onClick = {
                            if (running) {
                                cancelled.set(true)
                                running = false
                            } else {
                                start()
                            }
                        },
                    )
                    if (resultPath.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        NieveGlassChip("已保存", true, onClick = {})
                    }
                }
            }
            if (resultPath.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                ToolsCard {
                    Text("保存位置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(resultPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "文件为 TXT，可直接用文件管理器打开或分享。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (error.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "下载会逐章抓取并解密正文，章节较多时需要较长时间；可随时取消，已写入的内容会保留。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
