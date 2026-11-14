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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Expressive 加载指示器（MaterialShapes 形变）。尺寸由 [modifier] 决定，默认 48dp。 */
@Composable
fun YunXLoading(modifier: Modifier = Modifier) {
    LoadingIndicator(modifier = modifier)
}

/**
 * Nieve 液态玻璃进度条。参考开源社区常见的 “aurora / shimmer progress bar” 做法：
 * 细轨道 + 冰蓝渐变填充 + 沿填充区持续流动的高光 + 柔边。
 * 不引入第三方依赖，避免再带一套组件主题。
 */
@Composable
fun YunXWavyProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color,
    trackColor: Color,
    waving: Boolean = true,
) {
    val target = progress().coerceIn(0f, 1f)
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f),
        label = "glassProgress",
    )
    val transition = rememberInfiniteTransition(label = "progressShimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
        ),
        label = "progressShimmerX",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .height(10.dp)
            .clip(shape)
            .background(trackColor)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
    ) {
        if (fraction > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(shape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                color.copy(alpha = 0.62f),
                                color,
                                Color.White.copy(alpha = 0.62f),
                                color,
                            )
                        )
                    )
            ) {
                if (waving) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val band = max(size.width * 0.32f, 24.dp.toPx())
                        val x = -band + shimmer * (size.width + band * 2)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.34f),
                                    Color.Transparent,
                                ),
                                startX = x,
                                endX = x + band,
                            ),
                        )
                        // 头部高光，像玻璃边缘的一次反光
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width - 1.dp.toPx(), size.height / 2),
                                radius = size.height * 1.15f,
                            ),
                            radius = size.height * 1.15f,
                            center = Offset(size.width - 1.dp.toPx(), size.height / 2),
                        )
                    }
                }
            }
        }
    }
}

/** 波浪进度条（不确定进度）：用于"正在加载但无进度"的场景，如 WebView 页面加载。 */
@Composable
fun YunXWavyLoading(modifier: Modifier = Modifier) {
    LinearWavyProgressIndicator(modifier = modifier)
}
