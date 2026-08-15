package net.paigu.chahua.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.window.embedding.SplitController
import net.paigu.chahua.R
import net.paigu.chahua.data.models.ChatDto
import net.paigu.chahua.data.models.ThreadDto
import net.paigu.chahua.ui.chat.ChatActivity
import net.paigu.chahua.ui.chat.ChatScreen
import net.paigu.chahua.ui.chat.ChatViewModel
import net.paigu.chahua.ui.common.EmptyState
import net.paigu.chahua.ui.common.UserAvatar
import net.paigu.chahua.ui.common.formatListTime
import net.paigu.chahua.ui.common.messagePreviewWithSender
import net.paigu.chahua.ui.group.GroupInfoActivity
import net.paigu.chahua.ui.invite.InviteRedeemActivity
import net.paigu.chahua.ui.media.MediaViewerActivity
import net.paigu.chahua.ui.theme.LocalAppSettings
import net.paigu.chahua.ui.theme.ChahuaTheme

/** 聊天页 Fragment：手机为单栏列表；平板（宽屏）为「左列表 + 右聊天详情」双栏布局。 */
class ChatFragment : Fragment() {
    private val viewModel: ChatListViewModel by viewModels()
    private val detailViewModel: ChatViewModel by viewModels()
    private val inviteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val chatId = result.data?.getStringExtra(InviteRedeemActivity.EXTRA_CHAT_ID)
            val chatName = result.data?.getStringExtra(InviteRedeemActivity.EXTRA_CHAT_NAME)
            if (chatId != null) {
                startActivity(
                    ChatActivity.createIntent(
                        requireContext(),
                        chatId,
                        chatName ?: chatId,
                    ),
                )
            }
        }
    }

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
                    ChatContent(
                        viewModel = viewModel,
                        detailViewModel = detailViewModel,
                        embeddingSupported = embeddingSupported,
                        selectedTab = host?.selectedTab ?: 0,
                        onSelectTab = { tab -> host?.selectTab(tab) },
                        onOpenChat = { chatId, title ->
                            startActivity(ChatActivity.createIntent(requireContext(), chatId, title))
                        },
                        onOpenThread = { thread ->
                            startActivity(
                                ChatActivity.createThreadIntent(
                                    context = requireContext(),
                                    chatId = thread.chatId,
                                    title = thread.chatName,
                                    threadRootId = thread.threadRootMessage?.id,
                                    replyCount = thread.replyCount,
                                ),
                            )
                        },
                        onOpenArchivedThread = { thread ->
                            startActivity(
                                ChatActivity.createThreadIntent(
                                    context = requireContext(),
                                    chatId = thread.chatId,
                                    title = thread.chatName,
                                    threadRootId = thread.threadRootMessage?.id,
                                    replyCount = thread.replyCount,
                                    archived = true,
                                ),
                            )
                        },
                        onOpenMedia = { url, kind, fileName ->
                            startActivity(
                                MediaViewerActivity.createIntent(requireContext(), url, kind, fileName),
                            )
                        },
                        onOpenInvite = {
                            inviteLauncher.launch(
                                InviteRedeemActivity.createIntent(requireContext()),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** 自适应入口：宽屏（≥840dp）使用双栏，否则单栏列表。 */
@Composable
fun ChatContent(
    viewModel: ChatListViewModel,
    detailViewModel: ChatViewModel,
    embeddingSupported: Boolean,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenChat: (chatId: String, title: String) -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
    onOpenArchivedThread: (ThreadDto) -> Unit,
    onOpenMedia: (url: String, kind: String, fileName: String?) -> Unit,
    onOpenInvite: () -> Unit,
) {
    // 支持 Activity Embedding 的设备由系统负责左右分栏：这里只渲染聊天列表，
    // 点击后 ChatActivity 会由 WindowManager 放到右侧容器。
    if (embeddingSupported) {
        ChatListScreen(
            viewModel = viewModel,
            onOpenChat = onOpenChat,
            onOpenThread = onOpenThread,
            onOpenArchivedThread = onOpenArchivedThread,
            onOpenInvite = onOpenInvite,
        )
        return
    }

    val configuration = LocalConfiguration.current
    if (configuration.screenWidthDp >= 840) {
        WideChatLayout(
            viewModel = viewModel,
            detailViewModel = detailViewModel,
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
            onOpenArchivedThread = onOpenArchivedThread,
            onOpenMedia = onOpenMedia,
            onOpenInvite = onOpenInvite,
        )
    } else {
        ChatListScreen(
            viewModel = viewModel,
            onOpenChat = onOpenChat,
            onOpenThread = onOpenThread,
            onOpenArchivedThread = onOpenArchivedThread,
            onOpenInvite = onOpenInvite,
        )
    }
}

@Composable
private fun WideChatLayout(
    viewModel: ChatListViewModel,
    detailViewModel: ChatViewModel,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenArchivedThread: (ThreadDto) -> Unit,
    onOpenMedia: (url: String, kind: String, fileName: String?) -> Unit,
    onOpenInvite: () -> Unit,
) {
    val context = LocalContext.current
    var selectedChatId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedThreadId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedReplyCount by rememberSaveable { mutableStateOf(0L) }
    var selectedThreadArchived by rememberSaveable { mutableStateOf(false) }

    // 宽屏双栏中打开聊天时，返回键先关闭聊天回到列表（主页）。
    BackHandler(enabled = selectedChatId != null) {
        selectedChatId = null
        selectedTitle = null
        selectedThreadId = null
        selectedReplyCount = 0L
        selectedThreadArchived = false
    }

    WideFallbackFrame(
        selectedTab = selectedTab,
        onSelectTab = onSelectTab,
        leftContent = {
            ChatListScreen(
                viewModel = viewModel,
                onOpenChat = { chatId, title ->
                    selectedChatId = chatId
                    selectedTitle = title
                    selectedThreadId = null
                    selectedReplyCount = 0L
                    selectedThreadArchived = false
                },
                onOpenThread = { thread ->
                    selectedChatId = thread.chatId
                    selectedTitle = thread.chatName
                    selectedThreadId = thread.threadRootMessage?.id
                    selectedReplyCount = thread.replyCount
                    selectedThreadArchived = false
                },
                onOpenArchivedThread = { thread ->
                    selectedChatId = thread.chatId
                    selectedTitle = thread.chatName
                    selectedThreadId = thread.threadRootMessage?.id
                    selectedReplyCount = thread.replyCount
                    selectedThreadArchived = true
                },
                onOpenInvite = onOpenInvite,
            )
        },
        rightContent = {
            val chatId = selectedChatId
            if (chatId == null) {
                EmptyState(
                    text = stringResource(R.string.chat_select_from_list),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LaunchedEffect(chatId, selectedThreadId) {
                    detailViewModel.init(
                        chatId = chatId,
                        title = selectedTitle ?: chatId,
                        threadId = selectedThreadId,
                        replyCount = selectedReplyCount,
                    )
                }
                ChatScreen(
                    viewModel = detailViewModel,
                    onBack = null,
                    onOpenGroupInfo = {
                        context.startActivity(
                            GroupInfoActivity.createIntent(context, chatId),
                        )
                    },
                    threadArchived = selectedThreadArchived,
                    onArchiveDone = {
                        selectedChatId = null
                        selectedTitle = null
                        selectedThreadId = null
                        selectedReplyCount = 0L
                        selectedThreadArchived = false
                    },
                    onOpenThread = { rootId ->
                        context.startActivity(
                            ChatActivity.createThreadIntent(
                                context = context,
                                chatId = chatId,
                                title = selectedTitle ?: chatId,
                                threadRootId = rootId,
                                replyCount = 0L,
                            ),
                        )
                    },
                    onOpenMedia = onOpenMedia,
                    consumeNavigationBarsInset = true,
                )
            }
        },
    )
}

private enum class ChatTab(val titleRes: Int) {
    ALL(net.paigu.chahua.R.string.tab_all),
    GROUP(net.paigu.chahua.R.string.tab_group),
    THREADS(net.paigu.chahua.R.string.tab_threads),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onOpenChat: (chatId: String, title: String) -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
    onOpenArchivedThread: (ThreadDto) -> Unit,
    onOpenInvite: () -> Unit,
) {
    val settings = LocalAppSettings.current
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val tabs = buildList {
        if (settings.showAllTab) add(ChatTab.ALL)
        add(ChatTab.GROUP)
        add(ChatTab.THREADS)
    }
    val safeIndex = tabIndex.coerceIn(0, tabs.lastIndex.coerceAtLeast(0))
    val tab = tabs[safeIndex]
    val chats by viewModel.chats.collectAsState()
    val threads by viewModel.threads.collectAsState()
    val archivedChats by viewModel.archivedChats.collectAsState()
    val archivedThreads by viewModel.archivedThreads.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val errorText = error
    val latencyMs by viewModel.latencyMs.collectAsState()

    LaunchedEffect(safeIndex, settings.showAllTab) {
        when (tab) {
            ChatTab.ALL -> viewModel.loadAll()
            ChatTab.GROUP -> viewModel.loadChats()
            ChatTab.THREADS -> viewModel.loadThreads()
        }
    }

    fun refresh() {
        when (tab) {
            ChatTab.ALL -> viewModel.loadAll()
            ChatTab.GROUP -> viewModel.loadChats()
            ChatTab.THREADS -> viewModel.loadThreads()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_chats)) },
                actions = {
                    if (settings.showLatency) {
                        Text(
                            text = latencyMs?.let { stringResource(R.string.chat_latency_value, it) } ?: "--",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    IconButton(onClick = onOpenInvite) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.invite_join_group),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SecondaryTabRow(selectedTabIndex = safeIndex) {
                tabs.forEachIndexed { index, item ->
                    Tab(
                        selected = safeIndex == index,
                        onClick = { tabIndex = index },
                        text = { Text(stringResource(item.titleRes)) },
                    )
                }
            }
            val currentEmpty = when (tab) {
                ChatTab.ALL -> chats.isEmpty() && threads.isEmpty()
                ChatTab.GROUP -> chats.isEmpty()
                ChatTab.THREADS -> threads.isEmpty()
            }
            PullToRefreshBox(
                isRefreshing = loading && !currentEmpty,
                onRefresh = { refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (loading && currentEmpty) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (!errorText.isNullOrBlank() && currentEmpty) {
                        PullRefreshableCenteredContent {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = errorText,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = { refresh() }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    } else {
                        when (tab) {
                            ChatTab.ALL -> AllTabList(
                                chats = chats,
                                threads = threads,
                                onOpenChat = onOpenChat,
                                onOpenThread = onOpenThread,
                            )
                            ChatTab.GROUP -> {
                                if (chats.isEmpty()) {
                                    PullRefreshableCenteredContent {
                                        EmptyState(stringResource(R.string.empty_chats))
                                    }
                                } else {
                                    LazyColumn {
                                        items(chats, key = { it.id }) { chat ->
                                            val title = chat.name ?: stringResource(R.string.tab_group)
                                            ChatItem(
                                                chat = chat,
                                                onClick = { onOpenChat(chat.id, title) },
                                            )
                                            HorizontalDivider()
                                        }
                                    }
                                }
                            }
                            ChatTab.THREADS -> {
                                if (showArchived) {
                                    ArchivedList(
                                        archivedChats = archivedChats,
                                        archivedThreads = archivedThreads,
                                        onBack = { showArchived = false },
                                        onOpenChat = onOpenChat,
                                        onOpenThread = onOpenArchivedThread,
                                    )
                                } else {
                                    LazyColumn {
                                        if (archivedChats.isNotEmpty() || archivedThreads.isNotEmpty()) {
                                            item(key = "archived_entry") {
                                                ArchivedEntry(onClick = { showArchived = true })
                                            }
                                        }
                                        if (threads.isEmpty()) {
                                            item(key = "threads_empty") {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.empty_threads),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                        items(threads, key = { "${it.chatId}:${it.threadRootMessage?.id}" }) { thread ->
                                            ThreadItem(thread = thread, onClick = { onOpenThread(thread) })
                                            HorizontalDivider()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllTabList(
    chats: List<ChatDto>,
    threads: List<ThreadDto>,
    onOpenChat: (chatId: String, title: String) -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
) {
    if (chats.isEmpty() && threads.isEmpty()) {
        PullRefreshableCenteredContent {
            EmptyState(stringResource(R.string.empty_all))
        }
        return
    }
    LazyColumn {
        if (chats.isNotEmpty()) {
            item(key = "header_chats") {
                SectionHeader(text = stringResource(R.string.tab_group))
            }
            items(chats, key = { "chat:${it.id}" }) { chat ->
                val title = chat.name ?: stringResource(R.string.tab_group)
                ChatItem(chat = chat, onClick = { onOpenChat(chat.id, title) })
                HorizontalDivider()
            }
        }
        if (threads.isNotEmpty()) {
            item(key = "header_threads") {
                SectionHeader(text = stringResource(R.string.tab_threads))
            }
            items(threads, key = { "thread:${it.chatId}:${it.threadRootMessage?.id}" }) { thread ->
                ThreadItem(thread = thread, onClick = { onOpenThread(thread) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PullRefreshableCenteredContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ChatItem(chat: ChatDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(url = chat.avatar, name = chat.name, size = 48.dp, showBackground = false)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.name ?: stringResource(R.string.tab_group),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = messagePreviewWithSender(
                    chat.lastMessage,
                    stringResource(R.string.message_sender_unknown),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatListTime(chat.lastMessageAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (chat.unreadCount > 0) {
                Badge {
                    Text(if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString())
                }
            }
        }
    }
}

@Composable
private fun ThreadItem(thread: ThreadDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(url = thread.chatAvatar, name = thread.chatName, size = 48.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = thread.chatName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = messagePreviewWithSender(
                    thread.lastReply ?: thread.threadRootMessage,
                    stringResource(R.string.message_sender_unknown),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.thread_reply_count, thread.replyCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (thread.unreadCount > 0) {
                Badge {
                    Text(thread.unreadCount.toString())
                }
            }
        }
    }
}

@Composable
private fun ArchivedEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Archive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.archived_entry_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.archived_entry_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArchivedList(
    archivedChats: List<ChatDto>,
    archivedThreads: List<ThreadDto>,
    onBack: () -> Unit,
    onOpenChat: (chatId: String, title: String) -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
) {
    LazyColumn {
        item(key = "archived_back") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.archived_back),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider()
        }
        if (archivedChats.isEmpty() && archivedThreads.isEmpty()) {
            item(key = "archived_empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.archived_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            if (archivedChats.isNotEmpty()) {
                item(key = "archived_chats_header") {
                    SectionHeader(text = stringResource(R.string.tab_group))
                }
                items(archivedChats, key = { "archived_chat:${it.id}" }) { chat ->
                    val title = chat.name ?: stringResource(R.string.tab_group)
                    ChatItem(chat = chat, onClick = { onOpenChat(chat.id, title) })
                    HorizontalDivider()
                }
            }
            if (archivedThreads.isNotEmpty()) {
                item(key = "archived_threads_header") {
                    SectionHeader(text = stringResource(R.string.tab_threads))
                }
                items(
                    archivedThreads,
                    key = { "archived_thread:${it.chatId}:${it.threadRootMessage?.id}" },
                ) { thread ->
                    ThreadItem(thread = thread, onClick = { onOpenThread(thread) })
                    HorizontalDivider()
                }
            }
        }
    }
}
