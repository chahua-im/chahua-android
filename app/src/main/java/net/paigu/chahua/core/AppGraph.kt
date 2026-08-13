package net.paigu.chahua.core

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import com.github.awxkee.avifcoil.decoder.HeifDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.paigu.chahua.data.ApiClient
import net.paigu.chahua.data.ChatApi
import net.paigu.chahua.data.ChatEngine
import net.paigu.chahua.data.ChatStore
import net.paigu.chahua.data.ErrorLoggingInterceptor
import net.paigu.chahua.data.SessionManager
import net.paigu.chahua.data.SettingsManager
import net.paigu.chahua.data.SyncManager
import net.paigu.chahua.data.UserAgentInterceptor
import net.paigu.chahua.service.ChatMessagingService
import net.paigu.chahua.R
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * 轻量手动依赖注入容器：应用启动时初始化一次，供 ViewModel / Service / 图片加载共享。
 */
object AppGraph {

    lateinit var app: Application
        private set

    lateinit var session: SessionManager
        private set

    lateinit var settings: SettingsManager
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var api: ChatApi
        private set

    lateinit var store: ChatStore
        private set

    lateinit var engine: ChatEngine
        private set

    lateinit var syncManager: SyncManager
        private set

    lateinit var imageLoader: ImageLoader
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(application: Application) {
        app = application
        session = SessionManager(application)
        settings = SettingsManager(application)
        apiClient = ApiClient(session, application)
        api = ChatApi(apiClient, session)
        store = ChatStore()
        engine = ChatEngine(apiClient, api, store) { settings.snapshot().showLatency }
        store.currentUid = { session.snapshot().me?.uid ?: -1 }
        syncManager = SyncManager(api, store)
        engine.onConnected = { scope.launch { syncManager.syncAll() } }
        imageLoader = buildImageLoader(application, session)
    }

    /** 启动后台消息 Service（前台服务，负责 WebSocket 收发与推送通知）。 */
    fun startMessaging(context: Context) {
        val intent = Intent(context, ChatMessagingService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopMessaging(context: Context) {
        context.stopService(Intent(context, ChatMessagingService::class.java))
    }

    private fun buildImageLoader(context: Context, session: SessionManager): ImageLoader {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            val apiHost = session.snapshot().serverUrl.toHttpUrlOrNull()?.host
            val builder = if (apiHost != null && request.url.host == apiHost) {
                request.newBuilder().apply {
                    session.authHeaders().forEach { (k, v) -> header(k, v) }
                }
            } else {
                request.newBuilder()
            }
            chain.proceed(builder.build())
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(ErrorLoggingInterceptor())
            .addInterceptor(UserAgentInterceptor(context.getString(R.string.x_user_agent)))
            .build()
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(client))
                // 服务端默认头像为 noavatar.svg，需注册 SVG 解码器。
                add(SvgDecoder.Factory())
                // 仅 Android 12 以下注册 avif-coder 软解兜底 AVIF/HEIC，
                // 新设备不加载该解码器，完全走系统解码路径。
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    add(HeifDecoder.Factory())
                }
            }
            .crossfade(true)
            .logger(DebugLogger())
            .build()
    }
}
