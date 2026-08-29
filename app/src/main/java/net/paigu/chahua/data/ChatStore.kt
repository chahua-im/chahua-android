package net.paigu.chahua.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.paigu.chahua.data.models.ChatDto
import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.ReactionDto
import net.paigu.chahua.data.models.ThreadDto
import net.paigu.chahua.data.models.ThreadUpdateDto
import net.paigu.chahua.data.models.toPreview

enum class WsStatus { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * 进程内消息/会话缓存（单一数据源）。
 * 后台 Service 通过 WebSocket 写入，各页面的 ViewModel 读取。
 */
class ChatStore {

    /** 当前用户 UID（用于区分自己发送的消息，避免未读计数误增）。 */
    @Volatile
    var currentUid: () -> Int = { -1 }

    private val _chats = MutableStateFlow<List<ChatDto>>(emptyList())
    val chats: StateFlow<List<ChatDto>> = _chats.asStateFlow()

    private val _threads = MutableStateFlow<List<ThreadDto>>(emptyList())
    val threads: StateFlow<List<ThreadDto>> = _threads.asStateFlow()

    /** 消息时间线：key = chatId（顶层消息）或 "chatId:threadRootId"（话题内消息）。 */
    private val _messages = MutableStateFlow<Map<String, List<MessageDto>>>(emptyMap())
    val messages: StateFlow<Map<String, List<MessageDto>>> = _messages.asStateFlow()

    private val _connectionState = MutableStateFlow(WsStatus.DISCONNECTED)
    val connectionState: StateFlow<WsStatus> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _incoming = MutableSharedFlow<MessageDto>(extraBufferCapacity = 64)
    val incoming: SharedFlow<MessageDto> = _incoming.asSharedFlow()

    fun timelineKey(chatId: String, threadId: String?): String =
        if (threadId.isNullOrBlank()) chatId else "$chatId:$threadId"

    fun messagesFor(chatId: String, threadId: String?): Flow<List<MessageDto>> =
        _messages.map { it[timelineKey(chatId, threadId)] ?: emptyList() }.distinctUntilChanged()

    fun setConnectionState(state: WsStatus) {
        _connectionState.value = state
    }

    fun setError(message: String?) {
        _lastError.value = message
    }

    fun setChats(list: List<ChatDto>) {
        _chats.value = list
    }

    fun setThreads(list: List<ThreadDto>) {
        _threads.value = list
    }

    fun setMessages(chatId: String, threadId: String?, list: List<MessageDto>) {
        val key = timelineKey(chatId, threadId)
        _messages.update { current ->
            current + (key to sortMessages(list))
        }
    }

    fun appendMessages(chatId: String, threadId: String?, list: List<MessageDto>) {
        val key = timelineKey(chatId, threadId)
        _messages.update { current ->
            val existing = current[key].orEmpty()
            val merged = (existing + list).associateBy { it.id }.values.toList()
            current + (key to sortMessages(merged))
        }
    }

    /** 新消息到达（含自己发送的确认），写入对应时间线并更新会话列表。 */
    fun onNewMessage(msg: MessageDto) {
        val key = timelineKey(msg.chatId, msg.replyRootId)
        _messages.update { current ->
            val existing = current[key].orEmpty()
            val merged = (existing + msg).associateBy { it.id }.values.toList()
            current + (key to sortMessages(merged))
        }
        // 会话列表：更新最后一条消息预览与未读数
        _chats.update { chats ->
            chats.map { chat ->
                if (chat.id == msg.chatId) {
                    val isMine = msg.sender.uid == currentUid()
                    val isThreadReply = !msg.replyRootId.isNullOrBlank()
                    chat.copy(
                        lastMessage = if (msg.isDeleted) chat.lastMessage else msg.toPreview(),
                        lastMessageAt = msg.createdAt ?: chat.lastMessageAt,
                        unreadCount = if (isMine || isThreadReply) chat.unreadCount else chat.unreadCount + 1,
                    )
                } else chat
            }
        }
        _threads.update { threads ->
            if (msg.replyRootId.isNullOrBlank()) threads
            else threads.map { t ->
                if (t.chatId == msg.chatId && t.threadRootMessage?.id == msg.replyRootId) {
                    val isMine = msg.sender.uid == currentUid()
                    t.copy(
                        replyCount = t.replyCount + 1,
                        unreadCount = if (isMine) t.unreadCount else t.unreadCount + 1,
                    )
                } else t
            }
        }
        _incoming.tryEmit(msg)
    }

    fun onMessageUpdated(msg: MessageDto) {
        val key = timelineKey(msg.chatId, msg.replyRootId)
        _messages.update { current ->
            val existing = current[key].orEmpty()
            val replaced = existing.map { if (it.id == msg.id) msg else it }
            current + (key to sortMessages(replaced))
        }
    }

    /** 收藏/取消收藏贴纸后，同步更新本地所有包含该贴纸的消息。 */
    fun applyStickerFavorite(stickerId: String, favorite: Boolean) {
        _messages.update { current ->
            current.mapValues { (_, list) ->
                list.map { message ->
                    val sticker = message.sticker
                    if (sticker != null && sticker.id == stickerId) {
                        message.copy(sticker = sticker.copy(isFavorited = favorite))
                    } else {
                        message
                    }
                }
            }
        }
    }

    fun onMessageDeleted(chatId: String, threadId: String?, messageId: String) {
        val key = timelineKey(chatId, threadId)
        _messages.update { current ->
            val existing = current[key].orEmpty()
            val remaining = existing.map { m ->
                if (m.id == messageId) m.copy(isDeleted = true, message = null) else m
            }
            current + (key to sortMessages(remaining))
        }
    }

    fun removeMessages(chatId: String, threadId: String?, messageIds: List<String>) {
        val key = timelineKey(chatId, threadId)
        _messages.update { current ->
            val existing = current[key].orEmpty()
            val remaining = existing.filterNot { it.id in messageIds }
            current + (key to sortMessages(remaining))
        }
    }

    fun onReactionUpdate(chatId: String, messageId: String, reactions: List<ReactionDto>) {
        _messages.update { current ->
            current.mapValues { (key, list) ->
                if (key == chatId || key.startsWith("$chatId:")) {
                    list.map { m ->
                        if (m.id == messageId) {
                            // 广播的 reactedByMe 为 null，保留本地已知的“我是否点过”状态，
                            // 否则高亮会消失、再次点击会变成重复添加而不是取消。
                            val merged = reactions.map { incoming ->
                                val previous = m.reactions.firstOrNull { it.emoji == incoming.emoji }
                                if (incoming.reactedByMe == null && previous?.reactedByMe != null) {
                                    incoming.copy(reactedByMe = previous.reactedByMe)
                                } else {
                                    incoming
                                }
                            }
                            m.copy(reactions = merged)
                        } else {
                            m
                        }
                    }
                } else list
            }
        }
    }

    fun onThreadUpdate(payload: ThreadUpdateDto) {
        _threads.update { threads ->
            threads.map { t ->
                if (t.chatId == payload.chatId && t.threadRootMessage?.id == payload.threadRootId) {
                    t.copy(
                        replyCount = payload.replyCount,
                        lastReplyAt = payload.lastReplyAt ?: t.lastReplyAt,
                    )
                } else t
            }
        }
    }

    fun onChatArchiveState(chatId: String, archived: Boolean) {
        _chats.update { chats ->
            chats.map { if (it.id == chatId) it.copy(archived = archived) else it }
        }
    }

    /** 更新会话免打扰状态（群组信息页静音/取消静音后调用）。*/
    fun setChatMuted(chatId: String, mutedUntil: String?) {
        _chats.update { chats ->
            chats.map { if (it.id == chatId) it.copy(mutedUntil = mutedUntil) else it }
        }
    }

    /** 退出群组后从本地列表中移除该会话及其消息。*/
    fun removeChat(chatId: String) {
        _chats.update { chats -> chats.filterNot { it.id == chatId } }
        _threads.update { threads -> threads.filterNot { it.chatId == chatId } }
        _messages.update { messages ->
            messages.filterKeys { key -> key != chatId && !key.startsWith("$chatId:") }
        }
    }

    /** 归档话题后从活跃话题列表移除。*/
    fun removeThread(chatId: String, threadRootId: String) {
        _threads.update { threads ->
            threads.filterNot {
                it.chatId == chatId && it.threadRootMessage?.id == threadRootId
            }
        }
    }

    fun markChatRead(chatId: String, lastReadMessageId: String?, unreadCount: Long) {
        _chats.update { chats ->
            chats.map {
                if (it.id == chatId) {
                    it.copy(lastReadMessageId = lastReadMessageId, unreadCount = unreadCount)
                } else it
            }
        }
    }

    fun markThreadRead(chatId: String, threadRootId: String, unreadCount: Long) {
        _threads.update { threads ->
            threads.map {
                if (it.chatId == chatId && it.threadRootMessage?.id == threadRootId) {
                    it.copy(unreadCount = unreadCount)
                } else it
            }
        }
    }

    fun clear() {
        _chats.value = emptyList()
        _threads.value = emptyList()
        _messages.value = emptyMap()
        _connectionState.value = WsStatus.DISCONNECTED
    }

    private fun sortMessages(list: List<MessageDto>): List<MessageDto> =
        list.sortedWith(compareBy({ it.createdAt.orEmpty() }, { it.id }))
}

private fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    this.value = transform(this.value)
}
