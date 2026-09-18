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

data class RegisterUiState(
    val username: String = "",
    val nickname: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSubmitting: Boolean = false,
    val error: AuthError? = null,
) {
    val canSubmit: Boolean
        get() = username.isNotBlank() &&
            password.isNotEmpty() &&
            confirmPassword.isNotEmpty() &&
            !isSubmitting
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, error = null) }
    }

    fun onNicknameChange(value: String) {
        _uiState.update { it.copy(nickname = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, error = null) }
    }

    fun submit() {
        val current = _uiState.value
        if (!current.canSubmit) return

        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.register(
                username = current.username,
                nickname = current.nickname,
                password = current.password,
                confirmPassword = current.confirmPassword,
            )
            _uiState.update { state ->
                state.copy(
                    isSubmitting = false,
                    error = (result as? AuthResult.Failure)?.error,
                )
            }
        }
    }
}
