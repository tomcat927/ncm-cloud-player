package com.ncmcloud.player.core.log

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class RemoteLogConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val username: String,
    val targetPath: String,
    val lastUploadAt: String?,
    val lastUploadPath: String?,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && username.isNotBlank() && targetPath.isNotBlank()
}

data class RemoteLogUploadResult(val remotePath: String, val bytes: Int)

class RemoteLogService(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("remote_log_enabled")
        val BASE_URL = stringPreferencesKey("remote_log_base_url")
        val USERNAME = stringPreferencesKey("remote_log_username")
        val TARGET_PATH = stringPreferencesKey("remote_log_target_path")
        val PASSWORD = stringPreferencesKey("remote_log_password")
        val TOKEN = stringPreferencesKey("remote_log_token")
        val INSTALL_ID = stringPreferencesKey("remote_log_install_id")
        val LAST_UPLOAD_AT = stringPreferencesKey("remote_log_last_upload_at")
        val LAST_UPLOAD_PATH = stringPreferencesKey("remote_log_last_upload_path")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()
    private val octetMediaType = "application/octet-stream".toMediaType()
    private val salt = "https://github.com/alist-org/alist"

    suspend fun loadConfig(): RemoteLogConfig {
        val prefs = dataStore.data.first()
        return RemoteLogConfig(
            enabled = prefs[Keys.ENABLED] ?: false,
            baseUrl = prefs[Keys.BASE_URL] ?: "",
            username = prefs[Keys.USERNAME] ?: "",
            targetPath = prefs[Keys.TARGET_PATH] ?: "/ncm-cloud-player/logs",
            lastUploadAt = prefs[Keys.LAST_UPLOAD_AT],
            lastUploadPath = prefs[Keys.LAST_UPLOAD_PATH],
        )
    }

    suspend fun saveConfig(
        enabled: Boolean,
        baseUrl: String,
        username: String,
        password: String,
        targetPath: String,
    ) {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val normalizedPath = normalizeTargetPath(targetPath)
        dataStore.edit { prefs ->
            prefs[Keys.ENABLED] = enabled
            prefs[Keys.BASE_URL] = normalizedUrl
            prefs[Keys.USERNAME] = username.trim()
            prefs[Keys.TARGET_PATH] = normalizedPath
            if (password.isNotEmpty()) prefs[Keys.PASSWORD] = password
            prefs.remove(Keys.TOKEN)
        }
    }

    suspend fun testConnection(baseUrl: String, username: String, password: String): Boolean {
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        val savedPassword = dataStore.data.first()[Keys.PASSWORD] ?: ""
        val effectivePassword = if (password.isNotEmpty()) password else savedPassword
        if (username.isBlank() || effectivePassword.isEmpty()) {
            throw IllegalArgumentException("请填写 OpenList 用户名和密码")
        }
        login(normalizedUrl, username.trim(), effectivePassword)
        return true
    }

    suspend fun uploadDiagnosticLog(): RemoteLogUploadResult {
        val config = loadConfig()
        if (!config.isConfigured) throw IllegalStateException("请先配置 OpenList 远程日志")
        val password = dataStore.data.first()[Keys.PASSWORD] ?: ""
        var token = dataStore.data.first()[Keys.TOKEN] ?: ""
        if (token.isEmpty()) {
            if (password.isEmpty()) throw IllegalStateException("OpenList 密码未保存")
            token = login(config.baseUrl, config.username, password)
            saveToken(token)
        }

        val snapshot = buildRedactedSnapshot()
        val bytes = snapshot.toByteArray(Charsets.UTF_8)
        if (bytes.size > 2 * 1024 * 1024) throw IllegalStateException("日志超过 2MB，请先清理本地日志")

        val installId = loadInstallId()
        val now = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val fileName = "diagnostic-$now.txt"
        val remoteDir = "${config.targetPath}/install-$installId"
        val remotePath = "$remoteDir/$fileName"

        try {
            uploadWithToken(config.baseUrl, token, remoteDir, remotePath, bytes)
        } catch (e: AuthException) {
            if (password.isEmpty()) throw e
            token = login(config.baseUrl, config.username, password)
            saveToken(token)
            uploadWithToken(config.baseUrl, token, remoteDir, remotePath, bytes)
        }

        dataStore.edit { prefs ->
            prefs[Keys.LAST_UPLOAD_AT] = now
            prefs[Keys.LAST_UPLOAD_PATH] = remotePath
        }
        return RemoteLogUploadResult(remotePath, bytes.size)
    }

    private fun buildRedactedSnapshot(): String {
        val logs = AppLogger.getLogText()
        val redacted = redact(logs)
        return buildString {
            appendLine("ncm-cloud-player diagnostic log")
            appendLine("Generated: ${Date()}")
            appendLine("Privacy: redacted snapshot; credentials and personal content removed")
            appendLine()
            append(redacted)
        }
    }

    private fun redact(input: String): String {
        var value = input
        value = Regex("(?i)(authorization|cookie|set-cookie|password|access[_-]?token|token)\\s*[:=]\\s*[^\\s,;]+")
            .replace(value, "$1=[REDACTED]")
        value = Regex("(?i)bearer\\s+[a-z0-9._~+/-]+=*").replace(value, "Bearer [REDACTED]")
        value = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b").replace(value, "[EMAIL]")
        value = Regex("(?i)([a-z]:\\\\|/storage/|/data/)[^\\r\\n\\s]+").replace(value, "[LOCAL_PATH]")
        return value
    }

    private fun login(baseUrl: String, username: String, password: String): String {
        val digest = sha256("$password-$salt")
        val body = JSONObject()
            .put("username", username)
            .put("password", digest)
            .put("otp_code", "")
            .toString()
        val request = Request.Builder()
            .url("$baseUrl/api/auth/login/hash")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val payload = decodeResponse(response.body?.string().orEmpty())
            val token = payload.optJSONObject("data")?.optString("token").orEmpty()
            if (payload.optInt("code") != 200 || token.isEmpty()) {
                throw IllegalStateException(payload.optString("message", "OpenList 登录失败"))
            }
            return token
        }
    }

    private fun ensureDirectory(baseUrl: String, token: String, path: String) {
        val body = JSONObject().put("path", path).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/fs/mkdir")
            .header("Authorization", token)
            .post(body.toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val payload = decodeResponse(response.body?.string().orEmpty())
            val message = payload.optString("message").lowercase()
            if (payload.optInt("code") == 401) throw AuthException(payload.optString("message"))
            if (payload.optInt("code") != 200 && !message.contains("exist")) {
                throw IllegalStateException(payload.optString("message", "无法创建远程目录"))
            }
        }
    }

    private fun upload(baseUrl: String, token: String, remotePath: String, bytes: ByteArray) {
        val request = Request.Builder()
            .url("$baseUrl/api/fs/put")
            .header("Authorization", token)
            .header("File-Path", java.net.URLEncoder.encode(remotePath, "UTF-8"))
            .header("Content-Type", "application/octet-stream")
            .put(bytes.toRequestBody(octetMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val payload = decodeResponse(response.body?.string().orEmpty())
            if (payload.optInt("code") == 401) throw AuthException(payload.optString("message"))
            if (payload.optInt("code") != 200) {
                throw IllegalStateException(payload.optString("message", "上传诊断日志失败"))
            }
        }
    }

    private fun uploadWithToken(baseUrl: String, token: String, remoteDir: String, remotePath: String, bytes: ByteArray) {
        ensureDirectory(baseUrl, token, remoteDir)
        upload(baseUrl, token, remotePath, bytes)
    }

    private fun decodeResponse(body: String): JSONObject =
        runCatching { JSONObject(body) }.getOrElse { throw IllegalStateException("OpenList 请求失败") }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim().replace(Regex("/+$"), "")
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw IllegalArgumentException("OpenList 地址必须以 http:// 或 https:// 开头")
        }
        return trimmed
    }

    private fun normalizeTargetPath(value: String): String {
        var path = value.trim().ifEmpty { "/ncm-cloud-player/logs" }
        if (!path.startsWith("/")) path = "/$path"
        path = path.replace(Regex("/+"), "/")
        if (path == "/") throw IllegalArgumentException("远程日志不能上传到根目录")
        return path.replace(Regex("/+$"), "")
    }

    private suspend fun saveToken(token: String) {
        dataStore.edit { it[Keys.TOKEN] = token }
    }

    private suspend fun loadInstallId(): String {
        val prefs = dataStore.data.first()
        val existing = prefs[Keys.INSTALL_ID]
        if (!existing.isNullOrEmpty()) return existing
        val random = SecureRandom()
        val id = (1..6).joinToString("") { random.nextInt(256).toString(16).padStart(2, '0') }
        dataStore.edit { it[Keys.INSTALL_ID] = id }
        return id
    }

    private class AuthException(message: String) : Exception(message)
}

