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
import top.tobin.xrecord.R

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            onValueChange = viewModel::onUsernameChange,
            label = { Text(text = stringResource(R.string.auth_username)) },
            singleLine = true,
            enabled = !uiState.isSubmitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text(text = stringResource(R.string.auth_password)) },
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
            text = stringResource(R.string.auth_login_action),
            isSubmitting = uiState.isSubmitting,
            enabled = uiState.canSubmit,
            onClick = viewModel::submit,
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
