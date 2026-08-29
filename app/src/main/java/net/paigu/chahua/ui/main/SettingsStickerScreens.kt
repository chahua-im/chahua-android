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
internal fun StickerPacksScreen(
    stickersViewModel: StickersViewModel,
    pinnedReactions: List<String>,
    onPinnedReactionsChange: (List<String>) -> Unit,
    onBack: () -> Unit,
    onOpenPack: (String) -> Unit,
) {
    val packsState by stickersViewModel.packsState.collectAsState()
    val context = LocalContext.current
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { stickersViewModel.loadPacks() }
    LaunchedEffect(packsState.error) {
        packsState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            stickersViewModel.dismissPacksError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_emoji_stickers)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.settings_create_pack),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PinnedReactionsCard(
                pinnedReactions = pinnedReactions,
                onPinnedReactionsChange = onPinnedReactionsChange,
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    packsState.loading && packsState.packs.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    packsState.error != null && packsState.packs.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = packsState.error.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = { stickersViewModel.loadPacks() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                    packsState.packs.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.settings_packs_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    else -> {
                        LazyColumn {
                            items(packsState.packs, key = { it.id }) { pack ->
                                StickerPackRow(
                                    pack = pack,
                                    owned = pack.id in packsState.ownedIds,
                                    onClick = { onOpenPack(pack.id) },
                                    onMoveTop = {
                                        stickersViewModel.movePackToTop(pack.id) { err ->
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePackDialog(
            creating = packsState.creating,
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                stickersViewModel.createPack(name) { pack ->
                    showCreateDialog = false
                    onOpenPack(pack.id)
                }
            },
        )
    }
}

@Composable
private fun PinnedReactionsCard(
    pinnedReactions: List<String>,
    onPinnedReactionsChange: (List<String>) -> Unit,
) {
    var text by remember { mutableStateOf(pinnedReactions.joinToString("")) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_pinned_reactions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "(${pinnedReactions.size}/5)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    val normalized = extractEmojis(input)
                    if (normalized != pinnedReactions) {
                        onPinnedReactionsChange(normalized)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = { Text(stringResource(R.string.settings_pinned_reactions_hint)) },
                singleLine = true,
            )
        }
    }
}

/** 按 Unicode 字符簇拆分文本并去重、限量，用于从输入框中提取表情。 */
private fun extractEmojis(text: String): List<String> {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(text)
    val result = mutableListOf<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val cluster = text.substring(start, end)
        val looksLikeEmoji = cluster.any { it.code > 0x2FFF }
        if (looksLikeEmoji && cluster !in result) {
            result.add(cluster)
            if (result.size >= 5) break
        }
        start = end
        end = iterator.next()
    }
    return result
}

@Composable
private fun StickerPackRow(
    pack: StickerPackSummaryDto,
    owned: Boolean,
    onClick: () -> Unit,
    onMoveTop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (pack.previewSticker != null) {
                AuthAsyncImage(
                    url = pack.previewSticker.media.url,
                    contentDescription = pack.name,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.EmojiEmotions,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (owned) {
                    Badge(modifier = Modifier.padding(end = 6.dp)) {
                        Text(stringResource(R.string.settings_pack_owned))
                    }
                }
                Text(
                    text = pack.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.settings_pack_sticker_count, pack.stickerCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMoveTop) {
            Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = stringResource(R.string.settings_pack_move_top),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CreatePackDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        title = { Text(stringResource(R.string.settings_create_pack)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_pack_name)) },
                placeholder = { Text(stringResource(R.string.settings_pack_name_hint)) },
                singleLine = true,
                enabled = !creating,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank() && !creating,
            ) {
                Text(stringResource(R.string.settings_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerPackDetailScreen(
    packId: String,
    stickersViewModel: StickersViewModel,
    myUid: Int,
    onBack: () -> Unit,
) {
    val detailState by stickersViewModel.detailState.collectAsState()
    val context = LocalContext.current
    var showUnfavoriteConfirm by rememberSaveable { mutableStateOf(false) }
    var showAddSticker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(packId) { stickersViewModel.loadDetail(packId) }
    LaunchedEffect(detailState.error) {
        detailState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            stickersViewModel.dismissDetailError()
        }
    }

    val detail = detailState.detail
    val owned = detail != null && detail.ownerUid == myUid

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detail?.name ?: stringResource(R.string.settings_loading),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                actions = {
                    if (detail != null && owned) {
                        IconButton(
                            onClick = { showAddSticker = true },
                            enabled = !detailState.uploading,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.settings_add_sticker),
                            )
                        }
                    }
                    if (detail != null && !owned) {
                        TextButton(
                            onClick = { showUnfavoriteConfirm = true },
                            enabled = !detailState.working,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_unfavorite),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                detailState.loading && detail == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                detailState.error != null && detail == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = detailState.error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = { stickersViewModel.loadDetail(packId) }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                detail != null && detail.stickers.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.settings_pack_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                detail != null -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(detail.stickers, key = { it.id }) { sticker ->
                            AuthAsyncImage(
                                url = sticker.media.url,
                                contentDescription = sticker.emoji,
                                modifier = Modifier
                                    .size(72.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUnfavoriteConfirm) {
        AlertDialog(
            onDismissRequest = { showUnfavoriteConfirm = false },
            title = { Text(stringResource(R.string.settings_unfavorite_confirm_title)) },
            text = { Text(stringResource(R.string.settings_unfavorite_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnfavoriteConfirm = false
                        stickersViewModel.unsubscribePack(packId) { onBack() }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_unfavorite),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnfavoriteConfirm = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    if (showAddSticker) {
        AddStickerDialog(
            uploading = detailState.uploading,
            onDismiss = { showAddSticker = false },
            onUpload = { uri, emoji, name ->
                stickersViewModel.uploadSticker(packId, uri, emoji, name) {
                    showAddSticker = false
                    Toast.makeText(context, R.string.settings_sticker_uploaded, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun AddStickerDialog(
    uploading: Boolean,
    onDismiss: () -> Unit,
    onUpload: (Uri, String, String?) -> Unit,
) {
    var emoji by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> pickedUri = uri }

    AlertDialog(
        onDismissRequest = { if (!uploading) onDismiss() },
        title = { Text(stringResource(R.string.settings_add_sticker)) },
        text = {
            Column {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text(stringResource(R.string.settings_sticker_emoji)) },
                    placeholder = { Text(stringResource(R.string.settings_sticker_emoji_hint)) },
                    singleLine = true,
                    enabled = !uploading,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_sticker_name)) },
                    singleLine = true,
                    enabled = !uploading,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { pickImage.launch("image/*") },
                    enabled = !uploading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_sticker_pick_image))
                }
                pickedUri?.let { uri ->
                    AuthAsyncImage(
                        url = uri.toString(),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(120.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    pickedUri?.let { onUpload(it, emoji, name) }
                },
                enabled = emoji.isNotBlank() && pickedUri != null && !uploading,
            ) {
                Text(
                    text = stringResource(
                        if (uploading) R.string.settings_sticker_uploading else R.string.settings_sticker_upload,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !uploading) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
