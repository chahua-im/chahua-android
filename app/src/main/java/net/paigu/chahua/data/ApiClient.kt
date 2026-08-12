package net.paigu.chahua.data

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 全局 JSON 配置：后端字段为 camelCase、可能多出/缺少字段，均做容错处理。 */
object ApiJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }
}

class ApiException(
    val statusCode: Int,
    override val message: String,
) : Exception("HTTP $statusCode: $message")

/**
 * 轻量 HTTP 客户端封装：统一附加认证头、JSON 编解码、错误解析。
 * 错误响应为纯文本消息（非 JSON），按 API 文档直接透出。
 */
class ApiClient(private val session: SessionManager) {

    companion object {
        private const val LOGIN_URL =
            "https://www.shireyishunjian.com/main/shireyishunjian-telegram-api/chahua_login.php"
    }

    private val json = ApiJson.instance
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(ErrorLoggingInterceptor())
        .build()

    fun baseUrl(): String = session.snapshot().serverUrl.trimEnd('/')

    /** 账号密码登录：接口直接返回 JWT 纯文本。 */
    suspend fun loginWithCredentials(username: String, password: String): String {
        val formBody = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build()
        val request = Request.Builder()
            .url(LOGIN_URL)
            .header("Accept", "text/plain")
            .post(formBody)
            .build()
        val response = withContextIO { okHttpClient.newCall(request).execute() }
        return response.use { r ->
            val text = r.body?.string().orEmpty().trim()
            if (!r.isSuccessful || text.isEmpty()) {
                throw ApiException(r.code, text.ifBlank { r.message })
            }
            text
        }
    }

    suspend inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap()): T =
        execute("GET", path, query, responseSerializer = serializer())

    suspend inline fun <reified T, reified B> post(
        path: String,
        query: Map<String, String> = emptyMap(),
        body: B,
    ): T = execute("POST", path, query, body = body, responseSerializer = serializer())

    suspend inline fun <reified T, reified B> put(
        path: String,
        query: Map<String, String> = emptyMap(),
        body: B,
    ): T = execute("PUT", path, query, body = body, responseSerializer = serializer())

    suspend inline fun <reified T> putNoBody(path: String, query: Map<String, String> = emptyMap()): T =
        execute("PUT", path, query, responseSerializer = serializer())

    suspend inline fun <reified T, reified B> patch(
        path: String,
        query: Map<String, String> = emptyMap(),
        body: B,
    ): T = execute("PATCH", path, query, body = body, responseSerializer = serializer())

    suspend inline fun <reified T> delete(path: String, query: Map<String, String> = emptyMap()): T =
        execute("DELETE", path, query, responseSerializer = serializer())

    suspend fun noContent(method: String, path: String, query: Map<String, String> = emptyMap()) {
        executeRaw(method, path, query, body = null)
    }

    suspend fun uploadBytes(
        uploadUrl: String,
        headers: Map<String, String>,
        bytes: ByteArray,
        contentType: String,
    ) {
        val body = bytes.toRequestBody(contentType.toMediaType())
        val builder = Request.Builder().url(uploadUrl).put(body)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val call = okHttpClient.newCall(builder.build())
        val response = withContextIO { call.execute() }
        response.use { r ->
            if (!r.isSuccessful) {
                throw ApiException(r.code, "上传失败: ${r.body?.string() ?: r.message}")
            }
        }
    }

    /** multipart 上传：用于贴纸等文件字段 + 文本字段。 */
    suspend fun uploadMultipart(
        path: String,
        textFields: Map<String, String>,
        fileFieldName: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
    ): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                textFields.forEach { (k, v) ->
                    if (v.isNotBlank()) addFormDataPart(k, v)
                }
                addFormDataPart(
                    fileFieldName,
                    fileName,
                    bytes.toRequestBody(contentType.toMediaType()),
                )
            }
            .build()
        val builder = Request.Builder().url(buildUrl(path, emptyMap())).post(body)
        session.authHeaders().forEach { (k, v) -> builder.header(k, v) }
        val response = withContextIO { okHttpClient.newCall(builder.build()).execute() }
        return response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                throw ApiException(r.code, text.ifBlank { r.message })
            }
            text
        }
    }

    suspend fun <T> execute(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: Any? = null,
        responseSerializer: DeserializationStrategy<T>,
    ): T {
        val raw = executeRaw(method, path, query, body)
        if (raw.isBlank()) {
            throw ApiException(200, "Empty response body")
        }
        return withContextIO {
            json.decodeFromString(responseSerializer, raw)
        }
    }

    @OptIn(kotlinx.serialization.InternalSerializationApi::class)
    private suspend fun executeRaw(
        method: String,
        path: String,
        query: Map<String, String>,
        body: Any?,
    ): String {
        val builder = Request.Builder().url(buildUrl(path, query))
        session.authHeaders().forEach { (k, v) -> builder.header(k, v) }

        if (body != null) {
            val jsonText = when (body) {
                is String -> body
                else -> {
                    val bodySerializer = body::class.serializer() as KSerializer<Any>
                    json.encodeToString(bodySerializer, body)
                }
            }
            builder.method(method, jsonText.toRequestBody(jsonMedia))
        } else {
            builder.method(method, null)
        }

        val request = builder.build()
        val response = withContextIO { okHttpClient.newCall(request).execute() }
        return response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                throw ApiException(r.code, text.ifBlank { r.message })
            }
            text
        }
    }

    private fun buildUrl(path: String, query: Map<String, String>): HttpUrl {
        val base = baseUrl()
        val urlBuilder = base.toHttpUrl().newBuilder()
        val cleanPath = path.trimStart('/')
        if (cleanPath.isNotEmpty()) {
            cleanPath.split('/').forEach { seg ->
                if (seg.isNotEmpty()) urlBuilder.addPathSegment(seg)
            }
        }
        query.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        return urlBuilder.build()
    }
}

private suspend fun <T> withContextIO(block: () -> T): T =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
