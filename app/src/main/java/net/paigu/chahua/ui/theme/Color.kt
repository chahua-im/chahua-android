package net.paigu.chahua.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import net.paigu.chahua.data.ThemeColorOption

// 自定义兜底配色（Android 12 以下或系统未启用动态取色时使用）
val Green80 = Color(0xFFB9F6CA)
val GreenGrey80 = Color(0xFFC5E1A5)
val Teal80 = Color(0xFF80DEEA)

val Green40 = Color(0xFF2E7D32)
val GreenGrey40 = Color(0xFF558B2F)
val Teal40 = Color(0xFF00897B)

val ChatBubbleMineLight = Color(0xFFD7F0DC)
val ChatBubbleMineDark = Color(0xFF1B3A22)
val ChatBubbleOtherLight = Color(0xFFF0F1F3)
val ChatBubbleOtherDark = Color(0xFF2A2B2E)

internal val GreenLight = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0B3D0F),
    secondary = GreenGrey40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEDC8),
    onSecondaryContainer = Color(0xFF1B2E0A),
    tertiary = Teal40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB2DFDB),
    onTertiaryContainer = Color(0xFF00332E),
)

internal val GreenDark = darkColorScheme(
    primary = Green80,
    onPrimary = Color(0xFF0B3D0F),
    primaryContainer = ChatBubbleMineDark,
    onPrimaryContainer = Color(0xFFB7E4C7),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF1B2E0A),
    secondaryContainer = Color(0xFF2E4A1F),
    onSecondaryContainer = Color(0xFFDCEDC8),
    tertiary = Color(0xFF80CBC4),
    onTertiary = Color(0xFF00332E),
    tertiaryContainer = Color(0xFF00695C),
    onTertiaryContainer = Color(0xFFB2DFDB),
)

private val BlueLight = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D3C73),
    secondary = Color(0xFF546E7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFD8DC),
    onSecondaryContainer = Color(0xFF263238),
    tertiary = Color(0xFF00838F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB2EBF2),
    onTertiaryContainer = Color(0xFF00363D),
)

private val BlueDark = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D3C73),
    primaryContainer = Color(0xFF173A5E),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF263238),
    secondaryContainer = Color(0xFF37474F),
    onSecondaryContainer = Color(0xFFCFD8DC),
    tertiary = Color(0xFF80DEEA),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF006064),
    onTertiaryContainer = Color(0xFFB2EBF2),
)

private val PurpleLight = lightColorScheme(
    primary = Color(0xFF6A1B9A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1BEE7),
    onPrimaryContainer = Color(0xFF38006B),
    secondary = Color(0xFF7B1FA2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E5F5),
    onSecondaryContainer = Color(0xFF4A148C),
    tertiary = Color(0xFFAD1457),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8BBD0),
    onTertiaryContainer = Color(0xFF4A0020),
)

private val PurpleDark = darkColorScheme(
    primary = Color(0xFFCE93D8),
    onPrimary = Color(0xFF4A148C),
    primaryContainer = Color(0xFF4A148C),
    onPrimaryContainer = Color(0xFFE1BEE7),
    secondary = Color(0xFFBA68C8),
    onSecondary = Color(0xFF4A148C),
    secondaryContainer = Color(0xFF6A1B9A),
    onSecondaryContainer = Color(0xFFF3E5F5),
    tertiary = Color(0xFFF06292),
    onTertiary = Color(0xFF4A0020),
    tertiaryContainer = Color(0xFF880E4F),
    onTertiaryContainer = Color(0xFFF8BBD0),
)

private val OrangeLight = lightColorScheme(
    primary = Color(0xFFE65100),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFF7A3B00),
    secondary = Color(0xFFF57C00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFECB3),
    onSecondaryContainer = Color(0xFF5D2F00),
    tertiary = Color(0xFFC62828),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFCDD2),
    onTertiaryContainer = Color(0xFF7F0000),
)

private val OrangeDark = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF5D2F00),
    primaryContainer = Color(0xFF663D00),
    onPrimaryContainer = Color(0xFFFFE0B2),
    secondary = Color(0xFFFFA726),
    onSecondary = Color(0xFF5D2F00),
    secondaryContainer = Color(0xFF6D3A00),
    onSecondaryContainer = Color(0xFFFFECB3),
    tertiary = Color(0xFFEF9A9A),
    onTertiary = Color(0xFF7F0000),
    tertiaryContainer = Color(0xFF8E0000),
    onTertiaryContainer = Color(0xFFFFCDD2),
)

private val PinkLight = lightColorScheme(
    primary = Color(0xFFC2185B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF8BBD0),
    onPrimaryContainer = Color(0xFF7A0030),
    secondary = Color(0xFFD81B60),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE4EC),
    onSecondaryContainer = Color(0xFF560027),
    tertiary = Color(0xFF880E4F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF48FB1),
    onTertiaryContainer = Color(0xFF3E001D),
)

private val PinkDark = darkColorScheme(
    primary = Color(0xFFF48FB1),
    onPrimary = Color(0xFF7A0030),
    primaryContainer = Color(0xFF7A0030),
    onPrimaryContainer = Color(0xFFF8BBD0),
    secondary = Color(0xFFF06292),
    onSecondary = Color(0xFF560027),
    secondaryContainer = Color(0xFF880E4F),
    onSecondaryContainer = Color(0xFFFCE4EC),
    tertiary = Color(0xFFF48FB1),
    onTertiary = Color(0xFF3E001D),
    tertiaryContainer = Color(0xFF560027),
    onTertiaryContainer = Color(0xFFF48FB1),
)

fun ThemeColorOption.lightScheme(): ColorScheme = when (this) {
    ThemeColorOption.GREEN -> GreenLight
    ThemeColorOption.BLUE -> BlueLight
    ThemeColorOption.PURPLE -> PurpleLight
    ThemeColorOption.ORANGE -> OrangeLight
    ThemeColorOption.PINK -> PinkLight
    ThemeColorOption.SYSTEM -> GreenLight
    ThemeColorOption.CUSTOM -> GreenLight
}

fun ThemeColorOption.darkScheme(): ColorScheme = when (this) {
    ThemeColorOption.GREEN -> GreenDark
    ThemeColorOption.BLUE -> BlueDark
    ThemeColorOption.PURPLE -> PurpleDark
    ThemeColorOption.ORANGE -> OrangeDark
    ThemeColorOption.PINK -> PinkDark
    ThemeColorOption.SYSTEM -> GreenDark
    ThemeColorOption.CUSTOM -> GreenDark
}

/** 由色轮选中的种子色生成 Material3 配色。 */
fun customColorScheme(seed: Color, dark: Boolean): ColorScheme {
    return if (dark) {
        darkColorScheme(
            primary = lerp(seed, Color.White, 0.28f),
            onPrimary = lerp(seed, Color.Black, 0.55f),
            primaryContainer = lerp(seed, Color.Black, 0.68f),
            onPrimaryContainer = lerp(seed, Color.White, 0.72f),
            secondary = lerp(seed, Color.White, 0.45f),
            onSecondary = lerp(seed, Color.Black, 0.6f),
            secondaryContainer = lerp(seed, Color.Black, 0.72f),
            onSecondaryContainer = lerp(seed, Color.White, 0.8f),
            tertiary = Color(0xFF80CBC4),
            onTertiary = Color(0xFF00332E),
            tertiaryContainer = Color(0xFF00695C),
            onTertiaryContainer = Color(0xFFB2DFDB),
        )
    } else {
        val onPrimary = if (seed.luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White
        lightColorScheme(
            primary = seed,
            onPrimary = onPrimary,
            primaryContainer = lerp(seed, Color.White, 0.78f),
            onPrimaryContainer = lerp(seed, Color.Black, 0.5f),
            secondary = lerp(seed, Color.Black, 0.22f),
            onSecondary = Color.White,
            secondaryContainer = lerp(seed, Color.White, 0.8f),
            onSecondaryContainer = lerp(seed, Color.Black, 0.55f),
            tertiary = Color(0xFF00838F),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFB2EBF2),
            onTertiaryContainer = Color(0xFF00363D),
        )
    }
}
