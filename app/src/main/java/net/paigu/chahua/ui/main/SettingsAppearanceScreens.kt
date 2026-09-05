package net.paigu.chahua.ui.main

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.window.embedding.SplitController
import kotlin.math.roundToInt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import net.paigu.chahua.BuildConfig
import net.paigu.chahua.R
import net.paigu.chahua.data.AppLanguage
import net.paigu.chahua.data.FontSizeOption
import net.paigu.chahua.data.LogLevelOption
import net.paigu.chahua.data.SessionManager
import net.paigu.chahua.data.ThemeColorOption
import net.paigu.chahua.data.ThemeModeOption
import net.paigu.chahua.data.models.StickerPackSummaryDto
import net.paigu.chahua.data.models.SavedMessageDto
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.ui.auth.AuthActivity
import net.paigu.chahua.ui.chat.ChatActivity
import net.paigu.chahua.ui.common.AuthAsyncImage
import net.paigu.chahua.ui.common.EmptyState
import net.paigu.chahua.ui.common.UserAvatar
import net.paigu.chahua.ui.common.formatListTime
import net.paigu.chahua.ui.common.messagePreviewText
import net.paigu.chahua.ui.theme.ChahuaTheme
import java.text.BreakIterator
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceScreen(
    settings: net.paigu.chahua.data.AppSettings,
    onShowAllTabChange: (Boolean) -> Unit,
    onHideThreadsInAllTabChange: (Boolean) -> Unit,
    onSortAllByLatestChange: (Boolean) -> Unit,
    onFontSizeChange: (String) -> Unit,
    onThemeColorChange: (String) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onCustomThemeColorChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val fontOptions = FontSizeOption.entries
    val currentFont = FontSizeOption.from(settings.fontSizeKey)
    val themeOptions = ThemeColorOption.entries
    val currentTheme = ThemeColorOption.from(settings.themeColor)
    var showColorWheel by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_appearance)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowAllTabChange(!settings.showAllTab) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_show_all_tab),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.settings_show_all_tab_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = settings.showAllTab,
                        onCheckedChange = onShowAllTabChange,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = settings.showAllTab,
                            onClick = {
                                onHideThreadsInAllTabChange(!settings.hideThreadsInAllTab)
                            },
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_hide_threads_in_all_tab),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.settings_hide_threads_in_all_tab_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = settings.hideThreadsInAllTab,
                        onCheckedChange = onHideThreadsInAllTabChange,
                        enabled = settings.showAllTab,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = settings.showAllTab,
                            onClick = {
                                onSortAllByLatestChange(!settings.sortAllByLatest)
                            },
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_sort_all_by_latest),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.settings_sort_all_by_latest_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = settings.sortAllByLatest,
                        onCheckedChange = onSortAllByLatestChange,
                        enabled = settings.showAllTab,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_theme_mode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeModeOption.entries.forEach { option ->
                        FilterChip(
                            selected = option.key == settings.themeMode,
                            onClick = { onThemeModeChange(option.key) },
                            label = { Text(stringResource(option.displayNameRes)) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_font_size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Slider(
                        value = fontOptions.indexOf(currentFont).toFloat(),
                        onValueChange = { index ->
                            onFontSizeChange(fontOptions[index.roundToInt()].key)
                        },
                        valueRange = 0f..(fontOptions.size - 1).toFloat(),
                        steps = (fontOptions.size - 2).coerceAtLeast(0),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        fontOptions.forEach { option ->
                            Text(
                                text = stringResource(option.displayNameRes),
                                style = if (option == currentFont) {
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                    )
                                } else {
                                    MaterialTheme.typography.labelMedium
                                },
                                color = if (option == currentFont) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_theme_color),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    themeOptions.forEach { option ->
                        ThemeSwatch(
                            option = option,
                            customHex = settings.customThemeColor,
                            selected = option == currentTheme,
                            onClick = {
                                if (option == ThemeColorOption.CUSTOM) {
                                    showColorWheel = true
                                } else {
                                    onThemeColorChange(option.key)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showColorWheel) {
        ColorWheelDialog(
            initialColor = parseCustomThemeColor(settings.customThemeColor),
            onConfirm = { color ->
                onCustomThemeColorChange(color.toArgbHex())
                showColorWheel = false
            },
            onDismiss = { showColorWheel = false },
        )
    }
}

@Composable
private fun ThemeSwatch(
    option: ThemeColorOption,
    customHex: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(themeSwatchColor(option, customHex)),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(option.displayNameRes),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun themeSwatchColor(option: ThemeColorOption, customHex: String): Color = when (option) {
    ThemeColorOption.SYSTEM -> Color(0xFF2E7D32)
    ThemeColorOption.GREEN -> Color(0xFF2E7D32)
    ThemeColorOption.BLUE -> Color(0xFF1565C0)
    ThemeColorOption.PURPLE -> Color(0xFF6A1B9A)
    ThemeColorOption.ORANGE -> Color(0xFFE65100)
    ThemeColorOption.PINK -> Color(0xFFC2185B)
    ThemeColorOption.CUSTOM -> parseCustomThemeColor(customHex)
}

@Composable
private fun ColorWheelDialog(
    initialColor: Color,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = initialColor.hsvComponents()
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }
    var textInput by remember { mutableStateOf(initialColor.toArgbHex()) }

    val selected = Color.hsv(hue, saturation, value)
    val parsedFromText = parseHexColor(textInput)
    val textValid = parsedFromText != null
    val confirmEnabled = textInput.isBlank() || textValid

    fun applyHex(hex: String) {
        val color = parseHexColor(hex) ?: return
        val hsv = color.hsvComponents()
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme_custom)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ColorWheel(hue = hue, onHueChange = { hue = it })
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.settings_theme_brightness),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.weight(1f),
                        valueRange = 0.05f..1f,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { input ->
                        textInput = input
                        applyHex(input)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_theme_hex_hint)) },
                    singleLine = true,
                    isError = !textValid,
                    supportingText = if (!textValid) {
                        {
                            Text(stringResource(R.string.settings_theme_hex_invalid))
                        }
                    } else {
                        null
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(selected),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected) },
                enabled = confirmEnabled,
            ) {
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

/** 简易色轮：点击选择色相（饱和度/亮度由外部状态控制），白色圆点为当前选中色。 */
@Composable
private fun ColorWheel(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val diameter = 220.dp
    val radiusPx = with(density) { diameter.toPx() / 2f }
    val wheelColors = buildList {
        var hue = 0f
        repeat(12) {
            add(Color.hsv(hue, 0.85f, 0.92f))
            hue += 30f
        }
    }
    Box(
        modifier = modifier
            .size(diameter)
            .pointerInput(Unit) {
                fun updateHue(offset: Offset) {
                    val dx = offset.x - radiusPx
                    val dy = offset.y - radiusPx
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist <= radiusPx) {
                        val hue = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f
                        onHueChange(hue)
                    }
                }
                detectDragGestures(
                    onDragStart = { updateHue(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        updateHue(change.position)
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.sweepGradient(
                    *wheelColors.mapIndexed { index, color ->
                        (index / (wheelColors.size - 1f)) to color
                    }.toTypedArray(),
                ),
                radius = size.minDimension / 2f,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = size.minDimension * 0.35f,
            )
            val angleRad = Math.toRadians(hue.toDouble())
            val indicatorRadius = size.minDimension * 0.5f * 0.75f
            val cx = center.x + cos(angleRad) * indicatorRadius
            val cy = center.y + sin(angleRad) * indicatorRadius
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(cx.toFloat(), cy.toFloat()),
            )
            drawCircle(
                color = Color.hsv(hue, 0.85f, 0.92f),
                radius = 5.dp.toPx(),
                center = Offset(cx.toFloat(), cy.toFloat()),
            )
        }
    }
}

private fun Color.hsvComponents(): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return hsv
}

private fun parseHexColor(text: String): Color? {
    val clean = text.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    return runCatching {
        Color(android.graphics.Color.parseColor("#$clean"))
    }.getOrNull()
}

private fun parseCustomThemeColor(hex: String): Color =
    parseHexColor(hex) ?: Color(0xFF2E7D32)

private fun Color.toArgbHex(): String {
    val argb = toArgb()
    return "%06X".format(argb and 0xFFFFFF)
}
