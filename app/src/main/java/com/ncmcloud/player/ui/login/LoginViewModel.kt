package com.ncmcloud.player.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncmcloud.player.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data object Success : LoginState
    data class Error(val message: String) : LoginState
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

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
}
