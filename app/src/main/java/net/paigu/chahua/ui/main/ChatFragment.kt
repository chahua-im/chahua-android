package net.paigu.chahua.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import net.paigu.chahua.R
import net.paigu.chahua.data.models.ChatDto
import net.paigu.chahua.data.models.ThreadDto
import net.paigu.chahua.ui.chat.ChatActivity
import net.paigu.chahua.ui.chat.ChatScreen
import net.paigu.chahua.ui.chat.ChatViewModel
import net.paigu.chahua.ui.common.EmptyState
import net.paigu.chahua.ui.common.UserAvatar
import net.paigu.chahua.ui.common.formatListTime
import net.paigu.chahua.ui.common.messagePreviewText
import net.paigu.chahua.ui.media.MediaViewerActivity
import net.paigu.chahua.ui.theme.ChahuaTheme

/** 聊天页 Fragment：手机为单栏列表；平板（宽屏）为「左列表 + 右聊天详情」双栏布局。 */
class ChatFragment : Fragment() {
    private val viewModel: ChatListViewModel by viewModels()
    private val detailViewModel: ChatViewModel by viewModels()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ChahuaTheme {
                    ChatContent(
                        viewModel = viewModel,
                        detailViewModel = detailViewModel,
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
                        onOpenMedia = { url, kind ->
                            startActivity(
                                MediaViewerActivity.createIntent(requireContext(), url, kind, null),
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
    onOpenChat: (chatId: String, title: String) -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
    onOpenMedia: (url: String, kind: String) -> Unit,
) {
    val configuration = LocalConfiguration.current
    if (configuration.screenWidthDp >= 840) {
        WideChatLayout(
            viewModel = viewModel,
            detailViewModel = detailViewModel,
            onOpenMedia = onOpenMedia,
        )
    } else {
        ChatListScreen(
            viewModel = viewModel,
            onOpenChat = onOpenChat,
            onOpenThread = onOpenThread,
        )
    }
}

@Composable
private fun WideChatLayout(
    viewModel: ChatListViewModel,
    detailViewModel: ChatViewModel,
    onOpenMedia: (url: String, kind: String) -> Unit,
) {
    var selectedChatId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedThreadId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedReplyCount by rememberSaveable { mutableStateOf(0L) }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight(),
        ) {
            ChatListScreen(
                viewModel = viewModel,
                onOpenChat = { chatId, title ->
                    selectedChatId = chatId
                    selectedTitle = title
                    selectedThreadId = null
                    selectedReplyCount = 0L
                },
                onOpenThread = { thread ->
                    selectedChatId = thread.chatId
                    selectedTitle = thread.chatName
                    selectedThreadId = thread.threadRootMessage?.id
                    selectedReplyCount = thread.replyCount
                },
            )
        }
        VerticalDivider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
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
                    onOpenMedia = onOpenMedia,
                    consumeNavigationBarsInset = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onOpenChat: (chatId: String, title: String) -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val chats by viewModel.chats.collectAsState()
    val threads by viewModel.threads.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val errorText = error

    LaunchedEffect(tab) {
        if (tab == 0) viewModel.loadChats() else viewModel.loadThreads()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_chats)) },
                actions = {
                    IconButton(onClick = { if (tab == 0) viewModel.loadChats() else viewModel.loadThreads() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SecondaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.tab_group)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.tab_threads)) },
                )
            }
            if (loading && (if (tab == 0) chats else threads).isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (!errorText.isNullOrBlank() && (if (tab == 0) chats else threads).isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { if (tab == 0) viewModel.loadChats() else viewModel.loadThreads() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            } else {
                if (tab == 0) {
                    if (chats.isEmpty()) {
                        EmptyState(stringResource(R.string.empty_chats), modifier = Modifier.fillMaxSize())
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
                } else {
                    if (threads.isEmpty()) {
                        EmptyState(stringResource(R.string.empty_threads), modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn {
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

@Composable
private fun ChatItem(chat: ChatDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(url = chat.avatar, name = chat.name, size = 48.dp)
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
                text = messagePreviewText(
                    chat.lastMessage?.message,
                    chat.lastMessage?.messageType,
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
                text = thread.threadRootMessage?.message
                    ?: thread.lastReply?.message
                    ?: stringResource(R.string.tab_threads),
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
