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
import com.yunx.app.data.db.C139AccountEntity
import com.yunx.app.data.db.MultiAccountEntity

/** 已登录 139 云盘账号详情：统一的玻璃弹层。 */
@Composable
fun C139AccountSheet(
    account: C139AccountEntity,
    accounts: List<MultiAccountEntity>,
    onSetPrimary: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    NieveCookieAccountSheet(
        platformLabel = "中国移动云盘",
        nickname = account.remark.ifBlank { account.nickname },
        isPrimary = account.isPrimary,
        updatedAt = account.updatedAt,
        cookie = account.cookie,
        credentialLabel = "Cookie / Authorization",
        extraRows = if (account.authorization.isBlank()) {
            emptyList()
        } else {
            listOf("Authorization" to account.authorization.take(24) + "…")
        },
        accounts = accounts,
        onSetPrimary = onSetPrimary,
        onRename = onRename,
        onRemove = onRemove,
        onAddAccount = onAddAccount,
        onDismiss = onDismiss,
    )
}
