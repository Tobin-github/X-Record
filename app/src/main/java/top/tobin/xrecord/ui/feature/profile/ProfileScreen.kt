package top.tobin.xrecord.ui.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.tobin.xrecord.ui.components.PlaceholderScreen

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "我的",
        description = "账户与分类管理、预算、定期账单、数据备份、应用锁与外观设置将在此实现。",
        modifier = modifier,
    )
}
