package net.paigu.chahua.ui.main

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
internal fun HomeScreen(
    sessionState: SessionManager.SessionState,
    developerEnabled: Boolean,
    onOpenGeneral: () -> Unit,
    onOpenSavedMessages: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenStickers: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenFriendVerification: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onRevealDeveloper: () -> Unit,
    onLogout: () -> Unit,
) {
    var versionClicks by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
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
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(
                        url = sessionState.me?.avatarUrl,
                        name = sessionState.me?.username,
                        size = 56.dp,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = sessionState.me?.username
                                ?: stringResource(R.string.settings_not_logged_in),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = sessionState.me?.let { stringResource(R.string.settings_uid, it.uid) }
                                ?: stringResource(R.string.settings_uid, -1),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsEntryRow(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.settings_general),
                    onClick = onOpenGeneral,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsEntryRow(
                    icon = Icons.Filled.Bookmark,
                    title = stringResource(R.string.settings_saved_messages),
                    onClick = onOpenSavedMessages,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsEntryRow(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.settings_appearance),
                    onClick = onOpenAppearance,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsEntryRow(
                    icon = Icons.Filled.EmojiEmotions,
                    title = stringResource(R.string.settings_emoji_stickers),
                    onClick = onOpenStickers,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsEntryRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.settings_notifications),
                    onClick = onOpenNotifications,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsEntryRow(
                    icon = Icons.Filled.PersonAdd,
                    title = stringResource(R.string.settings_friend_verification),
                    onClick = onOpenFriendVerification,
                )
                if (developerEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsEntryRow(
                        icon = Icons.Filled.Code,
                        title = stringResource(R.string.settings_developer),
                        onClick = onOpenDeveloper,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        versionClicks += 1
                        if (versionClicks >= 5) {
                            onRevealDeveloper()
                            versionClicks = 0
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_dev_revealed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    .padding(top = 8.dp),
            )
            if (developerEnabled) {
                Text(
                    text = stringResource(R.string.settings_developer_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_logout))
            }
        }
    }
}

@Composable
internal fun SettingsEntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
