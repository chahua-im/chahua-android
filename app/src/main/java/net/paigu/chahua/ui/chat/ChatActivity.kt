package net.paigu.chahua.ui.chat

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.UserDto
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.AppLocale
import net.paigu.chahua.data.FontSizeOption
import net.paigu.chahua.ui.common.AuthAsyncImage
import net.paigu.chahua.ui.common.UserAvatar
import net.paigu.chahua.ui.common.formatTime
import net.paigu.chahua.ui.media.MediaViewerActivity
import net.paigu.chahua.ui.theme.ChahuaTheme
import net.paigu.chahua.ui.theme.LocalAppSettings

class ChatActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, AppGraph.settings.snapshot().language))
    }

    companion object {
        private const val EXTRA_CHAT_ID = "chat_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_THREAD_ID = "thread_root_id"
        private const val EXTRA_REPLY_COUNT = "reply_count"

        fun createIntent(context: Context, chatId: String, title: String): Intent =
            Intent(context, ChatActivity::class.java)
                .putExtra(EXTRA_CHAT_ID, chatId)
                .putExtra(EXTRA_TITLE, title)

        fun createThreadIntent(
            context: Context,
            chatId: String,
            title: String,
            threadRootId: String?,
            replyCount: Long,
        ): Intent = Intent(context, ChatActivity::class.java)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_THREAD_ID, threadRootId)
            .putExtra(EXTRA_REPLY_COUNT, replyCount)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: run {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.tab_chats)
        val threadId = intent.getStringExtra(EXTRA_THREAD_ID)
        val replyCount = intent.getLongExtra(EXTRA_REPLY_COUNT, 0L)
        viewModel.init(chatId, title, threadId, replyCount)

        setContent {
            ChahuaTheme {
                ChatScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onOpenMedia = { url, kind ->
                        startActivity(
                            MediaViewerActivity.createIntent(this, url, kind, title),
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: (() -> Unit)?,
    onOpenMedia: (url: String, kind: String) -> Unit,
    consumeNavigationBarsInset: Boolean = true,
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val context = LocalContext.current
    val me = viewModel.myUser()
    val enterToSend = LocalAppSettings.current.enterToSend

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun sendCurrentMessage() {
        val text = input.trim()
        if (text.isNotEmpty()) {
            viewModel.sendText(text)
            input = ""
        }
    }

    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.visibleItemsInfo.firstOrNull()?.index ?: 0 <= 3
        }
    }
    val needOlder by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3 && info.totalItemsCount > 30
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && nearBottom) {
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(needOlder) {
        if (needOlder) viewModel.loadOlder()
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.dismissError()
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.sendImage(uri.toString())
    }

    Scaffold(
        contentWindowInsets = if (consumeNavigationBarsInset) {
            ScaffoldDefaults.contentWindowInsets
        } else {
            WindowInsets(0, 0, 0, 0)
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (uiState.threadMode) {
                            Text(
                                text = stringResource(R.string.chat_thread_subtitle, uiState.threadReplyCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.chat_back),
                            )
                        }
                    }
                },
                actions = {
                    Text(
                        text = when (connectionState) {
                            net.paigu.chahua.data.WsStatus.CONNECTED -> stringResource(R.string.chat_status_online)
                            net.paigu.chahua.data.WsStatus.CONNECTING -> stringResource(R.string.chat_status_connecting)
                            net.paigu.chahua.data.WsStatus.DISCONNECTED -> stringResource(R.string.chat_status_offline)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connectionState == net.paigu.chahua.data.WsStatus.CONNECTED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Column(
                modifier = if (consumeNavigationBarsInset) {
                    Modifier.navigationBarsPadding().imePadding()
                } else {
                    Modifier.imePadding()
                },
            ) {
                ReplyBanner(
                    replyTarget = uiState.replyTarget,
                    onDismiss = { viewModel.setReplyTarget(null) },
                )
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        IconButton(onClick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }) {
                            Icon(Icons.Filled.Image, contentDescription = stringResource(R.string.chat_send_image))
                        }
                        TextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier
                                .weight(1f)
                                .onPreviewKeyEvent { event ->
                                    if (
                                        enterToSend &&
                                        event.type == KeyEventType.KeyDown &&
                                        event.key == Key.Enter &&
                                        !event.isShiftPressed
                                    ) {
                                        sendCurrentMessage()
                                        true
                                    } else {
                                        false
                                    }
                                },
                            placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default,
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = { if (enterToSend) sendCurrentMessage() },
                            ),
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                        IconButton(
                            onClick = { sendCurrentMessage() },
                            enabled = input.isNotBlank(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.chat_send),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.loading && messages.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (messages.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_empty),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(
                        items = messages.asReversed(),
                        key = { item ->
                            when (item) {
                                is ChatItem.Server -> "s:${item.message.id}"
                                is ChatItem.Pending -> "p:${item.pending.clientGeneratedId}"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is ChatItem.Server -> MessageBubble(
                                message = item.message,
                                mine = item.message.sender.uid == viewModel.myUid(),
                                myAvatarUrl = me.avatarUrl,
                                myName = me.name,
                                onOpenMedia = onOpenMedia,
                                onReply = { viewModel.setReplyTarget(item.message) },
                                onReact = { viewModel.toggleReaction(item.message, "\u2764\uFE0F") },
                                onDelete = { viewModel.deleteMessage(item.message) },
                            )
                            is ChatItem.Pending -> PendingBubble(
                                pending = item.pending,
                                myAvatarUrl = me.avatarUrl,
                                myName = me.name,
                            )
                        }
                    }
                    if (uiState.loadingOlder) {
                        item(key = "loading_older") {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyBanner(
    replyTarget: MessageDto?,
    onDismiss: () -> Unit,
) {
    if (replyTarget == null) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    R.string.chat_reply_preview,
                    replyTarget.sender.name ?: stringResource(R.string.chat_reply_message),
                    replyTarget.message ?: "…",
                ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.chat_cancel_reply),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageDto,
    mine: Boolean,
    myAvatarUrl: String?,
    myName: String?,
    onOpenMedia: (url: String, kind: String) -> Unit,
    onReply: () -> Unit,
    onReact: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val fontScale = FontSizeOption.from(LocalAppSettings.current.fontSizeKey).scale
    val messageStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = (16 * fontScale).sp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) {
            UserAvatar(
                url = message.sender.avatarUrl,
                name = message.sender.name,
                modifier = Modifier.padding(top = 20.dp),
                size = 32.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            if (!mine && message.sender.name != null) {
                Text(
                    text = message.sender.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            Box {
                Column(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (mine) 16.dp else 4.dp,
                                bottomEnd = if (mine) 4.dp else 16.dp,
                            ),
                        )
                        .background(
                            if (mine) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .combinedClickable(
                            onClick = { },
                            onLongClick = { menuExpanded = true },
                        )
                        .padding(10.dp),
                ) {
                    message.replyToMessage?.let { reply ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(
                                    text = reply.sender?.name ?: stringResource(R.string.chat_reply_message),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = reply.message ?: "…",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (!message.message.isNullOrBlank()) {
                        Text(
                            text = message.message,
                            style = messageStyle,
                            color = if (mine) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    message.sticker?.media?.url?.let { stickerUrl ->
                        AuthAsyncImage(
                            url = stickerUrl,
                            contentDescription = message.sticker?.emoji,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenMedia(stickerUrl, "image") },
                        )
                    }
                    message.attachments.forEach { attachment ->
                        when {
                            attachment.kind.startsWith("image") -> {
                                AuthAsyncImage(
                                    url = attachment.url,
                                    contentDescription = attachment.fileName,
                                    modifier = Modifier
                                        .widthIn(max = 220.dp)
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenMedia(attachment.url, "image") },
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            attachment.kind.startsWith("video") -> {
                                Box(
                                    modifier = Modifier
                                        .size(width = 200.dp, height = 120.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenMedia(attachment.url, "video") },
                                ) {
                                    AuthAsyncImage(
                                        url = attachment.url,
                                        contentDescription = attachment.fileName,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .align(Alignment.Center),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = stringResource(R.string.chat_play),
                                            tint = Color.White,
                                        )
                                    }
                                }
                            }
                            else -> {
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.AttachFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = attachment.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    if (message.reactions.isNotEmpty()) {
                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            message.reactions.forEach { reaction ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (reaction.reactedByMe == true) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .clickable(onClick = onReact),
                                ) {
                                    Text(
                                        text = "${reaction.emoji} ${reaction.count}",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_reply_to)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onReply()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_react)) },
                        leadingIcon = { Icon(Icons.Filled.MoreVert, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onReact()
                        },
                    )
                    if (!message.message.isNullOrBlank()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_copy)) },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("message", message.message))
                            },
                        )
                    }
                    if (mine) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_delete)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Text(
                text = formatTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                fontSize = 10.sp,
            )
        }
        if (mine) {
            Spacer(modifier = Modifier.width(8.dp))
            UserAvatar(
                url = myAvatarUrl,
                name = myName,
                modifier = Modifier.padding(top = 20.dp),
                size = 32.dp,
            )
        }
    }
}

@Composable
private fun PendingBubble(
    pending: PendingMessage,
    myAvatarUrl: String?,
    myName: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 280.dp)) {
            Row(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 4.dp,
                        ),
                    )
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pending.attachmentLocalUri?.let { uri ->
                    AuthAsyncImage(
                        url = uri,
                        contentDescription = stringResource(R.string.chat_send_image),
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                pending.text?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Text(
                text = stringResource(R.string.chat_sending),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        UserAvatar(
            url = myAvatarUrl,
            name = myName,
            modifier = Modifier.padding(top = 20.dp),
            size = 32.dp,
        )
    }
}
