package com.ncmcloud.player.core.api

import com.ncmcloud.player.core.model.EmptyBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// 账号鉴权相关的网易云 Retrofit 接口定义（跨业务域共用，由 core/auth 消费）。
interface NeteaseApiService {

    // 获取当前登录账号信息
    @POST("/eapi/nuser/account/get")
    suspend fun getAccountInfo(
        @Body body: EmptyBody = EmptyBody()
    ): AccountInfoResponse

    // 退出登录
    @POST("/eapi/logout")
    suspend fun logoutApi(
        @Body body: EmptyBody = EmptyBody()
    ): LogoutApiResponse

    // 获取二维码登录 key
    @POST("/weapi/login/qrcode/unikey")
    suspend fun getQrKey(
        @Body body: QrKeyRequest = QrKeyRequest()
    ): QrKeyResponse

    // 轮询二维码扫码状态；登录成功时 Cookie 经由 Set-Cookie 响应头下发，故需拿到原始 Response 读取头部
    @POST("/weapi/login/qrcode/client/login")
    suspend fun checkQrStatus(
        @Body body: QrCheckRequest
    ): Response<QrCheckResponse>

    // 扫码确认：本 App（已登录）作为扫码方，确认外部（如无痕网页）二维码的登录请求。
    // 2026-10 真机验证：旧路径 /eapi/login/qrcode/confirm 已不存在（服务端返回"接口未找到"）。
    // 现行端点取自官方扫码确认页（music.163.com/st/platform/scanlogin）网络层：
    // api/login/qrcode/server/login，type=1 上报已扫描 / type=2 确认授权 / type=3 拒绝，
    // userid 为扫码方账号 uid，clientTraceId 为本次登录事件链路 id。
    @POST("/eapi/login/qrcode/server/login")
    suspend fun confirmQrLogin(
        @Body body: QrLoginConfirmRequest
    ): QrLoginConfirmResponse

        // 发送短信验证码 v1（eapi）
    @POST("/eapi/middle/captcha/sent/v1")
    suspend fun sendCaptcha(
        @Body body: CaptchaSentRequest
    ): CaptchaSentResponse

        // 手机号验证码登录（eapi，与验证码 v1 同域共享会话）
    @POST("/eapi/w/login/cellphone")
    suspend fun loginCellphone(
        @Body body: LoginCellphoneRequest
    ): Response<LoginCellphoneResponse>
}

// ======================= 用户账户信息 =======================

@Serializable
data class AccountInfoResponse(
    val code: Int = 0,
    val account: Account? = null,
    val profile: UserProfile? = null
)

@Serializable
data class Account(
    val id: Long = 0,
    val userName: String = "",
    val type: Int = 0,
    val status: Int = 0,
)

@Serializable
data class UserProfile(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = "",
    @SerialName("backgroundUrl")
    val backgroundUrl: String = "",
    val signature: String = "",
)

@Serializable
data class LogoutApiResponse(
    val code: Int = 0
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= 二维码登录 =======================

@Serializable
data class QrKeyRequest(val type: Int = 1)

@Serializable
data class QrKeyResponse(
    val code: Int = 0,
    val unikey: String = ""
)

@Serializable
data class QrCheckRequest(
    val key: String,
    val type: Int = 1
)

@Serializable
data class QrCheckResponse(
    val code: Int = 0,
    val message: String? = null,
    @Transient
    val cookies: String? = null
)

// ======================= 扫码确认（本 App 作为已登录扫码方） =======================

// 对齐官方扫码确认页的实际请求字段；brand/device/envType 在 type=2 时随请求上报
@Serializable
data class QrLoginConfirmRequest(
    val key: String,
    val type: Int = 2,
    val userid: Long = 0,
    val clientTraceId: String = "",
    val isEd: Boolean = true,
    val brand: String = "",
    val device: String = "",
    val envType: String = "",
)

@Serializable
data class QrLoginConfirmResponse(
    val code: Int = 0,
    val message: String? = null,
    val redirectUrl: String? = null,
) {
    val isSuccess: Boolean get() = code == 200
}

// ======================= 短信验证码登录 =======================

@Serializable
data class CaptchaSentRequest(
    val ctcode: String = "86",
    val secrete: String = "music_middleuser_pclogin",
    val cellphone: String,
    val scene: String = "0",
)

@Serializable
data class CaptchaSentResponse(
    val code: Int = 0,
    val description: String? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class LoginCellphoneRequest(
    val type: String = "1",
    val https: String = "true",
    val phone: String,
    val countrycode: String = "86",
    val captcha: String,
    val remember: String = "true",
    val secureCaptcha: String = ""
)

@Serializable
data class LoginCellphoneResponse(
    val code: Int = 0,
    val profile: UserProfile? = null
)




