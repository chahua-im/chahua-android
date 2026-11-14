package net.paigu.chahua.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * FCM 设备令牌注册。
 *
 * Firebase 生成的令牌是“设备 + 应用”维度的，登录后上报给后端作为 Android 端推送订阅：
 * - App 进入主界面时调用 [registerIfPossible] 补齐订阅（后台已有则覆盖，幂等）；
 * - 令牌刷新时 [com.google.firebase.messaging.FirebaseMessagingService.onNewToken] 调用 [registerToken]；
 * - 未登录、设备无 Google Play 服务或后端未开启 FCM 时静默跳过，不影响聊天功能。
 */
class PushRegistration(
    private val context: Context,
    private val api: ChatApi,
    private val session: SessionManager,
) {

    companion object {
        private const val TAG = "PushRegistration"
    }

    private val mutex = Mutex()

    /** 已成功上报的令牌，避免每次进入前台都重复请求。 */
    @Volatile
    private var registeredToken: String? = null

    /** 登录态下把当前设备的 FCM 令牌同步到后端；失败静默，下次启动自动重试。 */
    suspend fun registerIfPossible(force: Boolean = false) {
        val token = currentToken() ?: return
        registerToken(token, force)
    }

    /** 上报指定令牌（来自 onNewToken 或 [registerIfPossible]）。 */
    suspend fun registerToken(token: String, force: Boolean = false) {
        val deviceToken = token.trim()
        if (deviceToken.isEmpty() || !session.snapshot().hasSession) return
        mutex.withLock {
            if (!force && deviceToken == registeredToken) return
            try {
                api.subscribeFcmPush(deviceToken)
                registeredToken = deviceToken
            } catch (e: Exception) {
                Log.w(TAG, "FCM subscribe failed: ${e.message}")
            }
        }
    }

    /** 退出登录时清理本设备的推送订阅，避免继续收到该账号的消息。 */
    suspend fun unregister() {
        val deviceToken = registeredToken ?: currentToken() ?: return
        mutex.withLock {
            try {
                api.unsubscribeFcmPush(deviceToken)
            } catch (e: Exception) {
                Log.w(TAG, "FCM unsubscribe failed: ${e.message}")
            } finally {
                registeredToken = null
            }
        }
    }

    private suspend fun currentToken(): String? {
        // 没有 google-services.json 或设备缺少 Google Play 服务时无法使用 FCM。
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return try {
            FirebaseMessaging.getInstance().token.awaitResult()
        } catch (e: Exception) {
            Log.w(TAG, "FCM token unavailable: ${e.message}")
            null
        }
    }

    /** [Task] 的挂起等待，避免为此额外引入 kotlinx-coroutines-play-services。 */
    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            val value = task.result
            if (task.isSuccessful && value != null) {
                cont.resume(value)
            } else {
                cont.resumeWithException(
                    task.exception ?: IllegalStateException("Firebase task returned no result"),
                )
            }
        }
    }
}
