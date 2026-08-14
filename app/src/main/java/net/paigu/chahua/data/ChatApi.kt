package net.paigu.chahua.data

import java.io.InputStream
import net.paigu.chahua.data.models.AuthTokenResponse
import net.paigu.chahua.data.models.CreateStickerPackBody
import net.paigu.chahua.data.models.CreateMessageBody
import net.paigu.chahua.data.models.FavoriteStickerListResponse
import net.paigu.chahua.data.models.GroupInfoDto
import net.paigu.chahua.data.models.InvitePreviewResponse
import net.paigu.chahua.data.models.ListChatAttachmentsResponse
import net.paigu.chahua.data.models.ListChatsResponse
import net.paigu.chahua.data.models.ListGroupsResponse
import net.paigu.chahua.data.models.ListMembersResponse
import net.paigu.chahua.data.models.ListMessagesResponse
import net.paigu.chahua.data.models.ListSavedMessagesResponse
import net.paigu.chahua.data.models.ListThreadsResponse
import net.paigu.chahua.data.models.MarkReadResponse
import net.paigu.chahua.data.models.MeResponse
import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.MuteBody
import net.paigu.chahua.data.models.MuteResponse
import net.paigu.chahua.data.models.RedeemInviteBody
import net.paigu.chahua.data.models.RedeemInviteResponse
import net.paigu.chahua.data.models.StickerPackDetailResponse
import net.paigu.chahua.data.models.StickerPackListResponse
import net.paigu.chahua.data.models.StickerPackSummaryDto
import net.paigu.chahua.data.models.StickerSummaryDto
import net.paigu.chahua.data.models.TicketResponse
import net.paigu.chahua.data.models.UploadUrlRequest
import net.paigu.chahua.data.models.UploadUrlResponse

/**
 * REST API 封装：覆盖聊天列表、消息、线程、群组、附件、用户等核心端点。
 * 所有 ID 以字符串形式传输（后端 Snowflake i64 序列化为字符串）。
 */
class ChatApi(
    private val client: ApiClient,
    private val session: SessionManager,
) {

    // ---- 登录 ----

    /** 通过 shireyishunjian 账号密码接口换取 JWT（接口返回纯文本 JWT）。 */
    suspend fun loginWithCredentials(username: String, password: String): String =
        client.loginWithCredentials(username, password)

    // ---- 用户 ----

    suspend fun me(): MeResponse = client.get("users/me")

    /** 用当前认证上下文换取 JWT（UIDHeader 模式升级为 JWT）。 */
    suspend fun authToken(): String = client.get<AuthTokenResponse>("users/auth-token").token

    // ---- WebSocket 票据 ----

    /** 已有 JWT 时直接复用 JWT；否则调用 /ws/ticket 换取短期票据。 */
    suspend fun wsTicket(): String {
        val s = session.snapshot()
        return if (s.isJwt && !s.authKey.isNullOrBlank()) s.authKey
        else client.get<TicketResponse>("ws/ticket").ticket
    }

    // ---- 聊天列表 / 线程列表 ----

    suspend fun chats(limit: Int = 100, after: String? = null, archived: Boolean = false): ListChatsResponse =
        client.get(
            "chats",
            buildMap {
                put("limit", limit.toString())
                after?.let { put("after", it) }
                if (archived) put("archived", "true")
            },
        )

    suspend fun threads(limit: Int = 100, before: String? = null, archived: Boolean = false): ListThreadsResponse =
        client.get(
            "threads",
            buildMap {
                put("limit", limit.toString())
                before?.let { put("before", it) }
                if (archived) put("archived", "true")
            },
        )

    // ---- 消息 ----

    suspend fun messages(
        chatId: String,
        before: String? = null,
        after: String? = null,
        around: String? = null,
        threadId: String? = null,
        max: Int = 100,
    ): ListMessagesResponse = client.get(
        "chats/$chatId/messages",
        buildMap {
            put("max", max.toString())
            before?.let { put("before", it) }
            after?.let { put("after", it) }
            around?.let { put("around", it) }
            threadId?.let { put("threadId", it) }
        },
    )

    suspend fun message(chatId: String, messageId: String): MessageDto =
        client.get("chats/$chatId/messages/$messageId")

    suspend fun searchMessages(
        chatId: String,
        q: String,
        limit: Int = 20,
        offset: Int = 0,
        sort: String = "relevance",
    ): ListMessagesResponse = client.get(
        "chats/$chatId/messages/search",
        buildMap {
            put("q", q)
            put("limit", limit.toString())
            put("offset", offset.toString())
            put("sort", sort)
        },
    )

    suspend fun sendMessage(chatId: String, body: CreateMessageBody): MessageDto =
        client.post("chats/$chatId/messages", body = body)

    suspend fun sendThreadReply(chatId: String, threadRootId: String, body: CreateMessageBody): MessageDto =
        client.post("chats/$chatId/threads/$threadRootId/messages", body = body)

    suspend fun deleteMessage(chatId: String, messageId: String) {
        client.noContent("DELETE", "chats/$chatId/messages/$messageId")
    }

    suspend fun editMessage(
        chatId: String,
        messageId: String,
        newText: String,
        attachmentIds: List<String> = emptyList(),
    ): MessageDto =
        client.patch(
            "chats/$chatId/messages/$messageId",
            body = EditMessageBody(message = newText, attachmentIds = attachmentIds),
        )

    // ---- 反应 ----

    suspend fun addReaction(chatId: String, messageId: String, emoji: String) {
        client.noContent("PUT", "chats/$chatId/messages/$messageId/reactions/$emoji")
    }

    suspend fun removeReaction(chatId: String, messageId: String, emoji: String) {
        client.noContent("DELETE", "chats/$chatId/messages/$messageId/reactions/$emoji")
    }

    // ---- 已读 ----

    suspend fun markChatRead(chatId: String, messageId: String): MarkReadResponse =
        client.post("chats/$chatId/read", body = ReadBody(messageId = messageId))

    suspend fun markThreadRead(threadRootId: String, messageId: String): MarkReadResponse =
        client.post("threads/$threadRootId/read", body = ReadBody(messageId = messageId))

    // ---- 附件 ----

    suspend fun uploadUrl(request: UploadUrlRequest): UploadUrlResponse =
        client.post("attachments/upload-url", body = request)

    suspend fun uploadFile(uploadUrl: String, headers: Map<String, String>, bytes: ByteArray, contentType: String) {
        client.uploadBytes(uploadUrl, headers, bytes, contentType)
    }

    suspend fun uploadStream(
        uploadUrl: String,
        headers: Map<String, String>,
        content: () -> InputStream,
        contentType: String,
        contentLength: Long?,
    ) {
        client.uploadStream(uploadUrl, headers, content, contentType, contentLength)
    }

    // ---- 群组 ----

    suspend fun groupSearch(q: String? = null, limit: Int = 50): ListGroupsResponse =
        client.get(
            "group",
            buildMap {
                q?.takeIf { it.isNotBlank() }?.let { put("q", it) }
                put("limit", limit.toString())
            },
        )

    suspend fun groupInfo(chatId: String): GroupInfoDto =
        client.get("group/$chatId")

    suspend fun muteChat(chatId: String, durationSeconds: Long? = null): MuteResponse =
        client.put("group/$chatId/mute", body = MuteBody(durationSeconds = durationSeconds))

    suspend fun unmuteChat(chatId: String) {
        client.noContent("DELETE", "group/$chatId/mute")
    }

    suspend fun members(
        chatId: String,
        limit: Int = 50,
        after: Int? = null,
        q: String? = null,
    ): ListMembersResponse = client.get(
        "group/$chatId/members",
        buildMap {
            put("limit", limit.toString())
            after?.let { put("after", it.toString()) }
            q?.takeIf { it.isNotBlank() }?.let { put("q", it) }
        },
    )

    suspend fun leaveGroup(chatId: String, uid: Int) {
        client.noContent("DELETE", "group/$chatId/members/$uid")
    }

    suspend fun archiveChat(chatId: String) {
        client.noContent("PUT", "chats/$chatId/archive")
    }

    suspend fun unarchiveChat(chatId: String) {
        client.noContent("DELETE", "chats/$chatId/archive")
    }

    suspend fun archiveThread(chatId: String, threadRootId: String) {
        client.noContent("PUT", "chats/$chatId/threads/$threadRootId/archive")
    }

    suspend fun unarchiveThread(chatId: String, threadRootId: String) {
        client.noContent("DELETE", "chats/$chatId/threads/$threadRootId/archive")
    }

    suspend fun chatAttachments(
        chatId: String,
        kind: String = "all",
        limit: Int = 30,
        before: String? = null,
        after: String? = null,
    ): ListChatAttachmentsResponse = client.get(
        "chats/$chatId/attachments",
        buildMap {
            put("kind", kind)
            put("limit", limit.toString())
            before?.let { put("before", it) }
            after?.let { put("after", it) }
        },
    )

    suspend fun chatSavedMessages(
        chatId: String,
        limit: Int = 50,
        before: String? = null,
    ): ListSavedMessagesResponse = client.get(
        "chats/$chatId/saved-messages",
        buildMap {
            put("limit", limit.toString())
            before?.let { put("before", it) }
        },
    )

    // ---- 邀请码 ----

    /** 按邀请码查询群聊预览，用于加入前确认。 */
    suspend fun invitePreview(code: String): InvitePreviewResponse =
        client.get("invites/invite", buildMap { put("inviteCode", code.trim()) })

    /** 兑换邀请码并加入群聊。 */
    suspend fun redeemInvite(code: String): RedeemInviteResponse =
        client.post("invites/redeem", body = RedeemInviteBody(code = code.trim()))

    // ---- 贴纸 / 表情包 ----

    suspend fun ownedStickerPacks(): StickerPackListResponse =
        client.get("stickers/packs/mine/owned")

    /** 当前用户收藏（订阅）的贴纸包。 */
    suspend fun subscribedStickerPacks(): StickerPackListResponse =
        client.get("stickers/packs/mine/subscribed")

    /** 我收藏的贴纸。 */
    suspend fun favoriteStickers(): FavoriteStickerListResponse =
        client.get("stickers/mine/favorites")

    suspend fun stickerPackDetail(packId: String): StickerPackDetailResponse =
        client.get("stickers/packs/$packId")

    suspend fun createStickerPack(name: String, description: String? = null): StickerPackSummaryDto =
        client.post("stickers/packs", body = CreateStickerPackBody(name = name, description = description))

    /** 取消收藏（退订）贴纸包。 */
    suspend fun unsubscribeStickerPack(packId: String) {
        client.noContent("DELETE", "stickers/packs/$packId/subscription")
    }

    /** 向自己拥有的贴纸包上传一张贴纸（multipart）。 */
    suspend fun uploadStickerToPack(
        packId: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
        emoji: String,
        name: String? = null,
    ): StickerSummaryDto {
        val raw = client.uploadMultipart(
            path = "stickers/packs/$packId/stickers",
            textFields = buildMap {
                put("emoji", emoji)
                name?.takeIf { it.isNotBlank() }?.let { put("name", it) }
            },
            fileFieldName = "file",
            fileName = fileName,
            contentType = contentType,
            bytes = bytes,
        )
        return ApiJson.instance.decodeFromString(StickerSummaryDto.serializer(), raw)
    }
}

@kotlinx.serialization.Serializable
data class EditMessageBody(
    val message: String,
    val attachmentIds: List<String> = emptyList(),
)

@kotlinx.serialization.Serializable
data class ReadBody(
    val messageId: String,
)
