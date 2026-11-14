package com.yunx.app.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Nieve 的 Material3 配色：深色模式统一为纯黑底，浅色模式保持干净的冷白，
 * 只把主色交给 Miuix/主题模式。解析、账号、下载、设置里的旧 M3 组件因此不会再
 * 从系统壁纸动态色里取出粉色/紫色大色块。
 */
@Composable
fun NieveMaterialTheme(
    dark: Boolean,
    accent: Color,
    content: @Composable () -> Unit,
) {
    val onAccent = if (accent.luminance() > 0.55f) Color.Black else Color.White
    val scheme = remember(dark, accent) {
        if (dark) {
            darkColorScheme(
                primary = accent,
                onPrimary = onAccent,
                primaryContainer = accent.copy(alpha = 0.22f),
                onPrimaryContainer = Color(0xFFF4F4F6),
                secondary = accent.copy(alpha = 0.82f),
                onSecondary = onAccent,
                secondaryContainer = Color(0xFF1A1A20),
                onSecondaryContainer = Color(0xFFE8E8EC),
                background = Color.Black,
                onBackground = Color(0xFFF4F4F6),
                surface = Color.Black,
                onSurface = Color(0xFFF4F4F6),
                surfaceVariant = Color(0xFF121216),
                onSurfaceVariant = Color(0xFFB8B8C2),
                surfaceContainerLowest = Color(0xFF050506),
                surfaceContainerLow = Color(0xFF0C0C0F).copy(alpha = 0.58f),
                surfaceContainer = Color(0xFF121216).copy(alpha = 0.66f),
                // 高/最高层容器保持不透明：AlertDialog / ModalBottomSheet 等弹窗用这两层做底色，
                // 之前半透明会让 WebView 透过弹窗“抽搐”、文字可读性差。
                surfaceContainerHigh = Color(0xFF15151A),
                surfaceContainerHighest = Color(0xFF1B1B21),
                outline = Color(0xFF4A4A55),
                outlineVariant = Color(0xFF2A2A32),
            )
        } else {
            lightColorScheme(
                primary = accent,
                onPrimary = onAccent,
                background = Color(0xFFF6F7FA),
                onBackground = Color(0xFF101014),
                surface = Color(0xFFF6F7FA),
                onSurface = Color(0xFF101014),
                surfaceVariant = Color(0xFFE8E9EF),
                onSurfaceVariant = Color(0xFF4A4A55),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color(0xFFF0F1F5),
                surfaceContainer = Color(0xFFEAEBF1),
                surfaceContainerHigh = Color(0xFFE2E3EA),
                surfaceContainerHighest = Color(0xFFD8D9E1),
            )
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** 壳层环境光：深色=纯黑底+极淡冰蓝；浅色=冷白底+淡蓝。给液态玻璃提供可折射的层次。 */
@Composable
fun NieveAmbientBackground(dark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (dark) {
            drawRect(Color.Black)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF12324A).copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.12f),
                    radius = size.width * 0.85f,
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * 0.88f, size.height * 0.12f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0E2A3A).copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.08f, size.height * 0.92f),
                    radius = size.width * 0.75f,
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.08f, size.height * 0.92f),
            )
            // 底部中央的微光：让常驻底栏背后始终有一点可折射的冷色，避免玻璃落在纯黑上看成黑块。
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF153B4D).copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.99f),
                    radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.5f, size.height * 0.99f),
            )
        } else {
            drawRect(Color(0xFFF4F6FA))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFD6EAF5).copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.1f),
                    radius = size.width * 0.9f,
                ),
                radius = size.width * 0.9f,
                center = Offset(size.width * 0.9f, size.height * 0.1f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFDDEBF3).copy(alpha = 0.4f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.99f),
                    radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.5f, size.height * 0.99f),
            )
        }
    }
}

@Composable
fun isNieveDark(colorMode: Int): Boolean = when (colorMode) {
    1, 4 -> false
    2, 5 -> true
    else -> isSystemInDarkTheme()
}
