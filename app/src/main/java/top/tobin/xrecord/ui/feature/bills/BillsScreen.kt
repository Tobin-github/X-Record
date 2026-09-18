package top.tobin.xrecord.ui.feature.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.roundToInt
import top.tobin.xrecord.R
import top.tobin.xrecord.core.money.MoneyFormatter
import top.tobin.xrecord.data.local.entity.BookEntity
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.TransactionType
import top.tobin.xrecord.ui.components.AmountSummaryRow
import top.tobin.xrecord.ui.theme.amountColor
import top.tobin.xrecord.ui.theme.parseHexColor

@Composable
fun BillsScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: BillsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var bookDialog by remember { mutableStateOf<BookDialogState?>(null) }
    var budgetTarget by remember { mutableStateOf<BudgetTarget?>(null) }

    val messageText = state.message?.let { message ->
        stringResource(
            when (message) {
                BillsMessage.BOOK_NAME_EMPTY -> R.string.bills_error_book_name
                BillsMessage.LAST_BOOK_CANNOT_DELETE -> R.string.bills_error_last_book
            },
        )
    }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        PeriodHeader(
            label = state.periodLabel,
            onPrevious = viewModel::showPreviousPeriod,
            onNext = viewModel::showNextPeriod,
        )
        AmountSummaryRow(
            incomeLabel = stringResource(R.string.records_income),
            income = state.income,
            expenseLabel = stringResource(R.string.records_expense),
            expense = state.expense,
            incomeColor = TransactionType.INCOME.amountColor(),
            expenseColor = TransactionType.EXPENSE.amountColor(),
        )
        StatisticsRow(state = state)
        HorizontalDivider()

        SectionTitle(text = stringResource(R.string.bills_books_title))
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            state.books.forEach { book ->
                BookRow(
                    book = book,
                    selected = book.id == state.currentBookId,
                    onSelect = { viewModel.selectBook(book.id) },
                    onRename = { bookDialog = BookDialogState.Rename(book) },
                    onSetDefault = { viewModel.setDefaultBook(book.id) },
                    onDelete = { viewModel.requestDeleteBook(book) },
                )
            }
            TextButton(onClick = { bookDialog = BookDialogState.Create }) {
                Text(text = stringResource(R.string.bills_new_book))
            }
        }
        HorizontalDivider()

        SectionTitle(text = stringResource(R.string.bills_budget_title))
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val totalBudget = state.totalBudget
            if (totalBudget == null) {
                TextButton(onClick = { budgetTarget = BudgetTarget.Total }) {
                    Text(text = stringResource(R.string.bills_set_total_budget))
                }
            } else {
                BudgetBlock(
                    title = stringResource(R.string.bills_total_budget),
                    progress = totalBudget,
                    onEdit = { budgetTarget = BudgetTarget.Total },
                    onRemove = { viewModel.removeBudget(totalBudget.budget) },
                )
            }

            state.categoryBudgets.forEach { progress ->
                BudgetBlock(
                    title = progress.categoryName
                        ?: stringResource(R.string.records_uncategorized),
                    progress = progress,
                    onEdit = {
                        budgetTarget = BudgetTarget.Category(
                            categoryId = progress.budget.categoryId,
                            currentAmount = progress.budget.amount,
                        )
                    },
                    onRemove = { viewModel.removeBudget(progress.budget) },
                )
            }

            TextButton(onClick = { budgetTarget = BudgetTarget.Category(null, null) }) {
                Text(text = stringResource(R.string.bills_add_category_budget))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }

    bookDialog?.let { dialog ->
        BookNameDialog(
            title = if (dialog is BookDialogState.Rename) {
                stringResource(R.string.bills_rename_book)
            } else {
                stringResource(R.string.bills_new_book)
            },
            initialName = (dialog as? BookDialogState.Rename)?.book?.name.orEmpty(),
            onDismiss = { bookDialog = null },
            onConfirm = { name ->
                when (dialog) {
                    is BookDialogState.Create -> viewModel.createBook(name)
                    is BookDialogState.Rename -> viewModel.renameBook(dialog.book.id, name)
                }
                bookDialog = null
            },
        )
    }

    budgetTarget?.let { target ->
        BudgetDialog(
            target = target,
            categories = state.expenseCategories,
            onDismiss = { budgetTarget = null },
            onConfirm = { categoryId, amount ->
                when (categoryId) {
                    null -> viewModel.setTotalBudget(amount)
                    else -> viewModel.setCategoryBudget(categoryId, amount)
                }
                budgetTarget = null
            },
        )
    }

    state.pendingDeleteBook?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteBook,
            title = { Text(text = stringResource(R.string.bills_delete_book)) },
            text = {
                Text(
                    text = if (state.pendingDeleteCount > 0) {
                        stringResource(
                            R.string.bills_delete_book_message,
                            state.pendingDeleteCount,
                        )
                    } else {
                        stringResource(R.string.bills_delete_book_without_transactions)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteBook) {
                    Text(text = stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDeleteBook) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private sealed interface BookDialogState {
    data object Create : BookDialogState

    data class Rename(val book: BookEntity) : BookDialogState
}

private sealed interface BudgetTarget {
    data object Total : BudgetTarget

    data class Category(val categoryId: Long?, val currentAmount: Long?) : BudgetTarget
}

@Composable
private fun PeriodHeader(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.records_prev_period),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.records_next_period),
            )
        }
    }
}

@Composable
private fun StatisticsRow(state: BillsUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Statistic(
            label = stringResource(R.string.records_balance),
            value = MoneyFormatter.format(state.balance),
            modifier = Modifier.weight(1f),
        )
        Statistic(
            label = stringResource(R.string.bills_daily_average),
            value = MoneyFormatter.format(state.dailyAverage),
            modifier = Modifier.weight(1f),
        )
        Statistic(
            label = stringResource(R.string.bills_month_over_month),
            value = state.monthOverMonth?.let { ratio ->
                val percent = (abs(ratio) * 100).roundToInt()
                if (ratio >= 0) "+$percent%" else "-$percent%"
            } ?: "—",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Statistic(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
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
private fun BookRow(
    book: BookEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = book.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (book.isDefault) {
            Text(
                text = stringResource(R.string.bills_default_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Text(text = "⋮", style = MaterialTheme.typography.titleMedium)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.bills_rename_book)) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                )
                if (!book.isDefault) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.bills_set_default)) },
                        onClick = {
                            menuExpanded = false
                            onSetDefault()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.bills_delete_book)) },
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
private fun BudgetBlock(
    title: String,
    progress: BudgetProgress,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val barColor = if (progress.overBudget) {
        MaterialTheme.colorScheme.error
    } else {
        parseHexColor(progress.categoryColor, MaterialTheme.colorScheme.primary)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEdit),
            )
            Text(
                text = MoneyFormatter.format(progress.budget.amount),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRemove) {
                Text(text = "×")
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.ratio.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(barColor),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (progress.overBudget) {
                stringResource(
                    R.string.bills_budget_over,
                    MoneyFormatter.format(-progress.remaining),
                )
            } else {
                stringResource(
                    R.string.bills_budget_remaining,
                    MoneyFormatter.format(progress.remaining),
                )
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (progress.overBudget) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun BookNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(R.string.bills_book_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
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
private fun BudgetDialog(
    target: BudgetTarget,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (categoryId: Long?, amount: Long) -> Unit,
) {
    val initialCategoryId = (target as? BudgetTarget.Category)?.categoryId
        ?: categories.firstOrNull()?.id
    var selectedCategoryId by remember { mutableStateOf(initialCategoryId) }
    val initialAmount = (target as? BudgetTarget.Category)?.currentAmount
    var amountText by remember {
        mutableStateOf(initialAmount?.let { MoneyFormatter.toPlainString(it) }.orEmpty())
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val isTotal = target is BudgetTarget.Total
    val parsedAmount = MoneyFormatter.parseToCents(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isTotal) {
                    stringResource(R.string.bills_set_total_budget)
                } else {
                    stringResource(R.string.bills_add_category_budget)
                },
            )
        },
        text = {
            Column {
                if (!isTotal) {
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoryMenuExpanded = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.bills_choose_category),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = categories.firstOrNull { it.id == selectedCategoryId }?.name
                                    ?: "—",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false },
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(text = category.name) },
                                    onClick = {
                                        selectedCategoryId = category.id
                                        categoryMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(text = stringResource(R.string.bills_budget_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = parsedAmount ?: return@TextButton
                    onConfirm(if (isTotal) null else selectedCategoryId, amount)
                },
                enabled = parsedAmount != null && parsedAmount > 0L,
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
