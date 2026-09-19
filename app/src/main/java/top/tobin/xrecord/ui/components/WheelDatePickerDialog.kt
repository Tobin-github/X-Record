package top.tobin.xrecord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import top.tobin.xrecord.R
import top.tobin.xrecord.core.util.DateSelection

/** 滚轮可见项数，取奇数才能让选中项正好落在正中。 */
private const val VISIBLE_ITEMS = 5
private val ITEM_HEIGHT = 40.dp

/**
 * 年 / 月 / 日三列滚轮日期选择器。
 *
 * 相比系统日历式选择器，滚轮在大跨度调年份时更快，也更符合记账用户
 * "补记前几天"的习惯。
 *
 * 关键约束是**日必须随年月联动**：从 3 月 31 日滚到 2 月时日期要收到 28（闰年 29），
 * 否则会构造出 2 月 31 日这种不存在的日期。
 */
@Composable
fun WheelDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var date by remember { mutableStateOf(initialDate) }

    val years = remember { (MIN_YEAR..MAX_YEAR).toList() }
    val months = remember { (1..12).toList() }
    val days = remember(date.year, date.monthValue) {
        (1..YearMonth.of(date.year, date.monthValue).lengthOfMonth()).toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.date_picker_title)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                // 中间高亮条，指示当前选中的那一行
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(ITEM_HEIGHT)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ITEM_HEIGHT * VISIBLE_ITEMS),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Wheel(
                        items = years,
                        selectedIndex = years.indexOf(date.year),
                        label = { stringResource(R.string.date_picker_year, it) },
                        onSelected = { year ->
                            date = withValidDay(year, date.monthValue, date.dayOfMonth)
                        },
                        modifier = Modifier.weight(1.3f),
                    )
                    Wheel(
                        items = months,
                        selectedIndex = months.indexOf(date.monthValue),
                        label = { stringResource(R.string.date_picker_month, it) },
                        onSelected = { month ->
                            date = withValidDay(date.year, month, date.dayOfMonth)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Wheel(
                        items = days,
                        selectedIndex = days.indexOf(date.dayOfMonth),
                        label = { stringResource(R.string.date_picker_day, it) },
                        onSelected = { day ->
                            date = withValidDay(date.year, date.monthValue, day)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(date) }) {
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

/** 把日期收拢到该月合法范围内，例如 3 月 31 日切到 2 月会变成 2 月 28/29 日。 */
private fun withValidDay(year: Int, month: Int, day: Int): LocalDate =
    DateSelection.clampToValidDay(year, month, day)

@Composable
private fun Wheel(
    items: List<Int>,
    selectedIndex: Int,
    label: @Composable (Int) -> String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0),
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // 取离视口中心最近的一项作为选中项，比用 firstVisibleItemIndex 更稳：
    // 手指停在两项之间时不会来回跳
    val centerIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2 - viewportCenter) }
                ?.index
        }
    }

    LaunchedEffect(centerIndex) {
        centerIndex?.let { index -> items.getOrNull(index)?.let(onSelected) }
    }

    // 外部值变化时（例如日期被收拢到 28 号）把滚轮同步过去
    LaunchedEffect(selectedIndex, items.size) {
        if (selectedIndex >= 0 && centerIndex != selectedIndex) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            vertical = ITEM_HEIGHT * (VISIBLE_ITEMS / 2),
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(items, key = { _, value -> value }) { index, value ->
            val selected = index == centerIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ITEM_HEIGHT)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(value),
                    textAlign = TextAlign.Center,
                    style = if (selected) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private const val MIN_YEAR = 1970
private const val MAX_YEAR = 2100
