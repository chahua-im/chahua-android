package net.paigu.chahua.data

import android.content.Context
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 单个视频的缓存状态（UI 展示用）。 */
sealed interface VideoCacheState {
    /** 未缓存，也没在缓存中。 */
    data object Idle : VideoCacheState

    /** 正在缓存：[total] 为 0 时表示总大小未知。 */
    data class Downloading(val bytes: Long, val total: Long) : VideoCacheState

    /** 已完整缓存，可直接播放。 */
    data object Cached : VideoCacheState
}

/**
 * 视频磁盘缓存：预缓存与播放共用同一个 Media3 [SimpleCache]，
 * 已缓存的部分不会重复下载；超过上限按最近最少使用淘汰。
 *
 * - 播放（聊天内预览、全屏查看器）都通过 [mediaSourceFactory] 走缓存，边播边写；
 * - [preCache] 在后台把整个视频写进缓存，并对外暴露进度；
 * - 缓存键就是视频 URL（同一 URL 复用同一份缓存）。
 */
object VideoCache {

    /** 缓存上限：512MB。 */
    private const val MAX_CACHE_BYTES = 512L * 1024 * 1024

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    @Volatile
    private var cache: SimpleCache? = null

    private var userAgent: String = "chahua-android"

    /** 按 URL 取需要附加的请求头（仅需鉴权的同源地址才加，避免把凭证发给第三方 CDN）。 */
    private var headersFor: (String) -> Map<String, String> = { emptyMap() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _states = MutableStateFlow<Map<String, VideoCacheState>>(emptyMap())

    /** 缓存任务状态（正在下载/已下载完成的条目）。 */
    val states: StateFlow<Map<String, VideoCacheState>> = _states.asStateFlow()

    fun init(
        context: Context,
        userAgent: String,
        headersFor: (String) -> Map<String, String>,
    ) {
        if (cache != null) return
        this.userAgent = userAgent
        this.headersFor = headersFor
        val dir = File(context.cacheDir, "video_cache")
        cache = SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    /** 是否已完整缓存。 */
    fun isCached(url: String): Boolean {
        if (url.isBlank()) return false
        val cache = cache ?: return false
        return runCatching {
            cache.isCached(url, 0, C.LENGTH_UNSET.toLong())
        }.getOrDefault(false)
    }

    /** 已缓存的字节数（未下载完时用于显示进度）。 */
    fun cachedBytes(url: String): Long {
        val cache = cache ?: return 0L
        return runCatching {
            cache.getCachedBytes(url, 0, C.LENGTH_UNSET.toLong())
        }.getOrDefault(0L)
    }

    /** 当前状态：已缓存 > 正在下载 > 未缓存。 */
    fun stateOf(url: String): VideoCacheState {
        if (isCached(url)) return VideoCacheState.Cached
        return _states.value[url] ?: VideoCacheState.Idle
    }

    /**
     * 后台把整个视频写进缓存。[totalHint] 为附件里的大小（未知传 0），
     * 仅用于展示进度，不影响下载。
     */
    fun preCache(url: String, totalHint: Long = 0L) {
        if (url.isBlank() || cache == null) return
        if (isCached(url)) {
            markCached(url)
            return
        }
        if (jobs[url]?.isActive == true) return
        _states.update { it + (url to VideoCacheState.Downloading(cachedBytes(url), totalHint)) }
        jobs[url] = scope.launch {
            val dataSource = cacheDataSource(url)
            val progress = CacheWriter.ProgressListener { requestLength, bytesCached, _ ->
                val total = if (requestLength > 0) requestLength else totalHint
                _states.update { it + (url to VideoCacheState.Downloading(bytesCached, total)) }
            }
            runCatching {
                CacheWriter(dataSource, DataSpec.Builder().setUri(url).build(), null, progress)
                    .cache()
            }
            runCatching { dataSource.close() }
            jobs.remove(url)
            if (isCached(url)) {
                markCached(url)
            } else {
                // 失败或被取消：回到未缓存状态，下次可以重试。
                _states.update { it - url }
            }
        }
    }

    /** 播放（聊天内预览 / 全屏查看器）用：读缓存、边播边写。 */
    fun mediaSourceFactory(url: String): MediaSource.Factory {
        val upstream = httpFactory(url)
        val cache = cache ?: return DefaultMediaSourceFactory(upstream)
        return DefaultMediaSourceFactory(
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream),
        )
    }

    private fun cacheDataSource(url: String): CacheDataSource =
        CacheDataSource.Factory()
            .setCache(requireNotNull(cache))
            .setUpstreamDataSourceFactory(httpFactory(url))
            .createDataSource()

    private fun httpFactory(url: String): DataSource.Factory {
        val factory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
        val headers = headersFor(url)
        if (headers.isNotEmpty()) factory.setDefaultRequestProperties(headers)
        return factory
    }

    private fun markCached(url: String) {
        _states.update { it + (url to VideoCacheState.Cached) }
    }
}
