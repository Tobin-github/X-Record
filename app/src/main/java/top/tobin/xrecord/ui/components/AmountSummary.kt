package top.tobin.xrecord.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.tobin.xrecord.core.money.MoneyFormatter

/**
 * 收入 / 支出 两栏汇总。
 *
 * 明细页与图表页共用，避免两处各写一份后字号、间距、口径慢慢跑偏。
 */
@Composable
fun AmountSummaryRow(
    incomeLabel: String,
    income: Long,
    expenseLabel: String,
    expense: Long,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryItem(incomeLabel, income, incomeColor, Modifier.weight(1f))
        SummaryItem(expenseLabel, expense, expenseColor, Modifier.weight(1f))
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
