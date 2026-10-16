package net.paigu.chahua.ui.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import coil3.disk.DiskCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.AppLog
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/** 一次保存请求（图片 / 视频 / 文件）。 */
data class MediaSaveRequest(
    val url: String,
    val kind: String,
    val fileName: String?,
)

/** 下载媒体文件并保存到系统相册（图片）或影片（视频）。 */
object MediaSaver {

    private const val TAG = "MediaSaver"

    /**
     * 与应用同生命周期的作用域：保存过程中退出界面 / 销毁 Activity
     * 都不会中断下载。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _saving = MutableStateFlow<Set<String>>(emptySet())

    /** 正在保存的媒体 URL，界面退出后再回来仍可显示进度。 */
    val saving: StateFlow<Set<String>> = _saving.asStateFlow()

    /**
     * 后台批量保存到相册 / 影片：顺序执行，避免一次打满带宽。
     * 回调在主线程执行，[onResult] 带上成功与失败数量。
     */
    fun saveToGalleryInBackground(
        context: Context,
        requests: List<MediaSaveRequest>,
        onEachSaved: (MediaSaveRequest) -> Unit = {},
        onResult: (saved: Int, failed: Int, firstError: String?) -> Unit = { _, _, _ -> },
    ) {
        if (requests.isEmpty()) return
        val appContext = context.applicationContext
        scope.launch {
            var saved = 0
            var failed = 0
            var firstError: String? = null
            requests.forEach { request ->
                _saving.update { it + request.url }
                val result = runCatching {
                    downloadToGallery(appContext, request.url, request.kind, request.fileName)
                }
                _saving.update { it - request.url }
                if (result.isSuccess) {
                    saved++
                    withContext(Dispatchers.Main) { onEachSaved(request) }
                } else {
                    failed++
                    if (firstError == null) firstError = result.exceptionOrNull()?.message
                }
            }
            withContext(Dispatchers.Main) { onResult(saved, failed, firstError) }
        }
    }

    /** 后台保存普通文件到系统“下载”目录；同样不随界面销毁而取消。 */
    fun saveFileInBackground(
        context: Context,
        url: String,
        fileName: String?,
        mime: String,
        onResult: (Result<String>) -> Unit,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            _saving.update { it + url }
            val result = runCatching { downloadFile(appContext, url, fileName, mime) }
            _saving.update { it - url }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /**
     * 保存媒体到相册 / 影片，返回最终保存的文件名。
     *
     * 图片优先复用 Coil 磁盘缓存：既然用户已经在查看器里看到这张图，说明这份字节早就下载好了，
     * 直接从缓存拷进 MediaStore 即可，不再发一次网络请求。
     *
     * 缓存命中拿到的就是原内容：Coil 3 的磁盘缓存只存网络响应的原始编码字节
     * （解码尺寸、裁剪、crossfade 等变换只影响内存缓存与解码结果），
     * 所以拷出来的文件与「用同一个 URL 重新下载」逐字节一致，也仍是原始尺寸与原始格式。
     *
     * 缓存未命中（没在本地展示过 / 被 LRU 淘汰 / 用户清过缓存 / 条目正在写入）时回退到网络下载，
     * 两者写入相册的逻辑完全一致。
     *
     * Android 10+ 走 MediaStore（无需权限）；Android 9 及以下写入公共目录，需要 WRITE_EXTERNAL_STORAGE 权限。
     */
    suspend fun downloadToGallery(
        context: Context,
        url: String,
        kind: String,
        fileName: String?,
    ): String = withContext(Dispatchers.IO) {
        val isVideo = kind.startsWith("video")
        val mime = kind
            .takeIf { it.startsWith("image/") || it.startsWith("video/") }
            ?: if (isVideo) "video/mp4" else "image/jpeg"
        val name = buildName(url, fileName, isVideo, mime)

        // 视频不走 Coil（播放与封面分别走 ExoPlayer 的 SimpleCache 与本地抽帧），
        // 且 SimpleCache 里只有整段缓存完整时才能还原出原始文件，这里不做复用。
        if (!isVideo && copyFromImageCache(context, url, mime, name)) {
            return@withContext name
        }

        val response = AppGraph.apiClient.okHttpClient.newCall(authRequest(url)).execute()
        response.use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("Empty response body")
            writeToGallery(context, body.byteStream(), isVideo, mime, name)
        }
        name
    }

    /** 下载普通文件并保存到系统“下载”目录（Q+ 用 MediaStore，无需权限）。 */
    suspend fun downloadFile(
        context: Context,
        url: String,
        fileName: String?,
        mime: String,
    ): String = withContext(Dispatchers.IO) {
        val name = buildDownloadName(url, fileName, mime)
        val response = AppGraph.apiClient.okHttpClient.newCall(authRequest(url)).execute()
        response.use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("Empty response body")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeFileToDownloads(context, body.byteStream(), mime, name)
            } else {
                writeFileToLegacyDownloads(context, body.byteStream(), mime, name)
            }
        }
        name
    }

    /**
     * 尝试把 Coil 磁盘缓存里的原始字节直接写进相册（只用于图片）。
     *
     * @return true 表示已经存好；false 表示没有缓存（或缓存读取失败），调用方应回退到网络下载。
     */
    private fun copyFromImageCache(
        context: Context,
        url: String,
        mime: String,
        name: String,
    ): Boolean {
        val snapshot = openCachedImage(url) ?: return false
        // 拷贝期间必须保持 snapshot 打开：打开状态下该条目不会被 LRU 清理或改写。
        return snapshot.use { snap ->
            runCatching {
                writeToGallery(context, snap.data.toFile().inputStream(), false, mime, name)
            }.onSuccess {
                AppLog.d(TAG, "保存命中图片缓存: $url")
            }.onFailure {
                AppLog.w(TAG, "读取图片缓存失败，改为下载: ${it.message}")
            }.isSuccess
        }
    }

    /**
     * 取 Coil 网络磁盘缓存里这个 URL 的条目；没有（未展示过 / 被 LRU 淘汰 / 用户清过缓存）
     * 或条目正在写入（尚未提交）时返回 null。
     *
     * Coil 3 的磁盘缓存 key 就是 URL 本身（`NetworkFetcher.diskCacheKey = diskCacheKey ?: url`），
     * 与 [net.paigu.chahua.ui.common.AuthAsyncImage] 展示时命中的是同一份条目，
     * 所以只要图片能显示出来，这里通常就能读到。
     *
     * 打开期间该条目不会被清理，调用方必须尽快 close。
     */
    private fun openCachedImage(url: String): DiskCache.Snapshot? =
        runCatching { AppGraph.imageLoader.diskCache?.openSnapshot(url) }.getOrNull()

    /** 统一的媒体请求：只对服务端同源地址附加鉴权头（与 Coil、视频缓存的取头判断一致）。 */
    private fun authRequest(url: String): Request =
        Request.Builder()
            .url(url)
            .get()
            .apply {
                AppGraph.session.authHeadersFor(url).forEach { (k, v) -> header(k, v) }
            }
            .build()

    /** 按系统版本把输入流写入相册（图片）或影片（视频）；输入流由写入方负责关闭。 */
    private fun writeToGallery(
        context: Context,
        input: InputStream,
        isVideo: Boolean,
        mime: String,
        name: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToMediaStore(context, input, isVideo, mime, name)
        } else {
            writeToLegacyStorage(context, input, isVideo, mime, name)
        }
    }

    private fun writeToMediaStore(
        context: Context,
        input: InputStream,
        isVideo: Boolean,
        mime: String,
        name: String,
    ) {
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (isVideo) "${Environment.DIRECTORY_MOVIES}/Chahua" else "${Environment.DIRECTORY_PICTURES}/Chahua",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IOException("MediaStore insert failed")
        try {
            val out = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("MediaStore open failed")
            out.use { output ->
                input.use { it.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun writeToLegacyStorage(
        context: Context,
        input: InputStream,
        isVideo: Boolean,
        mime: String,
        name: String,
    ) {
        val baseDir = Environment.getExternalStoragePublicDirectory(
            if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
        )
        val dir = File(baseDir, "Chahua").apply { mkdirs() }
        val file = File(dir, uniqueLegacyName(dir, name, isVideo, mime))
        FileOutputStream(file).use { out ->
            input.use { it.copyTo(out) }
        }
        context.sendBroadcast(
            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)),
        )
    }

    private fun writeFileToDownloads(
        context: Context,
        input: InputStream,
        mime: String,
        name: String,
    ) {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Chahua")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IOException("MediaStore insert failed")
        try {
            val out = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("MediaStore open failed")
            out.use { output ->
                input.use { it.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun writeFileToLegacyDownloads(
        context: Context,
        input: InputStream,
        mime: String,
        name: String,
    ) {
        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(baseDir, "Chahua").apply { mkdirs() }
        val file = File(dir, uniqueDownloadName(dir, name, mime))
        FileOutputStream(file).use { out ->
            input.use { it.copyTo(out) }
        }
        context.sendBroadcast(
            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)),
        )
    }

    private fun uniqueDownloadName(dir: File, name: String, mime: String): String {
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else downloadExtension(mime)
        var index = 1
        var candidate = "$stem($index)$ext"
        while (File(dir, candidate).exists()) {
            index++
            candidate = "$stem($index)$ext"
        }
        return candidate
    }

    private fun buildDownloadName(url: String, fileName: String?, mime: String): String {
        val raw = fileName
            ?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').substringBefore('?').ifBlank { null }
            ?: "file_${System.currentTimeMillis()}"
        val cleaned = raw
            .take(120)
            .map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim('_')
            .ifBlank { "file_${System.currentTimeMillis()}" }
        return if (cleaned.contains('.')) cleaned else "$cleaned${downloadExtension(mime)}"
    }

    private fun downloadExtension(mime: String): String = when (mime.lowercase()) {
        "application/pdf" -> ".pdf"
        "application/zip" -> ".zip"
        "application/x-zip-compressed" -> ".zip"
        "text/plain" -> ".txt"
        "application/json" -> ".json"
        "application/msword" -> ".doc"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx"
        "application/vnd.ms-excel" -> ".xls"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx"
        "application/vnd.ms-powerpoint" -> ".ppt"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> ".pptx"
        "audio/mpeg" -> ".mp3"
        else -> ".bin"
    }

    private fun uniqueLegacyName(dir: File, name: String, isVideo: Boolean, mime: String): String {
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else if (isVideo) ".mp4" else extensionForMime(mime)
        var index = 1
        var candidate = "$stem($index)$ext"
        while (File(dir, candidate).exists()) {
            index++
            candidate = "$stem($index)$ext"
        }
        return candidate
    }

    private fun buildName(url: String, fileName: String?, isVideo: Boolean, mime: String): String {
        val raw = fileName
            ?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').substringBefore('?').ifBlank { null }
            ?: if (isVideo) "video_${System.currentTimeMillis()}.mp4" else "image_${System.currentTimeMillis()}.jpg"
        val cleaned = raw
            .take(120)
            .map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim('_')
        return when {
            cleaned.contains('.') -> cleaned
            isVideo -> "$cleaned.mp4"
            else -> "$cleaned${extensionForMime(mime)}"
        }
    }

    private fun extensionForMime(mime: String): String = when (mime) {
        "image/png" -> ".png"
        "image/gif" -> ".gif"
        "image/webp" -> ".webp"
        "image/heic" -> ".heic"
        "image/heif" -> ".heif"
        else -> ".jpg"
    }
}
