package net.paigu.chahua.ui.chat

import android.app.Application
import android.graphics.BitmapFactory
import android.media.MediaCodecInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EncoderSelector
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.ReactionDto
import net.paigu.chahua.data.models.ReactionDetailResponse
import net.paigu.chahua.data.models.StickerPackDetailResponse
import net.paigu.chahua.data.models.StickerPackSummaryDto
import net.paigu.chahua.data.models.StickerSummaryDto
import net.paigu.chahua.data.models.UploadUrlRequest
import net.paigu.chahua.data.models.UserDto
import net.paigu.chahua.data.MAX_DISTINCT_REACTIONS_PER_MESSAGE
import net.paigu.chahua.data.MAX_REACTIONS_PER_USER_PER_MESSAGE
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.R
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

/** 输入框中待发送的附件草稿。 */
data class DraftAttachment(
    val uriString: String,
    val mimeType: String,
    val fileName: String,
    val kind: String, // image | video | file
    val compressVideo: Boolean = false,
)

private data class VideoSourceInfo(
    val width: Int,
    val height: Int,
    val frameRate: Int,
)

data class PendingMessage(
    val clientGeneratedId: String,
    val text: String?,
    val attachmentLocalUri: String?,
    val createdAt: String,
    val replyToId: String?,
    val attachmentKind: String = "image",
    val sticker: StickerSummaryDto? = null,
)

sealed interface ChatItem {
    val sortKey: String

    data class Server(val message: MessageDto) : ChatItem {
        override val sortKey: String get() = message.createdAt ?: message.id
    }

    data class Pending(val pending: PendingMessage) : ChatItem {
        override val sortKey: String get() = pending.createdAt
    }
}

data class ChatUiState(
    val loading: Boolean = false,
    val loadingOlder: Boolean = false,
    val error: String? = null,
    val replyTarget: MessageDto? = null,
    val threadMode: Boolean = false,
    val threadReplyCount: Long = 0,
    val title: String = "",
    val scrollToMessageId: String? = null,
)

data class StickerPanelUiState(
    val favorites: List<StickerSummaryDto> = emptyList(),
    val packs: List<StickerPackSummaryDto> = emptyList(),
    val details: Map<String, StickerPackDetailResponse> = emptyMap(),
    val selectedPackId: String? = null,
    val loadingPacks: Boolean = false,
    val loadingPackId: String? = null,
    val error: String? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val api = AppGraph.api
    private val engine = AppGraph.engine
    private val store = AppGraph.store

    private val _chatId = MutableStateFlow<String?>(null)
    private val _threadId = MutableStateFlow<String?>(null)
    private val _pending = MutableStateFlow<List<PendingMessage>>(emptyList())
    private val _uiState = MutableStateFlow(ChatUiState())
    private val _messages = MutableStateFlow<List<ChatItem>>(emptyList())
    private val _stickerPanel = MutableStateFlow(StickerPanelUiState())
    private val _mentionCandidates = MutableStateFlow<List<UserDto>>(emptyList())

    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    val messages: StateFlow<List<ChatItem>> = _messages.asStateFlow()
    val stickerPanel: StateFlow<StickerPanelUiState> = _stickerPanel.asStateFlow()
    val mentionCandidates: StateFlow<List<UserDto>> = _mentionCandidates.asStateFlow()
    val connectionState = store.connectionState
    val latencyMs = AppGraph.engine.latencyMs
    val quickReactionEmojis: StateFlow<List<String>> = AppGraph.settings.settingsState
        .map { it.quickReactionEmojis() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppGraph.settings.snapshot().quickReactionEmojis())

    private var initialized = false
    private var currentKey: String? = null
    private var messageJob: Job? = null
    private var mentionSearchJob: Job? = null

    fun init(
        chatId: String,
        title: String,
        threadId: String?,
        replyCount: Long,
        initialMessageId: String? = null,
    ) {
        val key = "$chatId|${threadId.orEmpty()}"
        if (initialized && currentKey == key) return
        currentKey = key
        initialized = true
        messageJob?.cancel()
        _chatId.value = chatId
        _threadId.value = threadId
        _pending.value = emptyList()
        _messages.value = emptyList()
        _mentionCandidates.value = emptyList()
        _uiState.value = ChatUiState(
            threadMode = !threadId.isNullOrBlank(),
            threadReplyCount = replyCount,
            title = title,
        )
        messageJob = viewModelScope.launch {
            combine(
                store.messagesFor(chatId, threadId),
                _pending,
            ) { serverList, pendingList ->
                val serverCgids = serverList.mapTo(HashSet()) { it.clientGeneratedId }
                val pending = pendingList.filterNot { it.clientGeneratedId in serverCgids }
                (serverList.filterNot { it.isDeleted }.map { ChatItem.Server(it) as ChatItem } +
                    pending.map { ChatItem.Pending(it) as ChatItem })
                    .sortedBy { it.sortKey }
            }.collect { _messages.value = it }
        }
        loadMessages()
        initialMessageId?.let { jumpToMessage(it) }
    }

    fun loadMessages() {
        val chatId = _chatId.value ?: return
        val threadId = _threadId.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                api.messages(chatId = chatId, threadId = threadId, max = 100)
            }
                .onSuccess { resp ->
                    store.setMessages(chatId, threadId, resp.messages)
                    resp.messages.lastOrNull()?.let { markRead(it.id) }
                    val target = resolveInitialScrollTarget(chatId, threadId)
                    val currentTarget = _uiState.value.scrollToMessageId
                    _uiState.value = _uiState.value.copy(
                        scrollToMessageId = currentTarget ?: target,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(error = it.message)
                }
            _uiState.value = _uiState.value.copy(loading = false)
        }
    }

    fun loadOlder() {
        val chatId = _chatId.value ?: return
        val threadId = _threadId.value
        val current = store.messages.value[store.timelineKey(chatId, threadId)].orEmpty()
        val oldest = current.firstOrNull() ?: return
        if (_uiState.value.loadingOlder) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingOlder = true)
            runCatching {
                api.messages(chatId = chatId, threadId = threadId, before = oldest.id, max = 50)
            }
                .onSuccess { resp ->
                    if (resp.messages.isNotEmpty()) {
                        store.appendMessages(chatId, threadId, resp.messages)
                    }
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(error = it.message)
                }
            _uiState.value = _uiState.value.copy(loadingOlder = false)
        }
    }

    fun setReplyTarget(message: MessageDto?) {
        _uiState.value = _uiState.value.copy(replyTarget = message)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun myUid(): Int = AppGraph.session.snapshot().me?.uid ?: -1

    fun myUser(): UserDto = AppGraph.session.snapshot().me?.let {
        UserDto(uid = it.uid, avatarUrl = it.avatarUrl, name = it.username)
    } ?: UserDto(uid = -1)

    fun sendText(text: String) {
        val chatId = _chatId.value ?: return
        val reply = _uiState.value.replyTarget
        val threadId = _threadId.value
        val pending = PendingMessage(
            clientGeneratedId = "android-${UUID.randomUUID()}",
            text = text,
            attachmentLocalUri = null,
            createdAt = Instant.now().toString(),
            replyToId = reply?.id,
        )
        _pending.update { it + pending }
        _uiState.value = _uiState.value.copy(replyTarget = null)
        viewModelScope.launch {
            engine.sendMessage(
                chatId = chatId,
                text = text,
                replyToId = reply?.id,
                replyRootId = threadId,
                clientGeneratedId = pending.clientGeneratedId,
            )
                .onSuccess { removePending(pending.clientGeneratedId) }
                .onFailure {
                    removePending(pending.clientGeneratedId)
                    _uiState.value = _uiState.value.copy(error = getString(R.string.chat_send_failed, it.message))
                }
        }
    }

    /** 选择图片后：上传到对象存储，再以附件形式发送消息。 */
    /** 从输入框发送：可带文字 + 附件草稿，也可只发送文字。 */
    fun sendDraft(text: String, attachment: DraftAttachment?) {
        if (text.isBlank() && attachment == null) return
        if (attachment == null) {
            sendText(text)
        } else {
            sendAttachment(text, attachment)
        }
    }

    fun jumpToMessage(messageId: String) {
        val chatId = _chatId.value ?: return
        val threadId = _threadId.value
        val current = store.messages.value[store.timelineKey(chatId, threadId)].orEmpty()
        if (current.any { it.id == messageId }) {
            _uiState.value = _uiState.value.copy(scrollToMessageId = messageId)
            return
        }
        viewModelScope.launch {
            runCatching {
                api.messages(chatId = chatId, threadId = threadId, around = messageId, max = 60)
            }
                .onSuccess { resp ->
                    store.appendMessages(chatId, threadId, resp.messages)
                    _uiState.value = _uiState.value.copy(scrollToMessageId = messageId)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        error = getString(R.string.chat_action_failed, it.message),
                    )
                }
        }
    }

    fun clearScrollTarget() {
        _uiState.value = _uiState.value.copy(scrollToMessageId = null)
    }

    /** 通用附件发送：读取 Uri -> 上传对象存储 -> 携带文字发送消息。 */
    fun sendAttachment(text: String, attachment: DraftAttachment) {
        val chatId = _chatId.value ?: return
        val reply = _uiState.value.replyTarget
        val threadId = _threadId.value
        viewModelScope.launch {
            var pending: PendingMessage? = null
            try {
                val context = getApplication<Application>()
                val uri = Uri.parse(attachment.uriString)
                val resolver = context.contentResolver
                val mime = attachment.mimeType.ifBlank {
                    resolver.getType(uri) ?: "application/octet-stream"
                }
                val name = attachment.fileName.ifBlank {
                    queryDisplayName(uri) ?: "file_${System.currentTimeMillis()}"
                }
                val imageBytes = if (attachment.kind == "image") {
                    resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException(getString(R.string.chat_read_file_failed))
                } else {
                    null
                }
                if (imageBytes != null && imageBytes.size > 20 * 1024 * 1024) {
                    _uiState.value = _uiState.value.copy(
                        error = getString(R.string.chat_image_too_large),
                    )
                    return@launch
                }
                val (width, height) = imageBytes?.let { imageBounds(it) } ?: (null to null)
                val fileSize = imageBytes?.size?.toLong()
                    ?: querySize(uri)
                    ?: resolver.openInputStream(uri)?.use { it.readBytes().size.toLong() }
                    ?: 0L
                val created = PendingMessage(
                    clientGeneratedId = "android-${UUID.randomUUID()}",
                    text = text.ifBlank { null },
                    attachmentLocalUri = attachment.uriString,
                    createdAt = Instant.now().toString(),
                    replyToId = reply?.id,
                    attachmentKind = attachment.kind,
                )
                pending = created
                _pending.update { it + created }
                _uiState.value = _uiState.value.copy(replyTarget = null)

                val upload = api.uploadUrl(
                    UploadUrlRequest(
                        filename = name,
                        contentType = mime,
                        size = fileSize,
                        width = width,
                        height = height,
                    ),
                )
                if (imageBytes != null) {
                    api.uploadFile(upload.uploadUrl, upload.uploadHeaders, imageBytes, mime)
                } else {
                    api.uploadStream(
                        uploadUrl = upload.uploadUrl,
                        headers = upload.uploadHeaders,
                        content = {
                            resolver.openInputStream(uri)
                                ?: throw IllegalStateException(
                                    getString(R.string.chat_read_file_failed),
                                )
                        },
                        contentType = mime,
                        contentLength = fileSize.takeIf { it > 0 },
                    )
                }
                engine.sendMessage(
                    chatId = chatId,
                    text = text.ifBlank { null },
                    replyToId = reply?.id,
                    replyRootId = threadId,
                    attachmentIds = listOf(upload.attachmentId),
                    clientGeneratedId = created.clientGeneratedId,
                )
                    .onSuccess { removePending(created.clientGeneratedId) }
                    .onFailure {
                        removePending(created.clientGeneratedId)
                        _uiState.value = _uiState.value.copy(error = getString(R.string.chat_send_failed, it.message))
                    }
            } catch (e: Exception) {
                removePending(pending?.clientGeneratedId)
                _uiState.value = _uiState.value.copy(error = getString(R.string.chat_send_failed, e.message))
            }
        }
    }

    /** 直接发送贴纸消息，不改变输入框中的文字。 */
    fun sendSticker(sticker: StickerSummaryDto) {
        val chatId = _chatId.value ?: return
        val reply = _uiState.value.replyTarget
        val threadId = _threadId.value
        val pending = PendingMessage(
            clientGeneratedId = "android-${UUID.randomUUID()}",
            text = null,
            attachmentLocalUri = null,
            createdAt = Instant.now().toString(),
            replyToId = reply?.id,
            sticker = sticker,
        )
        _pending.update { it + pending }
        _uiState.value = _uiState.value.copy(replyTarget = null)
        viewModelScope.launch {
            engine.sendMessage(
                chatId = chatId,
                text = null,
                replyToId = reply?.id,
                replyRootId = threadId,
                messageType = "sticker",
                stickerId = sticker.id,
                clientGeneratedId = pending.clientGeneratedId,
            )
                .onSuccess { removePending(pending.clientGeneratedId) }
                .onFailure {
                    removePending(pending.clientGeneratedId)
                    _uiState.value = _uiState.value.copy(
                        error = getString(R.string.chat_send_failed, it.message),
                    )
                }
        }
    }

    /** 压缩视频到本地缓存，完成后通过回调返回 file:// Uri。 */
    fun compressVideo(uriString: String, onReady: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val context = getApplication<Application>()
                val uri = Uri.parse(uriString)
                val videoInfo = queryVideoInfo(uri)
                val frameRate = (videoInfo?.frameRate ?: 30).coerceIn(1, 60)
                val presentation = if (videoInfo != null) {
                    val scale = minOf(1.0, 1920.0 / maxOf(videoInfo.width, videoInfo.height))
                    val outputWidth = (videoInfo.width * scale).roundToInt().coerceAtLeast(2)
                    val outputHeight = (videoInfo.height * scale).roundToInt().coerceAtLeast(2)
                    Presentation.createForWidthAndHeight(
                        outputWidth,
                        outputHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT,
                    )
                } else {
                    Presentation.createForShortSide(1080)
                }
                val effects = Effects(
                    emptyList<AudioProcessor>(),
                    listOf<Effect>(presentation),
                )
                val videoSettings = VideoEncoderSettings.Builder()
                    .setBitrate(10 * 1000 * 1000)
                    .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                    .setiFrameIntervalSeconds(1f)
                    .setMaxBFrames(0)
                    .build()
                val audioSettings = AudioEncoderSettings.Builder()
                    .setBitrate(128 * 1024)
                    .build()
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setVideoEncoderSelector(EncoderSelector.DEFAULT)
                    .setRequestedVideoEncoderSettings(videoSettings)
                    .setRequestedAudioEncoderSettings(audioSettings)
                    .setEnableFallback(true)
                    .build()
                val outputDir = File(context.cacheDir, "compressed_videos").apply { mkdirs() }
                val output = File(outputDir, "compressed_${System.currentTimeMillis()}.mp4")
                val transformer = Transformer.Builder(context)
                    .setEncoderFactory(encoderFactory)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            viewModelScope.launch {
                                onReady(Uri.fromFile(output).toString())
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exception: ExportException,
                        ) {
                            viewModelScope.launch {
                                onError(exception.message ?: "compress failed")
                            }
                        }
                    })
                    .build()
                transformer.start(
                    EditedMediaItem.Builder(MediaItem.fromUri(uriString))
                        .setFrameRate(frameRate)
                        .setEffects(effects)
                        .build(),
                    output.absolutePath,
                )
            }.onFailure {
                onError(it.message ?: "compress failed")
            }
        }
    }

    // ---- 表情面板 ----

    fun loadStickerPanel() {
        if (_stickerPanel.value.loadingPacks) return
        viewModelScope.launch {
            _stickerPanel.value = _stickerPanel.value.copy(loadingPacks = true, error = null)
            runCatching {
                val owned = api.ownedStickerPacks().packs
                val subscribed = api.subscribedStickerPacks().packs
                val favorites = api.favoriteStickers().stickers
                val ownedIds = owned.mapTo(HashSet()) { it.id }
                Triple(
                    owned + subscribed.filterNot { it.id in ownedIds },
                    favorites,
                    AppGraph.session.snapshot().me?.stickerPackOrder.orEmpty(),
                )
            }
                .onSuccess { (packs, favorites, order) ->
                    val sorted = sortPacksByOrder(packs, order)
                    _stickerPanel.value = _stickerPanel.value.copy(
                        favorites = favorites,
                        packs = sorted,
                        selectedPackId = sorted.firstOrNull()?.id,
                        loadingPacks = false,
                    )
                }
                .onFailure {
                    _stickerPanel.value = _stickerPanel.value.copy(
                        loadingPacks = false,
                        error = it.message,
                    )
                }
        }
    }

    fun selectStickerPack(packId: String) {
        val state = _stickerPanel.value
        if (state.details.containsKey(packId)) {
            _stickerPanel.value = state.copy(selectedPackId = packId)
            return
        }
        if (state.loadingPackId == packId) return
        viewModelScope.launch {
            _stickerPanel.value = state.copy(loadingPackId = packId, error = null)
            runCatching { api.stickerPackDetail(packId) }
                .onSuccess { detail ->
                    val current = _stickerPanel.value
                    _stickerPanel.value = current.copy(
                        details = current.details + (packId to detail),
                        selectedPackId = packId,
                        loadingPackId = null,
                    )
                }
                .onFailure {
                    _stickerPanel.value = _stickerPanel.value.copy(
                        loadingPackId = null,
                        error = it.message,
                    )
                }
        }
    }

    fun dismissStickerError() {
        _stickerPanel.value = _stickerPanel.value.copy(error = null)
    }

    /** 输入 @ 后按当前输入内容检索群成员，作为 @ 候选。*/
    fun searchMentionCandidates(query: String) {
        val chatId = _chatId.value ?: return
        mentionSearchJob?.cancel()
        mentionSearchJob = viewModelScope.launch {
            runCatching {
                api.members(
                    chatId = chatId,
                    limit = 20,
                    q = query.trim().takeIf { it.isNotBlank() },
                )
            }
                .onSuccess { resp ->
                    _mentionCandidates.value = resp.members.map {
                        UserDto(
                            uid = it.uid,
                            avatarUrl = it.avatarUrl,
                            name = it.username,
                        )
                    }
                }
                .onFailure {
                    _mentionCandidates.value = emptyList()
                }
        }
    }

    fun clearMentionCandidates() {
        _mentionCandidates.value = emptyList()
    }

    private fun sortPacksByOrder(
        packs: List<StickerPackSummaryDto>,
        order: List<net.paigu.chahua.data.models.StickerPackOrderItemDto>,
    ): List<StickerPackSummaryDto> {
        val usedAt = order.associate { it.stickerPackId to it.lastUsedOn }
        return packs.sortedWith(
            compareByDescending<StickerPackSummaryDto> { usedAt[it.id] ?: Long.MIN_VALUE }
                .thenBy { it.name },
        )
    }

    private fun removePending(clientGeneratedId: String?) {
        if (clientGeneratedId == null) return
        _pending.update { it.filterNot { p -> p.clientGeneratedId == clientGeneratedId } }
    }

    fun toggleReaction(message: MessageDto, emoji: String) {
        val chatId = _chatId.value ?: return
        val existing = message.reactions.firstOrNull { it.emoji == emoji }
        val add = existing?.reactedByMe != true

        if (add) {
            val myReactionCount = message.reactions.count { it.reactedByMe == true }
            if (myReactionCount >= MAX_REACTIONS_PER_USER_PER_MESSAGE) {
                _uiState.value = _uiState.value.copy(
                    error = getString(R.string.chat_reaction_limit, MAX_REACTIONS_PER_USER_PER_MESSAGE),
                )
                return
            }
            if (existing == null && message.reactions.size >= MAX_DISTINCT_REACTIONS_PER_MESSAGE) {
                _uiState.value = _uiState.value.copy(
                    error = getString(R.string.chat_reaction_distinct_limit, MAX_DISTINCT_REACTIONS_PER_MESSAGE),
                )
                return
            }
            viewModelScope.launch { AppGraph.settings.addRecentReaction(emoji) }
        }

        val newReactions = buildList {
            addAll(message.reactions)
            if (add) {
                val index = indexOfFirst { it.emoji == emoji }
                if (index >= 0) {
                    val current = this[index]
                    this[index] = current.copy(count = current.count + 1, reactedByMe = true)
                } else {
                    add(ReactionDto(emoji = emoji, count = 1, reactedByMe = true))
                }
            } else {
                val index = indexOfFirst { it.emoji == emoji }
                if (index >= 0) {
                    val current = this[index]
                    if (current.count <= 1) removeAt(index) else {
                        this[index] = current.copy(count = current.count - 1, reactedByMe = false)
                    }
                }
            }
        }
        // 乐观更新：立即上屏，请求失败时静默回滚（等待 WS reactionUpdated 覆盖）
        store.onReactionUpdate(chatId, message.id, newReactions)
        viewModelScope.launch {
            runCatching {
                if (add) api.addReaction(chatId, message.id, emoji)
                else api.removeReaction(chatId, message.id, emoji)
            }.onFailure {
                store.onReactionUpdate(chatId, message.id, message.reactions)
            }
        }
    }

    /** 拉取表态详情；失败时返回 null，由详情弹窗展示空态。 */
    suspend fun reactionDetails(messageId: String): ReactionDetailResponse? {
        val chatId = _chatId.value ?: return null
        return runCatching { api.reactionDetails(chatId, messageId) }.getOrNull()
    }

    fun deleteMessage(message: MessageDto, onSuccess: (() -> Unit)? = null) {
        val chatId = _chatId.value ?: return
        viewModelScope.launch {
            runCatching {
                api.deleteMessage(chatId, message.id)
                store.removeMessages(chatId, _threadId.value, listOf(message.id))
                refreshAroundMessage(message.id)
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = getString(R.string.chat_delete_failed, it.message))
            }.onSuccess {
                onSuccess?.invoke()
            }
        }
    }

    fun editMessage(
        messageId: String,
        newText: String,
        attachmentIds: List<String> = emptyList(),
    ) {
        val chatId = _chatId.value ?: return
        viewModelScope.launch {
            runCatching {
                api.editMessage(chatId, messageId, newText, attachmentIds)
            }
                .onSuccess { updated ->
                    store.onMessageUpdated(updated)
                    refreshAroundMessage(messageId)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        error = getString(R.string.chat_action_failed, it.message),
                    )
                }
        }
    }

    /** 归档当前话题并从活跃话题列表移除。*/
    fun archiveCurrentThread(onDone: () -> Unit = {}) {
        val chatId = _chatId.value ?: return
        val threadId = _threadId.value ?: return
        viewModelScope.launch {
            runCatching {
                api.archiveThread(chatId, threadId)
                store.removeThread(chatId, threadId)
            }
                .onSuccess { onDone() }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        error = getString(R.string.chat_action_failed, it.message),
                    )
                }
        }
    }

    /** 取消归档当前话题。*/
    fun unarchiveCurrentThread(onDone: () -> Unit = {}) {
        val chatId = _chatId.value ?: return
        val threadId = _threadId.value ?: return
        viewModelScope.launch {
            runCatching {
                api.unarchiveThread(chatId, threadId)
            }
                .onSuccess { onDone() }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        error = getString(R.string.chat_action_failed, it.message),
                    )
                }
        }
    }

    private suspend fun refreshAroundMessage(messageId: String) {
        val chatId = _chatId.value ?: return
        val threadId = _threadId.value
        runCatching {
            api.messages(
                chatId = chatId,
                threadId = threadId,
                around = messageId,
                max = 40,
            )
        }.onSuccess { resp ->
            store.appendMessages(chatId, threadId, resp.messages)
        }
    }

    /** 进入会话后，定位到第一条未读消息；必要时向前分页补齐未读消息。*/
    private suspend fun resolveInitialScrollTarget(chatId: String, threadId: String?): String? {
        val lastRead: String?
        val unread: Long
        if (threadId.isNullOrBlank()) {
            val chat = store.chats.value.firstOrNull { it.id == chatId }
                ?: runCatching { api.chats(limit = 100) }
                    .getOrNull()
                    ?.chats
                    ?.firstOrNull { it.id == chatId }
                    ?.also { store.setChats(store.chats.value + it) }
            lastRead = chat?.lastReadMessageId
            unread = chat?.unreadCount ?: 0L
        } else {
            val thread = store.threads.value.firstOrNull {
                it.chatId == chatId && it.threadRootMessage?.id == threadId
            }
            lastRead = thread?.lastReadMessageId
            unread = thread?.unreadCount ?: 0L
        }
        if (unread <= 0) return null

        var cursor = lastRead ?: "0"
        var firstUnread: String? = null
        repeat(10) {
            val resp = api.messages(chatId = chatId, after = cursor, threadId = threadId, max = 100)
            if (resp.messages.isEmpty()) return firstUnread
            store.appendMessages(chatId, threadId, resp.messages)
            firstUnread = resp.messages.first().id
            if (resp.messages.size < 100) return firstUnread
            cursor = resp.messages.last().id
        }
        return firstUnread
    }

    private fun getString(id: Int, vararg args: Any?): String =
        getApplication<Application>().getString(id, *args)

    private fun markRead(lastMessageId: String) {
        val chatId = _chatId.value ?: return
        val threadId = _threadId.value
        viewModelScope.launch {
            if (threadId.isNullOrBlank()) {
                runCatching { api.markChatRead(chatId, lastMessageId) }
                    .onSuccess { store.markChatRead(chatId, it.lastReadMessageId, it.unreadCount) }
            } else {
                runCatching { api.markThreadRead(threadId, lastMessageId) }
                    .onSuccess { store.markThreadRead(chatId, threadId, it.unreadCount) }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            getApplication<Application>().contentResolver.query(
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
    }

    private fun querySize(uri: Uri): Long? {
        return try {
            getApplication<Application>().contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
                ?: getApplication<Application>().contentResolver
                    .openAssetFileDescriptor(uri, "r")
                    ?.use { descriptor ->
                        descriptor.length.takeIf { it >= 0 }
                    }
        } catch (e: Exception) {
            null
        }
    }

    private fun queryVideoInfo(uri: Uri): VideoSourceInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(getApplication<Application>(), uri)
            var width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: return null
            var height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: return null
            if (width <= 0 || height <= 0) return null
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            if (rotation == 90 || rotation == 270) {
                val temp = width
                width = height
                height = temp
            }
            val frameRate = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()
                ?.roundToInt()
                ?: 30
            VideoSourceInfo(width = width, height = height, frameRate = frameRate)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun imageBounds(bytes: ByteArray): Pair<Int?, Int?> {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            options.outWidth to options.outHeight
        } catch (e: Exception) {
            null to null
        }
    }
}

private fun <T> MutableStateFlow<List<T>>.update(transform: (List<T>) -> List<T>) {
    this.value = transform(this.value)
}
