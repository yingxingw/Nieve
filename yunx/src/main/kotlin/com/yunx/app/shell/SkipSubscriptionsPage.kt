package com.yunx.app.shell

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.skip.data.config.ConfigViewModel
import com.android.skip.data.config.ParsedSubscription
import com.android.skip.data.config.SkipSubscription
import com.android.skip.data.config.SubscriptionParser
import com.android.skip.data.config.SubscriptionStore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 第三个（跳过/跳广告）板块里的多订阅管理页。
 * 这里把「配置更新」从设置里挪出来，和导入、查看、编辑放在同一个入口。
 */
@Composable
fun SkipSubscriptionsPage(
    context: Context,
    configViewModel: ConfigViewModel,
    onBack: () -> Unit,
) {
    val defaultUrl = context.getString(com.android.skip.R.string.store_default_config)
    val scope = rememberCoroutineScope()
    var subscriptions by remember { mutableStateOf(SubscriptionStore.ensureSeeded(defaultUrl)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SkipSubscription?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var detailParsed by remember { mutableStateOf<ParsedSubscription?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }

    fun reload() {
        subscriptions = SubscriptionStore.load()
    }

    fun syncAll() {
        if (syncing) return
        syncing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { configViewModel.readConfigNow() }.getOrNull()
            }
            reload()
            syncing = false
            Toast.makeText(
                context,
                if (result?.status?.name == "SUCCESS") "订阅已更新并应用" else "更新失败：${result?.value ?: "网络或规则格式问题"}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun openDetail(subscription: SkipSubscription) {
        detailId = subscription.id
        detailParsed = null
        if (subscription.content.isBlank()) {
            Toast.makeText(context, "这个订阅还没有缓存，请先更新", Toast.LENGTH_SHORT).show()
            return
        }
        detailLoading = true
        scope.launch {
            val parsed = withContext(Dispatchers.Default) {
                SubscriptionParser.parse(subscription.content)
            }
            detailParsed = parsed
            detailLoading = false
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }.orEmpty()
                }.getOrDefault("")
            }
            if (content.isBlank()) {
                Toast.makeText(context, "文件读取失败或为空", Toast.LENGTH_SHORT).show()
            } else {
                val name = uri.lastPathSegment?.substringAfterLast('/')
                    ?.substringBeforeLast('.')?.ifBlank { "本地规则文件" } ?: "本地规则文件"
                val subscription = SkipSubscription(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    content = content,
                    localOverride = true,
                )
                SubscriptionStore.update(subscription)
                reload()
                Toast.makeText(context, "已导入 $name，正在解析", Toast.LENGTH_SHORT).show()
                syncAll()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 120.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onBack),
            )
            Text(
                text = "在线订阅",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = "支持多个订阅源（GKD / SKIP），后导入的会追加而不会覆盖；可查看每个文件里的应用、规则组和规则数量，也可直接自定义原始内容。",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.64f),
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${subscriptions.count { it.enabled }} 个启用 / ${subscriptions.size} 个订阅",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "共 ${subscriptions.sumOf { it.ruleCount }} 条已转换规则",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                    )
                }
                TextButton(
                    text = if (syncing) "更新中…" else "全部更新",
                    onClick = { syncAll() },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(
                    text = "添加订阅",
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                TextButton(
                    text = "导入文件",
                    onClick = { filePicker.launch(arrayOf("application/json", "text/plain", "application/yaml", "*/*")) },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }

        SmallTitle(text = "订阅源")
        if (subscriptions.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "还没有订阅，点上面的「添加订阅」或「导入文件」",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        subscriptions.forEach { subscription ->
            SubscriptionCard(
                subscription = subscription,
                onToggle = { enabled ->
                    SubscriptionStore.update(subscription.copy(enabled = enabled))
                    reload()
                    syncAll()
                },
                onDetail = { openDetail(subscription) },
                onEdit = { editing = subscription },
                onDelete = {
                    SubscriptionStore.delete(subscription.id)
                    reload()
                    syncAll()
                },
            )
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = "GKD 订阅只会转换广告/开屏/弹窗类规则组中的文本和控件 ID；复杂父子选择器不会强行伪装成已生效。SKIP 原生 YAML/JSON 会完整载入。",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("") }
        WindowDialog(
            show = true,
            title = "添加订阅",
            summary = "支持 GKD JSON5 订阅和 SKIP YAML/JSON；多个订阅会合并生效",
            onDismissRequest = { showAddDialog = false },
        ) {
            Column {
                RuleTextField(
                    value = newName,
                    placeholder = "名称（可留空，自动识别）",
                    singleLine = true,
                    onValueChange = { newName = it },
                )
                Spacer(Modifier.height(8.dp))
                RuleTextField(
                    value = newUrl,
                    placeholder = "https://registry.npmmirror.com/gkd-subscription/latest/files",
                    singleLine = true,
                    onValueChange = { newUrl = it },
                )
                Text(
                    text = "也可以直接用「导入文件」选择本地 .json5/.yaml 文件。",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.58f),
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = "填入 GKD 示例",
                        onClick = {
                            newName = "GKD 订阅"
                            newUrl = "https://registry.npmmirror.com/gkd-subscription/latest/files"
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                    TextButton(text = "取消", onClick = { showAddDialog = false })
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        text = "添加并更新",
                        onClick = {
                            val url = newUrl.trim()
                            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                Toast.makeText(context, "请输入 http(s) 订阅地址", Toast.LENGTH_SHORT).show()
                            } else {
                                val sub = SkipSubscription(
                                    id = UUID.randomUUID().toString(),
                                    name = newName.trim().ifBlank { "远程订阅" },
                                    url = url,
                                )
                                SubscriptionStore.update(sub)
                                showAddDialog = false
                                reload()
                                syncAll()
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }

    editing?.let { subscription ->
        var editName by remember(subscription.id) { mutableStateOf(subscription.name) }
        var editUrl by remember(subscription.id) { mutableStateOf(subscription.url) }
        var editContent by remember(subscription.id) { mutableStateOf(subscription.content) }
        var localOverride by remember(subscription.id) { mutableStateOf(subscription.localOverride) }
        WindowDialog(
            show = true,
            title = "编辑订阅",
            summary = "可以直接修改原始规则内容；开启「固定本地内容」后不再被远程更新覆盖",
            onDismissRequest = { editing = null },
        ) {
            Column {
                RuleTextField(
                    value = editName,
                    placeholder = "订阅名称",
                    singleLine = true,
                    onValueChange = { editName = it },
                )
                Spacer(Modifier.height(8.dp))
                RuleTextField(
                    value = editUrl,
                    placeholder = "远程地址，留空表示纯本地",
                    singleLine = true,
                    onValueChange = { editUrl = it },
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.secondaryVariant)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    BasicTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = MiuixTheme.colorScheme.onBackground,
                            fontSize = 12.sp,
                        ),
                    )
                }
                SwitchPreference(
                    title = "固定本地内容",
                    summary = "远程更新时不覆盖这段规则",
                    checked = localOverride,
                    onCheckedChange = { localOverride = it },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "取消", onClick = { editing = null })
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        text = "保存",
                        onClick = {
                            val contentChanged = editContent != subscription.content
                            val updated = subscription.copy(
                                name = editName.trim().ifBlank { subscription.name },
                                url = editUrl.trim(),
                                content = editContent,
                                // 手动改过原文就默认固定，否则下一次远程更新会把自定义内容覆盖掉。
                                localOverride = localOverride || contentChanged,
                            )
                            SubscriptionStore.update(updated)
                            editing = null
                            reload()
                            syncAll()
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }

    if (detailId != null) {
        WindowDialog(
            show = true,
            title = "订阅配置",
            summary = detailParsed?.note ?: if (detailLoading) "正在解析…" else "读取失败",
            onDismissRequest = {
                detailId = null
                detailParsed = null
            },
        ) {
            val parsed = detailParsed
            if (parsed == null) {
                Text(
                    text = if (detailLoading) "正在读取规则文件…" else "没有可显示的配置",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${parsed.appCount} 应用 · ${parsed.groupCount} 组 · ${parsed.ruleCount} 条",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                    )
                    Text(
                        text = parsed.format.uppercase(),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                if (parsed.appCount == 0) {
                    Text(
                        text = "文件解析成功，但没有找到可转换的基础规则。",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                            .padding(top = 8.dp),
                    ) {
                        items(parsed.apps, key = { it.packageName }) { app ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                            ) {
                                Text(
                                    text = app.appName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 10.sp,
                                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                                )
                                app.groups.forEach { group ->
                                    Text(
                                        text = "· ${group.name}  ${group.ruleCount} 条" +
                                            if (!group.enabled) "（订阅内默认关闭）" else "",
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
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
private fun SubscriptionCard(
    subscription: SkipSubscription,
    onToggle: (Boolean) -> Unit,
    onDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
            title = subscription.name,
            summary = buildString {
                append(
                    when {
                        subscription.builtIn -> "内置"
                        subscription.localOverride -> "本地"
                        subscription.url.isNotBlank() -> "远程"
                        else -> "本地文件"
                    }
                )
                if (subscription.ruleCount > 0) {
                    append(" · ${subscription.appCount} 应用 · ${subscription.ruleCount} 条规则")
                } else {
                    append(" · 尚未解析")
                }
                if (subscription.lastError.isNotBlank()) {
                    append("\n${subscription.lastError}")
                }
            },
            checked = subscription.enabled,
            onCheckedChange = onToggle,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                text = "查看配置",
                onClick = onDetail,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
            TextButton(
                text = "编辑",
                onClick = onEdit,
            )
            TextButton(
                text = "删除",
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun RuleTextField(
    value: String,
    placeholder: String,
    singleLine: Boolean,
    onValueChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (singleLine) 48.dp else 120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.secondaryVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            textStyle = TextStyle(
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 13.sp,
            ),
        )
    }
}
