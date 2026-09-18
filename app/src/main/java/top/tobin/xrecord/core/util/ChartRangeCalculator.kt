package top.tobin.xrecord.core.util

import java.time.LocalDate

/** 图表页的时间维度。 */
enum class ChartRange {
    WEEK,
    MONTH,
    YEAR,
}

/** 趋势图的一个数据桶。 */
data class TrendBucket(
    val label: String,
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(endInclusive)
}

/**
 * 图表页的时间区间与趋势分桶。
 *
 * 与明细页保持同一口径：月维度直接复用 `PeriodCalculator`，用户改了每月起始日，
 * 图表和明细必须跟着一起变，不能各算各的。
 */
object ChartRangeCalculator {

    fun range(
        range: ChartRange,
        offset: Long,
        startDay: Int,
        today: LocalDate,
    ): AccountingPeriod = when (range) {
        ChartRange.WEEK -> {
            val start = today.minusDays(DAYS_IN_WEEK - 1).plusWeeks(offset)
            AccountingPeriod(start, start.plusDays(DAYS_IN_WEEK - 1))
        }

        ChartRange.MONTH ->
            PeriodCalculator.shift(PeriodCalculator.of(today, startDay), offset)

        ChartRange.YEAR -> {
            val year = today.year + offset.toInt()
            AccountingPeriod(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
        }
    }

    fun label(range: ChartRange, period: AccountingPeriod, startDay: Int): String = when (range) {
        ChartRange.WEEK -> {
            val start = period.start
            val end = period.endInclusive
            if (start.monthValue == end.monthValue) {
                "${start.monthValue}月${start.dayOfMonth}日 - ${end.dayOfMonth}日"
            } else {
                "${start.monthValue}月${start.dayOfMonth}日 - " +
                    "${end.monthValue}月${end.dayOfMonth}日"
            }
        }

        ChartRange.MONTH -> DateTimeUtils.periodLabel(period, startDay)
        ChartRange.YEAR -> "${period.start.year}年"
    }

    /** 趋势图的横轴分桶：周与月按天，年按月。 */
    fun buckets(range: ChartRange, period: AccountingPeriod): List<TrendBucket> =
        when (range) {
            ChartRange.WEEK, ChartRange.MONTH -> dayBuckets(range, period)
            ChartRange.YEAR -> monthBuckets(period.start.year)
        }

    /**
     * 横轴标签。
     *
     * 必须对**任意**下标都能算出结果，绝不能返回空串：图表切换数据做动画时，
     * Vico 会查询当前数据集之外的 x 值（例如从 31 天的月份切到 30 天的月份，
     * 动画期间仍会询问 x=30）。此时若返回空串，Vico 会直接抛
     * `IllegalStateException: CartesianValueFormatter.format returned a blank string`。
     *
     * 因此这里不查表，而是由日期直接推算，超出区间也能得到合理的标签。
     */
    fun axisLabel(range: ChartRange, periodStart: LocalDate, index: Int): String {
        if (range == ChartRange.YEAR) {
            return "${index.mod(MONTHS_IN_YEAR) + 1}月"
        }
        val date = periodStart.plusDays(index.toLong())
        return when {
            range == ChartRange.WEEK ->
                DateTimeUtils.weekdayLabel(date).removePrefix("星期")

            // 每月第一天带上月份，便于跨账期时辨认
            index == 0 || date.dayOfMonth == 1 -> "${date.monthValue}/${date.dayOfMonth}"
            else -> date.dayOfMonth.toString()
        }
    }

    fun bucketIndex(buckets: List<TrendBucket>, date: LocalDate): Int =
        buckets.indexOfFirst { it.contains(date) }

    private fun dayBuckets(range: ChartRange, period: AccountingPeriod): List<TrendBucket> {
        val result = mutableListOf<TrendBucket>()
        var date = period.start
        while (!date.isAfter(period.endInclusive)) {
            result += TrendBucket(
                // 与坐标轴共用同一套标签逻辑，避免两处慢慢跑偏
                label = axisLabel(range, period.start, result.size),
                start = date,
                endInclusive = date,
            )
            date = date.plusDays(1)
        }
        return result
    }

    private fun monthBuckets(year: Int): List<TrendBucket> =
        (1..MONTHS_IN_YEAR).map { month ->
            val start = LocalDate.of(year, month, 1)
            TrendBucket(
                label = axisLabel(ChartRange.YEAR, start, month - 1),
                start = start,
                endInclusive = start.withDayOfMonth(start.lengthOfMonth()),
            )
        }

    private const val DAYS_IN_WEEK = 7L
    private const val MONTHS_IN_YEAR = 12
}
