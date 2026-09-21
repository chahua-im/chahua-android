package net.paigu.chahua.data

import android.content.Context
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.paigu.chahua.data.models.StickerPackDetailResponse
import net.paigu.chahua.data.models.StickerPackSummaryDto
import net.paigu.chahua.data.models.StickerSummaryDto

/** 表情包预缓存状态，供设置页展示进度。 */
data class StickerPrecacheState(
    /** 是否正在预缓存。 */
    val running: Boolean = false,
    /** 本轮涉及的表情包数量。 */
    val packs: Int = 0,
    /** 本轮需要处理的图片总数。 */
    val total: Int = 0,
    /** 本轮从网络下载并写入缓存的数量。 */
    val downloaded: Int = 0,
    /** 命中磁盘缓存、无需下载的数量。 */
    val cached: Int = 0,
    /** 下载失败的数量（下次进入 App 会自动重试）。 */
    val failed: Int = 0,
    /** 上一次完成时间（毫秒时间戳），0 表示本次进程尚未跑过。 */
    val finishedAt: Long = 0L,
    /** 拉取表情包列表失败时的错误信息。 */
    val lastError: String? = null,
) {
    /** 本轮已处理数量。 */
    val handled: Int get() = downloaded + cached + failed
}

/**
 * 表情包预缓存：进入 App 后，在后台把当前账号「自建 + 订阅」表情包以及收藏表情的图片
 * 全部写入 Coil 磁盘缓存，之后打开表情面板、预览表情都不再等待网络。
 *
 * 之所以能直接复用展示用的缓存：Coil 网络请求的磁盘缓存 key 就是图片 URL
 * （`NetworkFetcher.diskCacheKey = options.diskCacheKey ?: url`），只要预缓存和
 * [net.paigu.chahua.ui.common.AuthAsyncImage] 用同一个 [ImageLoader]、同一个 URL，
 * 展示时命中的就是同一份缓存。
 *
 * 每张图片都会先探测磁盘缓存，已存在的直接跳过，所以：
 * - 只下载真正缺失的图片，系统清理 cacheDir 后也能自动补齐；
 * - 不会因为「记录过已完成」而永久失效（磁盘缓存本身才是唯一事实来源）。
 *
 * 触发时机是 [start]，由 MainActivity 在进入 App 时调用；同一进程内同一账号只跑一轮，
 * 避免 Activity 重建（旋屏、返回主页）反复请求接口。设置页的「立即缓存」走 `force = true`。
 */
class StickerPrecache(
    context: Context,
    private val api: ChatApi,
    private val session: SessionManager,
    private val imageLoader: ImageLoader,
) {

    private companion object {
        const val TAG = "StickerPrecache"

        /** 并发下载数：贴纸体积小，4 路足够快又不会打满连接池。 */
        const val CONCURRENCY = 4

        /** 解码尺寸：覆盖表情面板（72dp 单元格）与预览所需，避免按原图解码。 */
        const val DECODE_PX = 320
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 同一时刻只跑一轮：进入 App 与设置页手动触发可能同时到达。 */
    private var job: Job? = null

    /**
     * 本进程内已经预缓存过的账号（登录 key）。Activity 重建（旋屏、返回主页等）会重复调用
     * [start]，用它兜住；切换账号会得到不同的 key，因此会重新跑一轮。
     */
    @Volatile
    private var precachedSessionKey: String? = null

    private val _state = MutableStateFlow(StickerPrecacheState())
    val state: StateFlow<StickerPrecacheState> = _state.asStateFlow()

    /**
     * 触发一轮预缓存。
     *
     * @param force 手动触发时传 true，跳过一次进程一次账号的限制（已下载的图片仍会被跳过）。
     */
    fun start(force: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            val sessionKey = session.current().let { if (it.hasSession) it.authKey else null }
            if (sessionKey == null) {
                AppLog.d(TAG, "未登录，跳过预缓存")
                return@launch
            }
            if (!force && sessionKey == precachedSessionKey) {
                AppLog.d(TAG, "本进程已为当前账号预缓存过，跳过")
                return@launch
            }
            precachedSessionKey = sessionKey
            runCatching { precache() }
                .onFailure { AppLog.w(TAG, "预缓存中止: ${it.message}") }
        }
    }

    private suspend fun precache() {
        _state.value = StickerPrecacheState(running = true)

        val packs = runCatching { loadPacks() }.getOrElse { error ->
            AppLog.w(TAG, "读取表情包失败: ${error.message}")
            _state.value = StickerPrecacheState(lastError = error.message ?: "unknown")
            return
        }
        val urls = collectUrls(packs)
        _state.update { it.copy(packs = packs.size, total = urls.size) }
        if (urls.isEmpty()) {
            AppLog.i(TAG, "没有需要预缓存的表情")
            _state.update { it.copy(running = false, finishedAt = System.currentTimeMillis()) }
            return
        }
        AppLog.i(TAG, "开始预缓存: ${packs.size} 个表情包, ${urls.size} 张图片")

        val semaphore = Semaphore(CONCURRENCY)
        coroutineScope {
            urls.map { url -> async { semaphore.withPermit { handleOne(url) } } }.awaitAll()
        }

        _state.update { it.copy(running = false, finishedAt = System.currentTimeMillis()) }
        val done = _state.value
        AppLog.i(
            TAG,
            "预缓存完成: 下载 ${done.downloaded}, 命中 ${done.cached}, 失败 ${done.failed}",
        )
    }

    /** 处理单张图片：命中磁盘缓存就跳过，否则下载并写入缓存。 */
    private suspend fun handleOne(url: String) {
        if (isCached(url)) {
            _state.update { it.copy(cached = it.cached + 1) }
            return
        }
        val ok = runCatching { imageLoader.execute(request(url)) }
            .getOrNull() is SuccessResult
        _state.update {
            if (ok) it.copy(downloaded = it.downloaded + 1) else it.copy(failed = it.failed + 1)
        }
    }

    /** 探测磁盘缓存；缓存 key 即图片 URL，见类注释。 */
    private fun isCached(url: String): Boolean {
        val snapshot = runCatching { imageLoader.diskCache?.openSnapshot(url) }.getOrNull()
            ?: return false
        snapshot.close()
        return true
    }

    private fun request(url: String): ImageRequest = ImageRequest.Builder(appContext)
        .data(url)
        .size(Size(DECODE_PX, DECODE_PX))
        // 只写磁盘缓存：解码尺寸与面板实际请求不一致，写内存缓存命中不了，还会挤占头像等常用图。
        .memoryCachePolicy(CachePolicy.DISABLED)
        .build()

    /** 与表情面板一致：自建 + 订阅，自建的排前面并按 id 去重。 */
    private suspend fun loadPacks(): List<StickerPackSummaryDto> {
        val owned = api.ownedStickerPacks().packs
        val subscribed = api.subscribedStickerPacks().packs
        val ownedIds = owned.mapTo(HashSet()) { it.id }
        return owned + subscribed.filterNot { it.id in ownedIds }
    }

    /** 拉取预缓存所需的接口数据，再交给 [stickerPrecacheUrls] 汇总。 */
    private suspend fun collectUrls(packs: List<StickerPackSummaryDto>): List<String> {
        // 收藏表情可能来自未订阅的表情包，单独取一次。
        val favorites = runCatching { api.favoriteStickers().stickers }
            .getOrNull()
            .orEmpty()
        // 只有详情接口带完整贴纸列表；表情包数量有限，串行拉取避免瞬时并发过多。
        val details = packs.mapNotNull { pack ->
            runCatching { api.stickerPackDetail(pack.id) }.getOrNull()
        }
        return stickerPrecacheUrls(packs, details, favorites)
    }
}

/**
 * 汇总需要预缓存的图片 URL：贴纸包封面 → 收藏表情 → 包内全部贴纸，按首次出现顺序去重。
 *
 * 同一个表情可能既在贴纸包里又被收藏，也可能同时属于自建与订阅列表，因此必须去重，
 * 否则同一张图会被重复下载。
 */
internal fun stickerPrecacheUrls(
    packs: List<StickerPackSummaryDto>,
    details: List<StickerPackDetailResponse>,
    favorites: List<StickerSummaryDto>,
): List<String> {
    val urls = LinkedHashSet<String>()
    packs.forEach { pack ->
        pack.previewSticker?.media?.url?.takeIf { it.isNotBlank() }?.let(urls::add)
    }
    favorites.forEach { sticker ->
        sticker.media.url.takeIf { it.isNotBlank() }?.let(urls::add)
    }
    details.forEach { detail ->
        detail.stickers.forEach { sticker ->
            sticker.media.url.takeIf { it.isNotBlank() }?.let(urls::add)
        }
    }
    return urls.toList()
}
