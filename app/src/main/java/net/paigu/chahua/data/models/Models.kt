package net.paigu.chahua.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** 用户信息（消息发送者 / 话题参与者等）。 */
@Serializable
data class UserDto(
    val uid: Int,
    val avatarUrl: String? = null,
    val name: String? = null,
    val gender: Int = 0,
    val userGroup: UserGroupTagDto? = null,
)

/** 会话/群组信息中的对端用户摘要（MemberSummary，字段名为 username）。 */
@Serializable
data class MemberSummaryDto(
    val uid: Int,
    val username: String? = null,
    val avatarUrl: String? = null,
    val gender: Int = 0,
    val userGroup: UserGroupTagDto? = null,
) {
    fun toUserDto(): UserDto = UserDto(
        uid = uid,
        avatarUrl = avatarUrl,
        name = username,
        gender = gender,
        userGroup = userGroup,
    )
}

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

/** 邀请码对应的群聊信息。 */
@Serializable
data class InviteGroupDto(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val avatar: String? = null,
    val visibility: String = "private",
    val createdAt: String? = null,
    val mutedUntil: String? = null,
    val myRole: String? = null,
)

@Serializable
data class InviteResponse(
    val id: String,
    val code: String,
    val chatId: String,
    val inviteType: String = "generic",
    val creatorUid: Int? = null,
    val targetUid: Int? = null,
    val requiredChatId: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val revokedAt: String? = null,
    val usedAt: String? = null,
)

@Serializable
data class InvitePreviewResponse(
    val invite: InviteResponse,
    val chat: InviteGroupDto,
    val alreadyMember: Boolean = false,
)

@Serializable
data class RedeemInviteResponse(
    val chat: InviteGroupDto,
)

@Serializable
data class RedeemInviteBody(
    val code: String,
)

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
    val kind: String = "group",
    /** DM 会话中的对端用户；普通群聊为 null。 */
    val peer: MemberSummaryDto? = null,
)

/** 会话是否为 DM（私聊）。 */
val ChatDto.isDm: Boolean get() = kind == "dm"

/** DM 会话的展示名称：优先对端用户名，其次群名。 */
val ChatDto.displayName: String?
    get() = if (isDm) peer?.username?.takeIf { it.isNotBlank() } ?: name else name

@Serializable
data class ListChatsResponse(
    val chats: List<ChatDto> = emptyList(),
    val nextCursor: String? = null,
)

/** 话题列表项，对应 ThreadListItem。 */
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

/** 贴纸摘要（贴纸包详情 / 收藏贴纸列表项）。*/
@Serializable
data class StickerSummaryDto(
    val id: String,
    val media: StickerMediaDto,
    val emoji: String = "",
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val isFavorited: Boolean = false,
)

@Serializable
data class StickerPackPreviewStickerDto(
    val id: String,
    val media: StickerMediaDto,
    val emoji: String = "",
)

@Serializable
data class StickerPackSummaryDto(
    val id: String,
    val ownerUid: Int = 0,
    val ownerName: String? = null,
    val name: String = "",
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val stickerCount: Long = 0,
    val isSubscribed: Boolean = false,
    val previewSticker: StickerPackPreviewStickerDto? = null,
)

@Serializable
data class StickerPackListResponse(
    val packs: List<StickerPackSummaryDto> = emptyList(),
)

@Serializable
data class FavoriteStickerListResponse(
    val stickers: List<StickerSummaryDto> = emptyList(),
)

@Serializable
data class StickerPackDetailResponse(
    val id: String,
    val ownerUid: Int = 0,
    val ownerName: String? = null,
    val name: String = "",
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val stickerCount: Long = 0,
    val isSubscribed: Boolean = false,
    val previewSticker: StickerPackPreviewStickerDto? = null,
    val stickers: List<StickerSummaryDto> = emptyList(),
)

/** GET /stickers/{sticker_id} 响应：贴纸详情（展平 StickerSummary）+ 所属贴纸包。 */
@Serializable
data class StickerDetailResponse(
    val id: String,
    val media: StickerMediaDto,
    val emoji: String = "",
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val isFavorited: Boolean = false,
    val packs: List<StickerPackSummaryDto> = emptyList(),
)

/** 好友验证设置（GET/PUT /friends/me/settings）。mode: direct | need_message | question | forbid。 */
@Serializable
data class FriendSettingsResponse(
    val mode: String = "direct",
    val question: String? = null,
)

@Serializable
data class UpdateFriendSettingsBody(
    val mode: String,
    val question: String? = null,
)

/** 好友列表项，对应 FriendResponse。 */
@Serializable
data class FriendResponse(
    val user: MemberSummaryDto,
    val since: String? = null,
)

@Serializable
data class ListFriendsResponse(
    val friends: List<FriendResponse> = emptyList(),
)

/** 好友请求记录，对应 FriendRequestResponse。 */
@Serializable
data class FriendRequestDto(
    val id: String,
    val from: MemberSummaryDto,
    val to: MemberSummaryDto,
    val status: String = "pending",
    val createdAt: String? = null,
    val decidedAt: String? = null,
    /** 验证消息（need_message）或请求者答案（question）。 */
    val message: String? = null,
    /** 目标用户设置的问题（question 模式）。 */
    val question: String? = null,
)

/** 好友请求历史条目：服务端将请求字段展平并与 direction 并列返回。 */
@Serializable
data class FriendRequestHistoryEntryDto(
    val id: String,
    val from: MemberSummaryDto,
    val to: MemberSummaryDto,
    val status: String = "pending",
    val createdAt: String? = null,
    val decidedAt: String? = null,
    val message: String? = null,
    val question: String? = null,
    val direction: String = "incoming",
) {
    val request: FriendRequestDto
        get() = FriendRequestDto(
            id = id,
            from = from,
            to = to,
            status = status,
            createdAt = createdAt,
            decidedAt = decidedAt,
            message = message,
            question = question,
        )
}

@Serializable
data class ListFriendRequestHistoryResponse(
    val requests: List<FriendRequestHistoryEntryDto> = emptyList(),
)

@Serializable
data class PendingFriendRequestCountResponse(
    val pendingIncomingCount: Long = 0,
)

/** 目标用户的好友验证信息（GET /friends/add-info/{uid}）。 */
@Serializable
data class FriendAddInfoResponse(
    val mode: String = "direct",
    val question: String? = null,
)

/** 发送好友请求（POST /friends/requests）。 */
@Serializable
data class CreateFriendRequestBody(
    val toUid: Int,
    val message: String? = null,
)

@Serializable
data class CreateStickerPackBody(
    val name: String,
    val description: String? = null,
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
data class ReactionReactorDto(
    val uid: Int,
    val name: String? = null,
    val avatarUrl: String? = null,
    val sortIndex: Int? = null,
)

@Serializable
data class ReactionDto(
    val emoji: String,
    val count: Long = 0,
    val reactedByMe: Boolean? = null,
    val reactors: List<ReactionReactorDto>? = null,
)

@Serializable
data class ReactionGroupDto(
    val emoji: String,
    val reactors: List<ReactionReactorDto> = emptyList(),
)

@Serializable
data class ReactionDetailResponse(
    val reactions: List<ReactionGroupDto> = emptyList(),
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

@Serializable
data class GroupInfoDto(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val avatarImageId: String? = null,
    val avatar: String? = null,
    val visibility: String = "private",
    val createdAt: String? = null,
    val mutedUntil: String? = null,
    val myRole: String? = null,
    val kind: String = "group",
    /** DM 会话中的对端用户；普通群聊为 null。 */
    val peer: MemberSummaryDto? = null,
)

@Serializable
data class MuteBody(
    val durationSeconds: Long? = null,
)

@Serializable
data class MuteResponse(
    val mutedUntil: String? = null,
)

@Serializable
data class MemberDto(
    val uid: Int,
    val username: String? = null,
    val avatarUrl: String? = null,
    val role: String? = null,
    val joinedAt: String? = null,
    val gender: Int = 0,
)

@Serializable
data class ListMembersResponse(
    val members: List<MemberDto> = emptyList(),
    val nextCursor: Int? = null,
    val canManageMembers: Boolean = false,
)

@Serializable
data class ChatAttachmentDto(
    val id: String,
    val messageId: String = "",
    val messageCreatedAt: String? = null,
    val sender: UserDto? = null,
    val url: String = "",
    val kind: String = "other",
    val size: Long = 0,
    val fileName: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val order: Int = 0,
)

@Serializable
data class ListChatAttachmentsResponse(
    val attachments: List<ChatAttachmentDto> = emptyList(),
    val olderCursor: String? = null,
    val newerCursor: String? = null,
)

@Serializable
data class SavedAttachmentSnapshotDto(
    val id: String,
    val url: String = "",
    val kind: String = "other",
    val size: Long = 0,
    val fileName: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val order: Int = 0,
)

@Serializable
data class SavedSenderSnapshotDto(
    val uid: Int = 0,
    val name: String? = null,
    val avatarUrl: String? = null,
    val gender: Int = 0,
)

@Serializable
data class SavedChatSnapshotDto(
    val id: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
)

@Serializable
data class SavedMessageDto(
    val id: String,
    val originalChatId: String = "",
    val originalThreadRootId: String? = null,
    val originalMessageId: String = "",
    val originalReplyToMessageId: String? = null,
    val originalSenderUid: Int = 0,
    val originalCreatedAt: String? = null,
    val savedAt: String? = null,
    val message: String? = null,
    val messageType: String = "text",
    val attachments: List<SavedAttachmentSnapshotDto> = emptyList(),
    val sticker: MessagePreviewStickerDto? = null,
    val mentions: List<MentionDto> = emptyList(),
    val sender: SavedSenderSnapshotDto? = null,
    val chat: SavedChatSnapshotDto? = null,
    val canLocateContext: Boolean = false,
)

@Serializable
data class ListSavedMessagesResponse(
    val savedMessages: List<SavedMessageDto> = emptyList(),
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

/** 置顶消息，对应 PinResponse。 */
@Serializable
data class PinDto(
    val id: String,
    val chatId: String,
    val message: MessageDto,
    val pinnedBy: Int = 0,
    val pinnedAt: String? = null,
    val expiresAt: String? = null,
)

@Serializable
data class ListPinsResponse(
    val pins: List<PinDto> = emptyList(),
)

@Serializable
data class CreatePinBody(
    val messageId: String,
)

/** 新建群聊请求与响应。 */
@Serializable
data class CreateChatBody(
    val name: String? = null,
)

@Serializable
data class CreateChatResponse(
    val id: String,
    val name: String? = null,
    val createdAt: String? = null,
)

/** 更新群资料（管理员）。 */
@Serializable
data class UpdateGroupBody(
    val name: String? = null,
    val description: String? = null,
    val avatarImageId: String? = null,
)

@Serializable
data class GroupAvatarUploadUrlRequest(
    val filename: String,
    val contentType: String,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class GroupAvatarUploadUrlResponse(
    val imageId: String,
    val uploadUrl: String,
    val uploadHeaders: Map<String, String> = emptyMap(),
)

/** 邀请码列表 / 创建。 */
@Serializable
data class ListInvitesResponse(
    val invites: List<InviteResponse> = emptyList(),
)

@Serializable
data class CreateInviteBody(
    val chatId: String,
    val inviteType: String = "generic",
    val targetUid: Int? = null,
    val requiredChatId: String? = null,
    val expiresAt: String? = null,
)

@Serializable
data class SendInviteMessageBody(
    val sourceChatId: String,
    val destinationChatId: String,
    val inviteId: String? = null,
    val expiresAt: String? = null,
    val clientGeneratedId: String,
)

/** 贴纸包排序同步。 */
@Serializable
data class UpdateStickerPackOrderItemBody(
    val stickerPackId: String,
    val lastUsedOn: Long,
    val isAutoSort: Boolean? = null,
)

@Serializable
data class UpdateStickerPackOrderRequest(
    val order: List<UpdateStickerPackOrderItemBody> = emptyList(),
)
