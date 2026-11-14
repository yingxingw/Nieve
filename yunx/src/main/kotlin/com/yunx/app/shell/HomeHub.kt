package com.yunx.app.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.skip.util.AccessibilityState
import com.android.skip.util.AccessibilityStateUtils
import com.yunx.app.R
import io.github.seyud.weave.ui.about.effect.BgEffectBackground
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * Nieve hub dashboard (WeaveMask home-style): quick entries for the parser/download
 * and SKIP feature sets plus module status.
 */
@Composable
fun HomeHub(
    onOpenYunx: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenSkip: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    BgEffectBackground(
        dynamicBackground = false,
        modifier = Modifier.fillMaxSize(),
        isFullSize = true,
        effectBackground = false,
        drawSurface = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 108.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 30.sp,
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onBackground,
            )
            Text(
                text = "网盘解析下载 · 开屏广告自动跳过 · Miuix 界面",
                fontSize = 13.sp,
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            // ---------------- 解析下载 ----------------
            SmallTitle(text = "解析下载")
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "解析分享链接",
                    summary = "夸克 / UC / 迅雷 / 百度 / 123 / 115 / 蓝奏云，抖音 / B站 / 小红书 等视频图集",
                    startAction = {
                        Icon(Icons.Rounded.Link, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                    },
                    onClick = onOpenYunx,
                )
                ArrowPreference(
                    title = "下载管理",
                    summary = "Range 分片并发 · 断点续传 · 在线预览",
                    startAction = {
                        Icon(Icons.Rounded.Download, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                    },
                    onClick = onOpenDownloads,
                )
                ArrowPreference(
                    title = "网盘账号",
                    summary = "夸克 / UC / 百度 / 139 / 123 / 115 / 迅雷 多账号管理",
                    startAction = {
                        Icon(Icons.Rounded.AccountCircle, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                    },
                    onClick = onOpenAccounts,
                )
            }

            // ---------------- 自动跳过 ----------------
            SmallTitle(text = "自动跳过")
            val skipState = runCatching { AccessibilityStateUtils.getAccessibilityState() }
                .getOrDefault(AccessibilityState.STOPPED)
            val stateText = when (skipState) {
                AccessibilityState.STARTED -> "无障碍服务运行中"
                AccessibilityState.FAULTED -> "无障碍服务已开启但未运行"
                AccessibilityState.STOPPED -> "无障碍服务未开启"
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "跳过主界面",
                    summary = stateText,
                    startAction = {
                        Icon(Icons.Rounded.FastForward, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                    },
                    onClick = onOpenSkip,
                )
            }

            // ---------------- 外观与设置 ----------------
            SmallTitle(text = "外观与设置")
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "外观设置",
                    summary = "主题模式 · 模糊效果 · 浮动底栏",
                    startAction = {
                        Icon(Icons.Rounded.Palette, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                    },
                    onClick = onOpenSettings,
                )
                ArrowPreference(
                    title = "全部设置",
                    summary = "解析下载设置 · 跳过设置 · 白名单",
                    startAction = {
                        Icon(Icons.Rounded.Settings, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                    },
                    onClick = onOpenSettings,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
