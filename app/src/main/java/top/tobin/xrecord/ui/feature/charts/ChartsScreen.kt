package top.tobin.xrecord.ui.feature.charts

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.PieSize
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.pieSeries
import com.patrykandpatrick.vico.compose.pie.rememberPieChart
import kotlin.math.abs
import kotlin.math.roundToLong
import top.tobin.xrecord.R
import top.tobin.xrecord.core.money.MoneyFormatter
import top.tobin.xrecord.core.util.ChartRange
import top.tobin.xrecord.data.local.entity.TransactionType
import top.tobin.xrecord.ui.components.AmountSummaryRow
import top.tobin.xrecord.ui.theme.amountColor
import top.tobin.xrecord.ui.theme.parseHexColor

@Composable
fun ChartsScreen(
    modifier: Modifier = Modifier,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val expenseColor = TransactionType.EXPENSE.amountColor()
    val incomeColor = TransactionType.INCOME.amountColor()
    val vicoTheme = rememberM3VicoTheme(
        columnCartesianLayerColors = listOf(expenseColor, incomeColor),
        pieChartColors = listOf(expenseColor, incomeColor),
    )

    ProvideVicoTheme(theme = vicoTheme) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            RangeTabs(current = state.range, onSelect = viewModel::setRange)
            RangeHeader(
                label = state.rangeLabel,
                onPrevious = viewModel::showPreviousRange,
                onNext = viewModel::showNextRange,
            )
            AmountSummaryRow(
                incomeLabel = stringResource(R.string.records_income),
                income = state.totalIncome,
                expenseLabel = stringResource(R.string.records_expense),
                expense = state.totalExpense,
                incomeColor = incomeColor,
                expenseColor = expenseColor,
            )
            HorizontalDivider()

            SectionTitle(text = stringResource(R.string.charts_trend_title))
            TrendChart(points = state.trend)

            SectionTitle(text = stringResource(R.string.charts_category_title))
            CategoryTypeTabs(
                current = state.categoryType,
                onSelect = viewModel::setCategoryType,
            )
            if (state.slices.isEmpty()) {
                EmptyHint()
            } else {
                CategoryBreakdown(slices = state.slices)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RangeTabs(current: ChartRange, onSelect: (ChartRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChartRange.entries.forEach { range ->
            FilterChip(
                selected = range == current,
                onClick = { onSelect(range) },
                label = { Text(text = stringResource(range.labelRes())) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RangeHeader(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.charts_prev_range),
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
                contentDescription = stringResource(R.string.charts_next_range),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun TrendChart(points: List<TrendPoint>) {
    // Vico 的每个系列都要求至少有一个数据点，数据还没加载完时先占位。
    // 空列表直接交给 columnModel 会抛 "Series can't be empty" 并让界面崩溃。
    if (points.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        )
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val expenseColor = TransactionType.EXPENSE.amountColor()
    val incomeColor = TransactionType.INCOME.amountColor()

    // 金额以"分"存储，画图时换算成"元"，避免坐标轴出现七八位数
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            columnModel {
                series(points.map { it.expense / 100.0 })
                series(points.map { it.income / 100.0 })
            }
        }
    }

    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                rememberLineComponent(
                    fill = Fill(expenseColor),
                    thickness = 8.dp,
                    shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                ),
                rememberLineComponent(
                    fill = Fill(incomeColor),
                    thickness = 8.dp,
                    shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                ),
            ),
            columnCollectionSpacing = 6.dp,
        ),
        startAxis = VerticalAxis.rememberStart(
            valueFormatter = CartesianValueFormatter { _, value, _ -> formatAxisValue(value) },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            valueFormatter = CartesianValueFormatter { _, value, _ ->
                points.getOrNull(value.toInt())?.label.orEmpty()
            },
        ),
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(end = 12.dp),
    )
}

@Composable
private fun CategoryTypeTabs(current: TransactionType, onSelect: (TransactionType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
            FilterChip(
                selected = type == current,
                onClick = { onSelect(type) },
                label = {
                    Text(
                        text = if (type == TransactionType.EXPENSE) {
                            stringResource(R.string.records_expense)
                        } else {
                            stringResource(R.string.records_income)
                        },
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryBreakdown(slices: List<CategorySlice>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryDonut(slices = slices)
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.take(4).forEach { slice ->
                LegendRow(slice = slice)
            }
            if (slices.size > 4) {
                Text(
                    text = "…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    SectionTitle(text = stringResource(R.string.charts_ranking_title))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        slices.forEach { slice -> RankingRow(slice = slice) }
    }
}

@Composable
private fun CategoryDonut(slices: List<CategorySlice>) {
    val modelProducer = remember { PieChartModelProducer() }
    val fallback = MaterialTheme.colorScheme.primary
    val colors = slices.map { parseHexColor(it.colorHex, fallback) }

    LaunchedEffect(slices) {
        modelProducer.runTransaction {
            pieSeries { series(slices.map { it.amount }) }
        }
    }

    val chart = rememberPieChart(
        sliceProvider = PieChart.SliceProvider.series(
            colors.map { color -> PieChart.Slice(fill = Fill(color)) },
        ),
        spacing = 2.dp,
        innerSize = PieSize.Inner.fixed(72.dp),
    )

    PieChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = Modifier.size(170.dp),
    )
}

@Composable
private fun LegendRow(slice: CategorySlice) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(parseHexColor(slice.colorHex, MaterialTheme.colorScheme.primary)),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = slice.name ?: stringResource(R.string.records_uncategorized),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(slice.ratio * 100).roundToLong()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RankingRow(slice: CategorySlice) {
    val color = parseHexColor(slice.colorHex, MaterialTheme.colorScheme.primary)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = slice.name ?: stringResource(R.string.records_uncategorized),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = MoneyFormatter.format(slice.amount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${(slice.ratio * 100).roundToLong()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // 用 Box 宽度表示占比，避免依赖进度条组件的 API 细节
            Box(
                modifier = Modifier
                    .fillMaxWidth(slice.ratio.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(color),
            )
        }
    }
}

@Composable
private fun EmptyHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.charts_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChartRange.labelRes(): Int = when (this) {
    ChartRange.WEEK -> R.string.charts_range_week
    ChartRange.MONTH -> R.string.charts_range_month
    ChartRange.YEAR -> R.string.charts_range_year
}

/** 纵轴刻度：金额较大时用"万"，否则显示整数元。 */
private fun formatAxisValue(value: Double): String {
    val yuan = value.roundToLong()
    return if (abs(yuan) >= 10_000L) "${yuan / 10_000}万" else yuan.toString()
}
