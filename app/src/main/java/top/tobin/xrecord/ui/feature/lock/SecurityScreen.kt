package top.tobin.xrecord.ui.feature.lock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.tobin.xrecord.R

private const val PIN_LENGTH = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    var pinDialog by remember { mutableStateOf<PinDialogMode?>(null) }
    var timeoutMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.security_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.security_app_lock),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.security_app_lock_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { enabled ->
                        if (enabled) pinDialog = PinDialogMode.Set else viewModel.disable()
                    },
                )
            }

            if (config.enabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                SettingRow(
                    title = stringResource(R.string.security_change_pin),
                    onClick = { pinDialog = PinDialogMode.Change },
                )

                if (viewModel.biometricAvailable) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.security_biometric),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.security_biometric_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = config.biometricEnabled,
                            onCheckedChange = viewModel::setBiometricEnabled,
                        )
                    }
                }

                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { timeoutMenu = true }
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.security_auto_lock),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = timeoutLabel(config.timeoutSeconds),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = timeoutMenu,
                        onDismissRequest = { timeoutMenu = false },
                    ) {
                        TIMEOUT_OPTIONS.forEach { seconds ->
                            DropdownMenuItem(
                                text = { Text(text = timeoutLabel(seconds)) },
                                onClick = {
                                    viewModel.setTimeoutSeconds(seconds)
                                    timeoutMenu = false
                                },
                            )
                        }
                    }
                }

                SettingRow(
                    title = stringResource(R.string.security_lock_now),
                    onClick = viewModel::lockNow,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.security_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pinDialog?.let { mode ->
        PinSetupDialog(
            mode = mode,
            onDismiss = { pinDialog = null },
            onConfirm = { pin ->
                when (mode) {
                    PinDialogMode.Set -> viewModel.enable(pin)
                    PinDialogMode.Change -> viewModel.changePin(pin)
                }
                pinDialog = null
            },
        )
    }
}

private sealed interface PinDialogMode {
    data object Set : PinDialogMode

    data object Change : PinDialogMode
}

private val TIMEOUT_OPTIONS = listOf(0, 30, 60, 300, -1)

@Composable
private fun timeoutLabel(seconds: Int): String = stringResource(
    when (seconds) {
        0 -> R.string.security_timeout_now
        in 1..59 -> R.string.security_timeout_30s
        in 60..299 -> R.string.security_timeout_1m
        in 300..599 -> R.string.security_timeout_5m
        else -> R.string.security_timeout_never
    },
)

@Composable
private fun SettingRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinSetupDialog(
    mode: PinDialogMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val tooShort = pin.length != PIN_LENGTH
    val mismatch = confirm.isNotEmpty() && pin != confirm
    val canConfirm = !tooShort && pin == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (mode == PinDialogMode.Set) {
                        R.string.security_pin_set_title
                    } else {
                        R.string.security_pin_change_title
                    },
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(PIN_LENGTH) },
                    label = { Text(text = stringResource(R.string.security_pin_new)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = pin.isNotEmpty() && tooShort,
                    supportingText = if (tooShort && pin.isNotEmpty()) {
                        { Text(text = stringResource(R.string.security_pin_error_length)) }
                    } else {
                        null
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter(Char::isDigit).take(PIN_LENGTH) },
                    label = { Text(text = stringResource(R.string.security_pin_confirm)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(text = stringResource(R.string.security_pin_error_mismatch)) }
                    } else {
                        null
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }, enabled = canConfirm) {
                Text(text = stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}
