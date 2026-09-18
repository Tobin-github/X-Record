package top.tobin.xrecord.ui.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.tobin.xrecord.BuildConfig
import top.tobin.xrecord.R
import top.tobin.xrecord.data.local.entity.UserEntity
import top.tobin.xrecord.data.local.dao.LocalAccount

@Composable
fun ProfileScreen(
    user: UserEntity,
    snackbarHostState: SnackbarHostState,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAppearance: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSecurity: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val periodStartDay by viewModel.periodStartDay.collectAsStateWithLifecycle()
    val cleared by viewModel.cleared.collectAsStateWithLifecycle()
    val localAccounts by viewModel.localAccounts.collectAsStateWithLifecycle()
    val switchFailed by viewModel.switchFailed.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showPeriodStartDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    var pendingSwitchAccount by remember { mutableStateOf<LocalAccount?>(null) }

    val clearedMessage = stringResource(R.string.profile_cleared)
    LaunchedEffect(cleared) {
        if (cleared) {
            snackbarHostState.showSnackbar(clearedMessage)
            viewModel.consumeCleared()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = user.nickname.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = user.nickname, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { showAccountSwitcher = true }) {
                Text(text = stringResource(R.string.profile_switch_account))
            }
        }
        HorizontalDivider()

        SectionTitle(text = stringResource(R.string.profile_section_records))
        SettingRow(
            title = stringResource(R.string.profile_accounts),
            onClick = onOpenAccounts,
        )
        SettingRow(
            title = stringResource(R.string.profile_categories),
            onClick = onOpenCategories,
        )
        SettingRow(
            title = stringResource(R.string.profile_recurring),
            onClick = onOpenRecurring,
        )
        SettingRow(
            title = stringResource(R.string.profile_period_start_day),
            hint = stringResource(R.string.profile_period_start_day_hint),
            value = stringResource(R.string.profile_period_start_day_value, periodStartDay),
            onClick = { showPeriodStartDialog = true },
        )

        HorizontalDivider()
        SectionTitle(text = stringResource(R.string.profile_section_other))
        SettingRow(
            title = stringResource(R.string.profile_appearance),
            onClick = onOpenAppearance,
        )
        SettingRow(
            title = stringResource(R.string.profile_backup),
            onClick = onOpenBackup,
        )
        SettingRow(
            title = stringResource(R.string.profile_security),
            onClick = onOpenSecurity,
        )
        SettingRow(
            title = stringResource(R.string.profile_clear_data),
            hint = stringResource(R.string.profile_clear_data_hint),
            onClick = { showClearConfirm = true },
            danger = true,
        )
        SettingRow(
            title = stringResource(R.string.profile_about),
            value = stringResource(R.string.profile_version, BuildConfig.VERSION_NAME),
            onClick = null,
        )

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = { showLogoutConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text(text = stringResource(R.string.profile_logout))
        }
        TextButton(
            onClick = { viewModel.requestDeleteAccount(user.id) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_delete_account),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(text = stringResource(R.string.profile_logout_title)) },
            text = { Text(text = stringResource(R.string.profile_logout_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        viewModel.logout()
                    },
                ) {
                    Text(text = stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(text = stringResource(R.string.profile_clear_data_title)) },
            text = { Text(text = stringResource(R.string.profile_clear_data_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearAllTransactions()
                    },
                ) {
                    Text(text = stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showPeriodStartDialog) {
        var text by remember { mutableStateOf(periodStartDay.toString()) }
        val parsed = text.toIntOrNull()?.takeIf { it in 1..28 }

        AlertDialog(
            onDismissRequest = { showPeriodStartDialog = false },
            title = { Text(text = stringResource(R.string.profile_period_start_day)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.profile_period_start_day_range),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        parsed?.let(viewModel::setPeriodStartDay)
                        showPeriodStartDialog = false
                    },
                    enabled = parsed != null,
                ) {
                    Text(text = stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPeriodStartDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showAccountSwitcher) {
        AccountSwitcherDialog(
            accounts = localAccounts,
            currentUserId = user.id,
            onSelect = { account ->
                showAccountSwitcher = false
                if (account.id != user.id) {
                    viewModel.clearSwitchError()
                    pendingSwitchAccount = account
                }
            },
            onAddAccount = {
                showAccountSwitcher = false
                onAddAccount()
            },
            onDismiss = { showAccountSwitcher = false },
        )
    }

    pendingDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteAccount,
            title = { Text(text = stringResource(R.string.profile_delete_account)) },
            text = {
                Text(
                    text = if (summary.isNotEmpty) {
                        stringResource(
                            R.string.profile_delete_account_message,
                            user.username,
                            summary.transactionCount,
                            summary.bookCount,
                            summary.accountCount,
                            summary.categoryCount,
                        )
                    } else {
                        stringResource(R.string.profile_delete_account_simple, user.username)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeleteAccount(user.id) }) {
                    Text(
                        text = stringResource(R.string.profile_delete_account_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDeleteAccount) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingSwitchAccount?.let { account ->
        SwitchAccountPasswordDialog(
            account = account,
            failed = switchFailed,
            onSubmit = { password -> viewModel.switchAccount(account.id, password) },
            onDismiss = {
                pendingSwitchAccount = null
                viewModel.clearSwitchError()
            },
        )
    }
}

@Composable
private fun AccountSwitcherDialog(
    accounts: List<LocalAccount>,
    currentUserId: Long,
    onSelect: (LocalAccount) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.profile_switch_account)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                accounts.forEach { account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(account) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = account.nickname.take(1),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.nickname,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "@${account.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (account.id == currentUserId) {
                            Text(
                                text = stringResource(R.string.profile_current_account),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                HorizontalDivider()
                TextButton(onClick = onAddAccount) {
                    Text(text = stringResource(R.string.profile_add_account))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun SwitchAccountPasswordDialog(
    account: LocalAccount,
    failed: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    R.string.profile_switch_password_title,
                    account.nickname,
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(text = stringResource(R.string.auth_password)) },
                singleLine = true,
                isError = failed,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = if (failed) {
                    { Text(text = stringResource(R.string.profile_switch_error)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (password.isNotEmpty()) onSubmit(password) },
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(password) },
                enabled = password.isNotEmpty(),
            ) {
                Text(text = stringResource(R.string.profile_switch_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    onClick: (() -> Unit)?,
    hint: String? = null,
    value: String? = null,
    danger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (hint != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
