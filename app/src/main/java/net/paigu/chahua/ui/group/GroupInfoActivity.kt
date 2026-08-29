package net.paigu.chahua.ui.group

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.AppLocale
import net.paigu.chahua.data.models.ChatAttachmentDto
import net.paigu.chahua.data.models.MemberDto
import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.SavedMessageDto
import net.paigu.chahua.data.models.UserDto
import net.paigu.chahua.ui.chat.ChatActivity
import net.paigu.chahua.ui.common.AuthAsyncImage
import net.paigu.chahua.ui.common.UserAvatar
import net.paigu.chahua.ui.common.UserProfileDialog
import net.paigu.chahua.ui.common.formatListTime
import net.paigu.chahua.ui.common.messagePreviewText
import net.paigu.chahua.ui.media.MediaViewerActivity
import net.paigu.chahua.ui.theme.ChahuaTheme
import net.paigu.chahua.ui.theme.LocalAppSettings

class GroupInfoActivity : ComponentActivity() {

    private val viewModel: GroupInfoViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, AppGraph.settings.snapshot().language))
    }

    companion object {
        private const val EXTRA_CHAT_ID = "chat_id"

        fun createIntent(context: Context, chatId: String): Intent =
            Intent(context, GroupInfoActivity::class.java)
                .putExtra(EXTRA_CHAT_ID, chatId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: run {
            finish()
            return
        }
        viewModel.init(chatId)
        setContent {
            ChahuaTheme {
                GroupInfoScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onOpenMessage = { message ->
                        startActivity(
                            if (message.replyRootId.isNullOrBlank()) {
                                ChatActivity.createIntent(
                                    context = this,
                                    chatId = chatId,
                                    title = viewModel.uiState.value.info?.name ?: chatId,
                                    messageId = message.id,
                                )
                            } else {
                                ChatActivity.createThreadIntent(
                                    context = this,
                                    chatId = chatId,
                                    title = viewModel.uiState.value.info?.name ?: chatId,
                                    threadRootId = message.replyRootId,
                                    replyCount = 0L,
                                    messageId = message.id,
                                )
                            },
                        )
                    },
                    onOpenSavedMessage = { saved ->
                        startActivity(
                            if (saved.originalThreadRootId.isNullOrBlank()) {
                                ChatActivity.createIntent(
                                    context = this,
                                    chatId = chatId,
                                    title = saved.chat?.name?.takeIf { it.isNotBlank() } ?: chatId,
                                    messageId = saved.originalMessageId,
                                )
                            } else {
                                ChatActivity.createThreadIntent(
                                    context = this,
                                    chatId = chatId,
                                    title = saved.chat?.name?.takeIf { it.isNotBlank() } ?: chatId,
                                    threadRootId = saved.originalThreadRootId,
                                    replyCount = 0L,
                                    messageId = saved.originalMessageId,
                                )
                            },
                        )
                    },
                    onOpenMedia = { url, kind, fileName ->
                        startActivity(
                            MediaViewerActivity.createIntent(this, url, kind, fileName),
                        )
                    },
                    onLeftGroup = {
                        setResult(RESULT_OK)
                        finish()
                    },
                )
            }
        }
    }
}

private enum class GroupInfoMode {
    INFO,
    EDIT,
    INVITES,
    SEARCH,
    SAVED,
    MEMBERS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInfoScreen(
    viewModel: GroupInfoViewModel,
    onBack: () -> Unit,
    onOpenMessage: (MessageDto) -> Unit,
    onOpenSavedMessage: (SavedMessageDto) -> Unit,
    onOpenMedia: (url: String, kind: String, fileName: String?) -> Unit,
    onLeftGroup: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(GroupInfoMode.INFO) }
    var attachmentFilter by rememberSaveable { mutableStateOf(AttachmentFilter.IMAGE) }
    var searchText by remember { mutableStateOf("") }
    var profileUser by remember { mutableStateOf<UserDto?>(null) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var muting by remember { mutableStateOf(false) }

    LaunchedEffect(state.error, state.searchError) {
        (state.error ?: state.searchError)?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.dismissError()
        }
    }

    LaunchedEffect(mode) {
        if (mode == GroupInfoMode.SAVED && state.savedMessages.isEmpty()) {
            viewModel.loadSavedMessages()
        }
        if (mode == GroupInfoMode.MEMBERS && state.members.isEmpty() && !state.membersLoading) {
            viewModel.loadMembers()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (mode) {
                                GroupInfoMode.INFO -> R.string.group_info_title
                                GroupInfoMode.EDIT -> R.string.group_edit
                                GroupInfoMode.INVITES -> R.string.group_invites
                                GroupInfoMode.SEARCH -> R.string.group_search
                                GroupInfoMode.SAVED -> R.string.group_saved
                                GroupInfoMode.MEMBERS -> R.string.group_members
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (mode == GroupInfoMode.INFO) onBack() else mode = GroupInfoMode.INFO
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                actions = {
                    if (mode == GroupInfoMode.INFO && state.info?.myRole == "admin") {
                        IconButton(onClick = { mode = GroupInfoMode.EDIT }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.group_edit),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (mode) {
            GroupInfoMode.INFO -> if (state.info?.kind == "dm") {
                DmInfoContent(
                    state = state,
                    onMessage = {
                        val uid = state.info?.peer?.uid ?: return@DmInfoContent
                        val chatId = state.info?.id ?: return@DmInfoContent
                        onOpenMessage(
                            MessageDto(
                                id = "",
                                sender = UserDto(
                                    uid = uid,
                                    avatarUrl = state.info?.peer?.avatarUrl,
                                    name = state.info?.peer?.username,
                                ),
                                chatId = chatId,
                            ),
                        )
                    },
                    modifier = Modifier.padding(padding),
                )
            } else {
                InfoContent(
                    state = state,
                    viewModel = viewModel,
                    attachmentFilter = attachmentFilter,
                    onAttachmentFilterChange = { attachmentFilter = it },
                    onOpenSearch = { mode = GroupInfoMode.SEARCH },
                    onOpenSaved = { mode = GroupInfoMode.SAVED },
                    onOpenMembers = { mode = GroupInfoMode.MEMBERS },
                    onOpenInvites = { mode = GroupInfoMode.INVITES },
                    onToggleMute = {
                        if (!muting) {
                            muting = true
                            viewModel.toggleMute { muting = false }
                        }
                    },
                    onLeaveClick = { showLeaveConfirm = true },
                    onOpenMedia = onOpenMedia,
                    modifier = Modifier.padding(padding),
                )
            }
            GroupInfoMode.EDIT -> EditContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
            GroupInfoMode.INVITES -> InvitesContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
            GroupInfoMode.SEARCH -> SearchContent(
                state = state,
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                onSearch = { viewModel.searchMessages(searchText) },
                onOpenMessage = onOpenMessage,
                modifier = Modifier.padding(padding),
            )
            GroupInfoMode.SAVED -> SavedContent(
                state = state,
                onOpenSavedMessage = onOpenSavedMessage,
                modifier = Modifier.padding(padding),
            )
            GroupInfoMode.MEMBERS -> MembersContent(
                state = state,
                viewModel = viewModel,
                onOpenMember = { profileUser = it },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(stringResource(R.string.group_leave)) },
            text = { Text(stringResource(R.string.group_leave_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirm = false
                        viewModel.leaveGroup { ok ->
                            if (ok) onLeftGroup()
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.group_leave),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    profileUser?.let { user ->
        UserProfileDialog(
            user = user,
            onDismiss = { profileUser = null },
        )
    }
}
