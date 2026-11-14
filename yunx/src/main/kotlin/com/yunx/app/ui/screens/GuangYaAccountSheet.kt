/*
 * YunX (云析) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.yunx.app.ui.screens

import androidx.compose.runtime.Composable
import com.yunx.app.data.db.GuangYaAccountEntity
import com.yunx.app.data.db.MultiAccountEntity

/** 已登录光鸭云盘账号详情。 */
@Composable
fun GuangYaAccountSheet(
    account: GuangYaAccountEntity,
    accounts: List<MultiAccountEntity>,
    onSetPrimary: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    NieveCookieAccountSheet(
        platformLabel = "光鸭云盘",
        nickname = account.remark.ifBlank { account.nickname },
        isPrimary = account.isPrimary,
        updatedAt = account.updatedAt,
        cookie = account.accessToken,
        credentialLabel = "Access / Refresh Token",
        extraRows = listOfNotNull(
            account.account.takeIf { it.isNotBlank() }?.let { "登录账号" to it },
            account.deviceId.takeIf { it.isNotBlank() }?.let { "设备 ID" to it },
            account.deviceSign.takeIf { it.isNotBlank() }?.let { "设备签名" to it.take(18) + "…" },
        ),
        accounts = accounts,
        onSetPrimary = onSetPrimary,
        onRename = onRename,
        onRemove = onRemove,
        onAddAccount = onAddAccount,
        onDismiss = onDismiss,
    )
}
