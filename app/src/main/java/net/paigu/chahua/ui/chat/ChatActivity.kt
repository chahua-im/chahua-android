package net.paigu.chahua.ui.chat

import android.Manifest
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.PinDto
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
import net.paigu.chahua.ui.chat.components.AttachMenuSheet
import net.paigu.chahua.ui.chat.components.DraftAttachmentPreview
import net.paigu.chahua.ui.chat.components.EditBanner
import net.paigu.chahua.ui.chat.components.MentionSuggestions
import net.paigu.chahua.ui.chat.components.MessageBubble
import net.paigu.chahua.ui.chat.components.PendingBubble
import net.paigu.chahua.ui.chat.components.ReplyBanner
import net.paigu.chahua.ui.chat.components.StickerPanel
import net.paigu.chahua.ui.chat.components.VoicePhase
import net.paigu.chahua.ui.chat.components.VoiceRecorderRef
import net.paigu.chahua.ui.chat.components.VoiceRecordingPanel
import net.paigu.chahua.ui.chat.components.chatItemKey
import net.paigu.chahua.ui.group.GroupInfoActivity
import net.paigu.chahua.ui.main.MainActivity
import net.paigu.chahua.ui.media.MediaViewerActivity
import net.paigu.chahua.ui.media.MediaViewerItem
import net.paigu.chahua.ui.media.MediaSaver
import net.paigu.chahua.ui.theme.ChahuaTheme
import net.paigu.chahua.ui.theme.LocalAppSettings
import java.io.File
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 一次可选择的照片/视频数量上限。 */
private const val MAX_PICKED_ATTACHMENTS = 10

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
                    onOpenMedia = { items, index ->
                        startActivity(
                            MediaViewerActivity.createIntent(this, items, index),
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
    onOpenMedia: (items: List<MediaViewerItem>, index: Int) -> Unit,
    consumeNavigationBarsInset: Boolean = true,
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val latencyMs by viewModel.latencyMs.collectAsState()
    val quickReactions by viewModel.quickReactionEmojis.collectAsState()
    val context = LocalContext.current
    val me = viewModel.myUser()
    val appSettings = LocalAppSettings.current
    val enterToSend = appSettings.enterToSend
    val mentionCandidates by viewModel.mentionCandidates.collectAsState()

    var input by remember { mutableStateOf(TextFieldValue("")) }
    val activeMentionQuery = remember(input) { extractActiveMentionQuery(input.text) }

    LaunchedEffect(Unit) {
        val restored = viewModel.loadDraft()
        if (restored.isNotBlank()) {
            input = TextFieldValue(restored, selection = TextRange(restored.length))
        }
    }
    LaunchedEffect(input.text) {
        delay(400)
        viewModel.saveDraft(input.text)
    }

    LaunchedEffect(activeMentionQuery) {
        if (activeMentionQuery == null) {
            viewModel.clearMentionCandidates()
        } else {
            viewModel.searchMentionCandidates(activeMentionQuery)
        }
    }
    var editingMessage by remember { mutableStateOf<MessageDto?>(null) }
    var profileUser by remember { mutableStateOf<UserDto?>(null) }
    var profileFriendStatus by remember { mutableStateOf<Boolean?>(null) }
    var showAddFriendSheet by remember { mutableStateOf(false) }
    var reactionDetailsMessage by remember { mutableStateOf<MessageDto?>(null) }
    var emojiPickerMessage by remember { mutableStateOf<MessageDto?>(null) }
    var stickerPreviewId by remember { mutableStateOf<String?>(null) }
    var showPinList by remember { mutableStateOf(false) }
    var drafts by remember { mutableStateOf<List<DraftAttachment>>(emptyList()) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showEmojiPanel by remember { mutableStateOf(false) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var videoPickQueue by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showVideoDialog by remember { mutableStateOf(false) }
    var compressingVideo by remember { mutableStateOf(false) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var cameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var restoreScrollKey by remember { mutableStateOf<String?>(null) }
    var voicePhase by remember { mutableStateOf<VoicePhase?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var voiceDurationMs by remember { mutableStateOf(0L) }
    val voiceRecorderRef = remember { VoiceRecorderRef() }

    LaunchedEffect(profileUser) {
        val user = profileUser
        if (user == null) {
            profileFriendStatus = null
        } else {
            viewModel.isFriendWith(user.uid) { profileFriendStatus = it }
        }
    }

    fun beginVoiceRecording(appContext: Context) {
        if (voicePhase != null) return
        val dir = File(appContext.cacheDir, "voice").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        val started = runCatching {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
        }
        if (started.isSuccess) {
            voiceRecorderRef.recorder = recorder
            voiceFile = file
            voiceDurationMs = 0L
            voicePhase = VoicePhase.RECORDING
        } else {
            runCatching { recorder.release() }
            Toast.makeText(
                appContext,
                R.string.chat_voice_start_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val recordAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            beginVoiceRecording(context)
        } else {
            Toast.makeText(
                context,
                R.string.chat_voice_permission_denied,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun finishVoiceRecording() {
        runCatching { voiceRecorderRef.recorder?.stop() }
        runCatching { voiceRecorderRef.recorder?.release() }
        voiceRecorderRef.recorder = null
        voicePhase = VoicePhase.RECORDED
    }

    fun cancelVoiceRecording() {
        runCatching { voiceRecorderRef.recorder?.stop() }
        runCatching { voiceRecorderRef.recorder?.release() }
        voiceRecorderRef.recorder = null
        voiceFile?.delete()
        voiceFile = null
        voicePhase = null
    }

    fun sendVoiceRecording() {
        val file = voiceFile?.takeIf { it.exists() } ?: return
        voicePhase = null
        runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.onSuccess { uri ->
            viewModel.sendVoice(
                uriString = uri.toString(),
                mimeType = "audio/mp4",
                fileName = file.name,
                onDone = {
                    file.delete()
                    voiceFile = null
                },
            )
        }.onFailure {
            voiceFile = null
            Toast.makeText(
                context,
                context.getString(R.string.chat_voice_send_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(voicePhase) {
        while (voicePhase == VoicePhase.RECORDING) {
            delay(1000)
            voiceDurationMs += 1000
        }
    }

    fun requestVoiceRecording() {
        if (Build.VERSION.SDK_INT >= 23 &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            recordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            beginVoiceRecording(context)
        }
    }


    fun sendCurrentMessage() {
        val text = input.text.trim()
        val attachments = drafts
        val editing = editingMessage
        if (text.isEmpty() && attachments.isEmpty() && editing == null) return
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
            drafts = emptyList()
            showEmojiPanel = false
            return
        }
        if (attachments.isEmpty()) {
            viewModel.sendDraft(text, null)
        } else {
            // 多张图片合并为一条消息发送；视频/文件仍各自发送。
            val images = attachments.filter { it.kind == "image" }
            val others = attachments.filter { it.kind != "image" }
            if (images.isNotEmpty()) {
                viewModel.sendImagesBatch(text, images)
            }
            others.forEachIndexed { index, attachment ->
                viewModel.sendDraft(
                    if (images.isEmpty() && index == 0) text else "",
                    attachment,
                )
            }
        }
        input = TextFieldValue("")
        drafts = emptyList()
        editingMessage = null
        showEmojiPanel = false
    }

    fun addDraft(uri: Uri, kind: String, compressVideo: Boolean = false) {
        if (drafts.size >= MAX_PICKED_ATTACHMENTS) return
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: when (kind) {
            "image" -> "image/jpeg"
            "video" -> "video/mp4"
            else -> "application/octet-stream"
        }
        val name = queryDisplayName(context, uri)
            ?: "file_${System.currentTimeMillis()}"
        drafts = drafts + DraftAttachment(
            uriString = uri.toString(),
            mimeType = mime,
            fileName = name,
            kind = kind,
            compressVideo = compressVideo,
        )
        showEmojiPanel = false
    }

    /** 依次弹出待处理视频的压缩确认框。 */
    fun promptNextVideo() {
        val next = videoPickQueue.firstOrNull()
        if (next != null) {
            videoPickQueue = videoPickQueue.drop(1)
            pendingVideoUri = next
            showVideoDialog = true
        } else {
            pendingVideoUri = null
            showVideoDialog = false
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

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = MAX_PICKED_ATTACHMENTS,
        ),
    ) { uris ->
        uris.forEach { addDraft(it, "image") }
    }
    val pickVideos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = MAX_PICKED_ATTACHMENTS,
        ),
    ) { uris ->
        if (uris.isNotEmpty()) {
            videoPickQueue = uris
            promptNextVideo()
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
            videoPickQueue = listOf(it)
            promptNextVideo()
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
                        IconButton(
                            onClick = {
                                if (uiState.isDm) {
                                    uiState.peer?.let { profileUser = it.toUserDto() }
                                } else {
                                    onOpenGroupInfo()
                                }
                            },
                        ) {
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
                if (drafts.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        drafts.forEachIndexed { index, current ->
                            DraftAttachmentPreview(
                                draft = current,
                                onRemove = {
                                    drafts = drafts.filterIndexed { i, _ -> i != index }
                                },
                            )
                            if (index != drafts.lastIndex) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
                if (compressingVideo) {
                    Text(
                        text = stringResource(R.string.chat_video_compressing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                AnimatedVisibility(
                    visible = showEmojiPanel,
                    enter = expandVertically(tween(durationMillis = 220)) + fadeIn(
                        tween(durationMillis = 180),
                    ),
                    exit = shrinkVertically(tween(durationMillis = 160)) + fadeOut(
                        tween(durationMillis = 120),
                    ),
                ) {
                    StickerPanel(
                        viewModel = viewModel,
                        onSendSticker = { sticker ->
                            viewModel.sendSticker(sticker)
                        },
                    )
                }
                if (voicePhase == null) {
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
                            onClick = { requestVoiceRecording() },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = stringResource(R.string.chat_voice_record),
                            )
                        }
                        IconButton(
                            onClick = { sendCurrentMessage() },
                            enabled = (input.text.isNotBlank() || drafts.isNotEmpty()) &&
                                !compressingVideo,
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
                } else {
                    Surface(tonalElevation = 3.dp) {
                        VoiceRecordingPanel(
                            phase = voicePhase!!,
                            durationMs = voiceDurationMs,
                            onStop = { finishVoiceRecording() },
                            onCancel = { cancelVoiceRecording() },
                            onSend = { sendVoiceRecording() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (consumeNavigationBarsInset) {
                                        Modifier.navigationBarsPadding()
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
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
                        start = 12.dp,
                        top = if (uiState.pins.isNotEmpty() && !uiState.threadMode) 56.dp else 8.dp,
                        end = 12.dp,
                        bottom = 8.dp,
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
                        val itemModifier = Modifier
                            .padding(
                                top = if (grouping && sameSenderWithPrevious) 0.dp else 6.dp,
                            )
                            .animateItem()
                        when (item) {
                            is ChatItem.Server -> MessageBubble(
                                message = item.message,
                                mine = item.message.sender.uid == viewModel.myUid(),
                                myAvatarUrl = me.avatarUrl,
                                myName = me.name,
                                myUid = viewModel.myUid(),
                                isAdmin = uiState.myRole == "admin",
                                isPinned = uiState.pins.any { it.message.id == item.message.id },
                                modifier = itemModifier,
                                showAvatar = showAvatar,
                                showSenderName = showSenderName,
                                threadMode = uiState.threadMode,
                                onOpenMedia = onOpenMedia,
                                onDownloadFile = { url, fileName, kind ->
                                    scope.launch {
                                        runCatching {
                                            MediaSaver.downloadFile(
                                                context = context,
                                                url = url,
                                                fileName = fileName,
                                                mime = kind,
                                            )
                                        }
                                            .onSuccess {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.chat_file_downloaded),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                            .onFailure {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(
                                                        R.string.chat_file_download_failed,
                                                        it.message,
                                                    ),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                    }
                                },
                                onOpenSticker = { stickerId -> stickerPreviewId = stickerId },
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
                                    drafts = emptyList()
                                    showEmojiPanel = false
                                },
                                quickReactionEmojis = quickReactions,
                                onQuickReact = { emoji ->
                                    viewModel.toggleReaction(item.message, emoji)
                                },
                                onReactionToggle = { emoji ->
                                    viewModel.toggleReaction(item.message, emoji)
                                },
                                onOpenReactionDetails = {
                                    reactionDetailsMessage = item.message
                                },
                                onOpenEmojiPicker = {
                                    emojiPickerMessage = item.message
                                },
                                onDelete = {
                                    restoreScrollKey = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull()
                                        ?.key as? String
                                    viewModel.deleteMessage(item.message)
                                },
                                onSaveMessage = {
                                    viewModel.saveMessage(
                                        messageId = item.message.id,
                                        onDone = { toast ->
                                            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                },
                                onPinMessage = {
                                    viewModel.togglePin(
                                        message = item.message,
                                        onDone = { toast ->
                                            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                },
                                onFavoriteSticker = { favorite ->
                                    item.message.sticker?.id?.let { stickerId ->
                                        viewModel.setStickerFavorite(
                                            stickerId = stickerId,
                                            favorite = favorite,
                                            onDone = { toast ->
                                                Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    }
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
                if (uiState.pins.isNotEmpty() && !uiState.threadMode) {
                    PinBanner(
                        pin = uiState.pins.first(),
                        onClick = { showPinList = true },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            if (!nearBottom) {
                FloatingActionButton(
                    onClick = {
                        scope.launch { listState.scrollToItem(0) }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp)
                        .size(40.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Filled.ArrowDownward,
                        contentDescription = stringResource(R.string.chat_jump_to_latest),
                    )
                }
            }
        }
    }

    if (showAttachMenu) {
        AttachMenuSheet(
            onDismiss = { showAttachMenu = false },
            onPickPhoto = {
                showAttachMenu = false
                pickImages.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onPickVideo = {
                showAttachMenu = false
                pickVideos.launch(
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
                promptNextVideo()
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
                                addDraft(
                                    uri = Uri.parse(outputUri),
                                    kind = "video",
                                    compressVideo = true,
                                )
                                pendingVideoUri = null
                                promptNextVideo()
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
                                pendingVideoUri = null
                                promptNextVideo()
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
                        addDraft(videoUri, "video")
                        pendingVideoUri = null
                        promptNextVideo()
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
            onMessage = if (profileFriendStatus == true) {
                {
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
                }
            } else {
                null
            },
            onAddFriend = if (profileFriendStatus == false) {
                { showAddFriendSheet = true }
            } else {
                null
            },
        )
    }

    if (showAddFriendSheet) {
        val user = profileUser
        if (user != null) {
            AddFriendSheet(
                user = user,
                viewModel = viewModel,
                onDismiss = { showAddFriendSheet = false },
            )
        }
    }

    reactionDetailsMessage?.let { message ->
        ReactionDetailsSheet(
            messageId = message.id,
            loadDetails = { messageId -> viewModel.reactionDetails(messageId) },
            onDismiss = { reactionDetailsMessage = null },
            onAvatarClick = { profileUser = it },
        )
    }

    emojiPickerMessage?.let { message ->
        ReactionEmojiPickerSheet(
            onSelect = { emoji ->
                emojiPickerMessage = null
                viewModel.toggleReaction(message, emoji)
            },
            onDismiss = { emojiPickerMessage = null },
        )
    }

    stickerPreviewId?.let { stickerId ->
        StickerPreviewSheet(
            viewModel = viewModel,
            stickerId = stickerId,
            myUid = viewModel.myUid(),
            onDismiss = { stickerPreviewId = null },
            onManagePack = {
                stickerPreviewId = null
                Toast.makeText(
                    context,
                    context.getString(R.string.sticker_preview_manage_hint),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }

    if (showPinList) {
        AlertDialog(
            onDismissRequest = { showPinList = false },
            title = { Text(stringResource(R.string.chat_pin_list)) },
            text = {
                if (uiState.pins.isEmpty()) {
                    Text(stringResource(R.string.chat_pin_empty))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(
                            items = uiState.pins,
                            key = { it.id },
                        ) { pin ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showPinList = false
                                        viewModel.jumpToMessage(pin.message.id)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp),
                                ) {
                                    Text(
                                        text = pin.message.sender.name
                                            ?: stringResource(R.string.chat_unknown_sender),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val preview = when {
                                        !pin.message.message.isNullOrBlank() -> pin.message.message
                                        pin.message.sticker != null ->
                                            stringResource(R.string.chat_sticker_message)
                                        pin.message.attachments.isNotEmpty() ->
                                            stringResource(R.string.chat_attachment_message)
                                        else -> stringResource(R.string.chat_pinned_message)
                                    }
                                    Text(
                                        text = preview.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (uiState.myRole == "admin") {
                                    TextButton(
                                        onClick = {
                                            viewModel.togglePin(
                                                message = pin.message,
                                                onDone = { toast ->
                                                    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
                                                },
                                                onError = { err ->
                                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                },
                                            )
                                        },
                                    ) {
                                        Text(stringResource(R.string.chat_unpin))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPinList = false }) {
                    Text(stringResource(R.string.chat_close))
                }
            },
        )
    }
}

/** 置顶消息横幅：展示最近一条置顶内容，点击查看完整置顶列表。 */
@Composable
private fun PinBanner(
    pin: PinDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = stringResource(R.string.chat_pin),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            val preview = when {
                !pin.message.message.isNullOrBlank() -> pin.message.message
                pin.message.sticker != null -> stringResource(R.string.chat_sticker_message)
                pin.message.attachments.isNotEmpty() -> stringResource(R.string.chat_attachment_message)
                else -> stringResource(R.string.chat_pinned_message)
            }
            Text(
                text = preview.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
