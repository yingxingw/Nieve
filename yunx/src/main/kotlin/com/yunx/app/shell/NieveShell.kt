package com.yunx.app.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.ui.MainScreen
import com.yunx.app.ui.navigation.MainTab
import io.github.seyud.weave.ui.component.FloatingBottomBar
import io.github.seyud.weave.ui.component.FloatingBottomBarItem
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import io.github.seyud.weave.ui.theme.LocalEnableFloatingBottomBar
import io.github.seyud.weave.ui.theme.LocalEnableFloatingBottomBarBlur
import io.github.seyud.weave.ui.theme.WeaveMagiskTheme
import io.github.seyud.weave.ui.util.attachBarBlurBackdrop
import io.github.seyud.weave.ui.util.rememberBarBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shell pages. All four pages now live inside the same shell; 解析 no longer takes over the screen. */
enum class ShellTab(val label: String, val icon: ImageVector) {
    Home("首页", Icons.Rounded.Home),
    YunX("解析", Icons.Rounded.CloudDownload),
    Skip("跳过", Icons.Rounded.FastForward),
    Settings("设置", Icons.Rounded.Settings),
}

/**
 * Nieve unified shell — a single black/liquid-glass surface hosting:
 * 首页 / 解析下载 / 自动跳过 / 设置。
 *
 * The bottom bar is always present and always samples the page underneath it
 * through the same [androidx.compose.ui.graphics.layer.LayerBackdrop].
 */
@Composable
fun NieveShell(
    yunxTab: MainTab?,
    onYunxTabConsumed: () -> Unit,
    incomingLink: String?,
    onIncomingLinkConsumed: () -> Unit,
    incomingAddUrl: String?,
    onIncomingAddUrlConsumed: () -> Unit,
) {
    val settings = rememberShellSettings()
    WeaveMagiskTheme(colorMode = settings.colorMode) {
        val dark = isNieveDark(settings.colorMode)
        val accent = MiuixTheme.colorScheme.primary
        NieveMaterialTheme(dark = dark, accent = accent) {
            CompositionLocalProvider(
                LocalEnableBlur provides settings.blur,
                LocalEnableFloatingBottomBar provides settings.floatingBar,
                LocalEnableFloatingBottomBarBlur provides settings.blur,
            ) {
                var pageName by rememberSaveable { mutableStateOf(ShellTab.Home.name) }
                val page = ShellTab.valueOf(pageName)
                var skipRoute by rememberSaveable { mutableStateOf("dashboard") }
                var yunxSection by remember { mutableStateOf<MainTab?>(null) }
                var yunxImmersive by remember { mutableStateOf(false) }
                var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
                val selectTab: (ShellTab) -> Unit = { tab ->
                    if (tab == ShellTab.Skip && page != ShellTab.Skip) {
                        skipRoute = "dashboard"
                    }
                    pageName = tab.name
                }
                val setPage: (ShellTab) -> Unit = { pageName = it.name }

                // 分享 / 深链接 / 磁力进入时落在解析页，但底栏不会消失。
                LaunchedEffect(incomingLink, incomingAddUrl) {
                    if (incomingLink != null || incomingAddUrl != null) {
                        setPage(ShellTab.YunX)
                    }
                }
                // 外部 intent 指定了解析页内某个 Tab（通知 / 快捷方式）时，直接落到解析页。
                LaunchedEffect(yunxTab) {
                    if (yunxTab != null) setPage(ShellTab.YunX)
                }
                BackHandler(enabled = page == ShellTab.YunX) {
                    setPage(ShellTab.Home)
                }
                BackHandler(enabled = page == ShellTab.Settings && settingsPage != null) {
                    settingsPage = null
                }

                val surfaceColor = MiuixTheme.colorScheme.surface
                val blurBackdrop = rememberBarBlurBackdrop(settings.blur, surfaceColor)
                val barBackdrop = rememberLayerBackdrop {
                    drawRect(surfaceColor)
                    drawContent()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .attachBarBlurBackdrop(blurBackdrop)
                    ) {
                        NieveAmbientBackground(dark = isNieveDark(settings.colorMode))
                        AnimatedContent(
                            targetState = page,
                            transitionSpec = {
                                val forward = targetState.ordinal > initialState.ordinal
                                val slide = tween<androidx.compose.ui.unit.IntOffset>(
                                    durationMillis = 320,
                                    easing = FastOutSlowInEasing,
                                )
                                val dir = if (forward) 1 else -1
                                (fadeIn(tween(220)) + slideInHorizontally(slide) { it / 3 * dir })
                                    .togetherWith(
                                        fadeOut(tween(170)) +
                                            slideOutHorizontally(slide) { -it / 3 * dir }
                                    )
                            },
                            label = "shellPage",
                        ) { current ->
                            when (current) {
                                ShellTab.Home -> HomeHub(
                                    onOpenYunx = {
                                        yunxSection = MainTab.Resolve
                                        setPage(ShellTab.YunX)
                                    },
                                    onOpenDownloads = {
                                        yunxSection = MainTab.Download
                                        setPage(ShellTab.YunX)
                                    },
                                    onOpenAccounts = {
                                        yunxSection = MainTab.Drive
                                        setPage(ShellTab.YunX)
                                    },
                                    onOpenSkip = { setPage(ShellTab.Skip) },
                                    onOpenSettings = { setPage(ShellTab.Settings) },
                                )

                                ShellTab.YunX -> MainScreen(
                                    openTab = yunxSection ?: yunxTab,
                                    onOpenTabConsumed = {
                                        yunxSection = null
                                        onYunxTabConsumed()
                                    },
                                    incomingLink = incomingLink,
                                    onIncomingLinkConsumed = onIncomingLinkConsumed,
                                    incomingAddUrl = incomingAddUrl,
                                    onIncomingAddUrlConsumed = onIncomingAddUrlConsumed,
                                    shellMode = true,
                                    onImmersiveChanged = { yunxImmersive = it },
                                )

                                ShellTab.Skip -> SkipTab(
                                    route = skipRoute,
                                    onRouteChange = { skipRoute = it },
                                    onOpenAppearance = { setPage(ShellTab.Settings) },
                                    onExitToHome = { setPage(ShellTab.Home) },
                                )

                                ShellTab.Settings -> when (settingsPage) {
                                    "about" -> NieveAboutScreen(
                                        onBack = { settingsPage = null },
                                        onOpenDeveloper = { settingsPage = "developer" },
                                        onOpenCredits = { settingsPage = "credits" },
                                        onOpenPrivacy = { settingsPage = "privacy" },
                                    )

                                    "developer" -> NieveDeveloperScreen(
                                        onBack = { settingsPage = null },
                                    )

                                    "credits" -> NieveCreditsScreen(
                                        onBack = { settingsPage = "about" },
                                    )

                                    "privacy" -> NievePrivacyScreen(
                                        onBack = { settingsPage = "about" },
                                    )

                                    else -> ShellSettingsScreen(
                                        settings = settings,
                                        onOpenSkipSettings = {
                                            skipRoute = "settings"
                                            setPage(ShellTab.Skip)
                                        },
                                        onOpenSkipWhitelist = {
                                            skipRoute = "whitelist"
                                            setPage(ShellTab.Skip)
                                        },
                                        onOpenYunxSettings = {
                                            yunxSection = MainTab.Settings
                                            setPage(ShellTab.YunX)
                                        },
                                        onOpenAbout = { settingsPage = "about" },
                                        onOpenDeveloper = { settingsPage = "developer" },
                                    )
                                }
                            }
                        }
                    }

                    if (settings.floatingBar && (page != ShellTab.YunX || !yunxImmersive)) {
                        FloatingBottomBar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    bottom = 12.dp + WindowInsets.navigationBars
                                        .asPaddingValues().calculateBottomPadding()
                                ),
                            selectedIndex = { ShellTab.valueOf(pageName).ordinal },
                            onSelected = { selectTab(ShellTab.entries[it]) },
                            backdrop = blurBackdrop ?: barBackdrop,
                            tabsCount = ShellTab.entries.size,
                            isBackdropBlurEnabled = settings.blur,
                            isLiquidGlassEnabled = settings.blur,
                        ) {
                            ShellTab.entries.forEach { tab ->
                                val selected = tab.name == pageName
                                FloatingBottomBarItem(
                                    onClick = { selectTab(tab) },
                                    modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = if (selected) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = tab.label,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        color = if (selected) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Visible
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
