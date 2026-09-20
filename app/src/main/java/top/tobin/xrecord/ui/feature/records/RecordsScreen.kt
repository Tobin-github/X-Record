package top.tobin.xrecord.ui.feature.records

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import java.time.LocalDate
import kotlinx.coroutines.launch
import top.tobin.xrecord.R
import top.tobin.xrecord.core.money.MoneyFormatter
import top.tobin.xrecord.core.util.DateTimeUtils
import top.tobin.xrecord.data.local.dao.TransactionDetail
import top.tobin.xrecord.data.local.entity.TransactionType
import top.tobin.xrecord.ui.theme.amountColor
import top.tobin.xrecord.ui.theme.onColorFor
import top.tobin.xrecord.ui.theme.parseHexColor
import top.tobin.xrecord.ui.preview.PreviewData
import top.tobin.xrecord.ui.theme.XRecordTheme

@Composable
fun RecordsScreen(
    snackbarHostState: SnackbarHostState,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.records_deleted)
    val undoLabel = stringResource(R.string.records_undo)

    // 删除后立刻提供撤销：本地数据库删除是物理删除，没有回收站，误删必须能救回来
    val deleteWithUndo: (TransactionDetail) -> Unit = { detail ->
        viewModel.delete(detail.transaction)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restore(detail.transaction)
            }
        }
    }

    RecordsContent(
        state = state,
        onPreviousPeriod = viewModel::showPreviousPeriod,
        onNextPeriod = viewModel::showNextPeriod,
        onOpenTransaction = onOpenTransaction,
        onDeleteTransaction = deleteWithUndo,
        modifier = modifier,
    )
}

/** 明细页的无状态内容，便于在预览器中渲染。 */
@Composable
internal fun RecordsContent(
    state: RecordsUiState,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onDeleteTransaction: (TransactionDetail) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PeriodHeader(
            label = state.periodLabel,
            onPrevious = onPreviousPeriod,
            onNext = onNextPeriod,
        )
        SummaryRow(income = state.income, expense = state.expense, balance = state.balance)
        HorizontalDivider()

        when {
            state.isLoading -> Spacer(modifier = Modifier.fillMaxSize())
            state.isEmpty -> EmptyState()
            else -> TransactionList(
                groups = state.groups,
                onOpen = onOpenTransaction,
                onDelete = onDeleteTransaction,
            )
        }
    }
}

private fun previewRecordsState() = RecordsUiState(
    isLoading = false,
    periodLabel = "2026年9月",
    income = 1_850_000,
    expense = 426_800,
    balance = 1_423_200,
    groups = PreviewData.transactionDetails
        .groupBy { DateTimeUtils.toLocalDate(it.transaction.occurredAt) }
        .entries
        .sortedByDescending { it.key }
        .map { (date, items) ->
            DayGroup(
                date = date,
                expense = items
                    .filter { it.transaction.type == TransactionType.EXPENSE }
                    .sumOf { it.transaction.amount },
                income = items
                    .filter { it.transaction.type == TransactionType.INCOME }
                    .sumOf { it.transaction.amount },
                items = items,
            )
        },
)

@Preview(name = "明细 · 有数据", showBackground = true, heightDp = 720)
@Composable
private fun RecordsContentPreview() {
    XRecordTheme(dynamicColor = false) {
        RecordsContent(
            state = previewRecordsState(),
            onPreviousPeriod = {},
            onNextPeriod = {},
            onOpenTransaction = {},
            onDeleteTransaction = {},
        )
    }
}

@Preview(name = "明细 · 空账期", showBackground = true, heightDp = 720)
@Composable
private fun RecordsContentEmptyPreview() {
    XRecordTheme(dynamicColor = false) {
        RecordsContent(
            state = RecordsUiState(isLoading = false, periodLabel = "2026年10月"),
            onPreviousPeriod = {},
            onNextPeriod = {},
            onOpenTransaction = {},
            onDeleteTransaction = {},
        )
    }
}

@Composable
private fun PeriodHeader(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
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
private fun SummaryRow(income: Long, expense: Long, balance: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryItem(
            label = stringResource(R.string.records_income),
            amount = income,
            color = TransactionType.INCOME.amountColor(),
            modifier = Modifier.weight(1f),
        )
        SummaryItem(
            label = stringResource(R.string.records_expense),
            amount = expense,
            color = TransactionType.EXPENSE.amountColor(),
            modifier = Modifier.weight(1f),
        )
        SummaryItem(
            label = stringResource(R.string.records_balance),
            amount = balance,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryItem(
    label: String,
    amount: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = MoneyFormatter.format(amount),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TransactionList(
    groups: List<DayGroup>,
    onOpen: (Long) -> Unit,
    onDelete: (TransactionDetail) -> Unit,
) {
    val today = LocalDate.now()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        groups.forEach { group ->
            item(key = "day-${group.date}") {
                DayHeader(group = group, today = today)
            }
            items(group.items, key = { it.transaction.id }) { detail ->
                SwipeableTransactionRow(
                    detail = detail,
                    onClick = { onOpen(detail.transaction.id) },
                    onDelete = { onDelete(detail) },
                )
            }
        }
    }
}

@Composable
private fun DayHeader(group: DayGroup, today: LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${DateTimeUtils.dayLabel(group.date, today)} " +
                DateTimeUtils.weekdayLabel(group.date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (group.expense > 0L) {
            Text(
                text = "${stringResource(R.string.records_expense)} " +
                    MoneyFormatter.format(group.expense),
                style = MaterialTheme.typography.labelMedium,
                color = TransactionType.EXPENSE.amountColor(),
            )
        }
        if (group.expense > 0L && group.income > 0L) {
            Spacer(modifier = Modifier.width(12.dp))
        }
        if (group.income > 0L) {
            Text(
                text = "${stringResource(R.string.records_income)} " +
                    MoneyFormatter.format(group.income),
                style = MaterialTheme.typography.labelMedium,
                color = TransactionType.INCOME.amountColor(),
            )
        }
    }
}

@Composable
private fun SwipeableTransactionRow(
    detail: TransactionDetail,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    // 不用已废弃的 confirmValueChange：滑动到位后由状态变化触发删除，
    // 数据删除会让这一行离开组合，不会停留在滑走后的空白状态。
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = stringResource(R.string.records_delete),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) {
        TransactionRow(detail = detail, onClick = onClick)
    }
}

@Composable
private fun TransactionRow(detail: TransactionDetail, onClick: () -> Unit) {
    val transaction = detail.transaction
    val fallback = MaterialTheme.colorScheme.primary
    val badgeColor = parseHexColor(detail.categoryColor, fallback)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (transaction.type == TransactionType.TRANSFER) {
                        TransactionType.TRANSFER.amountColor().copy(alpha = 0.15f)
                    } else {
                        badgeColor.copy(alpha = 0.15f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (transaction.type == TransactionType.TRANSFER) {
                    stringResource(R.string.records_transfer).take(1)
                } else {
                    (detail.categoryName ?: "?").take(1)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (transaction.type == TransactionType.TRANSFER) {
                    TransactionType.TRANSFER.amountColor().onColorFor()
                } else {
                    badgeColor
                },
            )
        }

        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detail.title(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = detail.subtitle()
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = detail.amountText(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = transaction.type.amountColor(),
            maxLines = 1,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun TransactionDetail.title(): String = when (transaction.type) {
    TransactionType.TRANSFER -> stringResource(
        R.string.records_transfer_format,
        accountName,
        toAccountName.orEmpty(),
    )

    else -> categoryName ?: stringResource(R.string.records_uncategorized)
}

@Composable
private fun TransactionDetail.subtitle(): String = buildList {
    transaction.remark?.takeIf { it.isNotBlank() }?.let(::add)
    if (transaction.type != TransactionType.TRANSFER) add(accountName)
    add(DateTimeUtils.timeLabel(transaction.occurredAt))
}.joinToString(" · ")

@Composable
private fun TransactionDetail.amountText(): String {
    val amount = MoneyFormatter.format(transaction.amount)
    return when (transaction.type) {
        TransactionType.EXPENSE -> "-$amount"
        TransactionType.INCOME -> "+$amount"
        TransactionType.TRANSFER -> amount
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.records_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.records_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
