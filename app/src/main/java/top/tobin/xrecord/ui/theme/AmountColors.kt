package top.tobin.xrecord.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.tobin.xrecord.data.local.entity.TransactionType

/** 流水类型对应的金额颜色：支出红、收入绿、转账蓝。 */
@Composable
fun TransactionType.amountColor(): Color = when (this) {
    TransactionType.EXPENSE -> ExpenseRed
    TransactionType.INCOME -> IncomeGreen
    TransactionType.TRANSFER -> TransferBlue
}
