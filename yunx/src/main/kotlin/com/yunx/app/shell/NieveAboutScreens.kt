package com.yunx.app.shell

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.TimeUnit

/** 检查更新数据源：GitHub 上的 Nieve 更新通道。 */
object NieveUpdater {
    const val UPDATE_URL =
        "https://raw.githubusercontent.com/yingxingw/nieve-update/main/update.json"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val minVersionCode: Int,
        val downloadUrl: String,
        val changelog: String,
        val updateDate: String,
    )

    sealed interface CheckResult {
        data object UpToDate : CheckResult
        data class UpdateAvailable(val info: UpdateInfo) : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    suspend fun check(currentVersionCode: Int): CheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(UPDATE_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching CheckResult.Failed("更新源不可用（HTTP ${response.code}）")
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val info = UpdateInfo(
                    versionCode = json.optInt("versionCode", 0),
                    versionName = json.optString("versionName", "?"),
                    minVersionCode = json.optInt("minVersionCode", 0),
                    downloadUrl = json.optString("downloadUrl", ""),
                    changelog = json.optString("changelog", ""),
                    updateDate = json.optString("updateDate", ""),
                )
                if (info.versionCode > currentVersionCode) {
                    CheckResult.UpdateAvailable(info)
                } else {
                    CheckResult.UpToDate
                }
            }
        }.getOrElse { CheckResult.Failed("网络异常，请稍后重试") }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AboutPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.ArrowBack,
            contentDescription = "返回",
            tint = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBack)
                .padding(8.dp)
                .size(24.dp),
        )
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** 关于页：版本 + 检查更新 + 开发者 + 开源致谢 + 隐私协议。 */
@Composable
fun NieveAboutScreen(
    onBack: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pm = context.packageManager
    val versionName = runCatching {
        pm.getPackageInfo(context.packageName, 0).versionName
    }.getOrDefault("?")
    val versionCode = runCatching {
        pm.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
    }.getOrDefault(0)

    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<NieveUpdater.CheckResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 108.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        AboutPageHeader(title = "关于 Nieve", onBack = onBack)
        SmallTitle(text = "应用")
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "Nieve",
                summary = "版本 $versionName ($versionCode)",
                startAction = {
                    Icon(Icons.Rounded.Info, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
                onClick = {},
            )
            ArrowPreference(
                title = "检查更新",
                summary = "从 GitHub 更新通道获取最新版本信息",
                startAction = {
                    Icon(
                        if (checking) Icons.Rounded.Refresh else Icons.Rounded.SystemUpdate,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = null,
                    )
                },
                onClick = {
                    if (!checking) {
                        checking = true
                        scope.launch {
                            checkResult = NieveUpdater.check(versionCode)
                            checking = false
                        }
                    }
                },
            )
            ArrowPreference(
                title = "开发者",
                summary = "酷安初雪",
                startAction = {
                    Icon(Icons.Rounded.Person, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
                onClick = onOpenDeveloper,
            )
            ArrowPreference(
                title = "开源项目",
                summary = "本应用使用的开源项目与许可证",
                startAction = {
                    Icon(Icons.Rounded.Code, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
                onClick = onOpenCredits,
            )
            ArrowPreference(
                title = "隐私协议",
                summary = "数据收集与权限使用说明",
                startAction = {
                    Icon(Icons.Rounded.Policy, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
                onClick = onOpenPrivacy,
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    checkResult?.let { result ->
        when (result) {
            is NieveUpdater.CheckResult.UpToDate -> NieveGlassDialog(
                onDismissRequest = { checkResult = null },
                title = "已是最新版本",
                confirmText = "好的",
                onConfirm = { checkResult = null },
            ) {
                Text("当前版本 $versionName 已是最新。")
            }

            is NieveUpdater.CheckResult.UpdateAvailable -> NieveGlassDialog(
                onDismissRequest = { checkResult = null },
                title = "发现新版本 ${result.info.versionName}",
                confirmText = "前往下载",
                onConfirm = {
                    checkResult = null
                    if (result.info.downloadUrl.isNotBlank()) {
                        openUrl(context, result.info.downloadUrl)
                    }
                },
                dismissText = "以后再说",
                onDismiss = { checkResult = null },
            ) {
                Text(
                    "发布日期：${result.info.updateDate}\n\n" +
                        "更新内容：\n${result.info.changelog}"
                )
            }

            is NieveUpdater.CheckResult.Failed -> NieveGlassDialog(
                onDismissRequest = { checkResult = null },
                title = "检查更新失败",
                confirmText = "好的",
                onConfirm = { checkResult = null },
            ) {
                Text(result.message)
            }

            null -> Unit
        }
    }
}

/** 开发者介绍页。 */
@Composable
fun NieveDeveloperScreen(onBack: () -> Unit) {
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
        AboutPageHeader(title = "开发者", onBack = onBack)
        SmallTitle(text = "关于开发者")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "酷安初雪",
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "独立开发者，Android 深度玩家。\n" +
                        "喜欢折腾刷机、工具开发，Nieve 是继刷机工具箱之后的又一款自用三合一应用：" +
                        "把网盘解析下载、开屏广告自动跳过装进同一个液态玻璃壳里。\n\n" +
                        "如果 Nieve 帮到了你，欢迎在酷安 / GitHub 上反馈问题。",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        SmallTitle(text = "联系方式")
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "酷安",
                summary = "@初雪",
                startAction = {
                    Icon(Icons.Rounded.Person, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
                onClick = {
                    openUrl(context, "https://www.coolapk.com/u/39153771")
                },
            )
            ArrowPreference(
                title = "开源仓库",
                summary = "github.com/yingxingw/Nieve",
                startAction = {
                    Icon(Icons.Rounded.Code, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                },
                onClick = {
                    openUrl(context, "https://github.com/yingxingw/Nieve")
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private data class Credit(
    val name: String,
    val author: String,
    val url: String,
    val license: String,
    val usedFor: String,
)

private val credits = listOf(
    Credit("YunX（云析）", "CYQawa", "https://github.com/CYQawa/YunX", "AGPL-3.0", "网盘 / 视频解析与高速下载功能"),
    Credit("SKIP", "GuoXiCheng", "https://github.com/GuoXiCheng/SKIP", "GPL-3.0", "开屏广告自动跳过、云端规则同步"),
    Credit("WeaveMask", "Seyud", "https://github.com/Seyud/WeaveMask", "GPL-3.0", "Miuix 界面语言与液态玻璃组件"),
    Credit("Miuix KMP", "YuKongA", "https://github.com/YuKongA/miuix-kmp", "MIT", "Miuix 界面组件库"),
    Credit("Gopeed", "GopeedLab", "https://github.com/GopeedLab/gopeed", "GPL-3.0", "下载引擎（内置内核）"),
    Credit("AndroidX / Jetpack Compose", "Google", "https://source.android.com", "Apache-2.0", "应用基础框架与界面框架"),
)

/** 开源项目致谢页。 */
@Composable
fun NieveCreditsScreen(onBack: () -> Unit) {
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
        AboutPageHeader(title = "开源项目", onBack = onBack)
        SmallTitle(text = "致谢")
        Card(modifier = Modifier.fillMaxWidth()) {
            credits.forEach { credit ->
                ArrowPreference(
                    title = credit.name,
                    summary = "${credit.author} · ${credit.license} · ${credit.usedFor}",
                    startAction = {
                        Icon(Icons.Rounded.Code, modifier = Modifier.padding(end = 6.dp), contentDescription = null)
                    },
                    onClick = { openUrl(context, credit.url) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 隐私协议页。 */
@Composable
fun NievePrivacyScreen(onBack: () -> Unit) {
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
        AboutPageHeader(title = "隐私协议", onBack = onBack)
        SmallTitle(text = "Nieve 隐私协议")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "更新日期：2026-11-14\n\n" +
                        "1. 信息收集\n" +
                        "Nieve 没有账号系统，不接入广告 SDK，不收集、不上传你的任何个人信息。" +
                        "应用没有遥测与统计上报。\n\n" +
                        "2. 本地数据\n" +
                        "你在应用内登录的网盘 Cookie / Token、下载记录、自定义跳过规则、设置项" +
                        "均只保存在本机应用私有目录；卸载应用即全部删除。\n\n" +
                        "3. 网络访问\n" +
                        "应用联网仅用于：\n" +
                        "· 你主动发起的分享链接解析与文件下载；\n" +
                        "· 自动跳过功能的云端规则同步（规则文件托管在 skip.guoxicheng.top）；\n" +
                        "· 「检查更新」从 GitHub 更新通道（github.com/yingxingw/nieve-update）拉取版本信息。\n\n" +
                        "4. 无障碍服务\n" +
                        "无障碍服务只用于自动点击「跳过」按钮，不读取、不上传屏幕内容；" +
                        "跳过日志仅保存在本地，可随时在设置中关闭。\n\n" +
                        "5. 权限说明\n" +
                        "· 存储权限：写入你下载的文件；\n" +
                        "· 通知权限：展示下载进度与后台保活状态；\n" +
                        "· 悬浮窗权限：仅用于规则检视功能的悬浮窗。\n\n" +
                        "6. 开源与透明度\n" +
                        "Nieve 基于多个开源项目构建（见「开源项目」页），源码可自行审计，" +
                        "各上游项目遵守各自的 AGPL / GPL / MIT / Apache 许可证。\n\n" +
                        "7. 联系我们\n" +
                        "对本协议或隐私保护有疑问，可通过酷安 @初雪 或 GitHub（yingxingw）联系开发者。",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
