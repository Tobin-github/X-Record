package top.tobin.xrecord.ui.feature.charts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.tobin.xrecord.ui.components.PlaceholderScreen

@Composable
fun ChartsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "图表",
        description = "收支趋势、分类占比环形图、分类排行与资产变化曲线将在此实现，使用 Vico 绘制。",
        modifier = modifier,
    )
}
