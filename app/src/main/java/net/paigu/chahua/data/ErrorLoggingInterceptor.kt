package net.paigu.chahua.data

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 请求出错时输出错误日志到 Logcat，方便排查服务器访问问题。
 * 覆盖两种情况：
 * - 网络异常（IO 异常，如超时、连接失败、DNS 错误）；
 * - 非 2xx 的 HTTP 错误响应（附带响应体片段）。
 */
class ErrorLoggingInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            val response = chain.proceed(request)
            if (!response.isSuccessful) {
                val body = try {
                    response.peekBody(MAX_BODY_SNIPPET).string().trim()
                } catch (_: Exception) {
                    ""
                }
                Log.e(
                    TAG,
                    "HTTP ${response.code} ${request.method} ${request.url}" +
                        if (body.isBlank()) " (${response.message})" else ": $body",
                )
            }
            response
        } catch (e: IOException) {
            Log.e(TAG, "网络请求失败 ${request.method} ${request.url}", e)
            throw e
        }
    }

    private companion object {
        const val TAG = "ChahuaApi"
        const val MAX_BODY_SNIPPET = 1024L
    }
}
