package net.paigu.chahua.data

import net.paigu.chahua.data.models.AuthTokenResponse
import net.paigu.chahua.data.models.CreateStickerPackBody
import net.paigu.chahua.data.models.CreateMessageBody
import net.paigu.chahua.data.models.ListChatsResponse
import net.paigu.chahua.data.models.ListGroupsResponse
import net.paigu.chahua.data.models.ListMessagesResponse
import net.paigu.chahua.data.models.ListThreadsResponse
import net.paigu.chahua.data.models.MarkReadResponse
import net.paigu.chahua.data.models.MeResponse
import net.paigu.chahua.data.models.MessageDto
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
        threadId: String? = null,
        max: Int = 100,
    ): ListMessagesResponse = client.get(
        "chats/$chatId/messages",
        buildMap {
            put("max", max.toString())
            before?.let { put("before", it) }
            after?.let { put("after", it) }
            threadId?.let { put("threadId", it) }
        },
    )

    suspend fun sendMessage(chatId: String, body: CreateMessageBody): MessageDto =
        client.post("chats/$chatId/messages", body = body)

    suspend fun sendThreadReply(chatId: String, threadRootId: String, body: CreateMessageBody): MessageDto =
        client.post("chats/$chatId/threads/$threadRootId/messages", body = body)

    suspend fun editMessage(chatId: String, messageId: String, newText: String): MessageDto =
        client.patch(
            "chats/$chatId/messages/$messageId",
            body = EditMessageBody(message = newText),
        )

    suspend fun deleteMessage(chatId: String, messageId: String) {
        client.noContent("DELETE", "chats/$chatId/messages/$messageId")
    }

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

    // ---- 群组 ----

    suspend fun groupSearch(q: String? = null, limit: Int = 50): ListGroupsResponse =
        client.get(
            "group",
            buildMap {
                q?.takeIf { it.isNotBlank() }?.let { put("q", it) }
                put("limit", limit.toString())
            },
        )

    // ---- 贴纸 / 表情包 ----

    suspend fun ownedStickerPacks(): StickerPackListResponse =
        client.get("stickers/packs/mine/owned")

    /** 当前用户收藏（订阅）的贴纸包。 */
    suspend fun subscribedStickerPacks(): StickerPackListResponse =
        client.get("stickers/packs/mine/subscribed")

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
