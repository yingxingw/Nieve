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

package com.yunx.app.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yunx.app.shell.nieveGlassBorder
import com.yunx.app.shell.nieveGlassFill
import com.yunx.app.shell.nieveGlassHighlight

/** 多选底部批量操作项 */
internal data class MultiSelectAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit
)

/**
 * 多选模式底部批量操作栏（云盘页/解析页共用）。
 *
 * 形态：悬浮的玻璃胶囊（不再用 M3 FloatingToolbar 的实底色 + 6dp 灰阴影），
 * 每个操作是一个圆形图标按钮，长按出 Tooltip 说明。
 * ★ 只放图标不放文字：文字会让胶囊条变长、且与图标叠在一起显得拥挤（操作含义由无障碍标签给出）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MultiSelectBar(
    count: Int,
    actions: List<MultiSelectAction>,
    busy: Boolean = false
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val shape = RoundedCornerShape(50)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .clip(shape)
                .background(color = nieveGlassFill(), shape = shape)
                .background(nieveGlassHighlight(), shape)
                .border(1.dp, nieveGlassBorder(), shape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                val enabled = count > 0 && !busy
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { Text(action.label) } },
                    state = rememberTooltipState()
                ) {
                    val tint = if (enabled) action.tint
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(tint.copy(alpha = if (enabled) 0.10f else 0.04f), CircleShape)
                            .clickable(
                                enabled = enabled,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = action.onClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            modifier = Modifier.size(22.dp),
                            tint = tint
                        )
                    }
                }
            }
        }
    }
}
