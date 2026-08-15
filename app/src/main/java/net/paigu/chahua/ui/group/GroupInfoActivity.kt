package net.paigu.chahua.ui.group

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Image
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
            )
        },
    ) { padding ->
        when (mode) {
            GroupInfoMode.INFO -> InfoContent(
                state = state,
                viewModel = viewModel,
                attachmentFilter = attachmentFilter,
                onAttachmentFilterChange = { attachmentFilter = it },
                onOpenSearch = { mode = GroupInfoMode.SEARCH },
                onOpenSaved = { mode = GroupInfoMode.SAVED },
                onOpenMembers = { mode = GroupInfoMode.MEMBERS },
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

@Composable
private fun InfoContent(
    state: GroupInfoUiState,
    viewModel: GroupInfoViewModel,
    attachmentFilter: AttachmentFilter,
    onAttachmentFilterChange: (AttachmentFilter) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenMembers: () -> Unit,
    onToggleMute: () -> Unit,
    onLeaveClick: () -> Unit,
    onOpenMedia: (url: String, kind: String, fileName: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val attachments = if (attachmentFilter == AttachmentFilter.ALL) {
        state.attachments[AttachmentFilter.IMAGE].orEmpty() +
            state.attachments[AttachmentFilter.VIDEO].orEmpty()
    } else {
        state.attachments[attachmentFilter].orEmpty()
    }
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4 && info.totalItemsCount > 0
        }
    }

    LaunchedEffect(shouldLoadMore, attachmentFilter) {
        if (
            shouldLoadMore &&
            attachmentFilter != AttachmentFilter.ALL &&
            state.attachmentCursors[attachmentFilter] != null
        ) {
            viewModel.loadMoreAttachments(attachmentFilter)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.loading && state.info == null) {
                item(key = "group_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item(key = "group_header") {
                    GroupInfoHeader(
                        info = state.info,
                        state = state,
                        viewModel = viewModel,
                        onOpenSearch = onOpenSearch,
                        onOpenSaved = onOpenSaved,
                        onOpenMembers = onOpenMembers,
                        onToggleMute = onToggleMute,
                        onLeaveClick = onLeaveClick,
                    )
                }
                item(key = "media_tabs") {
                    MediaTabs(
                        state = state,
                        filter = attachmentFilter,
                        onFilterChange = onAttachmentFilterChange,
                    )
                }
                if (attachments.isEmpty()) {
                    item(key = "media_empty") {
                        if (state.loadingAttachments) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Text(
                                text = stringResource(
                                    when (attachmentFilter) {
                                        AttachmentFilter.ALL -> R.string.group_attachments_all_empty
                                        AttachmentFilter.IMAGE -> R.string.group_attachments_images_empty
                                        AttachmentFilter.VIDEO -> R.string.group_attachments_videos_empty
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 20.dp),
                            )
                        }
                    }
                } else {
                    attachments.chunked(3).forEachIndexed { rowIndex, row ->
                        item(key = "media_${attachmentFilter.name}_$rowIndex") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                row.forEach { attachment ->
                                    AttachmentThumb(
                                        attachment = attachment,
                                        onClick = {
                                            onOpenMedia(
                                                attachment.url,
                                                attachment.kind,
                                                attachment.fileName,
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(3 - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                if (state.loadingMoreAttachments) {
                    item(key = "media_loading_more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupInfoHeader(
    info: net.paigu.chahua.data.models.GroupInfoDto?,
    state: GroupInfoUiState,
    viewModel: GroupInfoViewModel,
    onOpenSearch: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenMembers: () -> Unit,
    onToggleMute: () -> Unit,
    onLeaveClick: () -> Unit,
) {
    if (info == null) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UserAvatar(
            url = info.avatar,
            name = info.name,
            size = 88.dp,
            showBackground = true,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = info.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (!info.description.isNullOrBlank()) {
            Text(
                text = info.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            GroupActionButton(
                icon = Icons.Filled.Search,
                label = stringResource(R.string.group_search),
                onClick = onOpenSearch,
            )
            GroupActionButton(
                icon = Icons.Filled.BookmarkBorder,
                label = stringResource(R.string.group_saved),
                onClick = onOpenSaved,
            )
            GroupActionButton(
                icon = Icons.Filled.People,
                label = stringResource(R.string.group_members),
                onClick = onOpenMembers,
            )
            GroupActionButton(
                icon = if (viewModel.isMuted()) {
                    Icons.Filled.NotificationsOff
                } else {
                    Icons.Filled.Notifications
                },
                label = stringResource(
                    if (viewModel.isMuted()) R.string.group_muted else R.string.group_mute,
                ),
                onClick = onToggleMute,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onLeaveClick,
            enabled = !state.leaving,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Filled.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (state.leaving) {
                    stringResource(R.string.group_leaving)
                } else {
                    stringResource(R.string.group_leave)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaTabs(
    state: GroupInfoUiState,
    filter: AttachmentFilter,
    onFilterChange: (AttachmentFilter) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.group_media),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val filters = listOf(AttachmentFilter.ALL, AttachmentFilter.IMAGE, AttachmentFilter.VIDEO)
        SecondaryScrollableTabRow(
            selectedTabIndex = filters.indexOf(filter).coerceAtLeast(0),
            edgePadding = 8.dp,
        ) {
            filters.forEach { item ->
                Tab(
                    selected = filter == item,
                    onClick = { onFilterChange(item) },
                    text = {
                        Text(
                            stringResource(
                                when (item) {
                                    AttachmentFilter.ALL -> R.string.group_attachments_all
                                    AttachmentFilter.IMAGE -> R.string.group_attachments_images
                                    AttachmentFilter.VIDEO -> R.string.group_attachments_videos
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun GroupActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AttachmentThumb(
    attachment: ChatAttachmentDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (attachment.kind.startsWith("image")) {
            AuthAsyncImage(
                url = attachment.url,
                contentDescription = attachment.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Filled.VideoLibrary,
                contentDescription = attachment.fileName,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchContent(
    state: GroupInfoUiState,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenMessage: (MessageDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.group_search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onSearch,
            enabled = !state.searching && searchText.trim().length >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.searching) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.group_search))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (state.searchResults.isEmpty() && !state.searching && state.searchError == null) {
            Text(
                text = stringResource(R.string.group_search_empty_prompt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(state.searchResults, key = { it.id }) { message ->
                    MessageResultRow(message = message, onClick = { onOpenMessage(message) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MessageResultRow(message: MessageDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            url = message.sender.avatarUrl,
            name = message.sender.name,
            size = 40.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.sender.name ?: stringResource(R.string.message_sender_unknown),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = messagePreviewText(message.message, message.messageType),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatListTime(message.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SavedContent(
    state: GroupInfoUiState,
    onOpenSavedMessage: (SavedMessageDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loadingSaved && state.savedMessages.isEmpty() -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.savedMessages.isEmpty() -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.group_saved_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            LazyColumn(modifier = modifier.fillMaxSize()) {
                items(state.savedMessages, key = { it.id }) { saved ->
                    SavedMessageRow(saved = saved, onClick = { onOpenSavedMessage(saved) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SavedMessageRow(saved: SavedMessageDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Bookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = saved.sender?.name ?: stringResource(R.string.message_sender_unknown),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = messagePreviewText(saved.message, saved.messageType),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatListTime(saved.savedAt ?: saved.originalCreatedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MembersContent(
    state: GroupInfoUiState,
    viewModel: GroupInfoViewModel,
    onOpenMember: (UserDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4 && info.totalItemsCount > 0
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreMembers()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.group_members_search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.loadMembers(query) },
            enabled = !state.membersLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.membersLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.group_search))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when {
            state.membersLoading && state.members.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.members.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.group_members_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    items(state.members, key = { it.uid }) { member ->
                        MemberRow(
                            member = member,
                            onClick = {
                                onOpenMember(
                                    UserDto(
                                        uid = member.uid,
                                        avatarUrl = member.avatarUrl,
                                        name = member.username,
                                    ),
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                    if (state.membersLoadingMore) {
                        item(key = "members_loading_more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: MemberDto,
    onClick: () -> Unit,
) {
    val showUidInChat = LocalAppSettings.current.showUidInChat
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            url = member.avatarUrl,
            name = member.username,
            size = 48.dp,
            onClick = onClick,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.username?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.message_sender_unknown),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(
                    if (member.role == "admin") {
                        R.string.group_member_admin
                    } else {
                        R.string.group_member
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showUidInChat) {
            Text(
                text = stringResource(R.string.user_profile_uid, member.uid),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
