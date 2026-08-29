package net.paigu.chahua.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.ui.chat.ChatActivity
import net.paigu.chahua.ui.main.MainActivity

/**
 * 后台消息收发服务（前台 Service）：
 * 持有 WebSocket 连接，应用退到后台后仍保持实时接收；有新消息时展示通知。
 * “常驻通知”开关只控制通知栏常驻通知的显示/隐藏，不影响服务运行与消息推送。
 */
class ChatMessagingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ACTION_SHOW_PERSISTENT_NOTIFICATION =
            "net.paigu.chahua.action.SHOW_PERSISTENT_NOTIFICATION"
        const val ACTION_HIDE_PERSISTENT_NOTIFICATION =
            "net.paigu.chahua.action.HIDE_PERSISTENT_NOTIFICATION"

        private const val CHANNEL_FOREGROUND = "chat_messaging"
        private const val CHANNEL_CHATS = "chat_messages"
        private const val CHANNEL_THREADS = "thread_messages"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val CHAT_NOTIFICATION_BASE = 2000
        private const val THREAD_NOTIFICATION_BASE = 5000
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        AppGraph.engine.start()
        startForegroundCompat(getString(R.string.service_running))
        applyPersistentNotificationPreference()
        scope.launch {
            AppGraph.store.incoming.collect { msg ->
                if (!AppGraph.engine.appActive && AppGraph.settings.snapshot().notificationsEnabled) {
                    notifyNewMessage(msg)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_PERSISTENT_NOTIFICATION -> startForegroundCompat(
                getString(R.string.service_running),
            )
            ACTION_HIDE_PERSISTENT_NOTIFICATION -> hideForegroundNotification()
            else -> applyPersistentNotificationPreference()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppGraph.engine.stop()
        super.onDestroy()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOREGROUND,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHATS,
                getString(R.string.notification_channel_chats_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.notification_channel_chats_desc)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_THREADS,
                getString(R.string.notification_channel_threads_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.notification_channel_threads_desc)
            },
        )
    }

    /** 按“常驻通知”开关决定通知栏常驻通知的显示/隐藏。 */
    private fun applyPersistentNotificationPreference() {
        if (AppGraph.settings.snapshot().persistentNotificationEnabled) {
            startForegroundCompat(getString(R.string.service_running))
        } else {
            hideForegroundNotification()
        }
    }

    /** 移除前台通知但保持服务与 WebSocket 继续运行。 */
    private fun hideForegroundNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /** 按群聊/话题分别推送：同一群聊或话题只保留一条通知，内容为 "user：message"。 */
    private fun notifyNewMessage(msg: MessageDto) {
        val threadMode = !msg.replyRootId.isNullOrBlank()
        val title = resolveTitle(msg)
        val sender = msg.sender.name?.takeIf { it.isNotBlank() }
            ?: getString(R.string.message_sender_unknown)
        val content = getString(R.string.message_sender_format, sender, previewText(msg))
        val notificationId = if (threadMode) {
            THREAD_NOTIFICATION_BASE + "$msg.chatId:${msg.replyRootId}".hashCode().mod(1000)
        } else {
            CHAT_NOTIFICATION_BASE + msg.chatId.hashCode().mod(1000)
        }
        val notification = NotificationCompat.Builder(
            this,
            if (threadMode) CHANNEL_THREADS else CHANNEL_CHATS,
        )
            .setSmallIcon(R.drawable.ic_notification_mono)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentIntent(msg, title, notificationId))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(this).notify(notificationId, notification)
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

    private fun contentIntent(msg: MessageDto, title: String, requestCode: Int): PendingIntent {
        val intent = if (msg.replyRootId.isNullOrBlank()) {
            ChatActivity.createIntent(this, msg.chatId, title)
        } else {
            ChatActivity.createThreadIntent(this, msg.chatId, title, msg.replyRootId, 0L)
        }
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startForegroundCompat(text: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.ic_notification_mono)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }
}
