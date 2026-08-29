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
            val apkAsset = release.assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true)
            }
            UpdateCheckResult(
                available = available,
                latestVersion = latestVersion,
                releaseNotes = release.body.orEmpty().trim(),
                downloadUrl = apkAsset?.browserDownloadUrl ?: release.htmlUrl,
            )
        }
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
