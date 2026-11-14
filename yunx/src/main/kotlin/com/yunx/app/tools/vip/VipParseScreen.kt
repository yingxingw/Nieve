package com.yunx.app.tools.vip

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yunx.app.shell.NieveGlassChip
import com.yunx.app.shell.NieveGlassIconButton
import com.yunx.app.shell.NieveGlassTextField
import com.yunx.app.tools.ToolActionButton
import com.yunx.app.tools.ToolsCard
import com.yunx.app.tools.toolsPrefs
import com.yunx.app.ui.SnackbarController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DISCLAIMER_KEY = "vip_parse_disclaimer_v1"
private const val DEFAULT_VIP_LINE_KEY = "789"
private const val LAST_VIP_LINE_KEY = "vip_last_line_key"

private fun rememberedVipLineKey(prefs: android.content.SharedPreferences): String {
    val saved = prefs.getString(LAST_VIP_LINE_KEY, null)
    return if (saved != null && VipParseLines.any { it.key == saved }) {
        saved
    } else {
        DEFAULT_VIP_LINE_KEY
    }
}

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipParseScreen(
    onExit: () -> Unit,
    onImmersiveChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findVipActivity() }
    val clipboard = LocalClipboardManager.current
    val prefs = remember(context) { toolsPrefs(context) }
    val fullscreener = remember { VipFullscreenController(context) }

    var accepted by remember { mutableStateOf(prefs.getBoolean(DISCLAIMER_KEY, false)) }
    var loadedOnce by remember { mutableStateOf(false) }
    var pageFullscreen by remember { mutableStateOf(false) }
    var previousOrientation by remember { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    var currentUrl by remember { mutableStateOf(VipSites.first().url) }
    var pageTitle by remember { mutableStateOf("") }
    var sourceTitle by remember { mutableStateOf("") }
    var sourceVideoUrl by remember { mutableStateOf<String?>(null) }
    var selectedLineKey by rememberSaveable { mutableStateOf(rememberedVipLineKey(prefs)) }
    var error by remember { mutableStateOf("") }
    var showInput by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }
    var showLines by remember { mutableStateOf(false) }
    var urlInput by rememberSaveable { mutableStateOf("") }
    var favorites by remember { mutableStateOf<List<VipFavorite>>(VipFavoriteStore.load(context)) }
    val zoomLevel = remember { floatArrayOf(1f) }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            settings.defaultTextEncodingName = "UTF-8"
            settings.userAgentString = DESKTOP_UA
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
    }

    fun effectiveVideoUrl(): String? {
        val live = webView.url?.takeIf { it.startsWith("http") } ?: currentUrl
        if (!isVipParsePage(live) && isVipSiteHome(live)) return null
        val video = if (isVipParsePage(live)) sourceVideoUrl ?: extractVipSourceUrl(live) else live
        return video?.let(::normalizeVipUrl)
    }

    fun resetVipZoom() {
        zoomLevel[0] = 1f
    }

    fun applyVipZoom(delta: Float) {
        zoomLevel[0] = (zoomLevel[0] + delta).coerceIn(0.6f, 2.0f)
        webView.requestFocus()
        webView.post {
            if (delta > 0) webView.zoomIn() else webView.zoomOut()
        }
    }

    fun currentFavoriteUrl(): String? = effectiveVideoUrl()

    fun currentFavoriteTitle(): String {
        val url = webView.url.orEmpty()
        return when {
            isVipParsePage(url) -> sourceTitle.ifBlank { pageTitle }.ifBlank { "解析视频" }
            else -> pageTitle.ifBlank { vipSiteOf(url)?.label ?: "视频页面" }
        }
    }

    fun loadSite(site: VipSite) {
        sourceVideoUrl = null
        sourceTitle = ""
        selectedLineKey = rememberedVipLineKey(prefs)
        error = ""
        resetVipZoom()
        webView.loadUrl(site.url)
    }

    fun loadVideoPage(raw: String) {
        val normalized = normalizeVipUrl(raw)
        if (normalized == null) {
            SnackbarController.show("没有识别到有效链接")
            return
        }
        sourceVideoUrl = null
        sourceTitle = ""
        selectedLineKey = rememberedVipLineKey(prefs)
        error = ""
        showInput = false
        resetVipZoom()
        webView.loadUrl(normalized)
    }

    fun switchLine(line: VipParseLine, override: String? = null) {
        val live = webView.url.orEmpty()
        if (!isVipParsePage(live) && isVipSiteHome(live)) {
            SnackbarController.show("先打开视频播放页，再选择解析线路")
            showInput = true
            return
        }
        val source = when {
            override != null -> override
            isVipParsePage(live) -> sourceVideoUrl ?: extractVipSourceUrl(live)
            else -> live.takeIf { it.startsWith("http") }
        }
        val video = source?.let(::normalizeVipUrl)
        if (video == null) {
            SnackbarController.show("先打开视频播放页，再选择解析线路")
            showInput = true
            return
        }
        if (!isVipParsePage(live) && pageTitle.isNotBlank()) sourceTitle = pageTitle
        sourceVideoUrl = video
        selectedLineKey = line.key
        prefs.edit().putString(LAST_VIP_LINE_KEY, line.key).apply()
        error = ""
        showLines = false
        resetVipZoom()
        webView.loadUrl(line.build(video))
    }

    fun toggleFavorite() {
        val video = currentFavoriteUrl()
        if (video == null) {
            SnackbarController.show("先打开一个视频页面")
            return
        }
        val existing = favorites.firstOrNull { it.videoUrl == video }
        favorites = if (existing != null) {
            SnackbarController.show("已取消收藏")
            favorites - existing
        } else {
            SnackbarController.show("已收藏当前视频")
            listOf(
                VipFavorite(
                    title = currentFavoriteTitle(),
                    videoUrl = video,
                    lineKey = selectedLineKey,
                    savedAt = System.currentTimeMillis(),
                )
            ) + favorites
        }
        VipFavoriteStore.save(context, favorites)
    }

    fun openFavorite(favorite: VipFavorite) {
        val line = VipParseLines.firstOrNull { it.key == favorite.lineKey } ?: VipParseLines.first()
        sourceVideoUrl = favorite.videoUrl
        sourceTitle = favorite.title
        selectedLineKey = line.key
        prefs.edit().putString(LAST_VIP_LINE_KEY, line.key).apply()
        showFavorites = false
        error = ""
        resetVipZoom()
        webView.loadUrl(line.build(favorite.videoUrl))
    }

    fun openInputDialog() {
        val pasted = clipboard.getText()?.text
        urlInput = normalizeVipUrl(pasted.orEmpty()).orEmpty()
        showInput = true
    }

    fun enterPageFullscreen() {
        if (pageFullscreen) return
        previousOrientation = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        pageFullscreen = true
    }

    fun exitPageFullscreen() {
        if (!pageFullscreen) return
        pageFullscreen = false
        activity?.requestedOrientation = previousOrientation
    }

    fun handleBack() {
        when {
            pageFullscreen -> exitPageFullscreen()
            webView.canGoBack() -> webView.goBack()
            else -> onExit()
        }
    }

    webView.webViewClient = remember(webView) {
        object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null) currentUrl = url
                resetVipZoom()
                error = ""
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!url.isNullOrBlank()) currentUrl = url
                pageTitle = view?.title.orEmpty()
                if (url != null && !isVipParsePage(url)) sourceTitle = pageTitle
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                err: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    error = err?.description?.toString().orEmpty().ifBlank { "网页加载失败" }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme?.lowercase() ?: return true
                return scheme != "http" && scheme != "https"
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull().orEmpty()
                return scheme != "http" && scheme != "https"
            }
        }
    }

    webView.webChromeClient = remember(webView) {
        object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                pageTitle = title.orEmpty()
                val url = view?.url.orEmpty()
                if (url.startsWith("http") && !isVipParsePage(url)) sourceTitle = pageTitle
            }

            override fun onShowCustomView(customView: View?, callback: CustomViewCallback?) {
                if (customView != null && callback != null) fullscreener.show(customView, callback)
            }

            override fun onHideCustomView() {
                fullscreener.hide()
            }
        }
    }

    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(webView, true)
    }

    BackHandler { handleBack() }

    LaunchedEffect(accepted) {
        if (accepted && !loadedOnce) {
            loadedOnce = true
            webView.loadUrl(VipSites.first().url)
        }
    }

    LaunchedEffect(pageFullscreen) {
        onImmersiveChanged(pageFullscreen)
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (pageFullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            fullscreener.release()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
            onImmersiveChanged(false)
            activity?.requestedOrientation = previousOrientation
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val parsingPage = isVipParsePage(currentUrl)
    val favoriteNow = currentFavoriteUrl()?.let { url -> favorites.any { it.videoUrl == url } } == true

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (pageFullscreen) Modifier else Modifier.statusBarsPadding())
    ) {
        Column(Modifier.fillMaxSize()) {
            if (!pageFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NieveGlassIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        onClick = { handleBack() },
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "VIP 解析",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (isVipParsePage(currentUrl)) {
                                val line = VipParseLines.firstOrNull { it.key == selectedLineKey }
                                "${line?.label ?: "解析线路"} · 解析中"
                            } else {
                                vipSiteOf(currentUrl)?.label?.plus(" · 桌面版") ?: "六线路 · 实时切换"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    NieveGlassIconButton(
                        icon = Icons.Outlined.ContentPaste,
                        contentDescription = "粘贴视频链接",
                        onClick = { openInputDialog() },
                    )
                    Spacer(Modifier.width(6.dp))
                    NieveGlassIconButton(
                        icon = Icons.Rounded.Bookmarks,
                        contentDescription = "收藏夹",
                        onClick = { showFavorites = true },
                    )
                    Spacer(Modifier.width(6.dp))
                    NieveGlassIconButton(
                        icon = Icons.Rounded.Fullscreen,
                        contentDescription = "横屏全屏",
                        onClick = { enterPageFullscreen() },
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VipSites.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { site ->
                                val selected = vipSiteOf(currentUrl)?.key == site.key
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    NieveGlassChip(
                                        text = site.label,
                                        selected = selected,
                                        onClick = { loadSite(site) },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize(),
                )
                if (error.isNotBlank()) {
                    ToolsCard(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row {
                            ToolActionButton(
                                text = "重新加载",
                                primary = true,
                                onClick = {
                                    error = ""
                                    webView.reload()
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            ToolActionButton(
                                text = "打开链接",
                                onClick = { openInputDialog() },
                            )
                        }
                    }
                }

                if (isVipParsePage(currentUrl) && !pageFullscreen) {
                    val saved = currentFavoriteUrl()?.let { url ->
                        favorites.any { it.videoUrl == url }
                    } == true
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                    ) {
                        VipPageOverlayPill(
                            icon = if (saved) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            text = if (saved) "已收藏" else "收藏",
                            active = saved,
                            onClick = { toggleFavorite() },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                    ) {
                        VipPageOverlayPill(
                            icon = Icons.Rounded.SwapHoriz,
                            text = "切换线路",
                            onClick = { showLines = true },
                        )
                    }
                }
            }

        }

        AnimatedVisibility(
            visible = showLines,
            enter = fadeIn(animationSpec = tween(280)),
            exit = fadeOut(animationSpec = tween(180)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showLines = false }
            )
        }

        AnimatedVisibility(
            visible = showLines,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    bottom = if (pageFullscreen) 84.dp else 150.dp,
                ),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(240)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(180)),
        ) {
            VipLinePanel(
                selectedLineKey = selectedLineKey,
                onSelect = { line ->
                    showLines = false
                    switchLine(line)
                },
                onClose = { showLines = false },
            )
        }

        if (!pageFullscreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, bottom = 40.dp),
            ) {
                VipFloatingDock(
                    favorite = favoriteNow,
                    onParse = { showLines = !showLines },
                    onFavorite = ::toggleFavorite,
                    onZoomOut = { applyVipZoom(-0.1f) },
                    onZoomIn = { applyVipZoom(0.1f) },
                )
            }
        }

        if (pageFullscreen && !showLines) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            ) {
                VipPageOverlayPill(
                    icon = if (favoriteNow) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    text = if (favoriteNow) "已收藏" else "收藏",
                    active = favoriteNow,
                    onClick = { toggleFavorite() },
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VipPageOverlayPill(
                    icon = Icons.Rounded.SwapHoriz,
                    text = "切换线路",
                    onClick = { showLines = true },
                )
                VipOverlayButton(Icons.Rounded.FullscreenExit, "退出全屏") { exitPageFullscreen() }
            }
        }
    }

    if (!accepted) {
        AlertDialog(
            onDismissRequest = onExit,
            title = { Text("免责声明") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "本功能仅提供第三方解析页面的聚合入口，不存储、不制作、不分发任何影视内容；所有内容与播放服务均来自第三方网站，本应用无法保证其合法性、可用性与安全性。\n\n" +
                            "请仅将本功能用于个人学习、测试或已获授权的视频；不得用于商业传播、盗链或其他侵犯版权的行为。继续使用即表示你已理解并自行承担相关风险和责任。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    accepted = true
                    prefs.edit().putBoolean(DISCLAIMER_KEY, true).apply()
                }) {
                    Text("同意并进入")
                }
            },
            dismissButton = {
                TextButton(onClick = onExit) {
                    Text("退出")
                }
            },
        )
    }

    if (showInput) {
        AlertDialog(
            onDismissRequest = { showInput = false },
            title = { Text("打开视频页") },
            text = {
                Column {
                    NieveGlassTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = "粘贴腾讯 / 爱奇艺 / B站 / 优酷视频地址",
                        singleLine = false,
                        minLines = 2,
                        onClear = { urlInput = "" },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "先打开桌面版视频页，再点下方解析线路。也可以直接粘贴已复制的分享文案。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { loadVideoPage(urlInput) },
                    enabled = urlInput.isNotBlank(),
                ) {
                    Text("打开")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInput = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showFavorites) {
        ModalBottomSheet(onDismissRequest = { showFavorites = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 28.dp),
            ) {
                Text(
                    "收藏夹",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "收藏会记住视频页和当前解析线路，点一下直接恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (favorites.isEmpty()) {
                    Text(
                        "还没有收藏，播放页点下方“收藏”即可保存。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 26.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 430.dp)) {
                        items(favorites, key = { it.videoUrl }) { favorite ->
                            val line = VipParseLines.firstOrNull { it.key == favorite.lineKey }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openFavorite(favorite) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        favorite.title.ifBlank { "未命名视频" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        listOfNotNull(
                                            line?.label,
                                            vipSiteOf(favorite.videoUrl)?.label,
                                            formatVipTime(favorite.savedAt),
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                androidx.compose.material3.IconButton(onClick = {
                                    favorites = favorites - favorite
                                    VipFavoriteStore.save(context, favorites)
                                }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "删除收藏",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VipFloatingDock(
    favorite: Boolean,
    onParse: () -> Unit,
    onFavorite: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier = Modifier
            .shadow(16.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VipToolButton(
            icon = Icons.Rounded.PlayArrow,
            label = "解析",
            primary = true,
            onClick = onParse,
        )
        VipToolButton(
            icon = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            label = if (favorite) "已收藏" else "收藏",
            tint = if (favorite) Color(0xFFFFB300) else null,
            onClick = onFavorite,
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .width(1.dp)
                .height(26.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        )
        VipToolButton(
            icon = Icons.Rounded.ZoomOut,
            label = "缩小",
            onClick = onZoomOut,
        )
        VipToolButton(
            icon = Icons.Rounded.ZoomIn,
            label = "放大",
            onClick = onZoomIn,
        )
    }
}

@Composable
private fun VipToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean = false,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (primary) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun VipLinePanel(
    selectedLineKey: String,
    onSelect: (VipParseLine) -> Unit,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(24.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f), shape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "选择解析线路",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "默认 789 更快；卡顿可换 TXNQ / 冰豆。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭线路面板",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        VipParseLines.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { line ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        NieveGlassChip(
                            text = line.label,
                            selected = line.key == selectedLineKey,
                            onClick = { onSelect(line) },
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VipPageOverlayPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.62f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (active) Color(0xFFFFD54F) else Color.White,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun VipOverlayButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color.Black.copy(alpha = 0.50f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(21.dp),
        )
    }
}

private fun formatVipTime(time: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(time))

private fun isVipSiteHome(url: String): Boolean {
    val clean = url.substringBefore('?').substringBefore('#').trimEnd('/')
    return VipSites.any { it.url.trimEnd('/') == clean }
}

private fun Context.findVipActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

/** HTML5 视频全屏：把 WebChromeClient 的 custom view 放进独立黑色全屏 Dialog。 */
private class VipFullscreenController(context: Context) {
    private val activity: Activity? = context.findVipActivity()
    private var dialog: Dialog? = null
    private var callback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    fun show(customView: View, customCallback: WebChromeClient.CustomViewCallback) {
        val host = activity ?: return
        if (dialog != null) hide()
        originalOrientation = host.requestedOrientation
        callback = customCallback

        val frame = FrameLayout(host).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        (customView.parent as? ViewGroup)?.removeView(customView)
        frame.addView(
            customView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )

        val newDialog = Dialog(host, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(frame)
            setCanceledOnTouchOutside(false)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    hide()
                    true
                } else {
                    false
                }
            }
            setOnDismissListener {
                val pending = callback
                callback = null
                dialog = null
                host.requestedOrientation = originalOrientation
                pending?.onCustomViewHidden()
            }
        }
        dialog = newDialog
        host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        newDialog.show()
        newDialog.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            val controller = WindowCompat.getInsetsController(this, decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun hide() {
        val current = dialog
        if (current == null) {
            callback?.onCustomViewHidden()
            callback = null
            return
        }
        current.dismiss()
    }

    fun release() {
        callback = null
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
        activity?.requestedOrientation = originalOrientation
    }
}
