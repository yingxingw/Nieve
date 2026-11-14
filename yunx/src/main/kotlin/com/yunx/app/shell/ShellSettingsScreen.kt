package com.yunx.app.shell

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yunx.app.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * WeaveMask-style settings page: appearance switches (theme mode / blur / floating
 * bottom bar) plus feature entries into the YunX and SKIP settings screens.
 */
@Composable
fun ShellSettingsScreen(
    settings: ShellSettingsState,
    onOpenSkipSettings: () -> Unit,
    onOpenSkipWhitelist: () -> Unit,
    onOpenYunxSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDeveloper: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 108.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        SmallTitle(text = "外观")
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "模糊效果",
                summary = "底栏与背景的实时模糊 / 液态玻璃效果",
                checked = settings.blur,
                onCheckedChange = { settings.updateBlur(it) },
                startAction = {
                    Icon(Icons.Rounded.Tune, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
            )
            SwitchPreference(
                title = "浮动底栏",
                summary = "关闭后页面内容延伸到底部",
                checked = settings.floatingBar,
                onCheckedChange = { settings.updateFloatingBar(it) },
                startAction = {
                    Icon(Icons.Rounded.Settings, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
            )
        }

        SmallTitle(text = "主题模式")
        Card(modifier = Modifier.fillMaxWidth()) {
            ShellSettingsState.COLOR_MODE_NAMES.forEachIndexed { index, name ->
                CheckboxPreference(
                    title = name,
                    checked = settings.colorMode == index,
                    onCheckedChange = { checked ->
                        if (checked) settings.updateColorMode(index)
                    },
                )
            }
        }

        SmallTitle(text = "功能设置")
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "自动跳过设置",
                summary = "严格模式 · 提示 · 日志 · 主题",
                startAction = {
                    Icon(Icons.Rounded.Palette, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
                onClick = onOpenSkipSettings,
            )
            ArrowPreference(
                title = "应用白名单",
                summary = "设置不参与自动跳过的应用",
                startAction = {
                    Icon(Icons.Rounded.PlaylistAddCheck, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
                onClick = onOpenSkipWhitelist,
            )
            ArrowPreference(
                title = "无障碍服务",
                summary = "系统设置中开启 / 关闭自动跳过服务",
                startAction = {
                    Icon(Icons.Rounded.Rule, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )
            ArrowPreference(
                title = "解析下载设置",
                summary = "下载引擎 · 代理 · 语言 · 主题（在解析页内）",
                startAction = {
                    Icon(Icons.Rounded.Info, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
                onClick = onOpenYunxSettings,
            )
        }

        SmallTitle(text = "关于")
        Card(modifier = Modifier.fillMaxWidth()) {
            val pm = context.packageManager
            val versionName = runCatching {
                pm.getPackageInfo(context.packageName, 0).versionName
            }.getOrDefault("?")
            ArrowPreference(
                title = "Nieve 版本",
                summary = "$versionName · Miuix · 酷安初雪",
                startAction = {
                    Icon(Icons.Rounded.Info, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
                onClick = onOpenAbout,
            )
            ArrowPreference(
                title = "开发者",
                summary = "酷安初雪",
                startAction = {
                    Icon(Icons.Rounded.Person, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
                onClick = onOpenDeveloper,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
