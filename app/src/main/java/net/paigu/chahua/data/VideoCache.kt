package net.paigu.chahua.data

import android.content.Context
import android.media.MediaDataSource
import android.os.SystemClock
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    /**
     * 预缓存进度的最小上报间隔。CacheWriter 每读满一个缓冲块（约 64KB）就回调一次，
     * 一条 200MB 的视频会有数千次回调；每次都更新 [states] 会让聊天里所有视频气泡反复重组。
     */
    private const val PROGRESS_EMIT_INTERVAL_MS = 250L

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
        this.userAgent = userAgent
        this.headersFor = headersFor
        open(context)
    }

    private fun open(context: Context) {
        if (cache != null) return
        val dir = File(context.cacheDir, "video_cache")
        cache = SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    /**
     * 清空整个缓存目录前必须先关掉 [SimpleCache]：
     * 它的索引与缓存分片都在 `cacheDir` 下，带着打开状态把这些文件删掉，
     * 内存里的索引就和磁盘对不上了（[isCached] 会说"已缓存"但读不到数据）。
     * 清完目录后再调用 [reopen]。
     */
    fun release() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _states.value = emptyMap()
        runCatching { cache?.release() }
        cache = null
    }

    /** [release] 之后重新打开缓存（沿用已记录的 User-Agent 与鉴权头）。 */
    fun reopen(context: Context) = open(context)

    /**
     * 某个视频的缓存状态流：只在这条视频自身状态变化时通知，避免所有气泡一起重组。
     * 没有任务记录时要回落到磁盘检查——上次启动前就缓存完的视频不会留下任务记录。
     */
    fun stateFlow(url: String, sizeHint: Long = 0L): Flow<VideoCacheState> =
        _states.map { it[url] }
            .distinctUntilChanged()
            .map { state ->
                state ?: if (isCached(url, sizeHint)) {
                    VideoCacheState.Cached
                } else {
                    VideoCacheState.Idle
                }
            }

    /** 播放把缓存写满后刷新状态，让聊天气泡的角标从"未缓存"变成"已缓存"。 */
    fun refreshState(url: String, sizeHint: Long = 0L) {
        if (url.isBlank() || cache == null) return
        if (isCached(url, sizeHint)) markCached(url)
    }

    /**
     * 是否已完整缓存。[sizeHint] 是视频总大小（未知传 0）。
     *
     * 注意**不能**写成 `cache.isCached(url, 0, C.LENGTH_UNSET)`：Media3 的
     * `CachedContent.getCachedBytesLength` 里 `checkArgument(length >= 0)`，
     * 传 -1 会抛 IllegalArgumentException，被 runCatching 吞掉后永远返回 false
     * （曾经因此让"已缓存"判断恒为假）。有体积就比已缓存字节数，没有就退回分片判断。
     */
    fun isCached(url: String, sizeHint: Long = 0L): Boolean {
        if (url.isBlank()) return false
        val cache = cache ?: return false
        if (sizeHint > 0L) {
            return runCatching { cache.getCachedBytes(url, 0, sizeHint) >= sizeHint }
                .getOrDefault(false)
        }
        // 体积未知：整段缓存只会留下一个"非空洞、开放结尾"的分片。
        return runCatching {
            val spans = cache.getCachedSpans(url)
            spans.size == 1 && spans.first().let { it.isCached && !it.isHoleSpan && it.isOpenEnded() }
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
    fun stateOf(url: String, sizeHint: Long = 0L): VideoCacheState {
        if (isCached(url, sizeHint)) return VideoCacheState.Cached
        return _states.value[url] ?: VideoCacheState.Idle
    }

    /**
     * 后台把整个视频写进缓存。[totalHint] 为附件里的大小（未知传 0），
     * 仅用于展示进度，不影响下载。
     */
    fun preCache(url: String, totalHint: Long = 0L) {
        if (url.isBlank() || cache == null) return
        if (isCached(url, totalHint)) {
            markCached(url)
            return
        }
        if (jobs[url]?.isActive == true) return
        _states.update { it + (url to VideoCacheState.Downloading(cachedBytes(url), totalHint)) }
        jobs[url] = scope.launch {
            val dataSource = cacheDataSource(url)
            // 节流：进度只用于展示，没必要每个缓冲块都推一次。
            var lastEmitAt = 0L
            val progress = CacheWriter.ProgressListener { requestLength, bytesCached, _ ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastEmitAt < PROGRESS_EMIT_INTERVAL_MS) return@ProgressListener
                lastEmitAt = now
                val total = if (requestLength > 0) requestLength else totalHint
                _states.update { it + (url to VideoCacheState.Downloading(bytesCached, total)) }
            }
            runCatching {
                CacheWriter(dataSource, DataSpec.Builder().setUri(url).build(), null, progress)
                    .cache()
            }
            runCatching { dataSource.close() }
            jobs.remove(url)
            if (isCached(url, totalHint)) {
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
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
        )
    }

    /**
     * 给 [android.media.MediaMetadataRetriever] 用的随机读数据源：
     * 优先读已缓存的部分，缺失的按需下载并写回缓存，不会重复下载整段视频。
     *
     * @param sizeHint 视频总大小（未知传 0）。
     */
    fun mediaDataSource(url: String, sizeHint: Long = 0L): MediaDataSource = object : MediaDataSource() {
        // 复用同一个 DataSource：抽帧时 MediaMetadataRetriever 会做大量随机读，
        // 每次 readAt 都新建 CacheDataSource 既浪费分配，也要反复获取缓存锁。
        // DataSource 的 open/close 仍需成对调用（Media3 要求 open 前必须 close）。
        private val source: DataSource? by lazy { dataSourceFor(url) }

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (size <= 0) return 0
            val source = source ?: return -1
            return try {
                source.open(
                    DataSpec.Builder()
                        .setUri(url)
                        .setPosition(position)
                        .setLength(size.toLong())
                        .build(),
                )
                var read = 0
                while (read < size) {
                    val n = source.read(buffer, offset + read, size - read)
                    if (n == C.RESULT_END_OF_INPUT) break
                    read += n
                }
                if (read == 0) -1 else read
            } catch (e: Exception) {
                -1
            } finally {
                runCatching { source.close() }
            }
        }

        override fun getSize(): Long = if (sizeHint > 0) sizeHint else -1L

        override fun close() = Unit
    }

    private fun dataSourceFor(url: String): DataSource? {
        val cache = cache ?: return httpFactory(url).createDataSource()
        return runCatching { cacheDataSource(url) }.getOrNull()
    }

    private fun cacheDataSource(url: String): CacheDataSource =
        CacheDataSource.Factory()
            .setCache(requireNotNull(cache))
            .setUpstreamDataSourceFactory(httpFactory(url))
            // 缓存分片损坏 / 索引与磁盘不一致时回退到网络，而不是让播放直接失败。
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
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
