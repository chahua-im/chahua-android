package net.paigu.chahua.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.paigu.chahua.R
import net.paigu.chahua.data.models.MeResponse
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chahua_session")

/**
 * 登录会话管理：服务器地址、登录 key（JWT 或开发模式 UID）、客户端 ID、当前用户信息。
 *
 * 认证策略与后端一致：
 * - 输入为 JWT 时使用 `Authorization: Bearer <jwt>`；
 * - 否则视为开发模式 UID，使用 `X-User-Id` + `X-Client-Id` 头（AUTH_METHOD=UIDHeader）。
 */
class SessionManager(context: Context) {

    companion object {
        /** 默认服务端（生产环境，nginx 反代去 /_api 前缀后转发到后端）。 */
        const val DEFAULT_SERVER_URL = "https://chahui.app/_api"

        fun looksLikeJwt(input: String): Boolean {
            val s = input.trim()
            return s.count { it == '.' } == 2 && s.split('.').all { it.isNotBlank() }
        }
    }

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val SERVER_URLS = stringPreferencesKey("server_urls")
        val AUTH_KEY = stringPreferencesKey("auth_key")
        val AUTH_MODE = stringPreferencesKey("auth_mode") // "jwt" | "uid"
        val CLIENT_ID = stringPreferencesKey("client_id")
        val ME_JSON = stringPreferencesKey("me_json")
    }

    data class SessionState(
        val serverUrl: String = DEFAULT_SERVER_URL,
        val serverUrls: List<String> = emptyList(),
        val authKey: String? = null,
        val isJwt: Boolean = false,
        val clientId: String? = null,
        val me: MeResponse? = null,
    ) {
        val hasSession: Boolean get() = !authKey.isNullOrBlank()
    }

    private val json = ApiJson.instance
    private val appContext = context.applicationContext
    private val prefs = appContext.dataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 客户端 ID（UUID），构造时同步创建，保证首个请求即可携带。 */
    val clientId: String = runBlocking {
        prefs.data.first()[Keys.CLIENT_ID] ?: UUID.randomUUID().toString().also {
            prefs.edit { p -> p[Keys.CLIENT_ID] = it }
        }
    }

    /** 当前会话状态流。 */
    val sessionState: Flow<SessionState> = prefs.data.map { p ->
        val meJson = p[Keys.ME_JSON]
        val serverUrl = p[Keys.SERVER_URL] ?: DEFAULT_SERVER_URL
        val storedUrls = p[Keys.SERVER_URLS]
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
        SessionState(
            serverUrl = serverUrl,
            serverUrls = if (serverUrl in storedUrls) storedUrls else storedUrls + serverUrl,
            authKey = p[Keys.AUTH_KEY],
            isJwt = p[Keys.AUTH_MODE] == "jwt",
            clientId = p[Keys.CLIENT_ID],
            me = if (meJson.isNullOrBlank()) null else runCatching {
                json.decodeFromString(MeResponse.serializer(), meJson)
            }.getOrNull(),
        )
    }

    /** 供非挂起场景（如请求拦截器）读取的最近一次会话快照。 */
    @Volatile
    private var snapshot: SessionState = SessionState()

    init {
        scope.launch {
            sessionState.collect { snapshot = it }
        }
    }

    suspend fun current(): SessionState = sessionState.first()

    fun snapshot(): SessionState = snapshot

    /** 获取（或创建）客户端 ID。 */
    suspend fun clientId(): String {
        val existing = prefs.data.first()[Keys.CLIENT_ID]
        if (!existing.isNullOrBlank()) return existing
        val id = UUID.randomUUID().toString()
        prefs.edit { p -> p[Keys.CLIENT_ID] = id }
        return id
    }

    /** 保存用户输入的 key（自动判断 JWT / UID）。 */
    suspend fun saveKey(input: String) {
        val key = input.trim()
        val mode = if (looksLikeJwt(key)) "jwt" else "uid"
        prefs.edit { p ->
            p[Keys.AUTH_KEY] = key
            p[Keys.AUTH_MODE] = mode
        }
        snapshot = snapshot.copy(authKey = key, isJwt = mode == "jwt")
    }

    /** 保存升级得到的 JWT。 */
    suspend fun saveJwt(token: String) {
        prefs.edit { p ->
            p[Keys.AUTH_KEY] = token.trim()
            p[Keys.AUTH_MODE] = "jwt"
        }
        snapshot = snapshot.copy(authKey = token.trim(), isJwt = true)
    }

    suspend fun setMe(me: MeResponse) {
        prefs.edit { p ->
            p[Keys.ME_JSON] = json.encodeToString(MeResponse.serializer(), me)
        }
        snapshot = snapshot.copy(me = me)
    }

    suspend fun setServerUrl(url: String) {
        val normalized = normalizeServerUrl(url)
        val urls = readServerUrls().let { existing ->
            if (normalized in existing) existing else existing + normalized
        }
        prefs.edit { p ->
            p[Keys.SERVER_URL] = normalized
            p[Keys.SERVER_URLS] = urls.joinToString("\n")
        }
        snapshot = snapshot.copy(serverUrl = normalized, serverUrls = urls)
    }

    /** 新增一个服务器地址到列表（不切换当前服务器）。返回是否真正新增。 */
    suspend fun addServerUrl(url: String): Boolean {
        val normalized = normalizeServerUrl(url)
        val existing = readServerUrls()
        if (normalized in existing) return false
        val urls = existing + normalized
        prefs.edit { p -> p[Keys.SERVER_URLS] = urls.joinToString("\n") }
        snapshot = snapshot.copy(serverUrls = urls)
        return true
    }

    /** 从列表删除服务器地址；若删除的是当前地址，则切换到列表中第一个（或默认地址）。 */
    suspend fun removeServerUrl(url: String) {
        val normalized = normalizeServerUrl(url)
        val existing = readServerUrls().filterNot { it == normalized }
        val urls = if (existing.isEmpty()) listOf(DEFAULT_SERVER_URL) else existing
        val current = snapshot.serverUrl
        val next = if (current == normalized) urls.first() else current
        prefs.edit { p ->
            p[Keys.SERVER_URL] = next
            p[Keys.SERVER_URLS] = urls.joinToString("\n")
        }
        snapshot = snapshot.copy(serverUrl = next, serverUrls = urls)
    }

    suspend fun clear() {
        prefs.edit { p ->
            p.remove(Keys.AUTH_KEY)
            p.remove(Keys.AUTH_MODE)
            p.remove(Keys.ME_JSON)
        }
        snapshot = snapshot.copy(authKey = null, isJwt = false, me = null)
    }

    /** 生成当前会话的认证请求头。 */
    fun authHeaders(): Map<String, String> {
        val s = snapshot
        val headers = mutableMapOf(
            "Accept" to "application/json",
            "X-App-Version" to appContext.getString(R.string.x_app_version),
        )
        headers["X-Client-Id"] = clientId
        val key = s.authKey
        if (key.isNullOrBlank()) return headers
        if (s.isJwt) {
            headers["Authorization"] = "Bearer $key"
        } else {
            headers["X-User-Id"] = key
        }
        return headers
    }

    private suspend fun readServerUrls(): List<String> =
        prefs.data.first()[Keys.SERVER_URLS]
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()

    private fun normalizeServerUrl(url: String): String {
        var normalized = url.trim().trimEnd('/')
        if (normalized.isBlank()) return DEFAULT_SERVER_URL
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        return normalized
    }

}
