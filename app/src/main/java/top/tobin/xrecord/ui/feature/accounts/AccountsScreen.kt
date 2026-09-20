package top.tobin.xrecord.ui.feature.accounts

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import top.tobin.xrecord.R
import top.tobin.xrecord.core.money.MoneyFormatter
import top.tobin.xrecord.data.local.dao.AccountWithBalance
import top.tobin.xrecord.data.local.entity.AccountEntity
import top.tobin.xrecord.data.local.entity.AccountType
import top.tobin.xrecord.data.repository.AccountDraft
import top.tobin.xrecord.data.repository.defaultIncludeInTotal
import top.tobin.xrecord.ui.theme.onColorFor
import top.tobin.xrecord.ui.theme.parseHexColor
import top.tobin.xrecord.ui.preview.PreviewData
import top.tobin.xrecord.ui.theme.XRecordTheme

@Composable
fun AccountsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AccountsContent(
        state = state,
        actions = AccountsActions(
            onSave = viewModel::save,
            onArchive = viewModel::setArchived,
            onDelete = viewModel::delete,
            onMessageShown = viewModel::consumeMessage,
        ),
        onNavigateBack = onNavigateBack,
    )
}

/** 账户页的操作，集中成一个对象，免得内容函数的参数列表失控。 */
internal class AccountsActions(
    val onSave: (AccountEntity?, AccountDraft) -> Unit = { _, _ -> },
    val onArchive: (Long, Boolean) -> Unit = { _, _ -> },
    val onDelete: (AccountEntity) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountsContent(
    state: AccountsUiState,
    actions: AccountsActions,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var editorTarget by remember { mutableStateOf<AccountEditorTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<AccountEntity?>(null) }

    val messageText = state.message?.let { message ->
        stringResource(
            when (message) {
                AccountsMessage.NAME_EMPTY -> R.string.accounts_error_name
                AccountsMessage.HAS_TRANSACTIONS -> R.string.accounts_error_in_use
                AccountsMessage.LAST_ACCOUNT -> R.string.accounts_error_last
            },
        )
    }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.accounts_title)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
        ) {
            if (state.active.isEmpty() && state.archived.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.accounts_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            if (state.active.isNotEmpty()) {
                item { SectionTitle(text = stringResource(R.string.accounts_active)) }
                items(state.active, key = { it.account.id }) { item ->
                    AccountRow(
                        item = item,
                        onEdit = { editorTarget = AccountEditorTarget.Edit(item.account) },
                        onArchive = { actions.onArchive(item.account.id, true) },
                        onDelete = { deleteTarget = item.account },
                    )
                }
            }

            if (state.archived.isNotEmpty()) {
                item { SectionTitle(text = stringResource(R.string.accounts_archived)) }
                items(state.archived, key = { it.account.id }) { item ->
                    AccountRow(
                        item = item,
                        onEdit = { editorTarget = AccountEditorTarget.Edit(item.account) },
                        onArchive = { actions.onArchive(item.account.id, false) },
                        onDelete = { deleteTarget = item.account },
                    )
                }
            }

            item {
                TextButton(onClick = { editorTarget = AccountEditorTarget.Create }) {
                    Text(text = stringResource(R.string.accounts_new))
                }
            }
        }
    }

    editorTarget?.let { target ->
        AccountEditorDialog(
            account = (target as? AccountEditorTarget.Edit)?.account,
            onDismiss = { editorTarget = null },
            onConfirm = { draft ->
                actions.onSave((target as? AccountEditorTarget.Edit)?.account, draft)
                editorTarget = null
            },
        )
    }

    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = stringResource(R.string.accounts_delete)) },
            text = { Text(text = stringResource(R.string.accounts_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.onDelete(account)
                        deleteTarget = null
                    },
                ) {
                    Text(text = stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private sealed interface AccountEditorTarget {
    data object Create : AccountEditorTarget

    data class Edit(val account: AccountEntity) : AccountEditorTarget
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun AccountRow(
    item: AccountWithBalance,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val color = parseHexColor(item.account.color, MaterialTheme.colorScheme.primary)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.account.name.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = color.onColorFor(),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.account.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.account.type.label(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = MoneyFormatter.format(item.balance),
            style = MaterialTheme.typography.titleMedium,
        )
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Text(text = "⋮", style = MaterialTheme.typography.titleMedium)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(
                                if (item.account.isArchived) {
                                    R.string.accounts_unarchive
                                } else {
                                    R.string.accounts_archive
                                },
                            ),
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onArchive()
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.accounts_delete)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountEditorDialog(
    account: AccountEntity?,
    onDismiss: () -> Unit,
    onConfirm: (AccountDraft) -> Unit,
) {
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var type by remember { mutableStateOf(account?.type ?: AccountType.CASH) }
    var includeInTotal by remember {
        mutableStateOf(account?.includeInTotal ?: AccountType.CASH.defaultIncludeInTotal())
    }
    var balanceText by remember {
        mutableStateOf(account?.let { MoneyFormatter.toPlainString(it.initialBalance) }.orEmpty())
    }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (account == null) {
                    stringResource(R.string.accounts_new)
                } else {
                    stringResource(R.string.accounts_title)
                },
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.accounts_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { typeMenuExpanded = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.accounts_type),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = type.label(), style = MaterialTheme.typography.bodyMedium)
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false },
                    ) {
                        AccountType.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(text = candidate.label()) },
                                onClick = {
                                    type = candidate
                                    // 换类型时同步更新"是否计入总资产"的默认值
                                    includeInTotal = candidate.defaultIncludeInTotal()
                                    typeMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text(text = stringResource(R.string.accounts_initial_balance)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.accounts_include_in_total),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = includeInTotal, onCheckedChange = { includeInTotal = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        AccountDraft(
                            name = name,
                            type = type,
                            initialBalance = MoneyFormatter.parseToCents(balanceText) ?: 0L,
                            includeInTotal = includeInTotal,
                        ),
                    )
                },
            ) {
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

@Composable
internal fun AccountType.label(): String = stringResource(
    when (this) {
        AccountType.CASH -> R.string.account_type_cash
        AccountType.DEBIT_CARD -> R.string.account_type_debit
        AccountType.CREDIT_CARD -> R.string.account_type_credit
        AccountType.ALIPAY -> R.string.account_type_alipay
        AccountType.WECHAT -> R.string.account_type_wechat
        AccountType.INVESTMENT -> R.string.account_type_investment
        AccountType.DEBT -> R.string.account_type_debt
        AccountType.OTHER -> R.string.account_type_other
    },
)

@Preview(name = "账户管理", showBackground = true, heightDp = 720)
@Composable
private fun AccountsContentPreview() {
    XRecordTheme(dynamicColor = false) {
        AccountsContent(
            state = AccountsUiState(isLoading = false, accounts = PreviewData.accounts),
            actions = AccountsActions(),
            onNavigateBack = {},
        )
    }
}
