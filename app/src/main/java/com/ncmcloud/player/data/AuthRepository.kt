package com.ncmcloud.player.data

import com.ncmcloud.player.core.api.NeteaseApiService
import com.ncmcloud.player.core.api.QrCheckRequest
import com.ncmcloud.player.core.api.QrCheckResponse
import com.ncmcloud.player.core.api.QrKeyResponse
import com.ncmcloud.player.core.auth.UserPreferences
import com.ncmcloud.player.core.auth.UserProfile

private const val QR_LOGIN_URL_PREFIX = "https://music.163.com/login?codekey="

class AuthRepository(
    private val apiService: NeteaseApiService,
    private val userPreferences: UserPreferences,
) {
    val cookies get() = userPreferences.cookies
    val userProfile get() = userPreferences.userProfile

    suspend fun getQrKey(): QrKeyResponse = apiService.getQrKey()

    suspend fun checkQrStatus(key: String): QrCheckResponse {
        val response = apiService.checkQrStatus(QrCheckRequest(key = key))
        val body = response.body()
            ?: throw IllegalStateException("二维码状态响应为空 httpCode=${response.code()}")
        val cookies = response.headers().values("Set-Cookie")
            .map { it.substringBefore(";").trim() }
            .filter { it.contains("=") }
            .joinToString("; ")
            .takeIf { it.isNotEmpty() }
        return body.copy(cookies = cookies)
    }

    fun qrLoginUrl(key: String): String = QR_LOGIN_URL_PREFIX + key

    suspend fun loginWithCookie(rawCookie: String) {
        val cookie = sanitizeCookie(rawCookie)
        if (!cookie.contains("MUSIC_U")) throw IllegalArgumentException("Cookie 必须包含 MUSIC_U")
        userPreferences.saveCookies(cookie)
        fetchAndSaveProfile()
    }

    suspend fun loginWithQrCookies(cookies: String) {
        if (!cookies.contains("MUSIC_U")) throw IllegalStateException("二维码登录未返回 MUSIC_U")
        userPreferences.saveCookies(cookies)
        fetchAndSaveProfile()
    }

    suspend fun logout() {
        runCatching { apiService.logoutApi() }
        userPreferences.clearUserProfile()
    }

    private suspend fun fetchAndSaveProfile() {
        val account = apiService.getAccountInfo()
        if (account.code != 200) throw IllegalStateException("账号信息接口返回 code=${account.code}")
        val profile = account.profile
        if (profile != null) {
            userPreferences.saveUserProfile(
                UserProfile(
                    uid = profile.userId,
                    nickname = profile.nickname,
                    avatarUrl = profile.avatarUrl,
                )
            )
        }
    }

    private fun sanitizeCookie(raw: String): String {
        return raw.trim().trim('"').replace("\n", "").replace("\r", "")
    }
}
