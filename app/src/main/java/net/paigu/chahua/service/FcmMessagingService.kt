package net.paigu.chahua.service

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.ApiJson

/**
 * FCM 后台推送接收端（进程被系统回收、前台 WebSocket 不在线时的兜底通道）。
 *
 * 后端只发送 data-only 消息，通知由 App 自己渲染：
 * - 复用 [ChatNotifications] 的渠道 / 分组 / 点击跳转，与前台 WebSocket 通知完全一致；
 * - 通知文案在客户端本地化（后端只提供结构化预览）。
 */
class FcmMessagingService : FirebaseMessagingService() {

    /**
     * Firebase 26 新增的注册回调；旧版 SDK 只会回调 [onNewToken]，两个都实现以覆盖不同版本。
     */
    override fun onRegistered(token: String) {
        registerToken(token)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        registerToken(token)
    }

    private fun registerToken(token: String) {
        AppGraph.scope.launch {
            runCatching { AppGraph.pushRegistration.registerToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = FcmPayload.parse(message.data) ?: return
        // 已登出，或 App 在前台时由 WebSocket 直接展示，无须重复通知。
        if (!AppGraph.session.snapshot().hasSession) return
        if (AppGraph.engine.appActive) return
        if (!AppGraph.settings.snapshot().notificationsEnabled) return

        ChatNotifications.ensureChannels(this)
        val sender = payload.senderName.takeIf { it.isNotBlank() }
            ?: getString(R.string.message_sender_unknown)
        ChatNotifications.notifyMessage(
            context = this,
            chatId = payload.chatId,
            threadRootId = payload.threadRootId,
            title = payload.chatName.takeIf { it.isNotBlank() } ?: getString(R.string.app_name),
            content = getString(R.string.message_sender_format, sender, payload.previewText(this)),
        )
    }
}

/** 后端 `push` 服务的 FCM data 字段（全部为字符串，预览为嵌套 JSON 文本）。 */
private data class FcmPayload(
    val chatId: String,
    val threadRootId: String?,
    val senderName: String,
    val chatName: String,
    val body: String,
    val preview: FcmMessagePreview?,
) {
    companion object {
        fun parse(data: Map<String, String>): FcmPayload? {
            val chatId = data["chatId"]?.takeIf { it.isNotBlank() } ?: return null
            val preview = data["messagePreview"]?.let { raw ->
                runCatching {
                    ApiJson.instance.decodeFromString(FcmMessagePreview.serializer(), raw)
                }.getOrNull()
            }
            return FcmPayload(
                chatId = chatId,
                threadRootId = data["threadRootId"]?.takeIf { it.isNotBlank() },
                senderName = data["senderName"].orEmpty(),
                chatName = data["chatName"].orEmpty(),
                body = data["body"].orEmpty(),
                preview = preview,
            )
        }
    }

    /** 本地化的通知正文：与前台 WebSocket 通知的 [ChatMessagingService] 文案保持一致。 */
    fun previewText(context: Context): String {
        val preview = preview
        if (preview != null) {
            if (preview.isDeleted) return context.getString(R.string.chat_message)
            preview.message?.takeIf { it.isNotBlank() }?.let { return it }
            preview.sticker?.emoji?.takeIf { it.isNotBlank() }?.let { return it }
            val kind = preview.attachments.firstOrNull()?.kind
            return when {
                preview.messageType == "image" || kind?.startsWith("image/") == true ->
                    context.getString(R.string.chat_image)
                preview.messageType == "video" || kind?.startsWith("video/") == true ->
                    context.getString(R.string.chat_video)
                preview.messageType == "audio" || kind?.startsWith("audio/") == true ->
                    context.getString(R.string.chat_audio)
                preview.messageType == "sticker" ->
                    context.getString(R.string.chat_sticker)
                preview.messageType == "file" || kind != null ->
                    context.getString(R.string.chat_file)
                else -> body.ifBlank { context.getString(R.string.chat_message) }
            }
        }
        return body.ifBlank { context.getString(R.string.chat_message) }
    }
}

@Serializable
private data class FcmMessagePreview(
    val message: String? = null,
    val messageType: String = "text",
    val sticker: FcmSticker? = null,
    val attachments: List<FcmAttachment> = emptyList(),
    val isDeleted: Boolean = false,
)

@Serializable
private data class FcmSticker(
    val emoji: String = "",
)

@Serializable
private data class FcmAttachment(
    val kind: String = "",
)
