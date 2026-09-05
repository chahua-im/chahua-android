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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
    FRIEND_VERIFICATION(R.string.settings_friend_verification),
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
    val updateState by viewModel.updateState.collectAsState()
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
            SettingsPage.FRIEND_VERIFICATION,
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

    AnimatedContent(
        targetState = page,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(tween(durationMillis = 220)) +
                slideInHorizontally(tween(durationMillis = 220)) { it / 12 })
                .togetherWith(fadeOut(tween(durationMillis = 120)))
        },
        label = "settingsPage",
    ) { targetPage ->
        when (targetPage) {
        SettingsPage.HOME -> HomeScreen(
            sessionState = sessionState,
            developerEnabled = settings.developerEnabled,
            onOpenGeneral = { navigate(SettingsPage.GENERAL) },
            onOpenSavedMessages = { navigate(SettingsPage.SAVED_MESSAGES) },
            onOpenAppearance = { navigate(SettingsPage.APPEARANCE) },
            onOpenStickers = { navigate(SettingsPage.STICKERS) },
            onOpenNotifications = { navigate(SettingsPage.NOTIFICATIONS) },
            onOpenFriendVerification = { navigate(SettingsPage.FRIEND_VERIFICATION) },
            onOpenDeveloper = { navigate(SettingsPage.DEVELOPER) },
            onRevealDeveloper = { viewModel.setDeveloperEnabled(true) },
            onLogout = viewModel::logout,
        )
        SettingsPage.GENERAL -> GeneralScreen(
            currentLanguage = AppLanguage.from(settings.language),
            enterToSend = settings.enterToSend,
            showAvatarsInMessages = settings.showAvatarsInMessages,
            updateState = updateState,
            onCheckForUpdates = viewModel::checkForUpdates,
            onDismissUpdateDialog = viewModel::dismissUpdateDialog,
            onDismissUpdateMessage = viewModel::dismissUpdateMessage,
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
            onHideThreadsInAllTabChange = viewModel::setHideThreadsInAllTab,
            onSortAllByLatestChange = viewModel::setSortAllByLatest,
            onFontSizeChange = viewModel::setFontSize,
            onThemeColorChange = viewModel::setThemeColor,
            onThemeModeChange = viewModel::setThemeMode,
            onCustomThemeColorChange = viewModel::setCustomThemeColor,
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
            onPersistentNotificationEnabledChange = viewModel::setPersistentNotificationEnabled,
            onRequestIgnoreBatteryOptimization = viewModel::requestIgnoreBatteryOptimization,
            onBack = { navigate(SettingsPage.HOME) },
        )
        SettingsPage.FRIEND_VERIFICATION -> FriendVerificationScreen(
            viewModel = viewModel,
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
}
