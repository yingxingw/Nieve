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
import com.yunx.app.data.db.MultiAccountEntity
import com.yunx.app.data.db.Pan115AccountEntity

/** 已登录 115 账号详情：统一的玻璃弹层。 */
@Composable
fun Pan115AccountSheet(
    account: Pan115AccountEntity,
    accounts: List<MultiAccountEntity>,
    onSetPrimary: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    NieveCookieAccountSheet(
        platformLabel = "115 生活",
        nickname = account.remark.ifBlank { account.nickname },
        isPrimary = account.isPrimary,
        updatedAt = account.updatedAt,
        cookie = account.cookie,
        accounts = accounts,
        onSetPrimary = onSetPrimary,
        onRename = onRename,
        onRemove = onRemove,
        onAddAccount = onAddAccount,
        onDismiss = onDismiss,
    )
}
