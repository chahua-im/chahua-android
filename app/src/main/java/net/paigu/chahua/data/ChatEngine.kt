package net.paigu.chahua.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import net.paigu.chahua.data.models.BulkDeletedDto
import net.paigu.chahua.data.models.ChatArchiveStateDto
import net.paigu.chahua.data.models.CreateMessageBody
import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.ReactionUpdateDto
import net.paigu.chahua.data.models.ThreadUpdateDto
import net.paigu.chahua.data.models.WsEnvelope
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import kotlin.math.min
import kotlin.random.Random

/**
 * WebSocket 实时引擎（运行在后台 Service 内）：
 * - 连接 /ws 并通过首条 auth 消息认证；
 * - 每 30s 心跳，指数退避断线重连（1s 起、上限 30s + 抖动）；
 * - 将服务端事件写入 [ChatStore]，供各页面实时刷新。
 */
class ChatEngine(
    private val apiClient: ApiClient,
    private val api: ChatApi,
    private val store: ChatStore,
    private val latencyEnabled: () -> Boolean = { false },
) {
    private val json = ApiJson.instance
    private val client: OkHttpClient = apiClient.okHttpClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var runJob: Job? = null
    private var pingJob: Job? = null
    private var webSocket: WebSocket? = null
    private var closedSignal = CompletableDeferred<Unit>()

    @Volatile
    private var stopped = true

    /** 应用是否在前台（影响心跳里上报的 state）。 */
    @Volatile
    var appActive: Boolean = true

    /** 连接建立（含重连成功）后触发的全量同步回调。 */
    @Volatile
    var onConnected: (() -> Unit)? = null

    private val _latencyMs = MutableStateFlow<Long?>(null)

    /** 最近一次 ping/pong 往返延迟（毫秒）；尚未测得或未连接时为 null。 */
    val latencyMs: StateFlow<Long?> = _latencyMs.asStateFlow()

    @Volatile
    private var lastPingSentAt: Long? = null

    fun start() {
        if (!stopped) return
        stopped = false
        _latencyMs.value = null
        runJob = scope.launch { connectionLoop() }
    }

    fun stop() {
        stopped = true
        runJob?.cancel()
        pingJob?.cancel()
        webSocket?.close(1000, "stop")
        webSocket = null
        lastPingSentAt = null
        _latencyMs.value = null
        store.setConnectionState(WsStatus.DISCONNECTED)
    }

    fun reconnectNow() {
        if (stopped) return
        runJob?.cancel()
        pingJob?.cancel()
        webSocket?.close(1000, "reconnect")
        webSocket = null
        lastPingSentAt = null
        _latencyMs.value = null
        store.setConnectionState(WsStatus.DISCONNECTED)
        runJob = scope.launch { connectionLoop() }
    }

    fun setAppState(state: String) {
        if (state == "active" || state == "inactive") {
            appActive = state == "active"
        }
        val socket = webSocket
        if (socket != null && !stopped) {
            socket.send(json.encodeToString(WsClientMessage(type = "appState", state = state)))
        }
    }

    /** 发送文本消息（话题内回复时自动走话题回复端点）。 */
    suspend fun sendMessage(
        chatId: String,
        text: String?,
        replyToId: String? = null,
        replyRootId: String? = null,
        attachmentIds: List<String> = emptyList(),
        messageType: String = "text",
        stickerId: String? = null,
        clientGeneratedId: String? = null,
    ): Result<MessageDto> {
        val cgid = clientGeneratedId ?: "android-${UUID.randomUUID()}"
        val body = CreateMessageBody(
            message = text,
            messageType = messageType,
            stickerId = stickerId,
            clientGeneratedId = cgid,
            replyToId = replyToId,
            replyRootId = replyRootId,
            attachmentIds = attachmentIds,
        )
        return runCatching {
            val msg = if (replyRootId.isNullOrBlank()) {
                api.sendMessage(chatId, body)
            } else {
                api.sendThreadReply(chatId, replyRootId, body)
            }
            store.onNewMessage(msg)
            msg
        }
    }

    private suspend fun connectionLoop() {
        var attempt = 0
        while (scope.isActive && !stopped) {
            store.setConnectionState(WsStatus.CONNECTING)
            val ticket = try {
                api.wsTicket()
            } catch (e: ApiException) {
                if (e.statusCode == 401) {
                    store.setError("认证失败，请重新登录")
                    stopped = true
                    break
                }
                AppLog.w("ChatEngine", "connect failed: ${e.message}")
                store.setError("连接失败: ${e.message}")
                delay(backoff(attempt++))
                continue
            } catch (e: Exception) {
                AppLog.w("ChatEngine", "connect failed: ${e.message}")
                store.setError("连接失败: ${e.message}")
                delay(backoff(attempt++))
                continue
            }

            val connected = openSocket(ticket)
            if (connected) {
                attempt = 0
                store.setConnectionState(WsStatus.CONNECTED)
                store.setError(null)
                AppLog.d("ChatEngine", "WebSocket connected")
                onConnected?.invoke()
                pingJob = scope.launch { pingLoop() }
                closedSignal.await()
                pingJob?.cancel()
                lastPingSentAt = null
                _latencyMs.value = null
                store.setConnectionState(WsStatus.DISCONNECTED)
            } else {
                lastPingSentAt = null
                _latencyMs.value = null
                store.setConnectionState(WsStatus.DISCONNECTED)
            }
            if (stopped) break
            delay(backoff(attempt++))
        }
    }

    /**
     * 打开 WebSocket 并发送 auth。
     * 服务端认证失败时会在 5 秒内直接关闭连接；宽限期内未关闭即视为认证成功。
     */
    private suspend fun openSocket(ticket: String): Boolean {
        val closedEarly = CompletableDeferred<Boolean>()
        val closed = CompletableDeferred<Unit>()
        val request = Request.Builder().url(wsUrl()).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(json.encodeToString(WsClientMessage(type = "auth", ticket = ticket)))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closedEarly.isCompleted) closedEarly.complete(true)
                if (!closed.isCompleted) closed.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                store.setError("实时连接断开: ${t.message}")
                if (!closedEarly.isCompleted) closedEarly.complete(true)
                if (!closed.isCompleted) closed.complete(Unit)
            }
        }

        val socket = client.newWebSocket(request, listener)
        webSocket = socket
        closedSignal = closed

        val closedDuringGrace = withTimeoutOrNull(5_000) { closedEarly.await() } ?: false
        return !closedDuringGrace
    }

    private suspend fun pingLoop() {
        while (scope.isActive && !stopped) {
            delay(if (latencyEnabled()) 5_000 else 30_000)
            val socket = webSocket
            if (socket != null) {
                lastPingSentAt = System.nanoTime()
                socket.send(
                    json.encodeToString(
                        WsClientMessage(
                            type = "ping",
                            state = if (appActive) "active" else "inactive",
                        ),
                    ),
                )
            }
        }
    }

    private fun handleFrame(text: String) {
        val envelope = try {
            json.decodeFromString(WsEnvelope.serializer(), text)
        } catch (e: Exception) {
            return
        }
        val payload: JsonElement? = envelope.payload
        when (envelope.type) {
            "message" -> decodePayload<MessageDto>(payload)?.let { store.onNewMessage(it) }
            "messageUpdated" -> decodePayload<MessageDto>(payload)?.let { store.onMessageUpdated(it) }
            "messageDeleted" -> decodePayload<MessageDto>(payload)?.let {
                store.onMessageDeleted(it.chatId, it.replyRootId, it.id)
            }
            "messagesBulkDeleted" -> decodePayload<BulkDeletedDto>(payload)?.let {
                store.removeMessages(it.chatId, null, it.messageIds)
            }
            "reactionUpdated" -> decodePayload<ReactionUpdateDto>(payload)?.let {
                store.onReactionUpdate(it.chatId, it.messageId, it.reactions)
            }
            "threadUpdate" -> decodePayload<ThreadUpdateDto>(payload)?.let { store.onThreadUpdate(it) }
            "chatArchiveStateChanged" -> decodePayload<ChatArchiveStateDto>(payload)?.let {
                store.onChatArchiveState(it.chatId, it.archived)
            }
            "pong" -> {
                val sentAt = lastPingSentAt
                if (sentAt != null) {
                    _latencyMs.value = (System.nanoTime() - sentAt) / 1_000_000
                    lastPingSentAt = null
                }
            }
            "presenceUpdate", "pinAdded", "pinRemoved", "stickerPackOrderUpdated",
            "threadMembershipChanged" -> Unit
        }
    }

    private inline fun <reified T> decodePayload(payload: JsonElement?): T? {
        if (payload == null) return null
        return try {
            json.decodeFromJsonElement(serializer(), payload)
        } catch (e: Exception) {
            null
        }
    }

    private fun wsUrl(): String {
        val base = apiClient.baseUrl()
        return when {
            base.startsWith("https://") -> "wss://${base.removePrefix("https://")}/ws"
            base.startsWith("http://") -> "ws://${base.removePrefix("http://")}/ws"
            else -> "ws://$base/ws"
        }
    }

    private fun backoff(attempt: Int): Long {
        val base = if (attempt <= 0) 0L else min(30_000L, 1_000L * (1L shl min(attempt, 5)))
        return base + Random.nextLong(0, 1_000)
    }
}

/** WebSocket 客户端 → 服务端消息。 */
@Serializable
data class WsClientMessage(
    val type: String,
    val ticket: String? = null,
    val state: String? = null,
)
