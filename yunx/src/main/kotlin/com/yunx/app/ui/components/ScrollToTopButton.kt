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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunx.app.R
import com.yunx.app.shell.nieveGlassBorder
import com.yunx.app.shell.nieveGlassFill
import com.yunx.app.shell.nieveGlassHighlight
import com.yunx.app.ui.theme.effectsDefault
import com.yunx.app.ui.theme.effectsFast
import com.yunx.app.ui.theme.spatialDefault
import com.yunx.app.ui.theme.spatialFast
import kotlinx.coroutines.launch

/**
 * 返回顶部按钮（Nieve 玻璃形态）：
 * - 列表上滑离开顶部后，右下角**弹**出来（淡入 + 弹簧过冲缩放），回到顶部后缩回；
 * - 圆形玻璃底 + 1dp 细描边 + 顶部高光，与页面其它玻璃卡片同一材质；
 * - 箭头用实心 ArrowUpward 并**几何居中**（不再像旧版那样靠 padding 微调、看着偏下）；
 * - 按下有轻微回弹，松手滚动回第一项。
 *
 * ★ 旧实现是 M3 的 SmallFloatingActionButton：默认带 6dp 灰阴影 + secondaryContainer 实底色，
 *   落在纯黑/玻璃页面上会变成一块"浮起来的灰圆"，图标又是空心折角箭头，视觉上像错位——
 *   这正是之前右下角看着乱的原因。这里改成无阴影的玻璃圆钮，视觉重心与卡片一致。
 *
 * 放置位置由调用方通过 modifier 指定（如 Box.align(BottomEnd)）。
 */
@Composable
internal fun ScrollToTopButton(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // 上滑离开顶部（首项不是 0，或首项有偏移）即显示
    val showButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "scrollTopPress"
    )
    AnimatedVisibility(
        visible = showButton,
        enter = fadeIn(effectsDefault()) +
            scaleIn(
                animationSpec = spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow),
                initialScale = 0.6f
            ),
        exit = fadeOut(effectsFast()) +
            scaleOut(spatialFast(), targetScale = 0.7f),
        modifier = modifier
    ) {
        val shape = CircleShape
        Box(
            modifier = Modifier
                .scale(pressScale)
                .size(46.dp)
                .clip(shape)
                .background(color = nieveGlassFill(), shape = shape)
                .background(nieveGlassHighlight(), shape)
                .border(width = 1.dp, color = nieveGlassBorder(), shape = shape)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(bounded = true),
                    onClick = { scope.launch { listState.animateScrollToItem(0) } }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(R.string.content_desc_scroll_to_top),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
