/*
 * YunX (云析) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.yunx.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yunx.app.data.db.MultiAccountEntity
import com.yunx.app.data.db.XunleiAccountEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 已登录迅雷账号详情：统一的液态玻璃弹层 + 账号概览 + 登录凭据 + 多账号管理。
 */
@Composable
fun XunleiAccountSheet(
    account: XunleiAccountEntity,
    accounts: List<MultiAccountEntity>,
    onSetPrimary: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    val loginTime = remember(account.updatedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(account.updatedAt))
    }
    val displayName = account.remark.ifBlank { account.nickname }.ifBlank { "迅雷账号" }
    val authLabel = if (account.authType.isBlank()) "App 授权" else "网页授权"

    NieveAccountSheet(onDismiss = onDismiss) {
        NieveAccountHeader(
            nickname = displayName,
            platformLabel = "迅雷云盘 · 已登录",
            badge = if (account.isPrimary) "主账号" else null,
        )
        Spacer(Modifier.height(18.dp))
        NieveAccountSectionTitle("账号信息")
        NieveAccountInfoCard {
            NieveAccountInfoRow("登录时间", loginTime)
            Spacer(Modifier.height(10.dp))
            NieveAccountInfoRow("授权方式", authLabel)
            Spacer(Modifier.height(10.dp))
            NieveAccountInfoRow("设备 ID", account.deviceId.ifBlank { "-" }, monospace = true)
            Spacer(Modifier.height(10.dp))
            NieveAccountInfoRow("账号标识", account.id, monospace = true)
        }
        Spacer(Modifier.height(18.dp))
        NieveAccountSectionTitle("登录凭据", trailing = "脱敏展示")
        NieveAccountInfoCard {
            NieveAccountInfoRow("Access", maskToken(account.accessToken), monospace = true)
            Spacer(Modifier.height(10.dp))
            NieveAccountInfoRow("Refresh", maskToken(account.refreshToken), monospace = true)
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

/** Token 只展示首尾，避免详情页把完整凭据铺在屏幕上。 */
private fun maskToken(raw: String): String {
    if (raw.isBlank()) return "-"
    if (raw.length <= 12) return raw.take(4) + "…"
    return raw.take(6) + "…" + raw.takeLast(4)
}
