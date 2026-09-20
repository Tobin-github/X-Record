package top.tobin.xrecord.ui.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    /** 从已登录状态下"添加账号"进入时，底部的"返回登录"文案不适用，可以关掉。 */
    showLoginLink: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RegisterContent(
        uiState = uiState,
        showLoginLink = showLoginLink,
        onUsernameChange = viewModel::onUsernameChange,
        onNicknameChange = viewModel::onNicknameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onSubmit = viewModel::submit,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/** 注册页的无状态内容，便于在预览器中渲染。 */
@Composable
internal fun RegisterContent(
    uiState: RegisterUiState,
    showLoginLink: Boolean,
    onUsernameChange: (String) -> Unit,
    onNicknameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // 必须让出系统栏：这块屏幕顶部有摄像头挖孔，不做内边距的话
            // 返回按钮会被压在状态栏下面，点击事件被系统截走，表现为"点了没反应"
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                text = stringResource(R.string.auth_register_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text(text = stringResource(R.string.auth_username)) },
            supportingText = { Text(text = stringResource(R.string.auth_username_hint)) },
            singleLine = true,
            enabled = !uiState.isSubmitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.nickname,
            onValueChange = onNicknameChange,
            label = { Text(text = stringResource(R.string.auth_nickname)) },
            supportingText = { Text(text = stringResource(R.string.auth_nickname_hint)) },
            singleLine = true,
            enabled = !uiState.isSubmitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text(text = stringResource(R.string.auth_password)) },
            singleLine = true,
            enabled = !uiState.isSubmitting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text(text = stringResource(R.string.auth_confirm_password)) },
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
            text = stringResource(R.string.auth_register_action),
            isSubmitting = uiState.isSubmitting,
            enabled = uiState.canSubmit,
            onClick = onSubmit,
        )

        Spacer(modifier = Modifier.height(8.dp))
        if (showLoginLink) {
            TextButton(
                onClick = onNavigateBack,
                enabled = !uiState.isSubmitting,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(text = stringResource(R.string.auth_to_login))
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Preview(name = "注册 · 默认", showBackground = true)
@Composable
private fun RegisterContentPreview() {
    XRecordTheme(dynamicColor = false) {
        RegisterContent(
            uiState = RegisterUiState(),
            showLoginLink = true,
            onUsernameChange = {},
            onNicknameChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onSubmit = {},
            onNavigateBack = {},
        )
    }
}

@Preview(name = "注册 · 密码不一致", showBackground = true)
@Composable
private fun RegisterContentErrorPreview() {
    XRecordTheme(dynamicColor = false) {
        RegisterContent(
            uiState = RegisterUiState(
                username = "tobin",
                nickname = "Tobin",
                password = "abc123",
                confirmPassword = "abc124",
                error = AuthError.PASSWORD_MISMATCH,
            ),
            showLoginLink = false,
            onUsernameChange = {},
            onNicknameChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onSubmit = {},
            onNavigateBack = {},
        )
    }
}
