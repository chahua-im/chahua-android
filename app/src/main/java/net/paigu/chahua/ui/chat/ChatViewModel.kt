package net.paigu.chahua.ui.chat

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.ReactionDto
import net.paigu.chahua.data.models.UploadUrlRequest
import net.paigu.chahua.data.models.UserDto
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.R
import java.time.Instant
import java.util.UUID

data class PendingMessage(
    val clientGeneratedId: String,
    val text: String?,
    val attachmentLocalUri: String?,
    val createdAt: String,
    val replyToId: String?,
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

    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    val messages: StateFlow<List<ChatItem>> = _messages.asStateFlow()
    val connectionState = store.connectionState
    val latencyMs = AppGraph.engine.latencyMs

    private var initialized = false
    private var currentKey: String? = null
    private var messageJob: Job? = null

    fun init(chatId: String, title: String, threadId: String?, replyCount: Long) {
        val key = "$chatId|${threadId.orEmpty()}"
        if (initialized && currentKey == key) return
        currentKey = key
        initialized = true
        messageJob?.cancel()
        _chatId.value = chatId
        _threadId.value = threadId
        _pending.value = emptyList()
        _messages.value = emptyList()
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
                (serverList.map { ChatItem.Server(it) as ChatItem } +
                    pending.map { ChatItem.Pending(it) as ChatItem })
                    .sortedBy { it.sortKey }
            }.collect { _messages.value = it }
        }
        loadMessages()
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
    fun sendImage(uriString: String) {
        val chatId = _chatId.value ?: return
        val reply = _uiState.value.replyTarget
        val threadId = _threadId.value
        viewModelScope.launch {
            var pending: PendingMessage? = null
            try {
                val context = getApplication<Application>()
                val uri = Uri.parse(uriString)
                val resolver = context.contentResolver
                val mime = resolver.getType(uri) ?: "image/jpeg"
                val name = queryDisplayName(uri) ?: "image_${System.currentTimeMillis()}.jpg"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException(getString(R.string.chat_read_image_failed))
                if (bytes.size > 20 * 1024 * 1024) {
                    _uiState.value = _uiState.value.copy(error = getString(R.string.chat_image_too_large))
                    return@launch
                }
                val (width, height) = imageBounds(bytes)
                val created = PendingMessage(
                    clientGeneratedId = "android-${UUID.randomUUID()}",
                    text = null,
                    attachmentLocalUri = uriString,
                    createdAt = Instant.now().toString(),
                    replyToId = reply?.id,
                )
                pending = created
                _pending.update { it + created }
                _uiState.value = _uiState.value.copy(replyTarget = null)

                val upload = api.uploadUrl(
                    UploadUrlRequest(
                        filename = name,
                        contentType = mime,
                        size = bytes.size.toLong(),
                        width = width,
                        height = height,
                    ),
                )
                api.uploadFile(upload.uploadUrl, upload.uploadHeaders, bytes, mime)
                engine.sendMessage(
                    chatId = chatId,
                    text = "",
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

    private fun removePending(clientGeneratedId: String?) {
        if (clientGeneratedId == null) return
        _pending.update { it.filterNot { p -> p.clientGeneratedId == clientGeneratedId } }
    }

    fun toggleReaction(message: MessageDto, emoji: String) {
        val chatId = _chatId.value ?: return
        val existing = message.reactions.firstOrNull { it.emoji == emoji }
        val add = existing?.reactedByMe != true
        val newReactions = buildList {
            addAll(message.reactions)
            if (add) {
                removeAll { it.emoji == emoji }
                add(ReactionDto(emoji = emoji, count = 1, reactedByMe = true))
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
        // 乐观更新，等待 WS reactionUpdated 或失败回滚
        store.onReactionUpdate(chatId, message.id, newReactions)
        viewModelScope.launch {
            runCatching {
                if (add) api.addReaction(chatId, message.id, emoji)
                else api.removeReaction(chatId, message.id, emoji)
            }.onFailure {
                store.onReactionUpdate(chatId, message.id, message.reactions)
                _uiState.value = _uiState.value.copy(error = getString(R.string.chat_action_failed, it.message))
            }
        }
    }

    fun deleteMessage(message: MessageDto) {
        val chatId = _chatId.value ?: return
        viewModelScope.launch {
            runCatching {
                api.deleteMessage(chatId, message.id)
                store.onMessageDeleted(chatId, _threadId.value, message.id)
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = getString(R.string.chat_delete_failed, it.message))
            }
        }
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
