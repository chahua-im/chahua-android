package net.paigu.chahua.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.paigu.chahua.R
import net.paigu.chahua.ui.chat.ChatActivity
import net.paigu.chahua.ui.main.MainActivity

/**
 * 会话通知的统一入口与 id 规则。
 *
 * 前台 WebSocket Service 与 FCM 后台推送共用这一份实现，保证：
 * - 同一个群聊 / 话题只保留一条通知（重复消息覆盖而不是堆叠）；
 * - 两条通道使用相同的通知渠道与点击跳转目标；
 * - 进程被系统回收后由 FCM 拉起时也能展示同样样式。
 */
object ChatNotifications {

    const val CHANNEL_FOREGROUND = "chat_messaging"
    const val CHANNEL_CHATS = "chat_messages"
    const val CHANNEL_THREADS = "thread_messages"
    const val FOREGROUND_NOTIFICATION_ID = 1001

    private const val CHAT_NOTIFICATION_BASE = 2000
    private const val THREAD_NOTIFICATION_BASE = 5000

    /** 会话通知 id；标记已读时用它取消对应通知。 */
    fun chatNotificationId(chatId: String): Int =
        CHAT_NOTIFICATION_BASE + chatId.hashCode().mod(1000)

    /** 话题通知 id；标记已读时用它取消对应通知。 */
    fun threadNotificationId(chatId: String, threadRootId: String): Int =
        THREAD_NOTIFICATION_BASE + "$chatId:$threadRootId".hashCode().mod(1000)

    fun notificationId(chatId: String, threadRootId: String?): Int =
        if (threadRootId.isNullOrBlank()) {
            chatNotificationId(chatId)
        } else {
            threadNotificationId(chatId, threadRootId)
        }

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOREGROUND,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHATS,
                context.getString(R.string.notification_channel_chats_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_chats_desc)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_THREADS,
                context.getString(R.string.notification_channel_threads_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_threads_desc)
            },
        )
    }

    /** 按群聊 / 话题分别推送：同一群聊或话题只保留一条通知。 */
    fun notifyMessage(
        context: Context,
        chatId: String,
        threadRootId: String?,
        title: String,
        content: String,
    ) {
        val threadMode = !threadRootId.isNullOrBlank()
        val notificationId = notificationId(chatId, threadRootId)
        val notification = NotificationCompat.Builder(
            context,
            if (threadMode) CHANNEL_THREADS else CHANNEL_CHATS,
        )
            .setSmallIcon(R.drawable.ic_notification_mono)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentIntent(context, chatId, threadRootId, title, notificationId))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /** 前台 Service 的常驻通知。 */
    fun foregroundNotification(context: Context, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.ic_notification_mono)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun contentIntent(
        context: Context,
        chatId: String,
        threadRootId: String?,
        title: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = if (threadRootId.isNullOrBlank()) {
            ChatActivity.createIntent(context, chatId, title)
        } else {
            ChatActivity.createThreadIntent(context, chatId, title, threadRootId, 0L)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
