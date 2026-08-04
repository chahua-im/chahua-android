package net.paigu.chahua.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** 用户信息（消息发送者 / 线程参与者等）。 */
@Serializable
data class UserDto(
    val uid: Int,
    val avatarUrl: String? = null,
    val name: String? = null,
    val gender: Int = 0,
    val userGroup: UserGroupTagDto? = null,
)

@Serializable
data class UserGroupTagDto(
    val groupId: Int,
    val name: String? = null,
    val chatGroupColor: String? = null,
    val chatGroupColorDark: String? = null,
)

/** GET /users/me 响应。 */
@Serializable
data class MeResponse(
    val uid: Int,
    val username: String,
    val avatarUrl: String? = null,
    val gender: Int = 0,
    val stickerPackOrder: List<StickerPackOrderItemDto> = emptyList(),
    val permissions: List<String> = emptyList(),
)

@Serializable
data class AuthTokenResponse(val token: String)

@Serializable
data class TicketResponse(val ticket: String)

@Serializable
data class StickerPackOrderItemDto(
    val stickerPackId: String,
    val lastUsedOn: Long = 0,
)

/** 聊天（会话）列表项，对应 ChatListItem。 */
@Serializable
data class ChatDto(
    val id: String,
    val name: String? = null,
    val avatar: String? = null,
    val lastMessageAt: String? = null,
    val unreadCount: Long = 0,
    val lastReadMessageId: String? = null,
    val lastMessage: MessagePreviewDto? = null,
    val mutedUntil: String? = null,
    val archived: Boolean = false,
)

@Serializable
data class ListChatsResponse(
    val chats: List<ChatDto> = emptyList(),
    val nextCursor: String? = null,
)

/** 话题（线程）列表项，对应 ThreadListItem。 */
@Serializable
data class ThreadDto(
    val chatId: String,
    val chatName: String,
    val chatAvatar: String? = null,
    val threadRootMessage: MessagePreviewDto? = null,
    val participants: List<UserDto> = emptyList(),
    val lastReply: MessagePreviewDto? = null,
    val replyCount: Long = 0,
    val lastReplyAt: String? = null,
    val unreadCount: Long = 0,
    val lastReadMessageId: String? = null,
    val subscribedAt: String? = null,
    val archived: Boolean = false,
)

@Serializable
data class ListThreadsResponse(
    val threads: List<ThreadDto> = emptyList(),
    val nextCursor: String? = null,
)

/** 消息，对应 MessageResponse。 */
@Serializable
data class MessageDto(
    val id: String,
    val message: String? = null,
    val messageType: String = "text",
    val sticker: StickerDto? = null,
    val replyRootId: String? = null,
    val clientGeneratedId: String = "",
    val sender: UserDto,
    val chatId: String,
    val createdAt: String? = null,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val hasAttachments: Boolean = false,
    val threadInfo: ThreadInfoDto? = null,
    val replyToMessage: MessagePreviewDto? = null,
    val attachments: List<AttachmentDto> = emptyList(),
    val reactions: List<ReactionDto> = emptyList(),
    val mentions: List<MentionDto> = emptyList(),
)

/** 将完整消息转为会话列表使用的轻量预览。 */
fun MessageDto.toPreview(): MessagePreviewDto = MessagePreviewDto(
    id = id,
    clientGeneratedId = clientGeneratedId,
    createdAt = createdAt,
    sender = sender,
    message = message,
    messageType = messageType,
    sticker = sticker?.let { MessagePreviewStickerDto(emoji = it.emoji) },
    attachments = attachments.map { MessagePreviewAttachmentDto(kind = it.kind) },
    isDeleted = isDeleted,
    mentions = mentions,
)

@Serializable
data class ThreadInfoDto(
    val replyCount: Long = 0,
)

@Serializable
data class MentionDto(
    val uid: Int,
    val username: String? = null,
    val avatarUrl: String? = null,
    val gender: Int = 0,
)

/** 消息预览（会话列表最后一条消息 / 被回复消息）。 */
@Serializable
data class MessagePreviewDto(
    val id: String,
    val clientGeneratedId: String = "",
    val createdAt: String? = null,
    val sender: UserDto? = null,
    val message: String? = null,
    val messageType: String = "text",
    val sticker: MessagePreviewStickerDto? = null,
    val attachments: List<MessagePreviewAttachmentDto> = emptyList(),
    val isDeleted: Boolean = false,
    val mentions: List<MentionDto> = emptyList(),
)

@Serializable
data class MessagePreviewStickerDto(val emoji: String = "")

@Serializable
data class MessagePreviewAttachmentDto(val kind: String = "")

@Serializable
data class StickerDto(
    val id: String,
    val emoji: String = "",
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val isFavorited: Boolean = false,
    val media: StickerMediaDto? = null,
)

@Serializable
data class StickerMediaDto(
    val id: String,
    val url: String,
    val contentType: String = "",
    val size: Long = 0,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class AttachmentDto(
    val id: String,
    val url: String,
    val kind: String = "other",
    val size: Long = 0,
    val fileName: String = "",
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class ReactionDto(
    val emoji: String,
    val count: Long = 0,
    val reactedByMe: Boolean? = null,
)

@Serializable
data class ListMessagesResponse(
    val messages: List<MessageDto> = emptyList(),
    val nextCursor: String? = null,
    val prevCursor: String? = null,
    val olderCursor: String? = null,
    val newerCursor: String? = null,
)

@Serializable
data class UploadUrlResponse(
    val attachmentId: String,
    val uploadUrl: String,
    val uploadHeaders: Map<String, String> = emptyMap(),
)

@Serializable
data class MarkReadResponse(
    val lastReadMessageId: String? = null,
    val unreadCount: Long = 0,
)

@Serializable
data class UnreadCountResponse(
    val unreadCount: Long = 0,
    val archivedUnreadCount: Long = 0,
    val unreadChatCount: Long = 0,
    val archivedUnreadChatCount: Long = 0,
)

/** 群组搜索列表项，对应 GroupSelectorItem。 */
@Serializable
data class GroupSelectorItem(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val avatar: String? = null,
    val visibility: String = "private",
    val role: String? = null,
)

@Serializable
data class ListGroupsResponse(
    val groups: List<GroupSelectorItem> = emptyList(),
    val nextCursor: String? = null,
)

/** 申请附件预签名上传地址的请求体。 */
@Serializable
data class UploadUrlRequest(
    val filename: String,
    val contentType: String,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
    val order: Int? = null,
)

/** 发送消息请求体，对应 CreateMessageBody。 */
@Serializable
data class CreateMessageBody(
    val message: String? = null,
    val messageType: String = "text",
    val stickerId: String? = null,
    val clientGeneratedId: String,
    val replyToId: String? = null,
    val replyRootId: String? = null,
    val attachmentIds: List<String> = emptyList(),
)

/** WebSocket 事件信封：{ "type": "...", "payload": { ... } }。 */
@Serializable
data class WsEnvelope(
    val type: String,
    val payload: JsonElement? = null,
)

@Serializable
data class ReactionUpdateDto(
    val messageId: String,
    val chatId: String,
    val reactions: List<ReactionDto> = emptyList(),
)

@Serializable
data class ThreadUpdateDto(
    val threadRootId: String,
    val chatId: String,
    val lastReplyAt: String? = null,
    val replyCount: Long = 0,
)

@Serializable
data class ChatArchiveStateDto(
    val chatId: String,
    val archived: Boolean = false,
    val mutedUntil: String? = null,
)

@Serializable
data class BulkDeletedDto(
    val chatId: String,
    val messageIds: List<String> = emptyList(),
)

@Serializable
data class PresenceUpdateDto(
    val activeConnections: Long = 0,
)
