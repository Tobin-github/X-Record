package top.tobin.xrecord.ui.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.tobin.xrecord.data.repository.AuthError
import top.tobin.xrecord.data.repository.AuthRepository
import top.tobin.xrecord.data.repository.AuthResult

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
) {
    val canSubmit: Boolean
        get() = username.isNotBlank() && password.isNotEmpty() && !isSubmitting
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun submit() {
        val current = _uiState.value
        if (!current.canSubmit) return

        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.login(current.username, current.password)
            // 登录成功后根导航会切换到主界面，此处的结果只用于展示失败原因
            _uiState.update { state ->
                state.copy(
                    isSubmitting = false,
                    error = (result as? AuthResult.Failure)?.error,
                )
            }
        }
    }
}
