package top.tobin.xrecord.ui.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import top.tobin.xrecord.R
import top.tobin.xrecord.data.backup.BackupSummary
import top.tobin.xrecord.ui.theme.XRecordTheme

@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        // 不限定 MIME：不同文件提供方对 .json 的识别不一致，
        // 允许任意文件更省事，内容是否合法由解析结果负责
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::prepareImport) }

    BackupContent(
        state = state,
        onExportClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
        onImportClick = { importLauncher.launch(arrayOf("*/*")) },
        onConfirmImport = viewModel::confirmImport,
        onCancelImport = viewModel::cancelImport,
        onMessageShown = viewModel::consumeMessage,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupContent(
    state: BackupUiState,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onMessageShown: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val messageText = state.message?.let { message ->
        stringResource(
            when (message) {
                BackupMessage.EXPORT_SUCCESS -> R.string.backup_export_success
                BackupMessage.EXPORT_FAILED -> R.string.backup_export_failed
                BackupMessage.IMPORT_SUCCESS -> R.string.backup_import_success
                BackupMessage.IMPORT_INVALID -> R.string.backup_error_invalid
                BackupMessage.IMPORT_UNSUPPORTED -> R.string.backup_error_unsupported
                BackupMessage.IMPORT_BROKEN -> R.string.backup_error_broken
                BackupMessage.READ_FAILED -> R.string.backup_error_read
            },
        )
    }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.backup_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.backup_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = onExportClick,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.backup_export))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.backup_export_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = onImportClick,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.backup_import))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.backup_import_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    state.pendingImport?.let { summary ->
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text(text = stringResource(R.string.backup_import_confirm_title)) },
            text = {
                Column {
                    Text(text = stringResource(R.string.backup_import_confirm_message))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(
                                R.string.backup_import_summary,
                                summary.transactionCount,
                                summary.bookCount,
                                summary.accountCount,
                                summary.categoryCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmImport) {
                    Text(
                        text = stringResource(R.string.backup_import_confirm_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelImport) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Preview(name = "数据备份 · 默认", showBackground = true)
@Composable
private fun BackupContentPreview() {
    XRecordTheme(dynamicColor = false) {
        BackupContent(
            state = BackupUiState(),
            onExportClick = {},
            onImportClick = {},
            onConfirmImport = {},
            onCancelImport = {},
            onMessageShown = {},
            onNavigateBack = {},
        )
    }
}

@Preview(name = "数据备份 · 导入确认", showBackground = true)
@Composable
private fun BackupContentConfirmPreview() {
    XRecordTheme(dynamicColor = false) {
        BackupContent(
            state = BackupUiState(
                pendingImport = BackupSummary(
                    bookCount = 1,
                    accountCount = 3,
                    categoryCount = 19,
                    transactionCount = 142,
                    budgetCount = 2,
                    recurringRuleCount = 4,
                ),
            ),
            onExportClick = {},
            onImportClick = {},
            onConfirmImport = {},
            onCancelImport = {},
            onMessageShown = {},
            onNavigateBack = {},
        )
    }
}
