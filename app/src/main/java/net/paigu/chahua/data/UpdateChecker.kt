package net.paigu.chahua.data

import android.os.Build
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.paigu.chahua.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request

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
 * 版本号优先从 release name 提取，失败再回退到 tag；
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

    // 只捕获数字版本号（不含 v 前缀），如 "v1.2.0" / "1.2.0" / "release-1.2.3"
    private val versionPattern = Regex("""(?i)\bv?(\d+(?:\.\d+)*)""")

    suspend fun checkLatest(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASE_API_URL)
            .header("User-Agent", "chahua-android/${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val remaining = response.header("X-RateLimit-Remaining")
                val reset = response.header("X-RateLimit-Reset")
                if ((response.code == 403 || response.code == 429) && remaining == "0") {
                    throw IOException("GitHub API rate limited (reset=$reset)")
                }
                throw IOException("GitHub HTTP ${response.code}")
            }

            val text = response.body?.string().orEmpty()
            val release = try {
                json.decodeFromString<GitHubRelease>(text)
            } catch (t: Throwable) {
                throw IOException("Malformed GitHub response", t)
            }

            val latestVersion = releaseVersion(release)
                ?: release.tagName.ifBlank { release.name }
            val available = compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0

            // 按设备 ABI 优先匹配对应的 apk（arm64-v8a / armeabi-v7a / x86_64 / x86）
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

    /**
     * 按设备 ABI 优先匹配对应的 apk。
     * 使用「非 [a-z0-9_]」作为 ABI 名边界，避免 x86 误匹配 x86_64。
     */
    private fun pickAssetForDevice(apks: List<GitHubAsset>): GitHubAsset? {
        if (apks.isEmpty()) return null

        val supportedAbis = runCatching { Build.SUPPORTED_ABIS.toList() }
            .getOrDefault(emptyList())

        for (abi in supportedAbis) {
            apks.firstOrNull { assetMatchesAbi(it.name, abi) }?.let { return it }

            // 兼容构建产物里常见的别名写法
            val aliases = when {
                abi.contains("arm64")   -> listOf("arm64-v8a", "aarch64")
                abi.contains("armeabi") -> listOf("armeabi-v7a", "armeabi")
                abi.contains("x86_64")  -> listOf("x86_64", "x64")
                abi.contains("x86")     -> listOf("x86")
                else -> listOf(abi)
            }
            for (a in aliases) {
                apks.firstOrNull { assetMatchesAbi(it.name, a) }?.let { return it }
            }
        }

        // 未匹配到具体 ABI 时回退到第一个 apk
        return apks.firstOrNull()
    }

    /**
     * 判断 APK 文件名里是否含有指定的 ABI 段。
     * 允许的分隔符是：字符串开头/结尾、`-`、`.` 等非 [a-z0-9_] 字符；
     * 因此 "x86" 不会命中 "x86_64"，但 "arm64" 可以命中 "arm64-v8a"。
     */
    private fun assetMatchesAbi(name: String, abi: String): Boolean {
        val regex = Regex(
            "(?i)(?:^|[^a-z0-9_])${Regex.escape(abi)}(?:[^a-z0-9_]|$)"
        )
        return regex.containsMatchIn(name)
    }

    /**
     * 依次从 release.name、release.tagName 中提取版本号，
     * 返回第一个成功提取的结果；都失败返回 null。
     */
    private fun releaseVersion(release: GitHubRelease): String? {
        for (raw in listOf(release.name, release.tagName)) {
            if (raw.isBlank()) continue
            val v = versionPattern.find(raw)?.groupValues?.getOrNull(1)
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    /** 正数表示 a 比 b 新；任一侧无法解析版本号时返回 0，避免误报更新。 */
    private fun compareVersions(a: String, b: String): Int {
        val av = versionNumbers(a) ?: return 0
        val bv = versionNumbers(b) ?: return 0

        // 明显不是版本号的情况：例如 release 名是 "2024-01-15" 会被解析成 [2024]，
        // 段数与正常版本号差异过大时，直接判定为不可比
        if (av.size == 1 && bv.size >= 2) return 0
        if (bv.size == 1 && av.size >= 2) return 0

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
