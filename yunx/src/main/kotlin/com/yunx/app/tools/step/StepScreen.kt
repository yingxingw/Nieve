package com.yunx.app.tools.step

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yunx.app.shell.NieveGlassDialog
import com.yunx.app.shell.NieveGlassTextField
import com.yunx.app.tools.ToolActionButton
import com.yunx.app.tools.ToolsCard
import com.yunx.app.tools.ToolsPage
import com.yunx.app.tools.ToolsSectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StepScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { ZeppClient(context) }
    val accounts = remember { mutableStateListOf<ZeppAccount>().apply { addAll(ZeppStore.load(context)) } }

    var selectedId by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var remark by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var stepText by rememberSaveable { mutableStateOf((accounts.firstOrNull()?.steps ?: 8000).toString()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<ZeppAccount?>(null) }

    fun persist() = ZeppStore.save(context, accounts)
    fun loadEditor(account: ZeppAccount) {
        selectedId = account.id
        remark = account.remark
        email = account.email
        password = account.password
        stepText = account.steps.toString()
        message = ""
    }
    fun updateAccount(updated: ZeppAccount) {
        val index = accounts.indexOfFirst { it.id == updated.id }
        if (index >= 0) accounts[index] = updated
    }

    ToolsPage(title = "修改步数", onBack = onExit) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                ToolsCard {
                    Text(
                        "Zepp Life 多账号",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "使用邮箱账号登录，登录后自动获取或绑定设备。每次同步会重新登录获取有效 Token。步数上限 98000。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    NieveGlassTextField(
                        value = remark,
                        onValueChange = { remark = it },
                        placeholder = "账号备注（选填，如：大号 / 家人）",
                        singleLine = true,
                        onClear = { remark = "" },
                    )
                    Spacer(Modifier.height(8.dp))
                    NieveGlassTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "Zepp 账号（邮箱）",
                        singleLine = true,
                        onClear = { email = "" },
                    )
                    Spacer(Modifier.height(8.dp))
                    NieveGlassTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Zepp 密码",
                        singleLine = true,
                        onClear = { password = "" },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToolActionButton(
                            text = if (busy) "登录中…" else "登录并保存",
                            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                            primary = true,
                            onClick = {
                                busy = true
                                message = ""
                                scope.launch {
                                    val result = runCatching { client.login(email, password) }
                                    busy = false
                                    result.onSuccess { account ->
                                        val old = accounts.firstOrNull { it.email.equals(email.trim(), true) }
                                        val saved = account.copy(
                                            id = old?.id ?: account.id,
                                            remark = remark.ifBlank { old?.remark ?: "" },
                                            steps = old?.steps ?: 8000,
                                        )
                                        val index = accounts.indexOfFirst { it.id == saved.id }
                                        if (index >= 0) accounts[index] = saved else accounts.add(saved)
                                        selectedId = saved.id
                                        stepText = saved.steps.toString()
                                        persist()
                                        message = "登录成功，已保存账号"
                                    }.onFailure { message = it.message ?: "登录失败" }
                                }
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        ToolActionButton(
                            text = "清空新建",
                            enabled = !busy,
                            onClick = {
                                selectedId = ""
                                remark = ""
                                email = ""
                                password = ""
                                stepText = "8000"
                                message = ""
                            },
                        )
                    }
                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (message.contains("成功")) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item { ToolsSectionTitle("账号列表（${accounts.size}）") }

            if (accounts.isEmpty()) {
                item {
                    ToolsCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "暂无账号，先在上方登录一个 Zepp 邮箱账号",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(accounts, key = { it.id }) { account ->
                    val selected = account.id == selectedId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                                RoundedCornerShape(16.dp),
                            )
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(16.dp),
                            )
                            .clickable { loadEditor(account) }
                            .padding(12.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) {
                                        Box(
                                            Modifier.size(7.dp)
                                                .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        account.remark.ifBlank { account.email },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    Text(
                                        "${account.email} · ${account.steps} 步",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    if (account.lastResult.isNotBlank()) {
                                        Text(
                                            account.lastResult,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (account.lastSuccess) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error,
                                            maxLines = 2,
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = "编辑",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp).clickable { loadEditor(account) },
                                )
                                Spacer(Modifier.width(14.dp))
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp).clickable { confirmDelete = account },
                                )
                            }
                        }
                    }
                }
            }

            item { ToolsSectionTitle("同步步数") }

            item {
                ToolsCard {
                    NieveGlassTextField(
                        value = stepText,
                        onValueChange = { input ->
                            if (input.length <= 5 && input.all { it.isDigit() }) stepText = input
                        },
                        placeholder = "输入步数，最大 98000",
                        singleLine = true,
                        onClear = { stepText = "" },
                    )
                    Spacer(Modifier.height(10.dp))
                    val current = accounts.firstOrNull { it.id == selectedId }
                    Row {
                        ToolActionButton(
                            text = if (busy) "同步中…" else "提交当前账号",
                            enabled = !busy && current != null && stepText.toIntOrNull() != null,
                            primary = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val steps = stepText.toIntOrNull() ?: return@ToolActionButton
                                val account = accounts.firstOrNull { it.id == selectedId } ?: return@ToolActionButton
                                busy = true
                                message = ""
                                scope.launch {
                                    val result = runCatching { client.applySteps(account, steps) }
                                    busy = false
                                    result.onSuccess { updated ->
                                        val saved = updated.copy(
                                            steps = steps,
                                            lastResult = "同步成功，今日 $steps 步",
                                            lastSuccess = true,
                                        )
                                        updateAccount(saved)
                                        persist()
                                        message = "同步成功"
                                    }.onFailure {
                                        val failed = account.copy(
                                            steps = steps,
                                            lastResult = "失败：" + (it.message ?: "未知错误"),
                                            lastSuccess = false,
                                        )
                                        updateAccount(failed)
                                        persist()
                                        message = failed.lastResult
                                    }
                                }
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        ToolActionButton(
                            text = if (busy) "同步中…" else "一键全部同步",
                            enabled = !busy && accounts.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                busy = true
                                message = ""
                                scope.launch {
                                    var ok = 0
                                    var fail = 0
                                    accounts.forEach { account ->
                                        val steps = account.steps
                                        val result = runCatching { client.applySteps(account, steps) }
                                        result.onSuccess { updated ->
                                            updateAccount(
                                                updated.copy(
                                                    steps = steps,
                                                    lastResult = "同步成功，今日 $steps 步",
                                                    lastSuccess = true,
                                                ),
                                            )
                                            ok++
                                        }.onFailure {
                                            updateAccount(
                                                account.copy(
                                                    lastResult = "失败：" + (it.message ?: "未知错误"),
                                                    lastSuccess = false,
                                                ),
                                            )
                                            fail++
                                        }
                                        persist()
                                    }
                                    busy = false
                                    message = "全部同步完成：成功 $ok，失败 $fail"
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "注意：步数会覆盖 Zepp 当日汇总数据；账号密码仅保存在本机应用私有目录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val deleting = confirmDelete
    if (deleting != null) {
        NieveGlassDialog(
            onDismissRequest = { confirmDelete = null },
            title = "删除账号",
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {
                accounts.removeAll { it.id == deleting.id }
                if (selectedId == deleting.id) {
                    val next = accounts.firstOrNull()
                    if (next != null) loadEditor(next) else {
                        selectedId = ""
                        remark = ""
                        email = ""
                        password = ""
                        stepText = "8000"
                    }
                }
                persist()
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        ) {
            Text(
                "确定删除 ${deleting.remark.ifBlank { deleting.email }}？",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
