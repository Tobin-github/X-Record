package top.tobin.xrecord.ui.feature.auth

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import top.tobin.xrecord.R
import top.tobin.xrecord.data.repository.AuthError

@StringRes
private fun AuthError.messageRes(): Int = when (this) {
    AuthError.USERNAME_INVALID -> R.string.auth_error_username_invalid
    AuthError.USERNAME_TAKEN -> R.string.auth_error_username_taken
    AuthError.PASSWORD_TOO_SHORT -> R.string.auth_error_password_too_short
    AuthError.PASSWORD_MISMATCH -> R.string.auth_error_password_mismatch
    AuthError.CREDENTIALS_INVALID -> R.string.auth_error_credentials
    AuthError.UNKNOWN -> R.string.auth_error_unknown
}

@Composable
fun AuthError.text(): String = stringResource(messageRes())
