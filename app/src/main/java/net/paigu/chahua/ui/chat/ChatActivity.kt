package net.paigu.chahua.ui.chat

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.window.embedding.ActivityEmbeddingController
import androidx.core.content.FileProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.UserDto
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.AppLocale
import net.paigu.chahua.data.FontSizeOption
import net.paigu.chahua.data.models.StickerSummaryDto
import net.paigu.chahua.ui.common.AuthAsyncImage
import net.paigu.chahua.ui.common.UserAvatar
import net.paigu.chahua.ui.common.UserProfileDialog
import net.paigu.chahua.ui.common.formatTime
import net.paigu.chahua.ui.common.messagePreviewText
import net.paigu.chahua.ui.common.renderMentionsAsText
import net.paigu.chahua.ui.group.GroupInfoActivity
import net.paigu.chahua.ui.main.MainActivity
import net.paigu.chahua.ui.media.MediaViewerActivity
import net.paigu.chahua.ui.theme.ChahuaTheme
import net.paigu.chahua.ui.theme.LocalAppSettings
import java.io.File
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.launch

class ChatActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private val groupInfoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            finishToHome()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, AppGraph.settings.snapshot().language))
    }

    companion object {
        private const val EXTRA_CHAT_ID = "chat_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_THREAD_ID = "thread_root_id"
        private const val EXTRA_REPLY_COUNT = "reply_count"
        private const val EXTRA_MESSAGE_ID = "message_id"
        private const val EXTRA_ARCHIVED = "archived"

        fun createIntent(
            context: Context,
            chatId: String,
            title: String,
            messageId: String? = null,
        ): Intent =
            Intent(context, ChatActivity::class.java)
                .putExtra(EXTRA_CHAT_ID, chatId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE_ID, messageId)

        fun createThreadIntent(
            context: Context,
            chatId: String,
            title: String,
            threadRootId: String?,
            replyCount: Long,
            messageId: String? = null,
            archived: Boolean = false,
        ): Intent = Intent(context, ChatActivity::class.java)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_THREAD_ID, threadRootId)
            .putExtra(EXTRA_REPLY_COUNT, replyCount)
            .putExtra(EXTRA_MESSAGE_ID, messageId)
            .putExtra(EXTRA_ARCHIVED, archived)
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
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID)
        val archived = intent.getBooleanExtra(EXTRA_ARCHIVED, false)
        viewModel.init(chatId, title, threadId, replyCount, messageId)

        // 嵌入到右栏时隐藏返回按钮，右栏独占屏幕；手机/窄屏仍显示返回键。
        val embedded = ActivityEmbeddingController.getInstance(this).isActivityEmbedded(this)

        setContent {
            ChahuaTheme {
                ChatScreen(
                    viewModel = viewModel,
                    onBack = if (embedded) null else ({ finishToHome() }),
                    onOpenGroupInfo = { groupInfoLauncher.launch(GroupInfoActivity.createIntent(this, chatId)) },
                    threadArchived = archived,
                    onArchiveDone = { finishToHome() },
                    onOpenThread = { rootId ->
                        startActivity(
                            ChatActivity.createThreadIntent(
                                context = this,
                                chatId = chatId,
                                title = title,
                                threadRootId = rootId,
                                replyCount = 0L,
                            ),
                        )
                    },
                    onOpenMedia = { url, kind, fileName ->
                        startActivity(
                            MediaViewerActivity.createIntent(this, url, kind, fileName),
                        )
                    },
                )
            }
        }
    }

    /** 聊天页返回时先回主页：任务栈里有 MainActivity 时直接关闭；
     *  从通知冷启动时聊天页是栈根，则先拉起主页再关闭，保证下一次返回才退出。 */
    private fun finishToHome() {
        if (isTaskRoot()) {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                ),
            )
        }
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: (() -> Unit)?,
    onOpenGroupInfo: () -> Unit = {},
    onOpenThread: (String) -> Unit = {},
    threadArchived: Boolean = false,
    onArchiveDone: () -> Unit = {},
    onOpenMedia: (url: String, kind: String, fileName: String?) -> Unit,
    consumeNavigationBarsInset: Boolean = true,
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val latencyMs by viewModel.latencyMs.collectAsState()
    val context = LocalContext.current
    val me = viewModel.myUser()
    val appSettings = LocalAppSettings.current
    val enterToSend = appSettings.enterToSend
    val mentionCandidates by viewModel.mentionCandidates.collectAsState()

    var input by remember { mutableStateOf(TextFieldValue("")) }
    val activeMentionQuery = remember(input) { extractActiveMentionQuery(input.text) }

    LaunchedEffect(activeMentionQuery) {
        if (activeMentionQuery == null) {
            viewModel.clearMentionCandidates()
        } else {
            viewModel.searchMentionCandidates(activeMentionQuery)
        }
    }
    var editingMessage by remember { mutableStateOf<MessageDto?>(null) }
    var profileUser by remember { mutableStateOf<UserDto?>(null) }
    var draft by remember { mutableStateOf<DraftAttachment?>(null) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showEmojiPanel by remember { mutableStateOf(false) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var showVideoDialog by remember { mutableStateOf(false) }
    var compressingVideo by remember { mutableStateOf(false) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var cameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    var restoreScrollKey by remember { mutableStateOf<String?>(null) }

    fun sendCurrentMessage() {
        val text = input.text.trim()
        val attachment = draft
        val editing = editingMessage
        if (text.isEmpty() && attachment == null && editing == null) return
        if (editing != null) {
            if (text.isNotBlank()) {
                viewModel.editMessage(
                    messageId = editing.id,
                    newText = text,
                    attachmentIds = editing.attachments.map { it.id },
                )
            }
            editingMessage = null
            input = TextFieldValue("")
            draft = null
            showEmojiPanel = false
            return
        }
        viewModel.sendDraft(text, attachment)
        input = TextFieldValue("")
        draft = null
        editingMessage = null
        showEmojiPanel = false
    }

    fun addDraft(uri: Uri, kind: String) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: when (kind) {
            "image" -> "image/jpeg"
            "video" -> "video/mp4"
            else -> "application/octet-stream"
        }
        val name = queryDisplayName(context, uri)
            ?: "file_${System.currentTimeMillis()}"
        draft = DraftAttachment(
            uriString = uri.toString(),
            mimeType = mime,
            fileName = name,
            kind = kind,
        )
        showEmojiPanel = false
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
        if (messages.isNotEmpty() && uiState.scrollToMessageId == null && nearBottom) {
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(uiState.scrollToMessageId, messages) {
        val target = uiState.scrollToMessageId ?: return@LaunchedEffect
        val index = messages.indexOfFirst {
            it is ChatItem.Server && it.message.id == target
        }
        if (index >= 0) {
            listState.scrollToItem(messages.lastIndex - index)
            viewModel.clearScrollTarget()
        }
    }
    LaunchedEffect(messages) {
        val key = restoreScrollKey ?: return@LaunchedEffect
        val index = messages.indexOfFirst { chatItemKey(it) == key }
        if (index >= 0) {
            listState.scrollToItem(messages.lastIndex - index)
            restoreScrollKey = null
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
        if (uri != null) addDraft(uri, "image")
    }
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            pendingVideoUri = uri
            showVideoDialog = true
        }
    }
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) addDraft(uri, "file")
    }
    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        if (ok) cameraPhotoUri?.let { addDraft(it, "image") }
    }
    val takeVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { ok ->
        if (ok) cameraVideoUri?.let {
            pendingVideoUri = it
            showVideoDialog = true
        }
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
                        Text(
                            text = uiState.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                    val statusText = when (connectionState) {
                        net.paigu.chahua.data.WsStatus.CONNECTED -> stringResource(R.string.chat_status_online)
                        net.paigu.chahua.data.WsStatus.CONNECTING -> stringResource(R.string.chat_status_connecting)
                        net.paigu.chahua.data.WsStatus.DISCONNECTED -> stringResource(R.string.chat_status_offline)
                    }
                    val latencyDisplay = if (appSettings.showLatency &&
                        connectionState == net.paigu.chahua.data.WsStatus.CONNECTED
                    ) {
                        latencyMs?.let { stringResource(R.string.chat_latency_value, it) } ?: "--"
                    } else {
                        null
                    }
                    val statusHeightModifier = if (uiState.threadMode) {
                        Modifier
                            .height(48.dp)
                            .wrapContentHeight(Alignment.CenterVertically)
                    } else {
                        Modifier
                    }
                    Row(
                        modifier = Modifier.padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = statusHeightModifier,
                            color = if (connectionState == net.paigu.chahua.data.WsStatus.CONNECTED) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (latencyDisplay != null) {
                            Text(
                                text = " · ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = latencyDisplay,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = statusHeightModifier,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (uiState.threadMode) {
                        IconButton(
                            onClick = {
                                if (threadArchived) {
                                    viewModel.unarchiveCurrentThread(onArchiveDone)
                                } else {
                                    viewModel.archiveCurrentThread(onArchiveDone)
                                }
                            },
                        ) {
                            Icon(
                                Icons.Filled.NotificationsOff,
                                contentDescription = stringResource(
                                    if (threadArchived) {
                                        R.string.chat_unarchive
                                    } else {
                                        R.string.chat_archive
                                    },
                                ),
                            )
                        }
                    } else {
                        IconButton(onClick = onOpenGroupInfo) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = stringResource(R.string.chat_group_info),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Column(
                // 输入区 Surface 延伸到透明导航栏后面（沉浸式），
                // 输入控件本身通过 Row 的 navigationBarsPadding 避开导航按钮。
                modifier = Modifier.imePadding(),
            ) {
                if (activeMentionQuery != null && mentionCandidates.isNotEmpty()) {
                    MentionSuggestions(
                        candidates = mentionCandidates,
                        onSelect = { user ->
                            input = replaceMentionToken(input, user.uid)
                        },
                    )
                }
                editingMessage?.let { editing ->
                    EditBanner(
                        message = editing,
                        onDismiss = {
                            editingMessage = null
                            input = TextFieldValue("")
                        },
                    )
                }
                ReplyBanner(
                    replyTarget = uiState.replyTarget,
                    onDismiss = { viewModel.setReplyTarget(null) },
                )
                draft?.let { current ->
                    DraftAttachmentPreview(
                        draft = current,
                        onRemove = { draft = null },
                    )
                }
                if (compressingVideo) {
                    Text(
                        text = stringResource(R.string.chat_video_compressing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (showEmojiPanel) {
                    StickerPanel(
                        viewModel = viewModel,
                        onSendSticker = { sticker ->
                            viewModel.sendSticker(sticker)
                        },
                    )
                }
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (consumeNavigationBarsInset) {
                                    Modifier.navigationBarsPadding()
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        IconButton(
                            onClick = {
                                showAttachMenu = true
                                showEmojiPanel = false
                                keyboard?.hide()
                            },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.chat_attach),
                                modifier = Modifier.size(24.dp),
                            )
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
                            shape = RoundedCornerShape(10.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                        IconButton(
                            onClick = {
                                showEmojiPanel = !showEmojiPanel
                                showAttachMenu = false
                                if (showEmojiPanel) keyboard?.hide()
                            },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                Icons.Filled.EmojiEmotions,
                                contentDescription = stringResource(R.string.chat_emoji),
                                tint = if (showEmojiPanel) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(
                            onClick = { sendCurrentMessage() },
                            enabled = (input.text.isNotBlank() || draft != null) && !compressingVideo,
                            modifier = Modifier.size(56.dp),
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
                ) {
                    itemsIndexed(
                        items = messages.asReversed(),
                        key = { _, item ->
                            when (item) {
                                is ChatItem.Server -> "s:${item.message.id}"
                                is ChatItem.Pending -> "p:${item.pending.clientGeneratedId}"
                            }
                        },
                    ) { index, item ->
                        val grouping = !appSettings.showAvatarsInMessages
                        val chronologicalIndex = messages.lastIndex - index
                        val previousItem = messages.getOrNull(chronologicalIndex - 1)
                        val newerItem = messages.getOrNull(messages.lastIndex - index + 1)
                        fun sameSender(a: ChatItem, b: ChatItem): Boolean = when {
                            a is ChatItem.Server && b is ChatItem.Server ->
                                a.message.sender.uid == b.message.sender.uid
                            a is ChatItem.Pending && b is ChatItem.Pending -> true
                            a is ChatItem.Pending && b is ChatItem.Server ->
                                viewModel.myUid() == b.message.sender.uid
                            a is ChatItem.Server && b is ChatItem.Pending ->
                                a.message.sender.uid == viewModel.myUid()
                            else -> false
                        }
                        fun burstClose(a: ChatItem, b: ChatItem): Boolean {
                            val aTime = when (a) {
                                is ChatItem.Server -> a.message.createdAt
                                is ChatItem.Pending -> a.pending.createdAt
                            }
                            val bTime = when (b) {
                                is ChatItem.Server -> b.message.createdAt
                                is ChatItem.Pending -> b.pending.createdAt
                            }
                            if (aTime == null || bTime == null) return true
                            return try {
                                kotlin.math.abs(
                                    Duration.between(Instant.parse(aTime), Instant.parse(bTime)).toMinutes(),
                                ) <= 10
                            } catch (e: Exception) {
                                true
                            }
                        }
                        val sameSenderWithPrevious = previousItem != null &&
                            sameSender(item, previousItem) &&
                            burstClose(item, previousItem)
                        val sameSenderWithNext = newerItem != null &&
                            sameSender(item, newerItem) &&
                            burstClose(item, newerItem)
                        val showAvatar = !grouping || !sameSenderWithNext
                        val showSenderName = !grouping || !sameSenderWithPrevious
                        val itemModifier = Modifier.padding(
                            top = if (grouping && sameSenderWithPrevious) 0.dp else 6.dp,
                        )
                        when (item) {
                            is ChatItem.Server -> MessageBubble(
                                message = item.message,
                                mine = item.message.sender.uid == viewModel.myUid(),
                                myAvatarUrl = me.avatarUrl,
                                myName = me.name,
                                myUid = viewModel.myUid(),
                                modifier = itemModifier,
                                showAvatar = showAvatar,
                                showSenderName = showSenderName,
                                threadMode = uiState.threadMode,
                                onOpenMedia = onOpenMedia,
                                onReply = { viewModel.setReplyTarget(item.message) },
                                onOpenReply = {
                                    item.message.replyToMessage?.id?.let(viewModel::jumpToMessage)
                                },
                                onOpenThread = { onOpenThread(item.message.id) },
                                onAvatarClick = { profileUser = it },
                                onEdit = {
                                    editingMessage = item.message
                                    input = TextFieldValue(
                                        text = item.message.message.orEmpty(),
                                        selection = TextRange(item.message.message?.length ?: 0),
                                    )
                                    draft = null
                                    showEmojiPanel = false
                                },
                                onReact = { viewModel.toggleReaction(item.message, "\u2764\uFE0F") },
                                onDelete = {
                                    restoreScrollKey = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull()
                                        ?.key as? String
                                    viewModel.deleteMessage(item.message)
                                },
                            )
                            is ChatItem.Pending -> PendingBubble(
                                pending = item.pending,
                                myAvatarUrl = me.avatarUrl,
                                myName = me.name,
                                myUid = viewModel.myUid(),
                                modifier = itemModifier,
                                showAvatar = showAvatar,
                                onAvatarClick = { profileUser = it },
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

    if (showAttachMenu) {
        AttachMenuSheet(
            onDismiss = { showAttachMenu = false },
            onPickPhoto = {
                showAttachMenu = false
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onPickVideo = {
                showAttachMenu = false
                pickVideo.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                )
            },
            onTakePhoto = {
                showAttachMenu = false
                cameraPhotoUri = createTempUri(context, "jpg")
                takePhoto.launch(cameraPhotoUri!!)
            },
            onTakeVideo = {
                showAttachMenu = false
                cameraVideoUri = createTempUri(context, "mp4")
                takeVideo.launch(cameraVideoUri!!)
            },
            onPickFile = {
                showAttachMenu = false
                openDocument.launch(arrayOf("*/*"))
            },
        )
    }

    val videoUri = pendingVideoUri
    if (videoUri != null && showVideoDialog) {
        AlertDialog(
            onDismissRequest = {
                showVideoDialog = false
                pendingVideoUri = null
            },
            title = { Text(stringResource(R.string.chat_video_compress_title)) },
            text = { Text(stringResource(R.string.chat_video_compress_question)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showVideoDialog = false
                        pendingVideoUri = null
                        compressingVideo = true
                        viewModel.compressVideo(
                            uriString = videoUri.toString(),
                            onReady = { outputUri ->
                                compressingVideo = false
                                draft = DraftAttachment(
                                    uriString = outputUri,
                                    mimeType = "video/mp4",
                                    fileName = "compressed_${System.currentTimeMillis()}.mp4",
                                    kind = "video",
                                    compressVideo = true,
                                )
                            },
                            onError = { message ->
                                compressingVideo = false
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.chat_video_compress_failed,
                                        message,
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    },
                ) {
                    Text(stringResource(R.string.chat_video_compress))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showVideoDialog = false
                        pendingVideoUri = null
                        addDraft(videoUri, "video")
                    },
                ) {
                    Text(stringResource(R.string.chat_video_no_compress))
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

private fun createTempUri(context: Context, extension: String): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File.createTempFile("capture_", ".$extension", dir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}

private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (e: Exception) {
    null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachMenuSheet(
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onTakePhoto: () -> Unit,
    onTakeVideo: () -> Unit,
    onPickFile: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.chat_attach),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AttachOption(
                    icon = Icons.Filled.Image,
                    label = stringResource(R.string.chat_attach_photo),
                    onClick = onPickPhoto,
                )
                AttachOption(
                    icon = Icons.Filled.VideoLibrary,
                    label = stringResource(R.string.chat_attach_video),
                    onClick = onPickVideo,
                )
                AttachOption(
                    icon = Icons.Filled.CameraAlt,
                    label = stringResource(R.string.chat_take_photo),
                    onClick = onTakePhoto,
                )
                AttachOption(
                    icon = Icons.Filled.Videocam,
                    label = stringResource(R.string.chat_take_video),
                    onClick = onTakeVideo,
                )
                AttachOption(
                    icon = Icons.Filled.Folder,
                    label = stringResource(R.string.chat_attach_file),
                    onClick = onPickFile,
                )
            }
        }
    }
}

@Composable
private fun AttachOption(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun DraftAttachmentPreview(
    draft: DraftAttachment,
    onRemove: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (draft.kind) {
                "image" -> AuthAsyncImage(
                    url = draft.uriString,
                    contentDescription = draft.fileName,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                "video" -> Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.VideoLibrary,
                        contentDescription = draft.fileName,
                        modifier = Modifier.size(24.dp),
                    )
                }
                else -> Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = draft.fileName,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = draft.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (draft.kind == "video") {
                    Text(
                        text = stringResource(
                            if (draft.compressVideo) {
                                R.string.chat_video_compressed
                            } else {
                                R.string.chat_video_original
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.chat_draft_remove),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerPanel(
    viewModel: ChatViewModel,
    onSendSticker: (StickerSummaryDto) -> Unit,
) {
    val state by viewModel.stickerPanel.collectAsState()
    val context = LocalContext.current
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadStickerPanel()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.dismissStickerError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val safeIndex = selectedTabIndex.coerceIn(0, state.packs.size)
        SecondaryScrollableTabRow(selectedTabIndex = safeIndex, edgePadding = 8.dp) {
            Tab(
                selected = safeIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = {
                    Text(
                        stringResource(R.string.chat_emoji_favorites),
                        maxLines = 1,
                    )
                },
            )
            state.packs.forEachIndexed { index, pack ->
                Tab(
                    selected = safeIndex == index + 1,
                    onClick = {
                        selectedTabIndex = index + 1
                        viewModel.selectStickerPack(pack.id)
                    },
                    text = {
                        Text(
                            text = pack.name,
                            maxLines = 1,
                        )
                    },
                )
            }
        }

        if (state.loadingPacks && state.packs.isEmpty() && state.favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (safeIndex == 0) {
            if (state.favorites.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.chat_emoji_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    gridItems(state.favorites, key = { it.id }) { sticker ->
                        StickerCell(sticker = sticker, onClick = { onSendSticker(sticker) })
                    }
                }
            }
        } else {
            val pack = state.packs.getOrNull(safeIndex - 1)
            if (pack == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.chat_emoji_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val detail = state.details[pack.id]
                if (detail == null) {
                    LaunchedEffect(pack.id) {
                        viewModel.selectStickerPack(pack.id)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (detail.stickers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_emoji_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        gridItems(detail.stickers, key = { it.id }) { sticker ->
                            StickerCell(sticker = sticker, onClick = { onSendSticker(sticker) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerCell(
    sticker: StickerSummaryDto,
    onClick: () -> Unit,
) {
    AuthAsyncImage(
        url = sticker.media.url,
        contentDescription = sticker.emoji,
        modifier = Modifier
            .size(72.dp)
            .clickable(onClick = onClick),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun MentionSuggestions(
    candidates: List<UserDto>,
    onSelect: (UserDto) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
    ) {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 12.dp,
                vertical = 6.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(candidates, key = { it.uid }) { user ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { onSelect(user) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(
                        url = user.avatarUrl,
                        name = user.name,
                        size = 24.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = user.name ?: stringResource(R.string.message_sender_unknown),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditBanner(
    message: MessageDto,
    onDismiss: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_edit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = renderMentionsAsText(message.message, message.mentions)
                        .ifBlank { messagePreviewText(message.message, message.messageType) },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.chat_cancel_edit),
                    modifier = Modifier.size(16.dp),
                )
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
                    renderMentionsAsText(replyTarget.message, replyTarget.mentions).ifBlank { "…" },
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
    myUid: Int,
    modifier: Modifier = Modifier,
    showAvatar: Boolean,
    showSenderName: Boolean,
    threadMode: Boolean,
    onOpenMedia: (url: String, kind: String, fileName: String?) -> Unit,
    onReply: () -> Unit,
    onOpenReply: () -> Unit,
    onOpenThread: () -> Unit,
    onAvatarClick: (UserDto) -> Unit,
    onEdit: () -> Unit,
    onReact: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val fontScale = FontSizeOption.from(LocalAppSettings.current.fontSizeKey).scale
    val showUidInChat = LocalAppSettings.current.showUidInChat
    val messageStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = (16 * fontScale).sp)
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val maxStickerHeight = (configuration.screenHeightDp * 0.2f).dp
    val replyThreshold = with(density) { 72.dp.toPx() }
    val maxSlide = with(density) { 160.dp.toPx() }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var replyTriggered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val slideOffset = remember { Animatable(0f) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) {
            if (showAvatar) {
                UserAvatar(
                    url = message.sender.avatarUrl,
                    name = message.sender.name,
                    modifier = Modifier.padding(top = 20.dp),
                    size = 32.dp,
                    onClick = { onAvatarClick(message.sender) },
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            if (!mine && showSenderName && message.sender.name != null) {
                Text(
                    text = if (showUidInChat) {
                        stringResource(
                            R.string.chat_sender_name_with_uid,
                            message.sender.name,
                            message.sender.uid,
                        )
                    } else {
                        message.sender.name
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            val stickerOnly = message.sticker != null &&
                message.replyToMessage == null &&
                message.message.isNullOrBlank()
            Box {
                Column(
                    modifier = Modifier
                        .graphicsLayer { translationX = slideOffset.value }
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (mine) 16.dp else 4.dp,
                                bottomEnd = if (mine) 4.dp else 16.dp,
                            ),
                        )
                        .pointerInput(onReply) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragDistance <= -replyThreshold && !replyTriggered) {
                                        onReply()
                                    }
                                    dragDistance = 0f
                                    replyTriggered = false
                                    scope.launch { slideOffset.animateTo(0f) }
                                },
                                onDragCancel = {
                                    dragDistance = 0f
                                    replyTriggered = false
                                    scope.launch { slideOffset.animateTo(0f) }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragDistance = (dragDistance + dragAmount)
                                        .coerceIn(-maxSlide, 0f)
                                    scope.launch { slideOffset.snapTo(dragDistance) }
                                    if (dragDistance <= -replyThreshold && !replyTriggered) {
                                        replyTriggered = true
                                        onReply()
                                        scope.launch { slideOffset.animateTo(0f) }
                                    }
                                },
                            )
                        }
                        .background(
                            if (stickerOnly) {
                                Color.Transparent
                            } else if (mine) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .combinedClickable(
                            onClick = { },
                            onLongClick = { menuExpanded = true },
                        )
                        .padding(if (stickerOnly) 0.dp else 10.dp),
                ) {
                    message.replyToMessage?.let { reply ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clickable(onClick = onOpenReply),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(
                                    text = reply.sender?.name ?: stringResource(R.string.chat_reply_message),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = renderMentionsAsText(reply.message, reply.mentions).ifBlank { "…" },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (!message.message.isNullOrBlank()) {
                        Text(
                            text = renderMentionsAsText(message.message, message.mentions),
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
                                .widthIn(max = 200.dp)
                                .heightIn(max = maxStickerHeight)
                                .clickable { onOpenMedia(stickerUrl, "image", null) },
                            contentScale = ContentScale.Fit,
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
                                        .clickable { onOpenMedia(attachment.url, "image", attachment.fileName) },
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            attachment.kind.startsWith("video") -> {
                                Box(
                                    modifier = Modifier
                                        .size(width = 200.dp, height = 120.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            onOpenMedia(attachment.url, "video", attachment.fileName)
                                        },
                                ) {
                                    if (attachment.fileName.isNotBlank()) {
                                        Text(
                                            text = attachment.fileName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(8.dp),
                                        )
                                    }
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
                    if (mine && (!message.message.isNullOrBlank() || message.attachments.isNotEmpty())) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_edit)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                    }
                    if (!threadMode) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_thread)) },
                            leadingIcon = { Icon(Icons.Filled.Forum, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenThread()
                            },
                        )
                    }
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
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_copy_link)) },
                        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            val link = buildPermalinkUrl(
                                chatId = message.chatId,
                                messageId = message.id,
                                apiBase = AppGraph.session.snapshot().serverUrl,
                            )
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("message link", link))
                            Toast.makeText(context, R.string.chat_link_copied, Toast.LENGTH_SHORT).show()
                        },
                    )
                    if (mine) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_recall)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            message.threadInfo?.let { info ->
                if (!threadMode && info.replyCount > 0) {
                    Text(
                        text = stringResource(R.string.thread_reply_count, info.replyCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenThread() }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
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
            if (showAvatar) {
                Spacer(modifier = Modifier.width(8.dp))
                UserAvatar(
                    url = myAvatarUrl,
                    name = myName,
                    modifier = Modifier.padding(top = 20.dp),
                    size = 32.dp,
                    onClick = {
                        onAvatarClick(
                            UserDto(
                                uid = myUid,
                                avatarUrl = myAvatarUrl,
                                name = myName,
                            ),
                        )
                    },
                )
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }
    }
}

private fun chatItemKey(item: ChatItem): String = when (item) {
    is ChatItem.Server -> "s:${item.message.id}"
    is ChatItem.Pending -> "p:${item.pending.clientGeneratedId}"
}

private fun extractActiveMentionQuery(input: String): String? {
    val atIndex = input.indexOfLast { it == '@' }
    if (atIndex < 0) return null
    if (atIndex > 0 && !input[atIndex - 1].isWhitespace()) return null
    val tail = input.substring(atIndex + 1)
    if (tail.any { it.isWhitespace() }) return null
    return tail
}

private fun replaceMentionToken(input: TextFieldValue, uid: Int): TextFieldValue {
    val text = input.text
    val atIndex = text.indexOfLast { it == '@' }
    if (atIndex < 0) return input
    val tail = text.substring(atIndex + 1)
    val queryLength = tail.takeWhile { !it.isWhitespace() }.length
    val end = atIndex + 1 + queryLength
    val beforeSpace = if (atIndex > 0 && text[atIndex - 1] != ' ') " " else ""
    val newText = text.substring(0, atIndex) + beforeSpace + "@[uid:$uid] " + text.substring(end)
    val cursor = atIndex + beforeSpace.length + "@[uid:$uid] ".length
    return TextFieldValue(
        text = newText,
        selection = TextRange(cursor),
    )
}

private fun buildPermalinkUrl(chatId: String, messageId: String, apiBase: String): String {
    val uri = Uri.parse(apiBase.trimEnd('/'))
    val origin = "${uri.scheme}://${uri.authority}"
    val bytes = ByteBuffer.allocate(16)
        .putLong(chatId.toLong())
        .putLong(messageId.toLong())
        .array()
    val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    return "$origin/m/$encoded"
}

@Composable
private fun PendingBubble(
    pending: PendingMessage,
    myAvatarUrl: String?,
    myName: String?,
    myUid: Int,
    showAvatar: Boolean,
    onAvatarClick: (UserDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
                pending.sticker?.let { sticker ->
                    AuthAsyncImage(
                        url = sticker.media.url,
                        contentDescription = sticker.emoji,
                        modifier = Modifier
                            .size(80.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                pending.attachmentLocalUri?.let { uri ->
                    when (pending.attachmentKind) {
                        "image" -> AuthAsyncImage(
                            url = uri,
                            contentDescription = stringResource(R.string.chat_send_image),
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        "video" -> Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.VideoLibrary,
                                contentDescription = stringResource(R.string.chat_video),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = pending.text ?: stringResource(R.string.chat_file),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                pending.text?.let {
                    Text(
                        text = renderMentionsAsText(it, emptyList()),
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
        if (showAvatar) {
            Spacer(modifier = Modifier.width(8.dp))
            UserAvatar(
                url = myAvatarUrl,
                name = myName,
                modifier = Modifier.padding(top = 20.dp),
                size = 32.dp,
                onClick = {
                    onAvatarClick(
                        UserDto(
                            uid = myUid,
                            avatarUrl = myAvatarUrl,
                            name = myName,
                        ),
                    )
                },
            )
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
    }
}
