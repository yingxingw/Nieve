/*
 * Nieve - account detail sheet UI kit.
 * 统一的账号详情弹层：液态玻璃面板、顶部账号卡、信息行与多账号列表共用一套材质。
 */

package com.yunx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.data.db.MultiAccountEntity
import com.yunx.app.shell.nieveGlassBorder
import com.yunx.app.shell.nieveGlassFill
import com.yunx.app.shell.nieveGlassHighlight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 所有账号详情弹层共用的玻璃外壳。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NieveAccountSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.42f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                        RoundedCornerShape(50)
                    )
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(bottom = 10.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shape)
                .background(nieveGlassFill(), shape)
                .background(nieveGlassHighlight(), shape)
                .border(1.dp, nieveGlassBorder(), shape)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            content = content,
        )
    }
}

/** 顶部账号信息：大头像 + 昵称 + 平台/登录态 + 可选徽标。 */
@Composable
internal fun NieveAccountHeader(
    nickname: String,
    platformLabel: String,
    badge: String? = null,
    avatarText: String = nickname.take(1).ifBlank { "N" },
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    CircleShape
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = avatarText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nickname.ifBlank { "未命名账号" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = platformLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(50)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/** 小节标题：例如「账号信息」「多账号」。 */
@Composable
internal fun NieveAccountSectionTitle(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** 信息行：左标签、右值，值可等宽（用于 Cookie / Token / 设备号）。 */
@Composable
internal fun NieveAccountInfoRow(
    label: String,
    value: String,
    monospace: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value.ifBlank { "-" },
            style = if (monospace) {
                MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 信息卡：玻璃底、细描边，内部用 [content] 放若干信息行。 */
@Composable
internal fun NieveAccountInfoCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(nieveGlassFill(), shape)
            .background(nieveGlassHighlight(), shape)
            .border(1.dp, nieveGlassBorder(), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

/**
 * Cookie / Token 类账号的通用详情弹层：概览 + 凭据（可展开复制）+ 多账号管理。
 * 夸克 / UC / 百度 / 139 / 115 / 蓝奏 / 光鸭 / 视频平台共用这一套，避免每个平台各写一版旧卡片。
 */
@Composable
internal fun NieveCookieAccountSheet(
    platformLabel: String,
    nickname: String,
    isPrimary: Boolean,
    updatedAt: Long,
    cookie: String,
    credentialLabel: String = "Cookie",
    extraRows: List<Pair<String, String>> = emptyList(),
    accounts: List<MultiAccountEntity>,
    onSetPrimary: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showFullCookie by rememberSaveable { mutableStateOf(false) }
    val previewLimit = 220
    val truncated = cookie.length > previewLimit
    val displayCookie = if (showFullCookie || !truncated) cookie else cookie.take(previewLimit) + "…"
    val loginTime = remember(updatedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(updatedAt))
    }

    NieveAccountSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            NieveAccountHeader(
                nickname = nickname.ifBlank { "未命名账号" },
                platformLabel = "$platformLabel · 已登录",
                badge = if (isPrimary) "主账号" else null,
            )
            Spacer(Modifier.height(18.dp))
            NieveAccountSectionTitle("账号信息")
            NieveAccountInfoCard {
                NieveAccountInfoRow("登录时间", loginTime)
                extraRows.forEach { (label, value) ->
                    Spacer(Modifier.height(10.dp))
                    NieveAccountInfoRow(label, value, monospace = true)
                }
            }
            Spacer(Modifier.height(18.dp))
            NieveAccountSectionTitle("登录凭据", trailing = if (truncated) "可展开" else "已脱敏")
            NieveAccountInfoCard {
                Text(
                    text = credentialLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = displayCookie.ifBlank { "-" },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (truncated) {
                        TextButton(onClick = { showFullCookie = !showFullCookie }) {
                            Text(if (showFullCookie) "收起" else "展开全部")
                        }
                    }
                    TextButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "nieve_account",
                                    cookie
                                )
                            )
                            android.widget.Toast.makeText(
                                context, "已复制 $credentialLabel", android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text("复制")
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            AccountListSection(
                accounts = accounts,
                onSetPrimary = onSetPrimary,
                onRename = onRename,
                onRemove = onRemove,
                onAddAccount = onAddAccount,
                onEmpty = onDismiss,
            )
        }
    }
}
