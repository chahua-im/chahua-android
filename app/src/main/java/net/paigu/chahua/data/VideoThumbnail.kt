package net.paigu.chahua.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 视频封面：用 [MediaMetadataRetriever] 抽第一帧，结果按 URL 落盘复用。
 *
 * 取帧的数据来自 [VideoCache.mediaDataSource]（优先命中缓存，缺失的按需下载并写回缓存），
 * 所以不会为了缩略图把整段视频重新下载一遍。
 */
object VideoThumbnail {

    /** 落盘封面长边像素。 */
    private const val MAX_EDGE_PX = 480

    private const val JPEG_QUALITY = 80

    /** 封面文件数量上限，超出后按最久未使用删除。 */
    private const val MAX_FILES = 300

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "video_thumbs")

    private fun cacheFile(context: Context, url: String): File =
        File(cacheDir(context), "${url.hashCode().toUInt().toString(16)}.jpg")

    /** 取封面：磁盘命中直接返回，否则抽帧、缩放、写盘。 */
    suspend fun load(context: Context, url: String, sizeHint: Long = 0L): Bitmap? =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext null
            val file = cacheFile(context, url)
            if (file.exists()) {
                file.setLastModified(System.currentTimeMillis())
                decode(file)?.let { return@withContext it }
            }
            val frame = extractFrame(url, sizeHint) ?: return@withContext null
            val scaled = scale(frame)
            if (scaled !== frame) frame.recycle()
            runCatching {
                file.parentFile?.mkdirs()
                file.outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                trimCache(file.parentFile)
            }
            scaled
        }

    /** 删除某个视频的封面（视频缓存被清理时一并调用）。 */
    fun evict(context: Context, url: String) {
        runCatching { cacheFile(context, url).delete() }
    }

    private fun extractFrame(url: String, sizeHint: Long): Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(VideoCache.mediaDataSource(url, sizeHint))
            // 优先第一帧（关键帧），拿不到再退回系统给的任意一帧。
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()

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
