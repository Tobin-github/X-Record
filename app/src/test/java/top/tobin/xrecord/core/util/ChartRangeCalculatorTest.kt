package top.tobin.xrecord.core.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartRangeCalculatorTest {

    private val today = LocalDate.of(2026, 9, 18)

    @Test
    fun `周维度是截至今天的滚动七天`() {
        val period = ChartRangeCalculator.range(ChartRange.WEEK, 0, startDay = 1, today = today)

        assertEquals(LocalDate.of(2026, 9, 12), period.start)
        assertEquals(LocalDate.of(2026, 9, 18), period.endInclusive)
    }

    @Test
    fun `周维度向前平移一周`() {
        val period = ChartRangeCalculator.range(ChartRange.WEEK, -1, startDay = 1, today = today)

        assertEquals(LocalDate.of(2026, 9, 5), period.start)
        assertEquals(LocalDate.of(2026, 9, 11), period.endInclusive)
    }

    @Test
    fun `月维度遵循每月起始日`() {
        val period = ChartRangeCalculator.range(ChartRange.MONTH, 0, startDay = 5, today = today)

        assertEquals(LocalDate.of(2026, 9, 5), period.start)
        assertEquals(LocalDate.of(2026, 10, 4), period.endInclusive)
    }

    @Test
    fun `年维度是自然年并可跨年平移`() {
        val thisYear = ChartRangeCalculator.range(ChartRange.YEAR, 0, startDay = 1, today = today)
        val lastYear = ChartRangeCalculator.range(ChartRange.YEAR, -1, startDay = 1, today = today)

        assertEquals(LocalDate.of(2026, 1, 1), thisYear.start)
        assertEquals(LocalDate.of(2026, 12, 31), thisYear.endInclusive)
        assertEquals(LocalDate.of(2025, 1, 1), lastYear.start)
    }

    @Test
    fun `周按月按天分桶年按月分桶`() {
        val week = ChartRangeCalculator.buckets(
            ChartRange.WEEK,
            ChartRangeCalculator.range(ChartRange.WEEK, 0, 1, today),
        )
        val month = ChartRangeCalculator.buckets(
            ChartRange.MONTH,
            ChartRangeCalculator.range(ChartRange.MONTH, 0, 1, today),
        )
        val year = ChartRangeCalculator.buckets(
            ChartRange.YEAR,
            ChartRangeCalculator.range(ChartRange.YEAR, 0, 1, today),
        )

        assertEquals(7, week.size)
        assertEquals(30, month.size)
        assertEquals(12, year.size)
        assertEquals("1月", year.first().label)
        assertEquals("12月", year.last().label)
    }

    @Test
    fun `月度分桶覆盖自定义起始日的完整账期`() {
        val period = ChartRangeCalculator.range(ChartRange.MONTH, 0, startDay = 5, today = today)

        val buckets = ChartRangeCalculator.buckets(ChartRange.MONTH, period)

        // 9 月 5 日到 10 月 4 日共 30 天
        assertEquals(30, buckets.size)
        assertEquals(LocalDate.of(2026, 9, 5), buckets.first().start)
        assertEquals(LocalDate.of(2026, 10, 4), buckets.last().start)
    }

    @Test
    fun `按日期找到所属分桶`() {
        val period = ChartRangeCalculator.range(ChartRange.MONTH, 0, startDay = 5, today = today)
        val buckets = ChartRangeCalculator.buckets(ChartRange.MONTH, period)

        assertEquals(0, ChartRangeCalculator.bucketIndex(buckets, LocalDate.of(2026, 9, 5)))
        assertEquals(1, ChartRangeCalculator.bucketIndex(buckets, LocalDate.of(2026, 9, 6)))
        assertEquals(29, ChartRangeCalculator.bucketIndex(buckets, LocalDate.of(2026, 10, 4)))
        assertEquals(-1, ChartRangeCalculator.bucketIndex(buckets, LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun `年维度分桶能容纳跨月流水`() {
        val period = ChartRangeCalculator.range(ChartRange.YEAR, 0, 1, today)
        val buckets = ChartRangeCalculator.buckets(ChartRange.YEAR, period)

        assertEquals(11, ChartRangeCalculator.bucketIndex(buckets, LocalDate.of(2026, 12, 31)))
        assertEquals(-1, ChartRangeCalculator.bucketIndex(buckets, LocalDate.of(2027, 1, 1)))
    }
}
