package com.ncmcloud.player.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ncmcloud.player.core.log.AppLogger
import android.os.Build
import com.ncmcloud.player.core.preferences.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val TAG = "UpdateService"
private const val OWNER = "tomcat927"
private const val REPO = "ncm-cloud-player"
private const val PROXY_PREFIX = "https://gh-proxy.com/"

data class UpdateInfo(
    val tagName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val fallbackDownloadUrl: String,
    val checksumUrl: String,
    val fallbackChecksumUrl: String,
    val releaseUrl: String,
    val releaseNotes: String?,
)

class UpdateService(
    private val context: Context,
    private val settingsPreferences: SettingsPreferences,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val manifestUrls = listOf(
        "${PROXY_PREFIX}https://github.com/$OWNER/$REPO/releases/latest/download/latest.json",
        "https://github.com/$OWNER/$REPO/releases/latest/download/latest.json",
    )

    private val apiUrl = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val preferMirror = settingsPreferences.updatePreferMirror.first()
        val currentVersionCode = currentVersionCode()
        val info = checkFromManifest(preferMirror) ?: checkFromGitHubApi()
        if (info == null) {
            AppLogger.i(TAG, "未获取到更新信息")
            null
        } else {
            AppLogger.i(TAG, "current=$currentVersionCode latest=${info.versionCode}")
            if (info.versionCode > currentVersionCode) info else null
        }
    }

    private fun currentVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    private suspend fun checkFromManifest(preferMirror: Boolean): UpdateInfo? {
        // 关闭镜像时清单也走直连；镜像开启时镜像源优先、直连兜底
        val urls = if (preferMirror) manifestUrls else manifestUrls.reversed()
        for (url in urls) {
            val body = readText(url) ?: continue
            val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
            val versionCode = json.optLong("version_code", -1L)
            val apk = json.optString("apk")
            val githubApk = json.optString("github_apk")
            if (versionCode <= 0 || apk.isEmpty() || githubApk.isEmpty()) continue
            // 镜像关闭时主备互换，下载与校验全部走 GitHub 直连
            val (primary, secondary) = if (preferMirror) apk to githubApk else githubApk to apk
            val (primaryChecksum, secondaryChecksum) = if (preferMirror) {
                json.optString("apk_sha256") to json.optString("github_apk_sha256")
            } else {
                json.optString("github_apk_sha256") to json.optString("apk_sha256")
            }
            return UpdateInfo(
                tagName = json.optString("tag_name"),
                versionCode = versionCode,
                downloadUrl = primary,
                fallbackDownloadUrl = secondary,
                checksumUrl = primaryChecksum,
                fallbackChecksumUrl = secondaryChecksum,
                releaseUrl = json.optString("release_url"),
                releaseNotes = json.optString("release_notes", null).takeIf { it.isNotBlank() },
            )
        }
        return null
    }

    private suspend fun checkFromGitHubApi(): UpdateInfo? {
        val preferMirror = settingsPreferences.updatePreferMirror.first()
        val body = readText(apiUrl, mapOf("Accept" to "application/vnd.github+json", "User-Agent" to REPO))
            ?: return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val tagName = json.optString("tag_name")
        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl = ""
        var checksumUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk")) apkUrl = url
            else if (name.endsWith(".apk.sha256")) checksumUrl = url
        }
        if (apkUrl.isEmpty()) return null
        val versionCode = versionCodeFromTag(tagName) ?: return null
        val (primaryDownload, fallbackDownload) = if (preferMirror) {
            "${PROXY_PREFIX}$apkUrl" to apkUrl
        } else {
            apkUrl to "${PROXY_PREFIX}$apkUrl"
        }
        val (primaryChecksum, fallbackChecksum) = if (preferMirror) {
            "${PROXY_PREFIX}$checksumUrl" to checksumUrl
        } else {
            checksumUrl to "${PROXY_PREFIX}$checksumUrl"
        }
        return UpdateInfo(
            tagName = tagName,
            versionCode = versionCode,
            downloadUrl = primaryDownload,
            fallbackDownloadUrl = fallbackDownload,
            checksumUrl = primaryChecksum,
            fallbackChecksumUrl = fallbackChecksum,
            releaseUrl = json.optString("html_url"),
            releaseNotes = json.optString("body", null).takeIf { it.isNotBlank() },
        )
    }

    suspend fun downloadApk(info: UpdateInfo, onProgress: suspend (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "开始下载 APK: ${info.tagName}, primary=${info.downloadUrl}, fallback=${info.fallbackDownloadUrl}")
        val dir = File(context.cacheDir, "apk_updates").apply { mkdirs() }
        val file = File(dir, "ncm-cloud-player-update.apk")
        if (file.exists()) file.delete()

        downloadTo(info.downloadUrl, file, onProgress) ?: downloadTo(info.fallbackDownloadUrl, file, onProgress)
            ?: throw IllegalStateException("APK 下载失败")

        val preferMirror = settingsPreferences.updatePreferMirror.first()
        // 完整性：镜像下载的 APK 优先用 GitHub 直连的校验和验证（防镜像篡改），直连下载则用直连校验和
        val expected = if (preferMirror) {
            readChecksum(info.fallbackChecksumUrl) ?: readChecksum(info.checksumUrl)
        } else {
            readChecksum(info.checksumUrl) ?: readChecksum(info.fallbackChecksumUrl)
        }
        AppLogger.i(TAG, "APK 下载结束: file=${file.absolutePath}, size=${file.length()}, checksumUrl=${info.checksumUrl}")
        if (expected != null) {
            val actual = sha256(file)
            AppLogger.i(TAG, "APK SHA-256: expected=$expected, actual=$actual")
            if (!actual.equals(expected, ignoreCase = true)) {
                throw IllegalStateException("SHA-256 校验失败")
            }
        } else {
            AppLogger.w(TAG, "未获取到 SHA-256 校验值，跳过校验")
        }
        file
    }

    fun createInstallIntent(file: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        AppLogger.i(TAG, "创建安装 Intent: file=${file.absolutePath}, size=${file.length()}, uri=$uri")
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private suspend fun downloadTo(url: String, target: File, onProgress: suspend (Float) -> Unit): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                AppLogger.i(TAG, "下载响应: code=${response.code}, url=$url, contentLength=${response.body?.contentLength() ?: -1}")
                if (!response.isSuccessful) {
                    AppLogger.w(TAG, "下载响应失败: code=${response.code}, url=$url")
                    return false
                }
                val body = response.body ?: return false
                val total = body.contentLength()
                val input = body.byteStream()
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var received = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        received += read
                        if (total > 0) onProgress((received.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            val exists = target.exists() && target.length() > 0
            AppLogger.i(TAG, "下载写入完成: url=$url, file=${target.absolutePath}, size=${target.length()}, ok=$exists")
            exists
        } catch (e: Exception) {
            AppLogger.e(TAG, "下载失败: $url", e)
            false
        }
    }

    private suspend fun readText(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "请求失败: $url", e)
            null
        }
    }

    private suspend fun readChecksum(url: String): String? {
        val text = readText(url) ?: run {
            AppLogger.w(TAG, "校验文件下载失败: $url")
            return null
        }
        val value = text.trim().split(Regex("\\s+")).firstOrNull()
        return value?.takeIf { it.length == 64 }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun versionCodeFromTag(tag: String): Long? {
        val match = Regex("-(\\d{8})(\\d{2})(\\d{2})(\\d{2})$").find(tag) ?: return null
        val (date, hour, minute, second) = match.destructured
        val year = date.substring(0, 4).toInt()
        val month = date.substring(4, 6).toInt()
        val day = date.substring(6, 8).toInt()
        val beijing = java.time.LocalDateTime.of(year, month, day, hour.toInt(), minute.toInt(), second.toInt())
        val epoch = beijing.atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond()
        return epoch
    }
}








