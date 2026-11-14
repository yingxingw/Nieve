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

package com.yunx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yunx.app.shell.nieveGlassBorder
import com.yunx.app.shell.nieveGlassFill
import com.yunx.app.shell.nieveGlassHighlight
import com.yunx.app.ui.rememberGlobalSnackbarHostState

internal val LocalFileOperationBusy = staticCompositionLocalOf { false }

/** 在状态迁移前阻止隐藏，避免只拦截回调后面板已经消失。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberOperationSheetState(busy: Boolean): SheetState {
    val currentBusy by rememberUpdatedState(busy)
    return rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !currentBusy }
    )
}

/**
 * 文件操作弹层外壳（网盘页 / 解析页共用）。
 *
 * 容器改成透明 + 内部玻璃面板：从「一整块不透明 M3 surface 升上来」变成
 * 「一块悬浮玻璃卡片升上来」，与账号详情弹层、玻璃弹窗同一材质；
 * 圆角只给上方两角，底部留 10dp 间距，弹层不会贴着系统手势条。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OperationSheet(
    busy: Boolean,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberOperationSheetState(busy),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { if (!busy) onDismiss() },
        sheetGesturesEnabled = !busy,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !busy,
            shouldDismissOnClickOutside = !busy
        ),
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
        }
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
        ) {
            CompositionLocalProvider(LocalFileOperationBusy provides busy) {
                Column(Modifier.weight(1f, fill = false)) { content() }
                SnackbarHost(rememberGlobalSnackbarHostState())
            }
        }
    }
}
