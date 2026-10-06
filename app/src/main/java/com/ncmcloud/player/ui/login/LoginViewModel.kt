package com.ncmcloud.player.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.ncmcloud.player.core.log.AppLogger
import com.ncmcloud.player.data.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "LoginViewModel"
private const val QR_SIZE_PX = 480
private const val POLL_INTERVAL_MS = 2500L
private const val MAX_CONSECUTIVE_POLL_FAILURES = 5

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data object Success : LoginState
    data class Error(val message: String) : LoginState
}

sealed interface QrLoginState {
    data object Idle : QrLoginState
    data object Loading : QrLoginState
    data class WaitingScan(val qrMatrix: BitMatrix) : QrLoginState
    data class WaitingConfirm(val qrMatrix: BitMatrix) : QrLoginState
    data object Expired : QrLoginState
    data class Error(val message: String) : QrLoginState
}

sealed interface SmsLoginState {
    data object Idle : SmsLoginState
    data object Sending : SmsLoginState
    data object CodeSent : SmsLoginState
    data object LoggingIn : SmsLoginState
    data class Error(val message: String) : SmsLoginState
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _qrState = MutableStateFlow<QrLoginState>(QrLoginState.Idle)
    val qrState: StateFlow<QrLoginState> = _qrState.asStateFlow()

    private val _smsState = MutableStateFlow<SmsLoginState>(SmsLoginState.Idle)
    val smsState: StateFlow<SmsLoginState> = _smsState.asStateFlow()

    private var pollJob: Job? = null

    fun login(cookie: String) {
        if (cookie.isBlank()) {
            _state.value = LoginState.Error("请粘贴 MUSIC_U Cookie")
            return
        }
        viewModelScope.launch {
            _state.value = LoginState.Loading
            _state.value = runCatching { authRepository.loginWithCookie(cookie) }
                .fold(
                    onSuccess = { LoginState.Success },
                    onFailure = { LoginState.Error(it.message ?: "登录失败") },
                )
        }
    }

    fun startQrLogin() {
        stopQrPolling()
        _qrState.value = QrLoginState.Loading
        viewModelScope.launch {
            val key = runCatching { authRepository.getQrKey() }
                .getOrNull()?.unikey
            if (key.isNullOrEmpty()) {
                _qrState.value = QrLoginState.Error("二维码生成失败，请重试")
                return@launch
            }
            val matrix = try {
                withContext(Dispatchers.Default) { generateQrMatrix(authRepository.qrLoginUrl(key)) }
            } catch (e: Exception) {
                AppLogger.e(TAG, "二维码点阵生成失败", e)
                _qrState.value = QrLoginState.Error("二维码生成失败，请重试")
                return@launch
            }
            _qrState.value = QrLoginState.WaitingScan(matrix)
            pollQrStatus(key, matrix)
        }
    }

    private fun pollQrStatus(key: String, matrix: BitMatrix) {
        pollJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (true) {
                delay(POLL_INTERVAL_MS)
                val response = runCatching { authRepository.checkQrStatus(key) }.getOrNull()
                if (response == null) {
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
                        _qrState.value = QrLoginState.Error("网络异常，请重试")
                        return@launch
                    }
                    continue
                }
                consecutiveFailures = 0
                when (response.code) {
                    800 -> {
                        _qrState.value = QrLoginState.Expired
                        return@launch
                    }
                    801 -> _qrState.value = QrLoginState.WaitingScan(matrix)
                    802 -> _qrState.value = QrLoginState.WaitingConfirm(matrix)
                    803 -> {
                        val cookies = response.cookies
                        if (cookies.isNullOrEmpty()) {
                            _qrState.value = QrLoginState.Error("登录成功但未获取到会话，请重试")
                        } else {
                            runCatching { authRepository.loginWithQrCookies(cookies) }
                                .onSuccess { _state.value = LoginState.Success }
                                .onFailure { _qrState.value = QrLoginState.Error(it.message ?: "登录失败") }
                        }
                        return@launch
                    }
                    else -> {
                        _qrState.value = QrLoginState.Error(response.message ?: "扫码异常(${response.code})")
                        return@launch
                    }
                }
            }
        }
    }

    fun stopQrPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun resetQrState() {
        stopQrPolling()
        _qrState.value = QrLoginState.Idle
    }

    fun sendCaptcha(phone: String) {
        if (phone.isBlank()) {
            _smsState.value = SmsLoginState.Error("请输入手机号")
            return
        }
        viewModelScope.launch {
            _smsState.value = SmsLoginState.Sending
            AppLogger.i(TAG, "验证码发送: ${phone.masked()}")
            _smsState.value = runCatching { authRepository.sendCaptcha(phone) }
                .fold(
                    onSuccess = {
                        AppLogger.i(TAG, "验证码发送成功: ${phone.masked()}")
                        SmsLoginState.CodeSent
                    },
                    onFailure = {
                        AppLogger.e(TAG, "验证码发送失败: ${phone.masked()}", it)
                        SmsLoginState.Error(it.message ?: "验证码发送失败")
                    },
                )
        }
    }

    fun loginWithCaptcha(phone: String, captcha: String) {
        if (phone.isBlank() || captcha.isBlank()) {
            _smsState.value = SmsLoginState.Error("请输入手机号和验证码")
            return
        }
        viewModelScope.launch {
            _smsState.value = SmsLoginState.LoggingIn
            AppLogger.i(TAG, "短信登录: ${phone.masked()}, 验证码长度=${captcha.length}")
            _smsState.value = runCatching { authRepository.loginWithCaptcha(phone, captcha) }
                .fold(
                    onSuccess = {
                        AppLogger.i(TAG, "短信登录成功: ${phone.masked()}")
                        _state.value = LoginState.Success
                        SmsLoginState.CodeSent
                    },
                    onFailure = {
                        AppLogger.e(TAG, "短信登录失败: ${phone.masked()}", it)
                        SmsLoginState.Error(it.message ?: "登录失败")
                    },
                )
            if (_smsState.value is SmsLoginState.CodeSent) {
                _state.value = LoginState.Success
            }
        }
    }

    private fun String.masked(): String =
        if (length >= 7) take(3) + "****" + takeLast(4) else "***"

    fun resetSmsState() {
        _smsState.value = SmsLoginState.Idle
    }

    private fun generateQrMatrix(content: String): BitMatrix =
        MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX)

    override fun onCleared() {
        stopQrPolling()
        super.onCleared()
    }
}
