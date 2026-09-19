package top.tobin.xrecord.ui.feature.recurring

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.tobin.xrecord.R
import top.tobin.xrecord.core.money.MoneyFormatter
import top.tobin.xrecord.core.util.DateTimeUtils
import top.tobin.xrecord.ui.components.WheelDatePickerDialog
import top.tobin.xrecord.data.local.dao.AccountWithBalance
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.RecurringFrequency
import top.tobin.xrecord.data.local.entity.RecurringRuleEntity
import top.tobin.xrecord.data.local.entity.TransactionType
import top.tobin.xrecord.data.repository.RecurringRuleDraft
import top.tobin.xrecord.ui.theme.amountColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRulesScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecurringRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editorTarget by remember { mutableStateOf<RecurringEditorTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<RecurringRuleEntity?>(null) }

    val messageText = state.message?.let { message ->
        stringResource(
            when (message) {
                RecurringMessage.NAME_EMPTY -> R.string.recurring_error_name
                RecurringMessage.AMOUNT_INVALID -> R.string.recurring_error_amount
                RecurringMessage.CATEGORY_REQUIRED -> R.string.recurring_error_category
                RecurringMessage.ACCOUNT_REQUIRED -> R.string.recurring_error_account
                RecurringMessage.TRANSFER_TARGET_REQUIRED ->
                    R.string.recurring_error_transfer_target

                RecurringMessage.TRANSFER_SAME_ACCOUNT -> R.string.recurring_error_transfer_same
                RecurringMessage.NO_BOOK -> R.string.recurring_error_no_book
                RecurringMessage.UNKNOWN -> R.string.recurring_error_unknown
            },
        )
    }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.recurring_title)) },
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
            item {
                Text(
                    text = stringResource(R.string.recurring_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            if (state.rules.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.recurring_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }

            items(state.rules, key = { it.id }) { rule ->
                RuleRow(
                    rule = rule,
                    onEdit = { editorTarget = RecurringEditorTarget.Edit(rule) },
                    onToggle = { viewModel.setEnabled(rule, it) },
                    onDelete = { deleteTarget = rule },
                )
                HorizontalDivider()
            }

            item {
                TextButton(onClick = { editorTarget = RecurringEditorTarget.Create }) {
                    Text(text = stringResource(R.string.recurring_new))
                }
            }
        }
    }

    editorTarget?.let { target ->
        RuleEditorDialog(
            rule = (target as? RecurringEditorTarget.Edit)?.rule,
            accounts = state.accounts,
            expenseCategories = state.expenseCategories,
            incomeCategories = state.incomeCategories,
            onDismiss = { editorTarget = null },
            onConfirm = { draft ->
                viewModel.save((target as? RecurringEditorTarget.Edit)?.rule?.id, draft)
                editorTarget = null
            },
        )
    }

    deleteTarget?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = stringResource(R.string.recurring_delete)) },
            text = { Text(text = stringResource(R.string.recurring_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(rule.id)
                        deleteTarget = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
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

private sealed interface RecurringEditorTarget {
    data object Create : RecurringEditorTarget

    data class Edit(val rule: RecurringRuleEntity) : RecurringEditorTarget
}

@Composable
private fun RuleRow(
    rule: RecurringRuleEntity,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = frequencyText(rule.frequency, rule.interval) +
                    " · " + stringResource(
                    R.string.recurring_next_at,
                    DateTimeUtils.fullDayLabel(DateTimeUtils.toLocalDate(rule.nextTriggerAt)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rule.autoCreate) {
                Text(
                    text = stringResource(R.string.recurring_auto_create_on),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = MoneyFormatter.format(rule.amount),
            style = MaterialTheme.typography.titleMedium,
            color = rule.type.amountColor(),
        )
        Switch(
            checked = rule.isEnabled,
            onCheckedChange = onToggle,
            modifier = Modifier.padding(start = 8.dp),
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
                    text = { Text(text = stringResource(R.string.recurring_edit)) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.recurring_delete)) },
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
private fun RuleEditorDialog(
    rule: RecurringRuleEntity?,
    accounts: List<AccountWithBalance>,
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (RecurringRuleDraft) -> Unit,
) {
    var name by remember { mutableStateOf(rule?.name.orEmpty()) }
    var type by remember { mutableStateOf(rule?.type ?: TransactionType.EXPENSE) }
    var amountText by remember {
        mutableStateOf(rule?.let { MoneyFormatter.toPlainString(it.amount) }.orEmpty())
    }
    var categoryId by remember { mutableStateOf(rule?.categoryId) }
    var accountId by remember {
        mutableStateOf(rule?.accountId ?: accounts.firstOrNull()?.account?.id)
    }
    var toAccountId by remember {
        mutableStateOf(rule?.toAccountId ?: accounts.getOrNull(1)?.account?.id)
    }
    var frequency by remember { mutableStateOf(rule?.frequency ?: RecurringFrequency.MONTHLY) }
    var intervalText by remember { mutableStateOf((rule?.interval ?: 1).toString()) }
    var nextTriggerAt by remember {
        mutableStateOf(rule?.nextTriggerAt ?: System.currentTimeMillis())
    }
    var autoCreate by remember { mutableStateOf(rule?.autoCreate ?: true) }

    var categoryMenu by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    var toAccountMenu by remember { mutableStateOf(false) }
    var frequencyMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val categories = if (type == TransactionType.INCOME) incomeCategories else expenseCategories
    val amount = MoneyFormatter.parseToCents(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (rule == null) R.string.recurring_new else R.string.recurring_edit,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.recurring_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { candidate ->
                        FilterChip(
                            selected = candidate == type,
                            onClick = {
                                type = candidate
                                categoryId = null
                            },
                            label = {
                                Text(
                                    text = when (candidate) {
                                        TransactionType.EXPENSE ->
                                            stringResource(R.string.editor_type_expense)

                                        TransactionType.INCOME ->
                                            stringResource(R.string.editor_type_income)

                                        TransactionType.TRANSFER ->
                                            stringResource(R.string.editor_type_transfer)
                                    },
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(text = stringResource(R.string.recurring_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (type != TransactionType.TRANSFER) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PickerRow(
                        label = stringResource(R.string.bills_choose_category),
                        value = categories.firstOrNull { it.id == categoryId }?.name
                            ?: stringResource(R.string.bills_choose_category),
                        expanded = categoryMenu,
                        onExpand = { categoryMenu = true },
                        onDismiss = { categoryMenu = false },
                        options = categories.map { it.id to it.name },
                        onSelect = { categoryId = it },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                PickerRow(
                    label = if (type == TransactionType.TRANSFER) {
                        stringResource(R.string.editor_account_from)
                    } else {
                        stringResource(R.string.editor_account)
                    },
                    value = accounts.firstOrNull { it.account.id == accountId }?.account?.name
                        ?: stringResource(R.string.editor_pick_account),
                    expanded = accountMenu,
                    onExpand = { accountMenu = true },
                    onDismiss = { accountMenu = false },
                    options = accounts.map { it.account.id to it.account.name },
                    onSelect = { accountId = it },
                )

                if (type == TransactionType.TRANSFER) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PickerRow(
                        label = stringResource(R.string.editor_account_to),
                        value = accounts.firstOrNull { it.account.id == toAccountId }?.account?.name
                            ?: stringResource(R.string.editor_pick_account),
                        expanded = toAccountMenu,
                        onExpand = { toAccountMenu = true },
                        onDismiss = { toAccountMenu = false },
                        options = accounts.map { it.account.id to it.account.name },
                        onSelect = { toAccountId = it },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                PickerRow(
                    label = stringResource(R.string.recurring_frequency),
                    value = frequencyText(frequency, intervalText.toIntOrNull() ?: 1),
                    expanded = frequencyMenu,
                    onExpand = { frequencyMenu = true },
                    onDismiss = { frequencyMenu = false },
                    options = RecurringFrequency.entries.map { it to frequencyBaseText(it) },
                    onSelect = { frequency = it },
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it.filter(Char::isDigit) },
                    label = { Text(text = stringResource(R.string.recurring_interval)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.recurring_next_date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = DateTimeUtils.fullDayLabel(
                            DateTimeUtils.toLocalDate(nextTriggerAt),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.recurring_auto_create),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.recurring_auto_create_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoCreate, onCheckedChange = { autoCreate = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedAmount = amount ?: return@TextButton
                    onConfirm(
                        RecurringRuleDraft(
                            name = name,
                            type = type,
                            amount = parsedAmount,
                            categoryId = categoryId,
                            accountId = accountId,
                            toAccountId = toAccountId,
                            frequency = frequency,
                            interval = intervalText.toIntOrNull() ?: 1,
                            nextTriggerAt = nextTriggerAt,
                            remark = null,
                            autoCreate = autoCreate,
                        ),
                    )
                },
                enabled = amount != null && amount > 0L,
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

    if (showDatePicker) {
        WheelDatePickerDialog(
            initialDate = DateTimeUtils.toLocalDate(nextTriggerAt),
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                nextTriggerAt = DateTimeUtils.withDate(nextTriggerAt, date)
                showDatePicker = false
            },
        )
    }
}

/** 泛型化的下拉选择行：分类、账户与频率共用同一套交互。 */
@Composable
private fun <T> PickerRow(
    label: String,
    value: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text = text) },
                    onClick = { onSelect(id) },
                )
            }
        }
    }
}

@Composable
private fun frequencyBaseText(frequency: RecurringFrequency): String = stringResource(
    when (frequency) {
        RecurringFrequency.DAILY -> R.string.recurring_unit_day
        RecurringFrequency.WEEKLY -> R.string.recurring_unit_week
        RecurringFrequency.MONTHLY -> R.string.recurring_unit_month
        RecurringFrequency.YEARLY -> R.string.recurring_unit_year
    },
)

@Composable
private fun frequencyText(frequency: RecurringFrequency, interval: Int): String {
    val step = interval.coerceAtLeast(1)
    return if (step == 1) {
        stringResource(
            when (frequency) {
                RecurringFrequency.DAILY -> R.string.recurring_every_day
                RecurringFrequency.WEEKLY -> R.string.recurring_every_week
                RecurringFrequency.MONTHLY -> R.string.recurring_every_month
                RecurringFrequency.YEARLY -> R.string.recurring_every_year
            },
        )
    } else {
        stringResource(
            R.string.recurring_every_n,
            step,
            frequencyBaseText(frequency),
        )
    }
}
