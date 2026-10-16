package net.paigu.chahua.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.AppSettings
import net.paigu.chahua.data.ThemeModeOption
import net.paigu.chahua.data.ThemeColorOption

/** 全局应用设置，供界面读取（字体大小、全部页等）。 */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

@Composable
fun ChahuaTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val settings by AppGraph.settings.settingsState.collectAsState(
        initial = AppGraph.settings.snapshot(),
    )
    val themeOption = ThemeColorOption.from(settings.themeColor)
    val darkTheme = when (ThemeModeOption.from(settings.themeMode)) {
        ThemeModeOption.DARK -> true
        ThemeModeOption.LIGHT -> false
        ThemeModeOption.SYSTEM -> isSystemInDarkTheme()
    }
    val customColor = parseCustomColor(settings.customThemeColor)
    val colorScheme = when {
        themeOption == ThemeColorOption.CUSTOM && customColor != null ->
            customColorScheme(customColor, darkTheme)
        themeOption == ThemeColorOption.SYSTEM && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> themeOption.darkScheme()
        else -> themeOption.lightScheme()
    }

    CompositionLocalProvider(LocalAppSettings provides settings) {
        // 窗口层也要跟着"应用内"主题走：XML 主题（values-night）和 windowBackground 都是
        // 按系统夜间模式选的，只在 Compose 里换色的话，未绘制区域会露出系统那套背景色
        // （宽屏右栏、页面切换瞬间），状态栏图标也会和实际背景撞色。
        val view = LocalView.current
        val activity = view.context.findActivity()
        LaunchedEffect(activity, darkTheme, colorScheme.background) {
            val window = activity?.window ?: return@LaunchedEffect
            window.setBackgroundDrawable(ColorDrawable(colorScheme.background.toArgb()))
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

/** 从任意 Context 往上找到宿主 Activity（Compose 里拿 window 用）。 */
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun parseCustomColor(hex: String): Color? {
    if (hex.isBlank()) return null
    return runCatching {
        Color(android.graphics.Color.parseColor("#${hex.removePrefix("#")}"))
    }.getOrNull()
}
