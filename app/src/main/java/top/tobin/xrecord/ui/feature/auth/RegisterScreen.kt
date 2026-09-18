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
import top.tobin.xrecord.R

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            IconButton(onClick = onNavigateToLogin) {
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
            onValueChange = viewModel::onUsernameChange,
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
            onValueChange = viewModel::onNicknameChange,
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
            onValueChange = viewModel::onPasswordChange,
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
            onValueChange = viewModel::onConfirmPasswordChange,
            label = { Text(text = stringResource(R.string.auth_confirm_password)) },
            singleLine = true,
            enabled = !uiState.isSubmitting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        AuthErrorText(error = uiState.error)

        Spacer(modifier = Modifier.height(24.dp))
        AuthSubmitButton(
            text = stringResource(R.string.auth_register_action),
            isSubmitting = uiState.isSubmitting,
            enabled = uiState.canSubmit,
            onClick = viewModel::submit,
        )

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onNavigateToLogin,
            enabled = !uiState.isSubmitting,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = stringResource(R.string.auth_to_login))
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}
