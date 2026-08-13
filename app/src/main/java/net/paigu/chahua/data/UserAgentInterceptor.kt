package net.paigu.chahua.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 显式设置 User-Agent，覆盖 OkHttp 默认的 `okhttp/4.12.0`。
 * UA 值来自 strings.xml 的 x_user_agent，便于统一维护。
 */
class UserAgentInterceptor(private val userAgent: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .build()
        return chain.proceed(request)
    }
}
