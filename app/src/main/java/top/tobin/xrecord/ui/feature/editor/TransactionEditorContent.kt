package top.tobin.xrecord.ui.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.tobin.xrecord.R
import top.tobin.xrecord.data.local.entity.TransactionType
import top.tobin.xrecord.data.repository.TransactionError
import top.tobin.xrecord.ui.theme.amountColor

/**
 * 记账表单主体，新增与编辑共用。
 *
 * 新增时放在底部弹层里，编辑时放在全屏页面里，两侧只提供外壳，表单本身只有这一份。
 */
@Composable
internal fun TransactionEditorContent(
    state: EditorUiState,
    onTypeChange: (TransactionType) -> Unit,
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onEvaluate: () -> Unit,
    onCategorySelect: (Long) -> Unit,
    onAccountSelect: (Long) -> Unit,
    onToAccountSelect: (Long) -> Unit,
    onDateChange: (Long) -> Unit,
    onRemarkChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransactionType.entries.forEach { type ->
                FilterChip(
                    selected = type == state.type,
                    onClick = { onTypeChange(type) },
                    label = { Text(text = type.label()) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateSelector(occurredAt = state.occurredAt, onChange = onDateChange)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = state.expression,
                style = MaterialTheme.typography.displaySmall,
                color = state.type.amountColor(),
                maxLines = 1,
            )
        }

        when (state.type) {
            TransactionType.TRANSFER -> {
                AccountSelector(
                    label = stringResource(R.string.editor_account_from),
                    selected = state.selectedAccount,
                    accounts = state.selectableAccounts,
                    onSelect = onAccountSelect,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                AccountSelector(
                    label = stringResource(R.string.editor_account_to),
                    selected = state.selectedToAccount,
                    accounts = state.selectableAccounts,
                    onSelect = onToAccountSelect,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Spacer(modifier = Modifier.height(120.dp))
            }

            else -> {
                CategoryGrid(
                    categories = state.categories,
                    selectedId = state.categoryId,
                    onSelect = onCategorySelect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                )
                AccountSelector(
                    label = stringResource(R.string.editor_account),
                    selected = state.selectedAccount,
                    accounts = state.selectableAccounts,
                    onSelect = onAccountSelect,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        OutlinedTextField(
            value = state.remark,
            onValueChange = onRemarkChange,
            placeholder = { Text(text = stringResource(R.string.editor_remark_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (state.error != null) {
            Text(
                text = state.error.message(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        AmountKeypad(
            canSave = state.canSave,
            isSaving = state.isSaving,
            onKey = onKey,
            onBackspace = onBackspace,
            onEvaluate = onEvaluate,
            onSave = onSave,
        )
    }
}

@Composable
private fun TransactionType.label(): String = stringResource(
    when (this) {
        TransactionType.EXPENSE -> R.string.editor_type_expense
        TransactionType.INCOME -> R.string.editor_type_income
        TransactionType.TRANSFER -> R.string.editor_type_transfer
    },
)

@Composable
internal fun TransactionError.message(): String = stringResource(
    when (this) {
        TransactionError.AMOUNT_INVALID -> R.string.editor_error_amount
        TransactionError.CATEGORY_REQUIRED -> R.string.editor_error_category
        TransactionError.ACCOUNT_REQUIRED -> R.string.editor_error_account
        TransactionError.TRANSFER_TARGET_REQUIRED -> R.string.editor_error_transfer_target
        TransactionError.TRANSFER_SAME_ACCOUNT -> R.string.editor_error_transfer_same
        TransactionError.NO_DEFAULT_BOOK -> R.string.editor_error_no_book
        TransactionError.UNKNOWN -> R.string.editor_error_unknown
    },
)
