package top.tobin.xrecord.ui.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import top.tobin.xrecord.R
import top.tobin.xrecord.data.repository.AuthError
import top.tobin.xrecord.ui.theme.XRecordTheme

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LoginContent(
        uiState = uiState,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onNavigateToRegister = onNavigateToRegister,
        modifier = modifier,
    )
}

/**
 * 登录页的无状态内容。
 *
 * 与 ViewModel 分离后才能在 Android Studio 的预览器里渲染——预览器不会
 * 构造 Hilt 依赖，直接预览带 `hiltViewModel()` 的页面只会得到一片空白。
 */
@Composable
internal fun LoginContent(
    uiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // 与注册页保持一致：留出状态栏与导航栏，避免内容被系统栏遮挡
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.auth_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(48.dp))
        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text(text = stringResource(R.string.auth_username)) },
            singleLine = true,
            enabled = !uiState.isSubmitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text(text = stringResource(R.string.auth_password)) },
            singleLine = true,
            enabled = !uiState.isSubmitting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        AuthErrorText(error = uiState.error)

        Spacer(modifier = Modifier.height(24.dp))
        AuthSubmitButton(
            text = stringResource(R.string.auth_login_action),
            isSubmitting = uiState.isSubmitting,
            enabled = uiState.canSubmit,
            onClick = onSubmit,
        )

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onNavigateToRegister,
            enabled = !uiState.isSubmitting,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = stringResource(R.string.auth_to_register))
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Preview(name = "登录 · 默认", showBackground = true)
@Composable
private fun LoginContentPreview() {
    XRecordTheme(dynamicColor = false) {
        LoginContent(
            uiState = LoginUiState(),
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onNavigateToRegister = {},
        )
    }
}

@Preview(name = "登录 · 密码错误", showBackground = true)
@Composable
private fun LoginContentErrorPreview() {
    XRecordTheme(dynamicColor = false) {
        LoginContent(
            uiState = LoginUiState(
                username = "tobin",
                password = "wrong",
                error = AuthError.CREDENTIALS_INVALID,
            ),
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onNavigateToRegister = {},
        )
    }
}
