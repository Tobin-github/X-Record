package top.tobin.xrecord.ui.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.tobin.xrecord.R
import top.tobin.xrecord.core.security.BiometricPromptLauncher
import top.tobin.xrecord.ui.components.findActivity

private const val PIN_LENGTH = 4

/**
 * 解锁界面。
 *
 * 忘记密码没有找回途径：应用锁是纯本地的，没有可用于验证身份的服务端。
 * 界面上如实写明只能清除应用数据，而不是给一个做不到的"找回"入口。
 */
@Composable
fun LockScreen(viewModel: AppLockViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val rejected by viewModel.pinRejected.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    var showForgot by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val biometricEnabled = config.biometricEnabled && viewModel.biometricAvailable

    fun tryBiometric() {
        if (activity == null) return
        BiometricPromptLauncher.authenticate(
            activity = activity,
            title = context.getString(R.string.lock_biometric_title),
            subtitle = context.getString(R.string.lock_biometric_subtitle),
            negativeButtonText = context.getString(R.string.action_cancel),
            onSuccess = { viewModel.unlock() },
            onFailure = { /* 用户取消或识别失败时留在密码界面 */ },
        )
    }

    // 开启生物识别后自动弹一次，用户取消也不强制
    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled) tryBiometric()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(96.dp))
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.lock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))
        PinDots(length = pin.length, error = rejected)

        if (rejected) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.lock_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        NumberPad(
            onDigit = { digit ->
                if (pin.length < PIN_LENGTH) {
                    viewModel.clearPinError()
                    pin += digit
                    if (pin.length == PIN_LENGTH) {
                        val entered = pin
                        pin = ""
                        viewModel.submitPin(entered)
                    }
                }
            },
            onBackspace = {
                viewModel.clearPinError()
                pin = pin.dropLast(1)
            },
        )

        Spacer(modifier = Modifier.height(16.dp))
        if (biometricEnabled) {
            TextButton(onClick = { tryBiometric() }) {
                Text(text = stringResource(R.string.lock_use_biometric))
            }
        }
        TextButton(onClick = { showForgot = true }) {
            Text(text = stringResource(R.string.lock_forgot))
        }
    }

    if (showForgot) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showForgot = false },
            title = { Text(text = stringResource(R.string.lock_forgot)) },
            text = { Text(text = stringResource(R.string.lock_forgot_message)) },
            confirmButton = {
                TextButton(onClick = { showForgot = false }) {
                    Text(text = stringResource(R.string.action_confirm))
                }
            },
        )
    }
}

@Composable
private fun PinDots(length: Int, error: Boolean) {
    val color = if (error) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(PIN_LENGTH) { index ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < length) color else MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        }
    }
}

@Composable
private fun NumberPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clip(CircleShape)
                            .then(
                                if (key.isEmpty()) {
                                    Modifier
                                } else {
                                    Modifier.clickable {
                                        if (key == "⌫") onBackspace() else onDigit(key[0])
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key.isNotEmpty()) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
