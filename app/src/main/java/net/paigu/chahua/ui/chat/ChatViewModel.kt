package net.paigu.chahua.ui.chat

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
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
import net.paigu.chahua.data.models.MemberSummaryDto
import net.paigu.chahua.data.models.FriendAddInfoResponse
import net.paigu.chahua.data.models.PinDto
import net.paigu.chahua.data.models.ReactionDto
import net.paigu.chahua.data.models.ReactionDetailResponse
import net.paigu.chahua.data.models.StickerPackDetailResponse
import net.paigu.chahua.data.models.StickerPackSummaryDto
import net.paigu.chahua.data.models.StickerDetailResponse
import net.paigu.chahua.data.models.StickerSummaryDto
import net.paigu.chahua.data.models.UploadUrlRequest
import net.paigu.chahua.data.models.UserDto
import net.paigu.chahua.data.MAX_DISTINCT_REACTIONS_PER_MESSAGE
import net.paigu.chahua.data.MAX_REACTIONS_PER_USER_PER_MESSAGE
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.R
import net.paigu.chahua.ui.media.VideoCompressor
import java.time.Instant
import java.util.UUID

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
    private val _stickerPreview = MutableStateFlow(StickerPreviewUiState())
    private val _mentionCandidates = MutableStateFlow<List<UserDto>>(emptyList())

    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    val messages: StateFlow<List<ChatItem>> = _messages.asStateFlow()
    val stickerPanel: StateFlow<StickerPanelUiState> = _stickerPanel.asStateFlow()
    val stickerPreview: StateFlow<StickerPreviewUiState> = _stickerPreview.asStateFlow()
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
                mergeChatItems(serverList, pendingList)
            }.collect { _messages.value = it }
        }
        // 群角色用于置顶等管理员操作，置顶列表用于横幅与取消置顶。
        viewModelScope.launch {
            runCatching { api.groupInfo(chatId) }
                .onSuccess { info ->
                    val dmTitle = if (info.kind == "dm") {
                        info.peer?.username?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.chat_dm_user, info.peer?.uid ?: 0)
                    } else {
                        null
                    }
                    _uiState.value = _uiState.value.copy(
                        myRole = info.myRole,
                        isDm = info.kind == "dm",
                        peer = info.peer,
                        title = dmTitle ?: if (title.isBlank() || title == chatId) info.name else title,
                    )
                }
            refreshPins(chatId)
        }
        loadMessages()
        initialMessageId?.let { jumpToMessage(it) }
    }

    /** 重新拉取置顶消息列表。 */
    fun refreshPins(chatId: String? = null) {
        val target = chatId ?: _chatId.value ?: return
        viewModelScope.launch {
            runCatching { api.pins(target) }
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(pins = resp.pins)
                }
        }
    }

    /** 置顶 / 取消置顶一条消息（仅管理员）。 */
    fun togglePin(message: MessageDto, onDone: (String) -> Unit, onError: (String) -> Unit) {
        val chatId = _chatId.value ?: return
        val existing = _uiState.value.pins.firstOrNull { it.message.id == message.id }
        viewModelScope.launch {
            val result = if (existing == null) {
                runCatching { api.createPin(chatId, message.id) }
            } else {
                runCatching {
                    api.deletePin(chatId, existing.id)
                    null
                }
            }
            result
                .onSuccess {
                    refreshPins(chatId)
                    onDone(
                        getString(
                            if (existing == null) {
                                R.string.chat_pinned
                            } else {
                                R.string.chat_unpinned
                            },
                        ),
                    )
                }
                .onFailure {
                    onError(it.message ?: getString(R.string.chat_action_failed))
                }
        }
    }

    /** 收藏消息。 */
    fun saveMessage(messageId: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { api.saveMessage(messageId) }
                .onSuccess { onDone(getString(R.string.chat_saved_message)) }
                .onFailure { onError(it.message ?: getString(R.string.chat_action_failed)) }
        }
    }

    /** 收藏 / 取消收藏贴纸。 */
    fun setStickerFavorite(
        stickerId: String,
        favorite: Boolean,
        onDone: (String) -> Unit,
        onError: (String) -> Unit,
        sticker: StickerSummaryDto? = null,
    ) {
        viewModelScope.launch {
            val result = if (favorite) {
                runCatching { api.favoriteSticker(stickerId) }
            } else {
                runCatching { api.unfavoriteSticker(stickerId) }
            }
            result
                .onSuccess {
                    store.applyStickerFavorite(stickerId, favorite)
                    val favoriteSummary = if (favorite) {
                        sticker?.copy(isFavorited = true) ?: findStickerSummary(stickerId)
                    } else {
                        null
                    }
                    _stickerPanel.value = applyStickerFavoriteToPanel(
                        panel = _stickerPanel.value,
                        stickerId = stickerId,
                        sticker = favoriteSummary,
                        favorite = favorite,
                    )
                    onDone(
                        getString(
                            if (favorite) {
                                R.string.chat_sticker_favorited
                            } else {
                                R.string.chat_sticker_unfavorited
                            },
                        ),
                    )
                }
                .onFailure { onError(it.message ?: getString(R.string.chat_action_failed)) }
        }
    }

    // ---- 聊天内贴纸预览 ----

    /** 打开贴纸预览并加载贴纸详情（含所属贴纸包）。 */
    fun loadStickerPreview(stickerId: String) {
        val current = _stickerPreview.value
        if (current.stickerId == stickerId && current.detail != null) return
        _stickerPreview.value = StickerPreviewUiState(stickerId = stickerId, loading = true)
        viewModelScope.launch {
            runCatching { api.stickerDetail(stickerId) }
                .onSuccess { detail ->
                    _stickerPreview.value = StickerPreviewUiState(
                        stickerId = stickerId,
                        detail = detail,
                        subscribed = detail.packs.firstOrNull()?.isSubscribed == true,
                    )
                }
                .onFailure {
                    _stickerPreview.value = StickerPreviewUiState(
                        stickerId = stickerId,
                        error = it.message ?: getString(R.string.chat_action_failed),
                    )
                }
        }
    }

    fun dismissStickerPreview() {
        _stickerPreview.value = StickerPreviewUiState()
    }

    /** 预览内收藏 / 取消收藏（乐观更新，失败回滚）。 */
    fun toggleStickerFavoriteFromPreview() {
        val state = _stickerPreview.value
        val detail = state.detail ?: return
        if (state.busyFavorite) return
        val favorite = !detail.isFavorited
        _stickerPreview.value = state.copy(
            busyFavorite = true,
            detail = detail.copy(isFavorited = favorite),
        )
        viewModelScope.launch {
            runCatching {
                if (favorite) api.favoriteSticker(detail.id)
                else api.unfavoriteSticker(detail.id)
            }
                .onSuccess {
                    store.applyStickerFavorite(detail.id, favorite)
                    val favoriteSummary = StickerSummaryDto(
                        id = detail.id,
                        media = detail.media,
                        emoji = detail.emoji,
                        name = detail.name,
                        description = detail.description,
                        createdAt = detail.createdAt,
                        isFavorited = favorite,
                    )
                    _stickerPanel.value = applyStickerFavoriteToPanel(
                        panel = _stickerPanel.value,
                        stickerId = detail.id,
                        sticker = favoriteSummary,
                        favorite = favorite,
                    )
                    _stickerPreview.value = _stickerPreview.value.copy(busyFavorite = false)
                }
                .onFailure {
                    _stickerPreview.value = _stickerPreview.value.copy(
                        busyFavorite = false,
                        detail = detail,
                        error = it.message ?: getString(R.string.chat_action_failed),
                    )
                }
        }
    }

    /** 预览内订阅 / 取消订阅第一个所属贴纸包（乐观更新，失败回滚）。 */
    fun toggleStickerPackSubscriptionFromPreview() {
        val state = _stickerPreview.value
        val pack = state.detail?.packs?.firstOrNull() ?: return
        if (state.busySubscribe) return
        val subscribed = !state.subscribed
        _stickerPreview.value = state.copy(
            busySubscribe = true,
            subscribed = subscribed,
        )
        viewModelScope.launch {
            runCatching {
                if (subscribed) api.subscribeStickerPack(pack.id)
                else api.unsubscribeStickerPack(pack.id)
            }
                .onSuccess {
                    _stickerPanel.value = _stickerPanel.value.copy(
                        packs = _stickerPanel.value.packs.map {
                            if (it.id == pack.id) it.copy(isSubscribed = subscribed) else it
                        },
                    )
                    _stickerPreview.value = _stickerPreview.value.copy(busySubscribe = false)
                }
                .onFailure {
                    _stickerPreview.value = _stickerPreview.value.copy(
                        busySubscribe = false,
                        subscribed = state.subscribed,
                        error = it.message ?: getString(R.string.chat_action_failed),
                    )
                }
        }
    }

    /** 从用户资料弹窗发起私聊：查找与 uid 的既有 DM 会话并跳转。 */
    fun openDmWith(
        uid: Int,
        onFound: (chatId: String, title: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                store.chats.value.firstOrNull { it.kind == "dm" && it.peer?.uid == uid }
                    ?: api.chats(limit = 100).chats
                        .firstOrNull { it.kind == "dm" && it.peer?.uid == uid }
            }
                .onSuccess { dm ->
                    if (dm == null) {
                        onError(getString(R.string.chat_dm_unavailable))
                    } else {
                        onFound(
                            dm.id,
                            dm.peer?.username?.takeIf { it.isNotBlank() } ?: dm.name ?: dm.id,
                        )
                    }
                }
                .onFailure { onError(it.message ?: getString(R.string.chat_dm_unavailable)) }
        }
    }

    /** 判断 uid 是否已经是当前用户的好友。 */
    fun isFriendWith(uid: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val isFriend = runCatching { api.friends().friends }
                .getOrNull()
                ?.any { it.user.uid == uid } == true
            onResult(isFriend)
        }
    }

    /** 目标用户的好友验证要求；失败返回 null。 */
    suspend fun friendAddInfo(uid: Int): FriendAddInfoResponse? =
        runCatching { api.friendAddInfo(uid) }.getOrNull()

    /** 发送好友请求；成功返回 true，失败时写入错误信息并返回 false。 */
    suspend fun sendFriendRequest(uid: Int, message: String?): Boolean {
        return runCatching { api.createFriendRequest(uid, message) }
            .onFailure {
                _uiState.value = _uiState.value.copy(
                    error = it.message ?: getString(R.string.chat_action_failed),
                )
            }
            .isSuccess
    }

    /** 根据当前消息缓存构造贴纸摘要（用于收藏后同步到收藏面板）。 */
    private fun findStickerSummary(stickerId: String): StickerSummaryDto? {
        val message = _messages.value.asSequence()
            .mapNotNull { (it as? ChatItem.Server)?.message }
            .firstOrNull { it.sticker?.id == stickerId }
        val media = message?.sticker?.media ?: return null
        return StickerSummaryDto(
            id = stickerId,
            media = media,
            emoji = message.sticker?.emoji.orEmpty(),
            name = message.sticker?.name,
            description = message.sticker?.description,
            createdAt = message.sticker?.createdAt,
            isFavorited = true,
        )
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

    /** 恢复 / 保存当前会话的输入草稿。 */
    suspend fun loadDraft(): String =
        AppGraph.settings.chatDraft(_chatId.value.orEmpty(), _threadId.value)

    suspend fun saveDraft(text: String) =
        AppGraph.settings.saveChatDraft(_chatId.value.orEmpty(), _threadId.value, text)

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
                // 后端规则：text 消息只能带图片/视频；普通文件必须用 file 消息类型，
                // 且 file 消息不能附带文字。因此文件+文字时先单独发送文字，再发送文件。
                val isFile = attachment.kind == "file"
                if (isFile && text.isNotBlank()) {
                    engine.sendMessage(
                        chatId = chatId,
                        text = text.trim(),
                        replyToId = reply?.id,
                        replyRootId = threadId,
                        clientGeneratedId = "android-${UUID.randomUUID()}",
                    )
                }
                engine.sendMessage(
                    chatId = chatId,
                    text = if (isFile) null else text.ifBlank { null },
                    replyToId = reply?.id,
                    replyRootId = threadId,
                    attachmentIds = listOf(upload.attachmentId),
                    messageType = if (isFile) "file" else "text",
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

    /** 多张图片合并为一条消息发送：逐张上传，最后用全部附件 ID 发送一次。 */
    fun sendImagesBatch(text: String, images: List<DraftAttachment>) {
        if (images.isEmpty()) return
        val chatId = _chatId.value ?: return
        val reply = _uiState.value.replyTarget
        val threadId = _threadId.value
        viewModelScope.launch {
            var pending: PendingMessage? = null
            try {
                val context = getApplication<Application>()
                val resolver = context.contentResolver
                val first = images.first()
                val created = PendingMessage(
                    clientGeneratedId = "android-${UUID.randomUUID()}",
                    text = text.ifBlank { null },
                    attachmentLocalUri = first.uriString,
                    createdAt = Instant.now().toString(),
                    replyToId = reply?.id,
                    attachmentKind = "image",
                    attachmentCount = images.size,
                )
                pending = created
                _pending.update { it + created }
                _uiState.value = _uiState.value.copy(replyTarget = null)

                val uploadedIds = mutableListOf<String>()
                images.forEach { attachment ->
                    val uri = Uri.parse(attachment.uriString)
                    val mime = attachment.mimeType.ifBlank {
                        resolver.getType(uri) ?: "image/jpeg"
                    }
                    val name = attachment.fileName.ifBlank {
                        queryDisplayName(uri) ?: "file_${System.currentTimeMillis()}"
                    }
                    val imageBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException(getString(R.string.chat_read_file_failed))
                    if (imageBytes.size > 20 * 1024 * 1024) {
                        removePending(created.clientGeneratedId)
                        _uiState.value = _uiState.value.copy(
                            error = getString(R.string.chat_image_too_large),
                        )
                        return@launch
                    }
                    val (width, height) = imageBounds(imageBytes)
                    val upload = api.uploadUrl(
                        UploadUrlRequest(
                            filename = name,
                            contentType = mime,
                            size = imageBytes.size.toLong(),
                            width = width,
                            height = height,
                        ),
                    )
                    api.uploadFile(upload.uploadUrl, upload.uploadHeaders, imageBytes, mime)
                    uploadedIds += upload.attachmentId
                }
                engine.sendMessage(
                    chatId = chatId,
                    text = text.ifBlank { null },
                    replyToId = reply?.id,
                    replyRootId = threadId,
                    attachmentIds = uploadedIds,
                    clientGeneratedId = created.clientGeneratedId,
                )
                    .onSuccess { removePending(created.clientGeneratedId) }
                    .onFailure {
                        removePending(created.clientGeneratedId)
                        _uiState.value = _uiState.value.copy(
                            error = getString(R.string.chat_send_failed, it.message),
                        )
                    }
            } catch (e: Exception) {
                removePending(pending?.clientGeneratedId)
                _uiState.value = _uiState.value.copy(
                    error = getString(R.string.chat_send_failed, e.message),
                )
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
        VideoCompressor.compress(getApplication(), uriString, viewModelScope, onReady, onError)
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

    /** 发送语音消息：上传音频附件后以 messageType=audio 发送。 */
    fun sendVoice(
        uriString: String,
        mimeType: String,
        fileName: String,
        onDone: () -> Unit,
    ) {
        val chatId = _chatId.value ?: return
        val threadId = _threadId.value
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val uri = Uri.parse(uriString)
                val resolver = context.contentResolver
                val name = fileName.ifBlank {
                    queryDisplayName(uri) ?: "voice_${System.currentTimeMillis()}.m4a"
                }
                val fileSize = querySize(uri)
                    ?: resolver.openInputStream(uri)?.use { it.readBytes().size.toLong() }
                    ?: 0L
                val upload = api.uploadUrl(
                    UploadUrlRequest(
                        filename = name,
                        contentType = mimeType,
                        size = fileSize,
                    ),
                )
                api.uploadStream(
                    uploadUrl = upload.uploadUrl,
                    headers = upload.uploadHeaders,
                    content = {
                        resolver.openInputStream(uri)
                            ?: throw IllegalStateException(getString(R.string.chat_read_file_failed))
                    },
                    contentType = mimeType,
                    contentLength = fileSize.takeIf { it > 0 },
                )
                engine.sendMessage(
                    chatId = chatId,
                    text = null,
                    replyRootId = threadId,
                    attachmentIds = listOf(upload.attachmentId),
                    messageType = "audio",
                )
                    .onFailure {
                        _uiState.value = _uiState.value.copy(
                            error = getString(R.string.chat_send_failed, it.message),
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = getString(R.string.chat_send_failed, e.message),
                )
            } finally {
                onDone()
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
