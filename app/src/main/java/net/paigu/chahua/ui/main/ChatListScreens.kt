package net.paigu.chahua.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.models.ChatDto
import net.paigu.chahua.data.models.FriendRequestHistoryEntryDto
import net.paigu.chahua.data.models.FriendResponse
import net.paigu.chahua.data.models.MemberSummaryDto
import net.paigu.chahua.data.models.ThreadDto
import net.paigu.chahua.data.models.UserDto
import net.paigu.chahua.data.models.displayName
import net.paigu.chahua.data.models.isDm
import net.paigu.chahua.ui.chat.ChatActivity
import net.paigu.chahua.ui.chat.ChatScreen
import net.paigu.chahua.ui.chat.ChatViewModel
import net.paigu.chahua.ui.common.EmptyState
import net.paigu.chahua.ui.common.UserAvatar
import net.paigu.chahua.ui.common.UserProfileDialog
import net.paigu.chahua.ui.common.formatListTime
import net.paigu.chahua.ui.common.messagePreviewText
import net.paigu.chahua.ui.common.messagePreviewWithSender
import net.paigu.chahua.ui.group.GroupInfoActivity
import net.paigu.chahua.ui.invite.InviteRedeemActivity
import net.paigu.chahua.ui.media.MediaViewerActivity
import net.paigu.chahua.ui.theme.LocalAppSettings
import net.paigu.chahua.ui.theme.ChahuaTheme
import kotlinx.coroutines.launch

private enum class ChatTab(val titleRes: Int) {
    ALL(net.paigu.chahua.R.string.tab_all),
    GROUP(net.paigu.chahua.R.string.tab_group),
    THREADS(net.paigu.chahua.R.string.tab_threads),
    FRIENDS(net.paigu.chahua.R.string.tab_friends),
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
    var showFriendRequests by rememberSaveable { mutableStateOf(false) }
    var profileUser by remember { mutableStateOf<UserDto?>(null) }
    val tabs = buildList {
        if (settings.showAllTab) add(ChatTab.ALL)
        add(ChatTab.GROUP)
        add(ChatTab.THREADS)
        add(ChatTab.FRIENDS)
    }
    val safeIndex = tabIndex.coerceIn(0, tabs.lastIndex.coerceAtLeast(0))
    val tab = tabs[safeIndex]
    val pagerState = rememberPagerState(initialPage = safeIndex) { tabs.size }
    val chats by viewModel.chats.collectAsState()
    val threads by viewModel.threads.collectAsState()
    val groupChats = chats.filterNot { it.isDm }
    val archivedChats by viewModel.archivedChats.collectAsState()
    val archivedThreads by viewModel.archivedThreads.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val friendsLoading by viewModel.friendsLoading.collectAsState()
    val errorText = error
    val latencyMs by viewModel.latencyMs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val permissions = AppGraph.session.snapshot().me?.permissions.orEmpty()
    val canCreateChat = permissions.any { it == "permission.all" || it == "chat.create" }

    // 左右滑动切换标签：页面变化时同步顶部标签选中态。
    LaunchedEffect(pagerState.currentPage) {
        tabIndex = pagerState.currentPage
    }

    LaunchedEffect(safeIndex, settings.showAllTab) {
        if (pagerState.currentPage != safeIndex) {
            pagerState.scrollToPage(safeIndex)
        }
        when (tab) {
            ChatTab.ALL -> viewModel.loadAll()
            ChatTab.GROUP -> viewModel.loadChats()
            ChatTab.THREADS -> viewModel.loadThreads()
            ChatTab.FRIENDS -> viewModel.loadFriends()
        }
    }

    LaunchedEffect(tab) {
        if (tab != ChatTab.FRIENDS) showFriendRequests = false
    }

    LaunchedEffect(showFriendRequests) {
        if (showFriendRequests) viewModel.loadFriendRequests()
    }

    LaunchedEffect(viewModel.friendsError) {
        viewModel.friendsError.value?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.dismissFriendsError()
        }
    }

    fun refresh() {
        when (tab) {
            ChatTab.ALL -> viewModel.loadAll()
            ChatTab.GROUP -> viewModel.loadChats()
            ChatTab.THREADS -> viewModel.loadThreads()
            ChatTab.FRIENDS -> viewModel.loadFriends()
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
                    IconButton(
                        onClick = {
                            if (canCreateChat) {
                                showCreateDialog = true
                            } else {
                                onOpenInvite()
                            }
                        },
                    ) {
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
            if (tab == ChatTab.FRIENDS && showFriendRequests) {
                FriendRequestsContent(
                    viewModel = viewModel,
                    onBack = { showFriendRequests = false },
                    onOpenProfile = { profileUser = it.toUserDto() },
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val pageTab = tabs.getOrNull(page) ?: return@HorizontalPager
                    val pageEmpty = when (pageTab) {
                        ChatTab.ALL -> chats.isEmpty() && threads.isEmpty()
                        ChatTab.GROUP -> groupChats.isEmpty()
                        ChatTab.THREADS -> threads.isEmpty()
                        ChatTab.FRIENDS -> false
                    }
                    PullToRefreshBox(
                        isRefreshing = if (pageTab == ChatTab.FRIENDS) {
                            friendsLoading
                        } else {
                            loading && !pageEmpty
                        },
                        onRefresh = { refresh() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (pageTab == ChatTab.FRIENDS) {
                                FriendsTabContent(
                                    viewModel = viewModel,
                                    onOpenRequests = { showFriendRequests = true },
                                    onOpenFriend = { member ->
                                        viewModel.openDmWith(
                                            uid = member.uid,
                                            onFound = { chatId, title ->
                                                context.startActivity(
                                                    ChatActivity.createIntent(context, chatId, title),
                                                )
                                            },
                                            onError = { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    },
                                )
                            } else if (loading && pageEmpty) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else if (!errorText.isNullOrBlank() && pageEmpty) {
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
                                when (pageTab) {
                                    ChatTab.ALL -> AllTabList(
                                        chats = chats,
                                        threads = threads,
                                        onOpenChat = onOpenChat,
                                        onOpenThread = onOpenThread,
                                        hideThreads = settings.hideThreadsInAllTab,
                                    )
                                    ChatTab.GROUP -> {
                                        if (groupChats.isEmpty()) {
                                            PullRefreshableCenteredContent {
                                                EmptyState(stringResource(R.string.empty_chats))
                                            }
                                        } else {
                                            LazyColumn {
                                                items(groupChats, key = { it.id }) { chat ->
                                                    val title = chat.displayName
                                                        ?: stringResource(R.string.tab_group)
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
                                                items(
                                                    threads,
                                                    key = { "${it.chatId}:${it.threadRootMessage?.id}" },
                                                ) { thread ->
                                                    ThreadItem(thread = thread, onClick = { onOpenThread(thread) })
                                                    HorizontalDivider()
                                                }
                                            }
                                        }
                                    }
                                    ChatTab.FRIENDS -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    profileUser?.let { user ->
        UserProfileDialog(
            user = user,
            onDismiss = { profileUser = null },
            onMessage = {
                profileUser = null
                viewModel.openDmWith(
                    uid = user.uid,
                    onFound = { chatId, title ->
                        context.startActivity(ChatActivity.createIntent(context, chatId, title))
                    },
                    onError = { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                )
            },
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!creating) showCreateDialog = false },
            title = { Text(stringResource(R.string.chat_create_chat)) },
            text = {
                Column {
                    if (canCreateChat) {
                        OutlinedTextField(
                            value = createName,
                            onValueChange = { createName = it },
                            label = { Text(stringResource(R.string.chat_create_name_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.chat_create_permission_denied),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            showCreateDialog = false
                            onOpenInvite()
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.invite_join_group))
                    }
                }
            },
            confirmButton = {
                if (canCreateChat) {
                    TextButton(
                        enabled = createName.isNotBlank() && !creating,
                        onClick = {
                            creating = true
                            scope.launch {
                                val created = runCatching {
                                    AppGraph.api.createChat(createName.trim())
                                }
                                creating = false
                                created
                                    .onSuccess { chat ->
                                        showCreateDialog = false
                                        createName = ""
                                        viewModel.loadChats()
                                        onOpenChat(chat.id, chat.name ?: chat.id)
                                    }
                                    .onFailure {
                                        Toast.makeText(
                                            context,
                                            it.message
                                                ?: context.getString(R.string.chat_create_failed),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.chat_create))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !creating,
                    onClick = { showCreateDialog = false },
                ) {
                    Text(stringResource(R.string.chat_cancel))
                }
            },
        )
    }
}

@Composable
private fun AllTabList(
    chats: List<ChatDto>,
    threads: List<ThreadDto>,
    onOpenChat: (chatId: String, title: String) -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
    hideThreads: Boolean,
) {
    val groupChats = chats.filterNot { it.isDm }
    val dmChats = chats.filter { it.isDm }
    val showThreads = !hideThreads && threads.isNotEmpty()
    if (groupChats.isEmpty() && dmChats.isEmpty() && !showThreads) {
        PullRefreshableCenteredContent {
            EmptyState(stringResource(R.string.empty_all))
        }
        return
    }
    LazyColumn {
        if (groupChats.isNotEmpty()) {
            item(key = "header_chats") {
                SectionHeader(text = stringResource(R.string.tab_group))
            }
            items(groupChats, key = { "chat:${it.id}" }) { chat ->
                val title = chat.name ?: stringResource(R.string.tab_group)
                ChatItem(chat = chat, onClick = { onOpenChat(chat.id, title) })
                HorizontalDivider()
            }
        }
        if (dmChats.isNotEmpty()) {
            item(key = "header_dms") {
                SectionHeader(text = stringResource(R.string.friends_section))
            }
            items(dmChats, key = { "dm:${it.id}" }) { chat ->
                val title = chat.displayName ?: stringResource(R.string.friends_section)
                ChatItem(chat = chat, onClick = { onOpenChat(chat.id, title) })
                HorizontalDivider()
            }
        }
        if (showThreads) {
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
internal fun SectionHeader(text: String) {
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
    val title = chat.displayName ?: stringResource(R.string.tab_group)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            url = if (chat.isDm) chat.peer?.avatarUrl else chat.avatar,
            name = title,
            size = 48.dp,
            showBackground = false,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
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
    val root = thread.threadRootMessage
    val title = messagePreviewText(root?.message, root?.messageType)
        .takeIf { it.isNotBlank() }
        ?: thread.chatName
    val initiator = root?.sender
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            UserAvatar(url = thread.chatAvatar, name = thread.chatName, size = 48.dp)
            if (initiator != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(1.dp),
                ) {
                    UserAvatar(
                        url = initiator.avatarUrl,
                        name = initiator.name,
                        size = 24.dp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
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
                    val title = chat.displayName ?: stringResource(R.string.tab_group)
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
