package net.paigu.chahua.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

private fun parseCustomColor(hex: String): Color? {
    if (hex.isBlank()) return null
    return runCatching {
        Color(android.graphics.Color.parseColor("#${hex.removePrefix("#")}"))
    }.getOrNull()
}
