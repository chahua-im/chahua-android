package net.paigu.chahua.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.paigu.chahua.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import android.os.Build

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

data class UpdateCheckResult(
    val available: Boolean,
    val latestVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
)

/**
 * 通过 GitHub Releases API 检测最新版本：
 * 版本号优先取 release name（v1.2.0 / 1.2.0），否则退回 tag；
 * 与本地 BuildConfig.VERSION_NAME 比较，只有高于本地版本才视为有更新。
 */
object UpdateChecker {

    private const val RELEASE_API_URL =
        "https://api.github.com/repos/chahua-im/chahua-android/releases/latest"

    private val json = ApiJson.instance
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val versionPattern = Regex("""(?i)\bv?(\d+(?:\.\d+)*)""")

    suspend fun checkLatest(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASE_API_URL)
            .header("User-Agent", "chahua-android/${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub HTTP ${response.code}")
            }
            val release = json.decodeFromString<GitHubRelease>(response.body?.string().orEmpty())
            val latestVersion = releaseVersion(release)
                ?: release.tagName.ifBlank { release.name }
            val available = compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0

            // 按设备 ABI 优先匹配对应的 apk（例如 arm64-v8a / armeabi-v7a / x86_64 / x86）
            val apkAssets = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            val apkAsset = pickAssetForDevice(apkAssets)

            UpdateCheckResult(
                available = available,
                latestVersion = latestVersion,
                releaseNotes = release.body.orEmpty().trim(),
                downloadUrl = apkAsset?.browserDownloadUrl ?: release.htmlUrl,
            )
        }
    }

    private fun pickAssetForDevice(apks: List<GitHubAsset>): GitHubAsset? {
        if (apks.isEmpty()) return null
        // 获取设备支持的 ABI 列表，按优先级排序（Android 提供）
        val supportedAbis = try {
            Build.SUPPORTED_ABIS.toList()
        } catch (t: Throwable) {
            emptyList()
        }
        // 优先按 ABI 名称在文件名中匹配（忽略大小写），按设备 ABI 顺序选择第一个匹配的 apk。
        for (abi in supportedAbis) {
            val match = apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }
            if (match != null) return match
            // 兼容常见别名（例如 aarch64 / arm64）
            val alias = when {
                abi.contains("arm64") -> listOf("arm64", "aarch64")
                abi.contains("armeabi") -> listOf("armeabi", "armeabi-v7a", "armeabi-v7a")
                abi.contains("x86_64") -> listOf("x86_64", "x64")
                abi.contains("x86") -> listOf("x86")
                else -> listOf(abi)
            }
            for (a in alias) {
                val match2 = apks.firstOrNull { it.name.contains(a, ignoreCase = true) }
                if (match2 != null) return match2
            }
        }
        // 未找到 ABI 匹配项时回退到第一个 apk
        return apks.firstOrNull()
    }

    private fun releaseVersion(release: GitHubRelease): String? {
        val candidate = listOf(release.name, release.tagName).firstOrNull { it.isNotBlank() }
            ?: return null
        return versionPattern.find(candidate)?.value ?: candidate
    }

    /** 正数表示 a 比 b 新；任一侧无法解析版本号时返回 0，避免误报更新。 */
    private fun compareVersions(a: String, b: String): Int {
        val av = versionNumbers(a) ?: return 0
        val bv = versionNumbers(b) ?: return 0
        val size = maxOf(av.size, bv.size)
        for (i in 0 until size) {
            val x = av.getOrElse(i) { 0 }
            val y = bv.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun versionNumbers(version: String): List<Int>? {
        val match = versionPattern.find(version) ?: return null
        return match.groupValues[1].split('.').mapNotNull { it.toIntOrNull() }
    }
}
