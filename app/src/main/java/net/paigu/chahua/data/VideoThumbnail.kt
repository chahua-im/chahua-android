package net.paigu.chahua.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.LruCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 封面 + 时长（时长取自 [MediaMetadataRetriever]，随封面一起落盘）。 */
data class VideoPoster(
    val bitmap: Bitmap?,
    val durationMs: Long,
)

/**
 * 视频封面：用 [MediaMetadataRetriever] 抽第一帧，结果按 URL 落盘复用。
 *
 * 取帧的数据来自 [VideoCache.mediaDataSource]（优先命中缓存，缺失的按需下载并写回缓存），
 * 只会读取解码所需的片段，不会为了缩略图把整段视频重新下载一遍。
 */
object VideoThumbnail {

    /** 落盘封面长边像素。 */
    private const val MAX_EDGE_PX = 480

    private const val JPEG_QUALITY = 80

    /** 封面文件数量上限，超出后按最久未使用删除。 */
    private const val MAX_FILES = 300

    /** 抽帧并发上限：群资料里一屏可能有多个视频，避免同时起太多解码器。 */
    private val semaphore = Semaphore(2)

    /** 内存里保留的封面总量上限：滑动聊天列表时不必反复解码同一张 JPEG。 */
    private const val MEMORY_CACHE_BYTES = 12 * 1024 * 1024

    private val memory = object : LruCache<String, VideoPoster>(MEMORY_CACHE_BYTES) {
        override fun sizeOf(key: String, value: VideoPoster): Int =
            value.bitmap?.allocationByteCount ?: 64
    }

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "video_thumbs")

    private fun cacheFile(context: Context, url: String): File =
        File(cacheDir(context), "${url.hashCode().toUInt().toString(16)}.jpg")

    private fun durationFile(context: Context, url: String): File =
        File(cacheDir(context), "${url.hashCode().toUInt().toString(16)}.dur")

    /** 读封面：内存/磁盘命中直接返回，否则抽帧、缩放、写盘（同时记录时长）。 */
    suspend fun load(context: Context, url: String, sizeHint: Long = 0L): VideoPoster? {
        if (url.isBlank()) return null
        memory.get(url)?.let { return it }
        val poster = withContext(Dispatchers.IO) {
            val file = cacheFile(context, url)
            val durationFile = durationFile(context, url)
            if (file.exists()) {
                file.setLastModified(System.currentTimeMillis())
                val bitmap = decode(file)
                val duration = durationFile.takeIf { it.exists() }
                    ?.let { runCatching { it.readText().trim().toLong() }.getOrNull() }
                    ?: 0L
                if (bitmap != null || duration > 0) return@withContext VideoPoster(bitmap, duration)
            }
            val extracted = semaphore.withPermit { extract(url, sizeHint) }
                ?: return@withContext null
            val frame = extracted.first
            val durationMs = extracted.second
            if (durationMs > 0) {
                runCatching {
                    file.parentFile?.mkdirs()
                    durationFile.writeText(durationMs.toString())
                }
            }
            if (frame == null) return@withContext VideoPoster(null, durationMs)
            val scaled = scale(frame)
            if (scaled !== frame) frame.recycle()
            runCatching {
                file.parentFile?.mkdirs()
                file.outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                trimCache(file.parentFile)
            }
            VideoPoster(scaled, durationMs)
        }
        if (poster != null) memory.put(url, poster)
        return poster
    }

    /** 删除某个视频的封面与时长记录（视频缓存被清理时一并调用）。 */
    fun evict(context: Context, url: String) {
        memory.remove(url)
        runCatching { cacheFile(context, url).delete() }
        runCatching { durationFile(context, url).delete() }
    }

    /** 清空内存里的封面（设置里"清理缓存"时调用）。 */
    fun clearMemory() {
        memory.evictAll()
    }

    /** 抽第一帧（关键帧）并读取时长；失败返回 null。 */
    private fun extract(url: String, sizeHint: Long): Pair<Bitmap?, Long>? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(VideoCache.mediaDataSource(url, sizeHint))
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            var duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            if (duration <= 0) duration = extractDurationUs(url, sizeHint) / 1000
            frame to duration
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()

    /** MediaMetadataRetriever 拿不到时长时，退回 MediaExtractor 读轨道时长（微秒）。 */
    private fun extractDurationUs(url: String, sizeHint: Long): Long = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(VideoCache.mediaDataSource(url, sizeHint))
            (0 until extractor.trackCount)
                .mapNotNull { index ->
                    val format = extractor.getTrackFormat(index)
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        format.getLong(MediaFormat.KEY_DURATION)
                    } else {
                        null
                    }
                }
                .maxOrNull() ?: 0L
        } finally {
            runCatching { extractor.release() }
        }
    }.getOrDefault(0L)

    private fun decode(file: File): Bitmap? = runCatching {
        BitmapFactory.decodeFile(file.absolutePath)
    }.getOrNull()

    private fun scale(bitmap: Bitmap): Bitmap {
        val maxEdge = maxOf(bitmap.width, bitmap.height)
        if (maxEdge <= MAX_EDGE_PX || maxEdge <= 0) return bitmap
        val ratio = MAX_EDGE_PX.toFloat() / maxEdge
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(bitmap, width, height, true) }
            .getOrDefault(bitmap)
    }

    /** 封面文件过多时按最后修改时间淘汰最旧的。 */
    private fun trimCache(dir: File?) {
        val files = dir?.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_FILES)
            .forEach { runCatching { it.delete() } }
    }
}
