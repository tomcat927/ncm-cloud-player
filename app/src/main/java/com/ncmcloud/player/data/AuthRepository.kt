package com.ncmcloud.player.data

import com.ncmcloud.player.core.api.CaptchaSentRequest
import com.ncmcloud.player.core.api.CaptchaSentResponse
import com.ncmcloud.player.core.api.LoginCellphoneRequest
import com.ncmcloud.player.core.api.LoginCellphoneResponse
import com.ncmcloud.player.core.api.NeteaseApiService
import com.ncmcloud.player.core.api.QrCheckRequest
import com.ncmcloud.player.core.api.QrCheckResponse
import com.ncmcloud.player.core.api.QrKeyResponse
import com.ncmcloud.player.core.api.QrLoginConfirmRequest
import com.ncmcloud.player.core.api.QrLoginConfirmResponse
import com.ncmcloud.player.core.auth.UserPreferences
import com.ncmcloud.player.core.auth.UserProfile
import com.ncmcloud.player.core.network.DeviceInfo
import kotlinx.coroutines.flow.first

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
        val cookies = parseSetCookie(response)
        return body.copy(cookies = cookies)
    }

    fun qrLoginUrl(key: String): String = QR_LOGIN_URL_PREFIX + key

    // 本 App 作为已登录扫码方，确认外部（无痕网页等）二维码的登录请求。
    // 走官方确认页同款 server/login 接口，时序对齐官方页：先 type=1 上报"已扫描"（801→802），
    // 再 type=2 确认授权（802→803）——实测跳过 type=1 直接 type=2 会返回「授权失败」。
    // [clientTraceId] 优先取二维码 URL 里的 login_traceId，没有则传空串（官方页同款行为）。
    suspend fun confirmQrLogin(key: String, clientTraceId: String): QrLoginConfirmResponse {
        val userid = currentUid()
        // 官方页对 type=1 的结果不阻断，失败也继续发起 type=2
        runCatching {
            apiService.confirmQrLogin(
                QrLoginConfirmRequest(key = key, type = 1, userid = userid, clientTraceId = clientTraceId)
            )
        }
        val resp = apiService.confirmQrLogin(
            QrLoginConfirmRequest(
                key = key,
                type = 2,
                userid = userid,
                clientTraceId = clientTraceId,
                brand = DeviceInfo.brand,
                device = DeviceInfo.model,
            )
        )
        if (!resp.isSuccess) {
            throw IllegalStateException(resp.message ?: "扫码确认失败 code=${resp.code}")
        }
        return resp
    }

    // 扫码方 uid：优先取本地缓存的资料，缺失时现拉一次账号信息兜底
    private suspend fun currentUid(): Long {
        userPreferences.userProfile.first()?.let { return it.uid }
        val account = apiService.getAccountInfo()
        if (account.code == 200) return account.account?.id ?: 0
        return 0
    }

    suspend fun sendCaptcha(phone: String, ctcode: String = "86"): CaptchaSentResponse {
        val resp = apiService.sendCaptcha(CaptchaSentRequest(cellphone = phone, ctcode = ctcode))
        if (!resp.isSuccess) throw IllegalStateException("验证码发送失败 code=${resp.code} ${resp.description.orEmpty()}")
        return resp
    }

    suspend fun loginWithCaptcha(phone: String, captcha: String, ctcode: String = "86") {
        val response = apiService.loginCellphone(
            LoginCellphoneRequest(phone = phone, captcha = captcha, countrycode = ctcode)
        )
        val body = response.body()
            ?: throw IllegalStateException("登录响应为空 httpCode=${response.code()}")
        if (body.code != 200) throw IllegalStateException("手机登录失败 code=${body.code}")
        val cookies = parseSetCookie(response)
            ?: throw IllegalStateException("登录成功但未获取到 Cookie")
        userPreferences.saveCookies(cookies)
        fetchAndSaveProfile()
    }

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

    // 拉取并保存账号资料（含 uid）。登录时资料拉取失败会留下"有 cookie 无 uid"的状态，
    // 需要 uid 的功能（如服务器端歌单）可调用此方法现补
    suspend fun refreshProfile() = fetchAndSaveProfile()

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

    private fun parseSetCookie(response: retrofit2.Response<*>): String? =
        response.headers().values("Set-Cookie")
            .map { it.substringBefore(";").trim() }
            .filter { it.contains("=") }
            .joinToString("; ")
            .takeIf { it.contains("MUSIC_U") }

    private fun sanitizeCookie(raw: String): String {
        return raw.trim().trim('"').replace("\n", "").replace("\r", "")
    }
}
