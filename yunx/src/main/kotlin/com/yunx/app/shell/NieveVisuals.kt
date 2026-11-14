package com.yunx.app.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
                secondaryContainer = accent.copy(alpha = 0.16f),
                onSecondaryContainer = Color(0xFFF4F4F6),
                surfaceTint = accent,
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
                primaryContainer = accent.copy(alpha = 0.16f),
                onPrimaryContainer = Color(0xFF101014),
                secondaryContainer = accent.copy(alpha = 0.14f),
                onSecondaryContainer = Color(0xFF101014),
                surfaceTint = accent,
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

/** 液态玻璃卡片的底色：浅色下是半透白，深色下是极淡的白膜，不再用灰扑扑的 surfaceContainer。 */
@Composable
fun nieveGlassFill(): Color {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    return if (light) Color.White.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.07f)
}

/** 玻璃边缘描边：浅色用低透明黑，深色用低透明白，保证卡片轮廓清楚。 */
@Composable
fun nieveGlassBorder(): Color {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    return if (light) Color(0xFF101014).copy(alpha = 0.10f) else Color.White.copy(alpha = 0.14f)
}

/** 玻璃顶部高光：叠一层自上而下的白色渐变，模拟折射亮边。 */
@Composable
fun nieveGlassHighlight(): Brush {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    return Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (light) 0.35f else 0.10f),
            Color.Transparent
        )
    )
}

/** Nieve 通用：玻璃圆形图标按钮（顶栏收藏 / 排序 / 返回 / 新增）。 */
@Composable
fun NieveGlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(shape)
            .background(color = nieveGlassFill(), shape = shape)
            .background(nieveGlassHighlight(), shape)
            .border(1.dp, nieveGlassBorder(), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Nieve 二级页统一过渡：新页带轻微缩放入场，旧页缩退。
 * [depth] 用来判断前进/后退方向（数值变大 = 进入更深一层）。
 */
@Composable
fun <T> NievePageTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "nievePage",
    depth: (T) -> Int = { 0 },
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            val forward = depth(targetState) >= depth(initialState)
            val enterSlide = spring<IntOffset>(
                dampingRatio = 0.84f,
                stiffness = Spring.StiffnessMediumLow
            )
            val enterScale = spring<Float>(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow
            )
            (
                fadeIn(tween(190)) +
                    slideInHorizontally(enterSlide) { if (forward) it / 3 else -it / 3 } +
                    scaleIn(initialScale = 0.94f, animationSpec = enterScale)
                ).togetherWith(
                fadeOut(tween(130)) +
                    slideOutHorizontally(tween(210)) { if (forward) -it / 6 else it / 6 } +
                    scaleOut(targetScale = 0.97f, animationSpec = tween(210))
            )
        },
        label = label
    ) { content(it) }
}

/**
 * Nieve 通用液态玻璃弹窗：替换 M3 原生 AlertDialog。
 * 半透明玻璃面板 + 顶部高光 + 细描边，标题左对齐，底部按钮是胶囊。
 */
@Composable
fun NieveGlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val shape = RoundedCornerShape(28.dp)
        var appeared by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { appeared = true }
        AnimatedVisibility(
            visible = appeared,
            enter = fadeIn(tween(140)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { -it / 6 } +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(
                        dampingRatio = 0.80f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), shape)
                .background(nieveGlassHighlight(), shape)
                .border(1.dp, nieveGlassBorder(), shape)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                content = content
            )
            if (confirmText != null || dismissText != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissText != null) {
                        val dismissShape = RoundedCornerShape(50)
                        Box(
                            modifier = Modifier
                                .clip(dismissShape)
                                .clickable { onDismiss?.invoke() ?: onDismissRequest() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = dismissText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (confirmText != null) {
                        Spacer(Modifier.width(8.dp))
                        val confirmShape = RoundedCornerShape(50)
                        Box(
                            modifier = Modifier
                                .clip(confirmShape)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    confirmShape
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
                                    confirmShape
                                )
                                .clickable { onConfirm?.invoke() ?: onDismissRequest() }
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = confirmText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/** 玻璃弹窗里的单个可选行：选中时主题色淡染 + 描边，右侧可带勾选点。 */
@Composable
fun NieveDialogOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else Color.Transparent,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) trailing()
        else if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            )
        }
    }
}
