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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.SystemUpdate
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
internal fun GeneralScreen(
    currentLanguage: AppLanguage,
    enterToSend: Boolean,
    showAvatarsInMessages: Boolean,
    updateState: UpdateUiState,
    onCheckForUpdates: () -> Unit,
    onDismissUpdateDialog: () -> Unit,
    onDismissUpdateMessage: () -> Unit,
    onBack: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenCache: () -> Unit,
    onEnterToSendChange: (Boolean) -> Unit,
    onShowAvatarsInMessagesChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(updateState.message) {
        updateState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onDismissUpdateMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_general)) },
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
                .padding(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsEntryRow(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.settings_language),
                    subtitle = stringResource(currentLanguage.displayNameRes),
                    onClick = onOpenLanguage,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsEntryRow(
                    icon = Icons.Filled.DeleteSweep,
                    title = stringResource(R.string.settings_cache),
                    onClick = onOpenCache,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsEntryRow(
                    icon = Icons.Filled.SystemUpdate,
                    title = stringResource(R.string.settings_check_update),
                    subtitle = stringResource(R.string.settings_check_update_desc),
                    onClick = onCheckForUpdates,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEnterToSendChange(!enterToSend) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_enter_to_send),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = enterToSend,
                        onCheckedChange = onEnterToSendChange,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowAvatarsInMessagesChange(!showAvatarsInMessages) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_show_avatars_in_messages),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.settings_show_avatars_in_messages_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = showAvatarsInMessages,
                        onCheckedChange = onShowAvatarsInMessagesChange,
                    )
                }
            }

            if (updateState.checking) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text(stringResource(R.string.update_checking)) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.update_checking_hint))
                        }
                    },
                    confirmButton = {},
                )
            }

            if (updateState.updateAvailable) {
                AlertDialog(
                    onDismissRequest = onDismissUpdateDialog,
                    title = {
                        Text(stringResource(R.string.update_available_title, updateState.latestVersion))
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = updateState.releaseNotes.ifBlank {
                                    stringResource(R.string.update_release_notes_empty)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (updateState.downloadUrl.isNotBlank()) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(updateState.downloadUrl))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                                onDismissUpdateDialog()
                            },
                        ) {
                            Text(stringResource(R.string.update_download))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissUpdateDialog) {
                            Text(stringResource(R.string.update_later))
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguageScreen(
    currentCode: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_language)) },
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
                .padding(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.code) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentCode == option.code,
                            onClick = { onSelect(option.code) },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(option.displayNameRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CacheScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val cacheState by viewModel.cacheState.collectAsState()
    val context = LocalContext.current
    var showConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadCacheSize() }
    LaunchedEffect(cacheState.message) {
        cacheState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.dismissCacheMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_cache)) },
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
                .padding(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_cache_total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (cacheState.computing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(top = 12.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = formatBytes(cacheState.totalBytes),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                HorizontalDivider()
                CacheDetailRow(
                    label = stringResource(R.string.settings_cache_images),
                    bytes = cacheState.coilBytes,
                )
                HorizontalDivider()
                CacheDetailRow(
                    label = stringResource(R.string.settings_cache_other),
                    bytes = (cacheState.totalBytes - cacheState.coilBytes).coerceAtLeast(0L),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { showConfirm = true },
                enabled = !cacheState.computing && !cacheState.clearing && cacheState.totalBytes > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (cacheState.clearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.settings_cache_clear))
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.settings_cache_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_cache_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        viewModel.clearCache()
                    },
                ) {
                    Text(stringResource(R.string.settings_cache_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun CacheDetailRow(label: String, bytes: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatBytes(bytes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
