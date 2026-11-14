package net.paigu.chahua.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.models.MessageDto

/**
 * 后台消息收发服务（前台 Service）：
 * 持有 WebSocket 连接，应用退到后台后仍保持实时接收；有新消息时展示通知。
 * “常驻通知”开关只控制通知栏常驻通知的显示/隐藏，不影响服务运行与消息推送。
 *
 * 通知样式与 id 规则见 [ChatNotifications]，与 FCM 后台推送共用。
 */
class ChatMessagingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /** 会话通知 id；与 [ChatNotifications.notifyMessage] 保持一致，便于标记已读后取消。 */
        fun chatNotificationId(chatId: String): Int = ChatNotifications.chatNotificationId(chatId)

        /** 话题通知 id；与 [ChatNotifications.notifyMessage] 保持一致。 */
        fun threadNotificationId(chatId: String, threadRootId: String): Int =
            ChatNotifications.threadNotificationId(chatId, threadRootId)
    }

    override fun onCreate() {
        super.onCreate()
        ChatNotifications.ensureChannels(this)
        AppGraph.engine.start()
        // 先满足前台服务启动要求，再按开关决定是否移除常驻通知。
        applyPersistentNotificationPreference()
        scope.launch {
            AppGraph.store.incoming.collect { msg ->
                val settings = AppGraph.settings.snapshot()
                if (!AppGraph.engine.appActive &&
                    settings.notificationsEnabled &&
                    settings.pushMethodOption.showsLocalNotifications
                ) {
                    notifyNewMessage(msg)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 任何经 startForegroundService 到达的命令都必须先调用 startForeground，
        // 否则超时会被系统判为 ForegroundServiceDidNotStartInTimeException 并杀进程。
        applyPersistentNotificationPreference()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppGraph.engine.stop()
        super.onDestroy()
    }

    /** 先启动前台（满足系统要求），再按“常驻通知”开关决定是否隐藏常驻通知。 */
    private fun applyPersistentNotificationPreference() {
        startForegroundCompat(getString(R.string.service_running))
        if (!AppGraph.settings.snapshot().persistentNotificationEnabled) {
            hideForegroundNotification()
        }
    }

    /** 移除前台通知但保持服务与 WebSocket 继续运行。 */
    private fun hideForegroundNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /** 按群聊/话题分别推送：同一群聊或话题只保留一条通知，内容为 "user：message"。 */
    private fun notifyNewMessage(msg: MessageDto) {
        val sender = msg.sender.name?.takeIf { it.isNotBlank() }
            ?: getString(R.string.message_sender_unknown)
        ChatNotifications.notifyMessage(
            context = this,
            chatId = msg.chatId,
            threadRootId = msg.replyRootId,
            title = resolveTitle(msg),
            content = getString(R.string.message_sender_format, sender, previewText(msg)),
        )
    }

    private fun resolveTitle(msg: MessageDto): String {
        val thread = msg.replyRootId?.let { rootId ->
            AppGraph.store.threads.value.firstOrNull {
                it.chatId == msg.chatId && it.threadRootMessage?.id == rootId
            }
        }
        val chat = AppGraph.store.chats.value.firstOrNull { it.id == msg.chatId }
        return thread?.chatName ?: chat?.name ?: msg.chatId
    }

    private fun previewText(msg: MessageDto): String {
        if (!msg.message.isNullOrBlank()) return msg.message
        if (!msg.sticker?.emoji.isNullOrBlank()) return msg.sticker!!.emoji
        return when (msg.messageType) {
            "image" -> getString(R.string.chat_image)
            "video" -> getString(R.string.chat_video)
            "audio" -> getString(R.string.chat_audio)
            "file" -> getString(R.string.chat_file)
            "sticker" -> getString(R.string.chat_sticker)
            else -> getString(R.string.chat_message)
        }
    }

    private fun startForegroundCompat(text: String) {
        val notification: Notification = ChatNotifications.foregroundNotification(this, text)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(
                ChatNotifications.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(ChatNotifications.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }
}
