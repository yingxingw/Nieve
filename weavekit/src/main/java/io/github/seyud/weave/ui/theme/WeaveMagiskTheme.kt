package io.github.seyud.weave.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

val LocalEnableBlur = staticCompositionLocalOf { false }
val LocalEnableFloatingBottomBar = staticCompositionLocalOf { false }
val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { false }
val LocalHomeLayoutMode = staticCompositionLocalOf { 0 }
val LocalEnableSwipeDismiss = staticCompositionLocalOf { true }
val LocalPagerGestureMode = staticCompositionLocalOf { 1 }
val LocalIsMonetTheme = staticCompositionLocalOf { false }

/**
 * WeaveMagisk 主题包装函数
 * 根据 colorMode 和 keyColor 创建 ThemeController 并应用 MiuixTheme
 *
 * colorMode 取值:
 * 0 = 跟随系统
 * 1 = 亮色
 * 2 = 暗色
 * 3 = Monet 跟随系统
 * 4 = Monet 亮色
 * 5 = Monet 暗色
 *
 * @param colorMode 颜色模式，0-5
 * @param keyColor Monet 种子色，null 表示使用系统壁纸色
 * @param content 子组件
 */
@Composable
fun WeaveMagiskTheme(
    colorMode: Int = 0,
    keyColor: Color? = null,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isMonetTheme = colorMode in 3..5
    val isDark = when (colorMode) {
        1 -> false
        2 -> true
        4 -> false
        5 -> true
        else -> isSystemDark
    }
    val controller = when (colorMode) {
        1 -> ThemeController(ColorSchemeMode.Light)
        2 -> ThemeController(ColorSchemeMode.Dark)
        3 -> ThemeController(
            ColorSchemeMode.MonetSystem,
            keyColor = keyColor,
            isDark = isSystemDark
        )
        4 -> ThemeController(
            ColorSchemeMode.MonetLight,
            keyColor = keyColor,
        )
        5 -> ThemeController(
            ColorSchemeMode.MonetDark,
            keyColor = keyColor,
        )
        else -> ThemeController(ColorSchemeMode.System)
    }
    CompositionLocalProvider(LocalIsMonetTheme provides isMonetTheme) {
        val rawColors = controller.currentColors()
        // Nieve 深色底：统一压成近纯黑，只保留主题的主色/强调色。
        // 不再反射 Android 框架的动态色：ColorFramework 在部分 ROM 上返回透明/错误色，
        // 这正是此前“Monet 深色”页面出现大色块异常的根因。
        val colors = if (isDark) {
            rawColors.copy(
                background = Color(0xFF000000),
                surface = Color(0xFF050506),
                surfaceVariant = Color(0xFF121216),
                surfaceContainer = Color(0xFF17171B).copy(alpha = 0.78f),
                // 弹窗类容器保持不透明，避免透出下层内容、影响可读性。
                surfaceContainerHigh = Color(0xFF17171C),
                surfaceContainerHighest = Color(0xFF1E1E24),
                onBackground = Color(0xFFF4F4F6),
                onSurface = Color(0xFFF4F4F6),
                onSurfaceContainer = Color(0xFFF4F4F6),
                onSurfaceContainerHigh = Color(0xFFE8E8EC),
                onSurfaceContainerHighest = Color(0xFFE8E8EC),
                // 深色模式下统一使用 Nieve 冰蓝主色。Monet 的深色种子色在部分 ROM /
                // 壁纸上会算出一组几乎不可见的暗色，导致开关、链接、选中态“消失”。
                primary = Color(0xFF8ED8FF),
                onPrimary = Color(0xFF00131B),
                primaryVariant = Color(0xFF5AAFD8),
                onPrimaryVariant = Color(0xFF00131B),
                primaryContainer = Color(0xFF13323F),
                onPrimaryContainer = Color(0xFFD8F2FF),
                secondary = Color(0xFFA9C8D8),
                onSecondary = Color(0xFF06171F),
                secondaryVariant = Color(0xFF7FA4B5),
                onSecondaryVariant = Color(0xFF06171F),
                secondaryContainer = Color(0xFF16262F),
                onSecondaryContainer = Color(0xFFD8ECF5),
                tertiaryContainer = Color(0xFF1A2A33),
                onTertiaryContainer = Color(0xFFD8ECF5),
                onBackgroundVariant = Color(0xFFB8C4CC),
                onSurfaceSecondary = Color(0xFFB8C4CC),
                onSurfaceVariantSummary = Color(0xFF98A6AE),
                onSurfaceVariantActions = Color(0xFFC8D4DC),
                disabledOnSurface = Color(0xFF6E7A82),
                onSurfaceContainerVariant = Color(0xFFB8C4CC),
                outline = Color(0xFF34383D),
                dividerLine = Color(0xFF2A2E33),
            )
        } else {
            rawColors
        }
        MiuixTheme(
            colors = colors,
            content = content
        )
    }
}
