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
