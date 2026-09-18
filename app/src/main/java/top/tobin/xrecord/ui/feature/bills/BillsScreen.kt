package top.tobin.xrecord.ui.feature.bills

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.tobin.xrecord.ui.components.PlaceholderScreen

@Composable
fun BillsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "账单",
        description = "账本管理与切换、月度收支汇总、总预算与分类预算进度将在此实现。",
        modifier = modifier,
    )
}
