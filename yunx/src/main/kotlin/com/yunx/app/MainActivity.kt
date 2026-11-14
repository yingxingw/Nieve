/*
 * Nieve launcher activity (unified shell).
 *
 * Based on YunX (云析) MainActivity — AGPL-3.0, Copyright (C) 2026 CYQawa.
 * Modified for the Nieve merge: the Compose content is now the WeaveMask-style
 * unified shell (see com.yunx.app.shell.NieveShell) hosting the parser/download + SKIP features.
 * The class name is load-bearing for YunX's entry self-check (entryMismatch).
 */
package com.yunx.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.yunx.app.crash.CrashHandler
import com.yunx.app.shell.NieveShell
import com.yunx.app.ui.navigation.MainTab
import com.yunx.app.ui.theme.ComposeEmptyActivityTheme
import com.yunx.app.ui.theme.LocaleController
import com.yunx.app.util.ArchiveProbe
import com.yunx.app.util.DeepLinks
import com.yunx.app.data.download.MagnetLink
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * 外部来源（目前是实况通知点击）请求直接打开的 Tab；null = 不指定，落在默认「解析」页。
     * 用 mutableStateOf 而不是 rememberSaveable：Compose 读它会自动重组，onNewIntent 里改它即可切页。
     */
    private val pendingTab: MutableState<MainTab?> = mutableStateOf(null)

    /** 深链接 / 分享 / 选中文本拿到的待解析文本（分享链接、中文口令、带链接的文案都行） */
    private val pendingLink: MutableState<String?> = mutableStateOf(null)

    /** 直链 / 磁力：交给下载页「添加下载任务」预填（磁力只有 Gopeed 引擎能下） */
    private val pendingAddUrl = mutableStateOf<String?>(null)

    /**
     * 应用内语言：在 Activity 建立 Context 前就按已保存语言替换 Configuration，
     * 这样整页字符串（以及 Compose 里所有 stringResource）从第一次组合起就是目标语言。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.wrap(newBase))
    }

    // ★ 通知权限不再在启动时申请/引导：统一收到引导页第 3 页，之后只有设置页里的手动入口会再申请。
    override fun onCreate(savedInstanceState: Bundle?) {
        // 应用内主题设置：始终深色/浅色时提前切换窗口主题，避免冷启动闪错背景色
        val darkModePref = getSharedPreferences("yunx_settings", Context.MODE_PRIVATE)
            .getInt("dark_mode", 0)
        when (darkModePref) {
            1 -> setTheme(R.style.Theme_ComposeEmptyActivity_Light)
            2 -> setTheme(R.style.Theme_ComposeEmptyActivity_Dark)
        }
        super.onCreate(savedInstanceState)
        // ★ 调试构建开放 WebView 远程调试（排查登录页问题）
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
        enableEdgeToEdge()
        runCatching {
            val hits = ArchiveProbe.fast(this).toMutableList()
            if (entryMismatch(this)) hits.add(4)
            if (hits.isNotEmpty()) {
                CrashHandler.terminate(hits.joinToString(","))
            }
        }
        // 实况通知点击进来：直接把初始 Tab 落成「下载」页；深链接 / 分享则把文本交给解析页。
        // ★ 只在首次创建时处理：旋转/进程重建会带着同一个 intent 再走一次 onCreate，重复消费会重复解析。
        if (savedInstanceState == null) {
            pendingTab.value = tabFromIntent(intent)
            handleDeepLink(intent)
        }
        setContent {
            ComposeEmptyActivityTheme {
                NieveShell(
                    yunxTab = pendingTab.value,
                    onYunxTabConsumed = { pendingTab.value = null },
                    incomingLink = pendingLink.value,
                    onIncomingLinkConsumed = { pendingLink.value = null },
                    incomingAddUrl = pendingAddUrl.value,
                    onIncomingAddUrlConsumed = { pendingAddUrl.value = null },
                )
            }
        }
    }

    /**
     * 应用已在前台时再点实况通知：SINGLE_TOP 让系统把 intent 送到这里（而不是再起一个实例），
     * 更新 pendingTab 后由 MainScreen 消费并切到「下载」Tab。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tabFromIntent(intent)?.let { pendingTab.value = it }
        handleDeepLink(intent)
    }

    /**
     * 深链接 / 系统分享 / 选中文本解析：把 intent 里的文本交给解析链路。
     *
     * 磁力与普通链接分开：磁力只有 Gopeed 引擎能下，不能走分享解析，直接预填下载页；
     * 其余（分享链接 / 中文口令 / 一整句带链接的文案）统一交给解析页。
     */
    private fun handleDeepLink(intent: Intent?) {
        val text = DeepLinks.textFrom(
            action = intent?.action,
            data = intent?.dataString,
            extraText = extraText(intent)
        ) ?: return
        if (MagnetLink.isMagnet(text)) {
            pendingAddUrl.value = text
            pendingTab.value = MainTab.Download
        } else {
            pendingLink.value = text
            pendingTab.value = MainTab.Resolve
        }
    }

    /** 文本类 action 携带的正文：ACTION_SEND 用 EXTRA_TEXT，PROCESS_TEXT 用 EXTRA_PROCESS_TEXT */
    private fun extraText(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_PROCESS_TEXT ->
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        else -> null
    }

    /** [EXTRA_OPEN_TAB] 指定打开哪个 Tab：download / resolve / drive / settings */
    private fun tabFromIntent(intent: Intent?): MainTab? =
        when (intent?.getStringExtra(EXTRA_OPEN_TAB)) {
            TAB_DOWNLOAD -> MainTab.Download
            TAB_RESOLVE -> MainTab.Resolve
            TAB_DRIVE -> MainTab.Drive
            TAB_SETTINGS -> MainTab.Settings
            else -> null
        }

    companion object {
        /** 「点击后打开哪个 Tab」的 intent extra 键（由 DownloadService 的通知 contentIntent 写入） */
        const val EXTRA_OPEN_TAB = "com.yunx.app.extra.OPEN_TAB"

        /** [EXTRA_OPEN_TAB] 的取值：打开「下载」Tab */
        const val TAB_DOWNLOAD = "download"

        /** [EXTRA_OPEN_TAB] 的取值：打开「解析」Tab */
        const val TAB_RESOLVE = "resolve"

        /** [EXTRA_OPEN_TAB] 的取值：打开「账号」Tab */
        const val TAB_DRIVE = "drive"

        /** [EXTRA_OPEN_TAB] 的取值：打开解析页内「设置」Tab */
        const val TAB_SETTINGS = "settings"
    }
}
