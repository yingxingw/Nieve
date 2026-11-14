package com.yunx.app.shell

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.android.skip.MyApp
import com.android.skip.data.SyncWorker
import com.android.skip.data.config.ConfigViewModel
import com.android.skip.ui.alive.notificationbar.NotificationBarViewModel
import com.android.skip.ui.inspect.InspectActivity
import com.android.skip.ui.main.start.StartAccessibilityViewModel
import com.android.skip.ui.settings.log.SkipLogViewModel
import com.android.skip.ui.settings.recent.RecentViewModel
import com.android.skip.ui.settings.strict.StrictViewModel
import com.android.skip.ui.settings.tip.TipViewModel
import com.android.skip.ui.whitelist.WhiteListViewModel
import com.android.skip.util.AccessibilityState
import com.android.skip.util.DataStoreUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * SKIP feature set, rendered as native Nieve pages instead of the old standalone
 * SKIP activities. Both the dashboard and the settings/log pages use the same
 * Miuix language as the rest of the shell.
 */
@Composable
fun SkipTab(
    route: String,
    onRouteChange: (String) -> Unit,
    onOpenAppearance: () -> Unit,
    onExitToHome: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity ?: return

    val startAccessibilityViewModel: StartAccessibilityViewModel = viewModel()
    val configViewModel: ConfigViewModel = viewModel()
    val recentViewModel: RecentViewModel = viewModel()
    val tipViewModel: TipViewModel = viewModel()
    val strictViewModel: StrictViewModel = viewModel()
    val skipLogViewModel: SkipLogViewModel = viewModel()
    val whiteListViewModel: WhiteListViewModel = viewModel()
    val notificationBarViewModel: NotificationBarViewModel = viewModel()

    LaunchedEffect(Unit) {
        // 云端规则同步：立即拉一次，并恢复每 12 小时的周期同步（只同步跳过规则，
        // 不再做原 SKIP 的软件版本检查）。
        configViewModel.readConfig()
        runCatching {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                context.getString(com.android.skip.R.string.worker_sync),
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
    val configPostState by configViewModel.configPostState.observeAsState()
    LaunchedEffect(configPostState) {
        configPostState?.let { configViewModel.loadConfig(it) }
    }
    val excludeFromRecent by recentViewModel.excludeFromRecent.observeAsState()
    LaunchedEffect(excludeFromRecent) {
        excludeFromRecent?.let { exclude ->
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.let { manager ->
                manager.appTasks.forEach { task -> task?.setExcludeFromRecents(exclude) }
            }
        }
    }

    val accessibilityState by startAccessibilityViewModel.accessibilityState.collectAsState()
    val running = accessibilityState == AccessibilityState.STARTED

    BackHandler(enabled = route != SkipRoute.Dashboard) {
        onRouteChange(SkipRoute.Dashboard)
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        NievePageTransition(
            targetState = route,
            modifier = Modifier.fillMaxSize(),
            label = "skipPage",
            depth = ::skipRouteDepth,
        ) { currentRoute ->
        when (currentRoute) {
        SkipRoute.Settings -> SkipSettingsPage(
            context = context,
            activity = activity,
            recentViewModel = recentViewModel,
            tipViewModel = tipViewModel,
            strictViewModel = strictViewModel,
            skipLogViewModel = skipLogViewModel,
            configViewModel = configViewModel,
            onBack = { onRouteChange(SkipRoute.Dashboard) },
            onOpenLogs = { onRouteChange(SkipRoute.Logs) },
            onOpenAppearance = onOpenAppearance,
        )

        SkipRoute.Logs -> SkipLogPage(
            skipLogViewModel = skipLogViewModel,
            onBack = { onRouteChange(SkipRoute.Settings) },
        )

        SkipRoute.Whitelist -> SkipWhitelistPage(
            context = context,
            whiteListViewModel = whiteListViewModel,
            onBack = { onRouteChange(SkipRoute.Dashboard) },
        )

        SkipRoute.Alive -> SkipAlivePage(
            context = context,
            notificationBarViewModel = notificationBarViewModel,
            onBack = { onRouteChange(SkipRoute.Dashboard) },
        )

        else -> SkipDashboard(
            context = context,
            activity = activity,
            running = running,
            accessibilityState = accessibilityState,
            onOpenSettings = { onRouteChange(SkipRoute.Settings) },
            onOpenWhitelist = { onRouteChange(SkipRoute.Whitelist) },
            onOpenAlive = { onRouteChange(SkipRoute.Alive) },
        )
    }
        }
    }

// 首次进入的「跳过教程」弹窗已移除：点击服务开关直接跳系统无障碍设置。
}

private object SkipRoute {
    const val Dashboard = "dashboard"
    const val Settings = "settings"
    const val Logs = "logs"
    const val Whitelist = "whitelist"
    const val Alive = "alive"
}

/** 跳过页层级：首页 0，列表/设置 1，设置里的日志 2。 */
private fun skipRouteDepth(route: String): Int = when (route) {
    SkipRoute.Dashboard -> 0
    SkipRoute.Logs -> 2
    else -> 1
}

/** 打开无障碍设置：带 NEW_TASK / CLEAR_TOP，失败时回退系统设置主页并提示。 */
private fun openAccessibilitySettings(activity: ComponentActivity) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    runCatching { activity.startActivity(intent) }.onFailure {
        runCatching {
            activity.startActivity(
                Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Toast.makeText(
                activity,
                "无法打开无障碍设置，请手动前往：系统设置 → 无障碍",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
private fun SkipDashboard(
    context: Context,
    activity: ComponentActivity,
    running: Boolean,
    accessibilityState: AccessibilityState,
    onOpenSettings: () -> Unit,
    onOpenWhitelist: () -> Unit,
    onOpenAlive: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 120.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "自动跳过",
            fontSize = 30.sp,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Text(
            text = "无障碍服务 · 开屏广告自动跳过",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )

        SmallTitle(text = "服务状态")
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "自动跳过服务",
                summary = when (accessibilityState) {
                    AccessibilityState.STARTED -> "运行中，已可自动点击「跳过」"
                    AccessibilityState.FAULTED -> "服务已开启但未运行，点此重新启用"
                    AccessibilityState.STOPPED -> "未开启，点此前往无障碍设置"
                },
                checked = running,
                onCheckedChange = {
                    // 不再弹教程弹窗，直接前往系统无障碍设置开启服务。
                    openAccessibilitySettings(activity)
                },
                startAction = {
                    Icon(
                        imageVector = if (running) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                        contentDescription = null,
                        tint = if (running) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(end = 6.dp),
                    )
                },
            )
        }

        SmallTitle(text = "功能")
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "应用白名单",
                summary = "设置不参与自动跳过的应用",
                startAction = {
                    Icon(
                        Icons.Rounded.FastForward,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                },
                onClick = onOpenWhitelist,
            )
            ArrowPreference(
                title = "规则检视",
                summary = "查看当前界面命中的跳过规则",
                startAction = {
                    Icon(
                        Icons.Rounded.BugReport,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                },
                onClick = {
                    activity.startActivity(Intent(MyApp.context, InspectActivity::class.java))
                },
            )
            ArrowPreference(
                title = "保活设置",
                summary = "常驻通知栏 · 省电策略 · 自启动 · 后台锁定",
                onClick = onOpenAlive,
            )
            ArrowPreference(
                title = "跳过设置",
                summary = "提示 · 严格模式 · 日志 · 自定义规则 · 云端规则",
                onClick = onOpenSettings,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SkipSettingsPage(
    context: Context,
    activity: ComponentActivity,
    recentViewModel: RecentViewModel,
    tipViewModel: TipViewModel,
    strictViewModel: StrictViewModel,
    skipLogViewModel: SkipLogViewModel,
    configViewModel: ConfigViewModel,
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAppearance: () -> Unit,
) {
    val excludeFromRecent by recentViewModel.excludeFromRecent.observeAsState(false)
    val tipEnable by tipViewModel.enable.observeAsState(false)
    val strictEnable by strictViewModel.enable.observeAsState(false)
    val logEnable by skipLogViewModel.enable.observeAsState(false)
    val notificationsEnabled = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val scope = rememberCoroutineScope()

    var showConfigDialog by remember { mutableStateOf(false) }
    var configText by remember {
        mutableStateOf(
            DataStoreUtils.getSyncData(
                context.getString(com.android.skip.R.string.store_custom_config),
                ""
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 120.dp),
    ) {
        SkipPageHeader(title = "跳过设置", onBack = onBack)

        SmallTitle(text = "行为")
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "从最近任务隐藏",
                summary = "避免在系统最近任务中出现",
                checked = excludeFromRecent,
                onCheckedChange = { recentViewModel.changeExcludeFromRecent(it) },
            )
            SwitchPreference(
                title = "跳过提示",
                summary = "点击「跳过」时显示 Toast / 通知提示",
                checked = tipEnable,
                onCheckedChange = { tipViewModel.changeEnable(it) },
            )
            SwitchPreference(
                title = "严格模式",
                summary = "只对匹配度更高的「跳过」文本执行点击",
                checked = strictEnable,
                onCheckedChange = { strictViewModel.changeEnable(it) },
            )
        }

        SmallTitle(text = "日志与规则")
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "记录跳过日志",
                summary = "保存命中记录与异常堆栈",
                checked = logEnable,
                onCheckedChange = { skipLogViewModel.changeEnable(it) },
            )
            ArrowPreference(
                title = "查看跳过日志",
                summary = "按日期查看点击记录与异常",
                onClick = {
                    skipLogViewModel.loadDailyLogs()
                    onOpenLogs()
                },
            )
            ArrowPreference(
                title = "检查规则更新",
                summary = "从云端拉取最新跳过规则并立即应用",
                onClick = {
                    scope.launch {
                        configViewModel.readConfig()
                        Toast.makeText(
                            context,
                            "已检查云端规则，如有更新将自动应用",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
            )
            ArrowPreference(
                title = "自定义规则",
                summary = "编辑本地规则文本，留空恢复默认",
                onClick = {
                    configText = DataStoreUtils.getSyncData(
                        context.getString(com.android.skip.R.string.store_custom_config),
                        ""
                    )
                    showConfigDialog = true
                },
            )
            ArrowPreference(
                title = "通知权限",
                summary = if (notificationsEnabled) "已授权" else "未授权，点击前往系统设置",
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    activity.startActivity(intent)
                },
            )
        }

        SmallTitle(text = "外观")
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = "界面主题",
                summary = "跳过页已与 Nieve 外壳统一，使用全局外观设置",
                onClick = onOpenAppearance,
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showConfigDialog) {
        WindowDialog(
            show = true,
            title = "自定义规则",
            summary = "每行一条规则；留空保存会恢复默认规则",
            onDismissRequest = { showConfigDialog = false },
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 360.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.secondaryVariant)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (configText.isEmpty()) {
                        Text(
                            text = "输入规则文本…",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    BasicTextField(
                        value = configText,
                        onValueChange = { configText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = MiuixTheme.colorScheme.onBackground,
                            fontSize = 14.sp,
                        ),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showConfigDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(20.dp))
                    TextButton(
                        text = "保存",
                        onClick = {
                            showConfigDialog = false
                            val key = context.getString(
                                com.android.skip.R.string.store_custom_config
                            )
                            if (configText.isBlank()) {
                                DataStoreUtils.removeSync(key)
                            } else {
                                DataStoreUtils.putSyncData(key, configText)
                            }
                            configViewModel.readConfig()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SkipAlivePage(
    context: Context,
    notificationBarViewModel: NotificationBarViewModel,
    onBack: () -> Unit,
) {
    val enable by notificationBarViewModel.enable.observeAsState(false)
    var showBackstage by remember { mutableStateOf(false) }
    val isXiaomi = Build.MANUFACTURER.equals("Xiaomi", true) ||
        Build.MANUFACTURER.equals("Redmi", true) ||
        Build.BRAND.equals("Xiaomi", true)

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 120.dp),
    ) {
        SkipPageHeader(title = "保活设置", onBack = onBack)

        SmallTitle(text = "后台保活")
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "常驻通知栏",
                summary = "无障碍服务运行时保持前台通知，降低被回收概率",
                checked = enable,
                onCheckedChange = { notificationBarViewModel.changeEnable(it) },
            )
            ArrowPreference(
                title = "省电策略",
                summary = "关闭电池优化，避免后台被系统限制",
                onClick = {
                    if (isXiaomi) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }.onFailure { toast("无法打开省电策略设置") }
                    } else {
                        toast("当前设备无小米省电策略入口")
                    }
                },
            )
            ArrowPreference(
                title = "自启动",
                summary = "在系统安全中心允许 Nieve 自启动",
                onClick = {
                    if (isXiaomi) {
                        runCatching {
                            context.startActivity(
                                Intent().apply {
                                    component = ComponentName(
                                        "com.miui.securitycenter",
                                        "com.miui.permcenter.autostart.AutoStartManagementActivity",
                                    )
                                }
                            )
                        }.onFailure { toast("无法打开自启动设置") }
                    } else {
                        toast("当前设备无小米自启动入口")
                    }
                },
            )
            ArrowPreference(
                title = "后台锁定",
                summary = "在最近任务中锁定 Nieve，避免被一键清理",
                onClick = { showBackstage = true },
            )
            ArrowPreference(
                title = "唤醒问题排查",
                summary = "搜索当前机型后台限制的排查方法",
                onClick = {
                    val query = Uri.encode("${Build.MANUFACTURER} ${Build.MODEL} 后台 保活 设置")
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.baidu.com/s?wd=$query"))
                    )
                },
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showBackstage) {
        WindowDialog(
            show = true,
            title = "后台锁定",
            summary = "打开系统最近任务，长按或下拉 Nieve 卡片，点击锁定图标。不同厂商的入口名称略有差异。",
            onDismissRequest = { showBackstage = false },
        ) {
            TextButton(
                text = "知道了",
                onClick = { showBackstage = false },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SkipWhitelistPage(
    context: Context,
    whiteListViewModel: WhiteListViewModel,
    onBack: () -> Unit,
) {
    val allApps = remember { mutableStateListOf<SkipAppEntry>() }
    val checked = remember { mutableStateMapOf<String, Boolean>() }
    var queryValue by remember { mutableStateOf(TextFieldValue("")) }
    val query = queryValue.text
    var showSystem by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { info ->
                    SkipAppEntry(
                        appName = info.loadLabel(pm).toString().ifBlank { info.packageName },
                        packageName = info.packageName,
                        system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }
                .sortedBy { it.appName.lowercase() }
        }
        allApps.clear()
        allApps.addAll(apps)
        apps.forEach { app ->
            checked[app.packageName] = whiteListViewModel.isAppInWhiteList(app.packageName)
        }
    }

    val filtered = allApps.filter { app ->
        (showSystem || !app.system) &&
            (query.isBlank() ||
                app.appName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
    ) {
        SkipPageHeader(title = "应用白名单", onBack = onBack)

        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "显示系统应用",
                summary = "默认只列出用户安装的应用",
                checked = showSystem,
                onCheckedChange = { showSystem = it },
            )
        }

        TextField(
            value = queryValue,
            onValueChange = { queryValue = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            label = "搜索应用名或包名",
            singleLine = true,
            useLabelAsPlaceholder = true,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { it.packageName }) { app ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = app.appName,
                        summary = app.packageName,
                        checked = checked[app.packageName] == true,
                        onCheckedChange = { isChecked ->
                            checked[app.packageName] = isChecked
                            whiteListViewModel.updateWhiteList(app.packageName, isChecked)
                        },
                        startAction = {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.secondaryVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = app.appName.take(1).ifBlank { "?" },
                                    fontSize = 15.sp,
                                    color = MiuixTheme.colorScheme.onBackground,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

private data class SkipAppEntry(
    val appName: String,
    val packageName: String,
    val system: Boolean,
)

@Composable
private fun SkipLogPage(
    skipLogViewModel: SkipLogViewModel,
    onBack: () -> Unit,
) {
    val dailyLogs by skipLogViewModel.dailyLogs.observeAsState(emptyList())
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 120.dp),
    ) {
        SkipPageHeader(title = "跳过日志", onBack = onBack)

        if (dailyLogs.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "暂无日志。开启「记录跳过日志」后再试。",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            dailyLogs.forEach { day ->
                SmallTitle(text = day.date)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        day.entries.forEach { entry ->
                            val time = Instant.ofEpochMilli(entry.timestamp)
                                .atZone(ZoneId.systemDefault())
                                .format(timeFormatter)
                            Text(
                                text = "$time · ${entry.level} · ${entry.source}",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                            Text(
                                text = entry.message,
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                            entry.stackTrace?.let {
                                Text(
                                    text = it,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SkipPageHeader(title: String, onBack: () -> Unit) {
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
