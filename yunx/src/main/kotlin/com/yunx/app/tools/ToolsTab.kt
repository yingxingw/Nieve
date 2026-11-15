package com.yunx.app.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.shell.NievePageTransition
import com.yunx.app.tools.beauty.BeautyScreen
import com.yunx.app.tools.movie.MovieTab
import com.yunx.app.tools.music.MusicTab
import com.yunx.app.tools.vip.VipParseScreen
import com.yunx.app.tools.web.WebSourceScreen

private data class ToolEntry(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val toolEntries = listOf(
    ToolEntry("movie", "影视大全", "多线路搜索 · 详情 · 选集播放", Icons.Rounded.Movie),
    ToolEntry("vip", "VIP 解析", "小T / 小Q / 小B / 小Y · 六线路", Icons.Rounded.LiveTv),
    ToolEntry("music", "音乐", "本地 + 三平台聚合搜索 · 免费下载", Icons.Rounded.LibraryMusic),
    ToolEntry("web", "网页源码抓取", "抓取 HTML 源码 · 复制 · 分享", Icons.Rounded.Code),
    ToolEntry("beauty", "随机美女视频", "分类切换 · 随机播放 · 自动连播", Icons.Rounded.Videocam),
)

@Composable
fun ToolsTab(
    initialRoute: String = "hub",
    onImmersiveChanged: (Boolean) -> Unit = {},
    onChromeHiddenChanged: (Boolean) -> Unit = {},
) {
    var route by rememberSaveable { mutableStateOf(initialRoute) }

    LaunchedEffect(route) {
        onChromeHiddenChanged(route == "vip")
    }
    DisposableEffect(Unit) {
        onDispose { onChromeHiddenChanged(false) }
    }

    BackHandler(enabled = route != "hub") {
        route = "hub"
    }

    NievePageTransition(
        targetState = route,
        modifier = Modifier.fillMaxSize(),
        label = "toolsPage",
        depth = { if (it == "hub") 0 else 1 },
    ) { current ->
        when (current) {
            "hub" -> ToolsHome(onOpen = { route = it })
            "movie" -> MovieTab(
                onExit = { route = "hub" },
                onImmersiveChanged = onImmersiveChanged,
            )
            "vip" -> VipParseScreen(
                onExit = { route = "hub" },
                onImmersiveChanged = onImmersiveChanged,
            )
            "music" -> MusicTab(
                initialSource = "online",
                onExit = { route = "hub" },
            )
            "web" -> WebSourceScreen(onExit = { route = "hub" })
            "beauty" -> BeautyScreen(onExit = { route = "hub" })
        }
    }
}

@Composable
private fun ToolsHome(onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 20.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 6.dp)) {
                Text(
                    text = "功能",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "影视 · 音乐 · 实用工具",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
        items(toolEntries) { entry ->
            ToolsIconTile(
                icon = entry.icon,
                title = entry.title,
                subtitle = entry.subtitle,
                onClick = { onOpen(entry.route) },
            )
        }
    }
}
