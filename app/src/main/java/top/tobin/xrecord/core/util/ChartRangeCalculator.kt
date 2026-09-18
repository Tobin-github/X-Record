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

    fun bucketIndex(buckets: List<TrendBucket>, date: LocalDate): Int =
        buckets.indexOfFirst { it.contains(date) }

    private fun dayBuckets(range: ChartRange, period: AccountingPeriod): List<TrendBucket> {
        val result = mutableListOf<TrendBucket>()
        var date = period.start
        while (!date.isAfter(period.endInclusive)) {
            val label = when {
                // 一周只有 7 天，显示星期几更好认
                range == ChartRange.WEEK ->
                    DateTimeUtils.weekdayLabel(date).removePrefix("星期")

                date.dayOfMonth == 1 || result.isEmpty() -> "${date.monthValue}/${date.dayOfMonth}"
                else -> date.dayOfMonth.toString()
            }
            result += TrendBucket(label = label, start = date, endInclusive = date)
            date = date.plusDays(1)
        }
        return result
    }

    private fun monthBuckets(year: Int): List<TrendBucket> =
        (1..MONTHS_IN_YEAR).map { month ->
            val start = LocalDate.of(year, month, 1)
            TrendBucket(
                label = "${month}月",
                start = start,
                endInclusive = start.withDayOfMonth(start.lengthOfMonth()),
            )
        }

    private const val DAYS_IN_WEEK = 7L
    private const val MONTHS_IN_YEAR = 12
}
