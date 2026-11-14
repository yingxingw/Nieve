package com.yunx.app.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yunx.app.ui.theme.effectsDefault
import com.yunx.app.ui.theme.effectsFast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    return if (light) {
        Color(0xFFF2F8FF).copy(alpha = 0.72f)
    } else {
        Color(0xFFBBD6F2).copy(alpha = 0.09f)
    }
}

/** 玻璃边缘描边：浅色用低透明黑，深色用低透明白，保证卡片轮廓清楚。 */
@Composable
fun nieveGlassBorder(): Color {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    return if (light) {
        Color(0xFF8FB8D8).copy(alpha = 0.42f)
    } else {
        Color(0xFF9CC8EE).copy(alpha = 0.22f)
    }
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

/**
 * 液态玻璃面板修饰符（卡片 / 输入框 / 行容器统一材质）：
 * 半透玻璃底 + 顶部高光 + 1dp 细描边，且**先裁圆角再画底**，圆角外不会漏出颜色。
 */
@Composable
fun Modifier.nieveGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(color = nieveGlassFill(), shape = shape)
    .background(nieveGlassHighlight(), shape)
    .border(width = 1.dp, color = nieveGlassBorder(), shape = shape)

/**
 * 玻璃胶囊按钮：替代 M3 的 Button / TextButton。
 *
 * - [primary] = true：主题色淡染 + 主题色描边（确认类动作）；
 *   false：纯玻璃底 + 中性描边（取消 / 次级动作）。
 * - 按下有轻微回弹（弹簧缩放），与整页的 Expressive 动效同源。
 */
@Composable
fun NieveGlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "nievePillPress",
    )
    val shape = RoundedCornerShape(50)
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .then(
                if (primary) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.16f else 0.07f), shape)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.40f else 0.16f), shape)
                } else {
                    Modifier
                        .background(nieveGlassFill(), shape)
                        .background(nieveGlassHighlight(), shape)
                        .border(1.dp, nieveGlassBorder(), shape)
                }
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}

/**
 * 玻璃输入框：替代 M3 OutlinedTextField 在解析/搜索场景里的白底描边表单。
 *
 * 用 [BasicTextField] 自绘是为了让输入区与页面其它玻璃卡片同材质（同一个圆角、同一层高光、
 * 同一根细描边），同时保留系统自带的选区、复制粘贴与输入法行为。
 */
@Composable
fun NieveGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    onClear: (() -> Unit)? = null,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = if (focused) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
    } else {
        nieveGlassBorder()
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interaction,
        decorationBox = { innerTextField ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(color = nieveGlassFill(), shape = shape)
                    .background(nieveGlassHighlight(), shape)
                    .border(width = 1.dp, color = borderColor, shape = shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                            )
                        }
                        innerTextField()
                    }
                    if (onClear != null && value.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                .clickable(onClick = onClear),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}

/**
 * 玻璃选择胶囊（分类 / 标签筛选）：未选中是中性玻璃，选中是主题色淡染 + 加重描边。
 * 与 NieveGlassPillButton 的区别：这是"状态"，不是"动作"，所以没有按压缩放。
 */
@Composable
fun NieveGlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), shape)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.44f),
                            shape
                        )
                } else {
                    Modifier
                        .background(nieveGlassFill(), shape)
                        .background(nieveGlassHighlight(), shape)
                        .border(1.dp, nieveGlassBorder(), shape)
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
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
 *
 * ★ 同层切换（depth 相等，例如解析页里换目录、换分享）只做淡入淡出：
 *   那属于「同一页换内容」，再叠一次横向滑动会像整页被推走，反而更乱。
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
            val fromDepth = depth(initialState)
            val toDepth = depth(targetState)
            if (fromDepth == toDepth) {
                fadeIn(effectsDefault()) togetherWith fadeOut(effectsFast())
            } else {
                val forward = toDepth >= fromDepth
                val deepForward = forward && toDepth >= 2
                val enterSlide = spring<IntOffset>(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow
                )
                val enterScale = spring<Float>(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow
                )
                (
                    fadeIn(tween(260)) +
                        slideInHorizontally(enterSlide) { if (forward) it else -it } +
                        (if (deepForward) {
                            slideInVertically(enterSlide) { -it / 2 }
                        } else {
                            EnterTransition.None
                        }) +
                        scaleIn(initialScale = 0.86f, animationSpec = enterScale)
                    ).togetherWith(
                    fadeOut(tween(160)) +
                        slideOutHorizontally(tween(260)) { if (forward) -it / 3 else it / 3 } +
                        (if (deepForward) {
                            slideOutVertically(tween(260)) { it / 6 }
                        } else {
                            ExitTransition.None
                        }) +
                        scaleOut(targetScale = 0.94f, animationSpec = tween(260))
                )
            }
        },
        label = label
    ) { content(it) }
}

/**
 * Nieve 通用液态玻璃弹窗：替换 M3 原生 AlertDialog。
 * 半透明玻璃面板 + 顶部高光 + 细描边，标题左对齐，底部按钮是胶囊。
 *
 * ★ 进出场共用一套「淡入 + 上移 + 缩放」：弹出时从下方浮起，关闭时先回落再真正消隐。
 *   点按钮 / 点遮罩 / 返回键三条路径都走 [dismiss]，所以观感一致，不会某一处"啪"地消失。
 */
@Composable
fun NieveGlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissible: Boolean = true,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var appeared by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    fun dismiss(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            appeared = false
            delay(180)
            action()
        }
    }
    Dialog(
        onDismissRequest = { if (dismissible) dismiss(onDismissRequest) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
        )
    ) {
        val shape = RoundedCornerShape(28.dp)
        AnimatedVisibility(
            visible = appeared,
            enter = fadeIn(tween(140)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { it / 7 } +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(
                        dampingRatio = 0.80f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            exit = fadeOut(tween(150)) +
                slideOutVertically(tween(180)) { it / 10 } +
                scaleOut(targetScale = 0.94f, animationSpec = tween(180))
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
                                .clickable { dismiss(onDismiss ?: onDismissRequest) }
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
                                    MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (confirmEnabled) 0.16f else 0.06f
                                    ),
                                    confirmShape
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (confirmEnabled) 0.40f else 0.14f
                                    ),
                                    confirmShape
                                )
                                .clickable(enabled = confirmEnabled) {
                                    dismiss(onConfirm ?: onDismissRequest)
                                }
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = confirmText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (confirmEnabled) 1f else 0.4f
                                ),
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

/** 居中错误弹窗：登录失败、校验失败等必须让用户看到的提示，避免被底栏/导航条遮挡。 */
@Composable
fun NieveErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    title: String = "登录失败",
) {
    NieveGlassDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = "知道了",
        onConfirm = onDismiss,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
