package net.paigu.chahua.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException

/** FCM 设备令牌的获取状态。 */
enum class FcmTokenState { UNKNOWN, LOADING, READY, FAILED }

/** 开发者设置里展示的 FCM 状态快照。 */
data class PushDiagnostics(
    /** 应用内是否带有 Firebase 配置（google-services.json）并初始化成功。 */
    val fcmConfigured: Boolean = false,
    val tokenState: FcmTokenState = FcmTokenState.UNKNOWN,
    val token: String? = null,
    /** 服务端是否确认该令牌已订阅；null 表示未查询或查询失败。 */
    val backendSubscribed: Boolean? = null,
    val lastError: String? = null,
)

/**
 * FCM 设备令牌注册与状态上报。
 *
 * Firebase 生成的令牌是“设备 + 应用”维度的，登录后上报给后端作为 Android 端推送订阅：
 * - App 进入主界面时调用 [registerIfPossible] 补齐订阅（后台已有则覆盖，幂等）；
 * - 令牌刷新时 [com.google.firebase.messaging.FirebaseMessagingService.onRegistered]
 *   / `onNewToken` 调用 [registerToken]；
 * - 推送方式为「仅 WebSocket」时退订，其余情况保持订阅；
 * - 未登录、设备无 Google Play 服务或后端未开启 FCM 时静默跳过，不影响聊天功能。
 *
 * [diagnostics] 供开发者设置页展示当前 FCM 状态。
 */
class PushRegistration(
    private val context: Context,
    private val api: ChatApi,
    private val session: SessionManager,
    private val settings: SettingsManager,
) {

    private companion object {
        private const val TAG = "PushRegistration"
    }

    private val mutex = Mutex()

    private val _diagnostics = MutableStateFlow(PushDiagnostics())
    val diagnostics: StateFlow<PushDiagnostics> = _diagnostics.asStateFlow()

    /** 已成功上报的令牌，避免每次进入前台都重复请求。 */
    @Volatile
    private var registeredToken: String? = null

    private val pushMethod: PushMethodOption
        get() = PushMethodOption.from(settings.snapshot().pushMethod)

    /** 登录态下把当前设备的 FCM 令牌同步到后端；失败静默，下次启动自动重试。 */
    suspend fun registerIfPossible(force: Boolean = false) {
        if (!pushMethod.usesFcm) return
        val token = fetchToken() ?: return
        registerToken(token, force)
    }

    /** 上报指定令牌（来自 onNewToken / onRegistered 或 [registerIfPossible]）。 */
    suspend fun registerToken(token: String, force: Boolean = false) {
        val deviceToken = token.trim()
        if (deviceToken.isEmpty() || !session.snapshot().hasSession) return
        if (!pushMethod.usesFcm) return
        mutex.withLock {
            if (!force && deviceToken == registeredToken) return
            try {
                api.subscribeFcmPush(deviceToken)
                registeredToken = deviceToken
                _diagnostics.update { it.copy(token = deviceToken, lastError = null) }
            } catch (e: Exception) {
                Log.w(TAG, "FCM subscribe failed: ${e.message}")
                _diagnostics.update { it.copy(lastError = e.message) }
            }
        }
    }

    /** 退出登录或切换到「仅 WebSocket」时清理本设备的推送订阅。 */
    suspend fun unregister() {
        // 只用已经知道的令牌：设备无法访问 FCM 时 fetchToken() 会等超时，而那种情况
        // 本来也没有成功的订阅需要清理。
        val deviceToken = registeredToken ?: _diagnostics.value.token ?: return
        mutex.withLock {
            try {
                api.unsubscribeFcmPush(deviceToken)
                _diagnostics.update { it.copy(backendSubscribed = false) }
            } catch (e: Exception) {
                Log.w(TAG, "FCM unsubscribe failed: ${e.message}")
                _diagnostics.update { it.copy(lastError = e.message) }
            } finally {
                registeredToken = null
            }
        }
    }

    /** 推送方式切换后重新对齐服务端订阅，并刷新展示状态。 */
    suspend fun applyMethod() {
        if (pushMethod.usesFcm) {
            registerIfPossible(force = true)
        } else {
            unregister()
        }
        refresh()
    }

    /**
     * 刷新开发者设置里展示的 FCM 状态。
     *
     * [forceTokenFetch] 为 false 时复用已缓存的令牌，避免每次打开页面都触发一次
     * FCM 注册（在无法访问 Google 的设备上要等超时）。
     */
    suspend fun refresh(forceTokenFetch: Boolean = false) {
        val configured = FirebaseApp.getApps(context).isNotEmpty()
        _diagnostics.update { it.copy(fcmConfigured = configured) }
        if (!configured) {
            _diagnostics.update {
                it.copy(
                    tokenState = FcmTokenState.UNKNOWN,
                    token = null,
                    backendSubscribed = null,
                )
            }
            return
        }
        // 「仅 WebSocket」且还没有令牌时不主动获取：该模式不订阅 FCM，在无法访问
        // Google 的设备上只会白等一次超时。点「刷新状态」仍会强制获取。
        if (!forceTokenFetch && !pushMethod.usesFcm && _diagnostics.value.token == null) {
            _diagnostics.update {
                it.copy(
                    tokenState = FcmTokenState.UNKNOWN,
                    token = null,
                    backendSubscribed = null,
                )
            }
            return
        }
        val token = if (forceTokenFetch) fetchToken() else (_diagnostics.value.token ?: fetchToken())
        if (token == null) {
            _diagnostics.update { it.copy(backendSubscribed = null) }
            return
        }
        val subscribed = queryBackendStatus(token)
        _diagnostics.update { it.copy(backendSubscribed = subscribed) }
    }

    private suspend fun queryBackendStatus(token: String): Boolean? {
        if (!session.snapshot().hasSession) return null
        return try {
            val status = api.fcmPushSubscriptionStatus(token)
            status.hasActiveSubscription || status.hasMatchingSubscription == true
        } catch (e: Exception) {
            Log.w(TAG, "FCM subscription status query failed: ${e.message}")
            _diagnostics.update { it.copy(lastError = e.message) }
            null
        }
    }

    // getToken() 在新版 Firebase 里被标记为 deprecated（推荐 register() + onRegistered
    // 回调），但 register() 不会在令牌未变化时回调，无法覆盖“启动时补齐/转移订阅”的场景；
    // 令牌刷新由 FcmMessagingService.onRegistered/onNewToken 负责，这里仍需要主动取一次。
    @Suppress("DEPRECATION")
    private suspend fun fetchToken(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) {
            _diagnostics.update { it.copy(fcmConfigured = false) }
            return null
        }
        _diagnostics.update { it.copy(tokenState = FcmTokenState.LOADING) }
        return try {
            val token = FirebaseMessaging.getInstance().token.awaitResult()
            _diagnostics.update {
                it.copy(tokenState = FcmTokenState.READY, token = token, lastError = null)
            }
            token
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FCM token unavailable: ${e.message}")
            _diagnostics.update {
                it.copy(
                    tokenState = FcmTokenState.FAILED,
                    token = null,
                    lastError = e.message ?: e.javaClass.simpleName,
                )
            }
            null
        }
    }

    /**
     * [Task] 的挂起等待，避免为此额外引入 kotlinx-coroutines-play-services。
     *
     * 注意：失败的任务调用 `Task.getResult()` 会直接抛 RuntimeException，而回调里
     * 逃逸的异常会让进程崩溃（FCM 注册失败时就是这种情况），所以必须先判断
     * [Task.isSuccessful] 再取结果。
     */
    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                cont.resumeWithException(
                    task.exception ?: IllegalStateException("Firebase task failed"),
                )
                return@addOnCompleteListener
            }
            val value = task.result
            if (value != null) {
                cont.resume(value)
            } else {
                cont.resumeWithException(
                    IllegalStateException("Firebase task returned no result"),
                )
            }
        }
    }
}
