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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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
import net.paigu.chahua.BuildConfig
import net.paigu.chahua.R
import net.paigu.chahua.data.AppLanguage
import net.paigu.chahua.data.FontSizeOption
import net.paigu.chahua.data.LogLevelOption
import net.paigu.chahua.data.SessionManager
import net.paigu.chahua.data.ThemeColorOption
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

class SettingsFragment : Fragment() {
    private val viewModel: SettingsViewModel by viewModels()
    private val stickersViewModel: StickersViewModel by viewModels()
    private val savedMessagesViewModel: SavedMessagesViewModel by viewModels()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val embeddingSupported =
            SplitController.getInstance(requireContext()).splitSupportStatus ==
                SplitController.SplitSupportStatus.SPLIT_AVAILABLE
        val host = activity as? MainActivity

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ChahuaTheme {
                    val wideFallback =
                        !embeddingSupported && LocalConfiguration.current.screenWidthDp >= 840
                    val settingsContent: @Composable () -> Unit = {
                        SettingsScreen(
                            viewModel = viewModel,
                            stickersViewModel = stickersViewModel,
                            savedMessagesViewModel = savedMessagesViewModel,
                            onLoggedOut = {
                                startActivity(
                                    Intent(requireContext(), AuthActivity::class.java).addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                                    ),
                                )
                            },
                        )
                    }
                    if (wideFallback) {
                        WideFallbackFrame(
                            selectedTab = host?.selectedTab ?: 1,
                            onSelectTab = { tab -> host?.selectTab(tab) },
                            leftContent = settingsContent,
                            rightContent = {
                                EmptyState(
                                    text = stringResource(R.string.chat_select_from_list),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                        )
                    } else {
                        settingsContent()
                    }
                }
            }
        }
    }
}

private enum class SettingsPage(val titleRes: Int) {
    HOME(R.string.tab_settings),
    GENERAL(R.string.settings_general),
    SAVED_MESSAGES(R.string.settings_saved_messages),
    CACHE(R.string.settings_cache),
    LANGUAGE(R.string.settings_language),
    APPEARANCE(R.string.settings_appearance),
    STICKERS(R.string.settings_emoji_stickers),
    STICKER_PACK(R.string.settings_emoji_stickers),
    NOTIFICATIONS(R.string.settings_notifications),
    DEVELOPER(R.string.settings_developer),
}

@Composable
private fun SettingsScreen(
    viewModel: SettingsViewModel,
    stickersViewModel: StickersViewModel,
    savedMessagesViewModel: SavedMessagesViewModel,
    onLoggedOut: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState(initial = SessionManager.SessionState())
    val settings by viewModel.settingsState.collectAsState(initial = AppGraph.settings.snapshot())
    val context = LocalContext.current

    var pageName by rememberSaveable { mutableStateOf(SettingsPage.HOME.name) }
    var selectedPackId by rememberSaveable { mutableStateOf<String?>(null) }

    val page = remember(pageName) { SettingsPage.valueOf(pageName) }
    // BackHandler 的回调可能捕获到旧的一次组合，这里始终读取最新页面状态。
    val currentPage by rememberUpdatedState(page)

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) onLoggedOut()
    }

    fun navigate(target: SettingsPage) {
        pageName = target.name
    }

    // 系统返回键与顶栏返回箭头行为一致：子页面逐级返回，设置主页不拦截，
    // 由 MainActivity 负责切回主页（聊天）。
    fun navigateBack() {
        when (currentPage) {
            SettingsPage.GENERAL,
            SettingsPage.APPEARANCE,
            SettingsPage.SAVED_MESSAGES,
            SettingsPage.STICKERS,
            SettingsPage.NOTIFICATIONS,
            SettingsPage.DEVELOPER -> navigate(SettingsPage.HOME)
            SettingsPage.CACHE,
            SettingsPage.LANGUAGE -> navigate(SettingsPage.GENERAL)
            SettingsPage.STICKER_PACK -> {
                selectedPackId = null
                navigate(SettingsPage.STICKERS)
            }
            SettingsPage.HOME -> Unit
        }
    }

    BackHandler(
        enabled = currentPage != SettingsPage.HOME,
        onBack = ::navigateBack,
    )

    when (page) {
        SettingsPage.HOME -> HomeScreen(
            sessionState = sessionState,
            developerEnabled = settings.developerEnabled,
            onOpenGeneral = { navigate(SettingsPage.GENERAL) },
            onOpenSavedMessages = { navigate(SettingsPage.SAVED_MESSAGES) },
            onOpenAppearance = { navigate(SettingsPage.APPEARANCE) },
            onOpenStickers = { navigate(SettingsPage.STICKERS) },
            onOpenNotifications = { navigate(SettingsPage.NOTIFICATIONS) },
            onOpenDeveloper = { navigate(SettingsPage.DEVELOPER) },
            onRevealDeveloper = { viewModel.setDeveloperEnabled(true) },
            onLogout = viewModel::logout,
        )
        SettingsPage.GENERAL -> GeneralScreen(
            currentLanguage = AppLanguage.from(settings.language),
            enterToSend = settings.enterToSend,
            showAvatarsInMessages = settings.showAvatarsInMessages,
            onBack = { navigate(SettingsPage.HOME) },
            onOpenLanguage = { navigate(SettingsPage.LANGUAGE) },
            onOpenCache = { navigate(SettingsPage.CACHE) },
            onEnterToSendChange = viewModel::setEnterToSend,
            onShowAvatarsInMessagesChange = viewModel::setShowAvatarsInMessages,
        )
        SettingsPage.SAVED_MESSAGES -> SavedMessagesScreen(
            viewModel = savedMessagesViewModel,
            onBack = { navigate(SettingsPage.HOME) },
            onOpenMessage = { saved ->
                if (saved.canLocateContext) {
                    val title = saved.chat?.name ?: saved.originalChatId
                    val threadRootId = saved.originalThreadRootId
                    val intent = if (threadRootId != null) {
                        ChatActivity.createThreadIntent(
                            context = context,
                            chatId = saved.originalChatId,
                            title = title,
                            threadRootId = threadRootId,
                            replyCount = 0L,
                            messageId = saved.originalMessageId,
                        )
                    } else {
                        ChatActivity.createIntent(
                            context = context,
                            chatId = saved.originalChatId,
                            title = title,
                            messageId = saved.originalMessageId,
                        )
                    }
                    context.startActivity(intent)
                }
            },
        )
        SettingsPage.CACHE -> CacheScreen(
            viewModel = viewModel,
            onBack = { navigate(SettingsPage.GENERAL) },
        )
        SettingsPage.LANGUAGE -> LanguageScreen(
            currentCode = settings.language,
            onSelect = { code ->
                viewModel.setLanguage(code) {
                    context.findActivity()?.recreate()
                }
            },
            onBack = { navigate(SettingsPage.GENERAL) },
        )
        SettingsPage.APPEARANCE -> AppearanceScreen(
            settings = settings,
            onShowAllTabChange = viewModel::setShowAllTab,
            onFontSizeChange = viewModel::setFontSize,
            onThemeColorChange = viewModel::setThemeColor,
            onBack = { navigate(SettingsPage.HOME) },
        )
        SettingsPage.STICKERS -> StickerPacksScreen(
            stickersViewModel = stickersViewModel,
            pinnedReactions = settings.pinnedReactions,
            onPinnedReactionsChange = viewModel::setPinnedReactions,
            onBack = { navigate(SettingsPage.HOME) },
            onOpenPack = { packId ->
                selectedPackId = packId
                navigate(SettingsPage.STICKER_PACK)
            },
        )
        SettingsPage.STICKER_PACK -> {
            val packId = selectedPackId
            if (packId == null) {
                LaunchedEffect(Unit) { navigate(SettingsPage.STICKERS) }
            } else {
                StickerPackDetailScreen(
                    packId = packId,
                    stickersViewModel = stickersViewModel,
                    myUid = sessionState.me?.uid ?: -1,
                    onBack = {
                        selectedPackId = null
                        navigate(SettingsPage.STICKERS)
                    },
                )
            }
        }
        SettingsPage.NOTIFICATIONS -> NotificationsScreen(
            settings = settings,
            onNotificationsEnabledChange = viewModel::setNotificationsEnabled,
            onBack = { navigate(SettingsPage.HOME) },
        )
        SettingsPage.DEVELOPER -> DeveloperScreen(
            viewModel = viewModel,
            sessionState = sessionState,
            settings = settings,
            onBack = { navigate(SettingsPage.HOME) },
            onCloseDeveloper = {
                viewModel.setDeveloperEnabled(false)
                navigate(SettingsPage.HOME)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    sessionState: SessionManager.SessionState,
    developerEnabled: Boolean,
    onOpenGeneral: () -> Unit,
    onOpenSavedMessages: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenStickers: () -> Unit,
    onOpenNotifications: () -> Unit,
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
private fun SettingsEntryRow(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralScreen(
    currentLanguage: AppLanguage,
    enterToSend: Boolean,
    showAvatarsInMessages: Boolean,
    onBack: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenCache: () -> Unit,
    onEnterToSendChange: (Boolean) -> Unit,
    onShowAvatarsInMessagesChange: (Boolean) -> Unit,
) {
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageScreen(
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
private fun CacheScreen(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceScreen(
    settings: net.paigu.chahua.data.AppSettings,
    onShowAllTabChange: (Boolean) -> Unit,
    onFontSizeChange: (String) -> Unit,
    onThemeColorChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val fontOptions = FontSizeOption.entries
    val currentFont = FontSizeOption.from(settings.fontSizeKey)
    val themeOptions = ThemeColorOption.entries
    val currentTheme = ThemeColorOption.from(settings.themeColor)

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
                            selected = option == currentTheme,
                            onClick = { onThemeColorChange(option.key) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(
    option: ThemeColorOption,
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
                    .background(themeSwatchColor(option)),
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

private fun themeSwatchColor(option: ThemeColorOption): Color = when (option) {
    ThemeColorOption.SYSTEM -> Color(0xFF2E7D32)
    ThemeColorOption.GREEN -> Color(0xFF2E7D32)
    ThemeColorOption.BLUE -> Color(0xFF1565C0)
    ThemeColorOption.PURPLE -> Color(0xFF6A1B9A)
    ThemeColorOption.ORANGE -> Color(0xFFE65100)
    ThemeColorOption.PINK -> Color(0xFFC2185B)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerPacksScreen(
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
private fun StickerPackDetailScreen(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsScreen(
    settings: net.paigu.chahua.data.AppSettings,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        permissionGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_notifications)) },
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNotificationsEnabledChange(!settings.notificationsEnabled) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_notifications_enabled),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.settings_notifications_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = settings.notificationsEnabled,
                        onCheckedChange = onNotificationsEnabledChange,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_notification_permission),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(
                            if (permissionGranted) {
                                R.string.settings_notification_granted
                            } else {
                                R.string.settings_notification_denied
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (permissionGranted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                TextButton(
                    onClick = {
                        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_open_system_settings))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperScreen(
    viewModel: SettingsViewModel,
    sessionState: SessionManager.SessionState,
    settings: net.paigu.chahua.data.AppSettings,
    onBack: () -> Unit,
    onCloseDeveloper: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddServer by rememberSaveable { mutableStateOf(false) }
    var showLogLevelDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_developer)) },
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
            Text(
                text = stringResource(R.string.settings_developer_menu),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                DeveloperSwitchRow(
                    title = stringResource(R.string.settings_developer_enable),
                    description = stringResource(R.string.settings_developer_enable_desc),
                    checked = settings.developerEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) onCloseDeveloper()
                    },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_developer_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_server_list),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                sessionState.serverUrls.forEachIndexed { index, url ->
                    val active = url == sessionState.serverUrl
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.switchServerUrl(url) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = active,
                            onClick = { viewModel.switchServerUrl(url) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                            )
                            if (active) {
                                Text(
                                    text = stringResource(R.string.settings_server_active),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (index < sessionState.serverUrls.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                TextButton(
                    onClick = { showAddServer = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_server_add))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_developer_features),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                DeveloperSwitchRow(
                    title = stringResource(R.string.settings_show_uid_in_chat),
                    description = stringResource(R.string.settings_show_uid_in_chat_desc),
                    checked = settings.showUidInChat,
                    onCheckedChange = viewModel::setShowUidInChat,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                DeveloperSwitchRow(
                    title = stringResource(R.string.settings_show_latency),
                    description = stringResource(R.string.settings_show_latency_desc),
                    checked = settings.showLatency,
                    onCheckedChange = viewModel::setShowLatency,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogLevelDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_log_level),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(
                                LogLevelOption.from(settings.logLevel).displayNameRes,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_user_details),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                DeveloperDetailRow(
                    label = stringResource(R.string.settings_uid_label),
                    value = sessionState.me?.uid?.toString() ?: "-",
                    context = context,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                DeveloperDetailRow(
                    label = stringResource(R.string.settings_auth_key),
                    value = sessionState.authKey ?: "-",
                    context = context,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                DeveloperDetailRow(
                    label = stringResource(R.string.settings_auth_mode),
                    value = if (sessionState.isJwt) "JWT" else "UID",
                    context = context,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                DeveloperDetailRow(
                    label = stringResource(R.string.settings_client_id),
                    value = sessionState.clientId ?: "-",
                    context = context,
                )
            }
        }
    }

    if (showAddServer) {
        AddServerDialog(
            onDismiss = { showAddServer = false },
            onAdd = { url ->
                viewModel.addServerUrl(url)
                showAddServer = false
            },
        )
    }

    if (showLogLevelDialog) {
        LogLevelDialog(
            currentKey = settings.logLevel,
            onSelect = { key ->
                viewModel.setLogLevel(key)
                showLogLevelDialog = false
            },
            onDismiss = { showLogLevelDialog = false },
        )
    }
}

@Composable
private fun DeveloperSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun LogLevelDialog(
    currentKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_log_level)) },
        text = {
            Column {
                LogLevelOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.key) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option.key == currentKey,
                            onClick = { onSelect(option.key) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(option.displayNameRes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_server_add_dialog_title)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_server_label)) },
                singleLine = true,
                supportingText = {
                    Text(stringResource(R.string.server_url_hint))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(url.trim()) },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(R.string.settings_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun DeveloperDetailRow(
    label: String,
    value: String,
    context: Context,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
            )
        }
        if (value != "-") {
            IconButton(onClick = { copyToClipboard(context, label, value) }) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.settings_copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 全局收藏消息页：点击可跳回原消息，右侧可取消收藏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedMessagesScreen(
    viewModel: SavedMessagesViewModel,
    onBack: () -> Unit,
    onOpenMessage: (SavedMessageDto) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .collect { loadMore ->
                if (loadMore) viewModel.loadOlder()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_saved_messages)) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading && state.items.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.items.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.settings_saved_messages_empty),
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = state.items,
                            key = { it.id },
                        ) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenMessage(item) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = listOf(
                                            item.sender?.name,
                                            item.chat?.name,
                                        ).filterNotNull().joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = messagePreviewText(item.message, item.messageType),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = formatListTime(item.savedAt ?: item.originalCreatedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.remove(item) },
                                    enabled = state.workingId != item.id,
                                ) {
                                    Text(stringResource(R.string.settings_unsave))
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, R.string.settings_copied, Toast.LENGTH_SHORT).show()
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
