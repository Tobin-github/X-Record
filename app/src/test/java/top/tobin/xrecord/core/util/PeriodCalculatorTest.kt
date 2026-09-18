package top.tobin.xrecord.core.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodCalculatorTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `起始日为 1 时等同于自然月`() {
        val period = PeriodCalculator.of(LocalDate.of(2026, 9, 18), startDay = 1)

        assertEquals(LocalDate.of(2026, 9, 1), period.start)
        assertEquals(LocalDate.of(2026, 9, 30), period.endInclusive)
        assertEquals("2026-09", period.key)
    }

    @Test
    fun `起始日为 5 时账期跨越自然月`() {
        val period = PeriodCalculator.of(LocalDate.of(2026, 9, 18), startDay = 5)

        assertEquals(LocalDate.of(2026, 9, 5), period.start)
        assertEquals(LocalDate.of(2026, 10, 4), period.endInclusive)
    }

    @Test
    fun `起始日当天归属新账期`() {
        val period = PeriodCalculator.of(LocalDate.of(2026, 9, 5), startDay = 5)

        assertEquals(LocalDate.of(2026, 9, 5), period.start)
        assertEquals(LocalDate.of(2026, 10, 4), period.endInclusive)
    }

    @Test
    fun `起始日前一天归属上一个账期`() {
        val period = PeriodCalculator.of(LocalDate.of(2026, 9, 4), startDay = 5)

        assertEquals(LocalDate.of(2026, 8, 5), period.start)
        assertEquals(LocalDate.of(2026, 9, 4), period.endInclusive)
    }

    @Test
    fun `起始日为 28 时平年二月不越界`() {
        val before = PeriodCalculator.of(LocalDate.of(2026, 2, 27), startDay = 28)
        val on = PeriodCalculator.of(LocalDate.of(2026, 2, 28), startDay = 28)

        assertEquals(LocalDate.of(2026, 1, 28), before.start)
        assertEquals(LocalDate.of(2026, 2, 27), before.endInclusive)

        assertEquals(LocalDate.of(2026, 2, 28), on.start)
        assertEquals(LocalDate.of(2026, 3, 27), on.endInclusive)
    }

    @Test
    fun `起始日为 28 时闰年二月廿九仍在同一账期`() {
        val period = PeriodCalculator.of(LocalDate.of(2028, 2, 29), startDay = 28)

        assertEquals(LocalDate.of(2028, 2, 28), period.start)
        assertEquals(LocalDate.of(2028, 3, 27), period.endInclusive)
    }

    @Test
    fun `起始日超出范围时被钳制到合法区间`() {
        assertEquals(28, PeriodCalculator.normalizeStartDay(31))
        assertEquals(1, PeriodCalculator.normalizeStartDay(0))
        assertEquals(15, PeriodCalculator.normalizeStartDay(15))
    }

    @Test
    fun `连续向后平移账期仍能正确回到月末`() {
        // 回归测试：若用 end.plusMonths 推算，这里会退化成 2026-03-28
        val january = PeriodCalculator.of(LocalDate.of(2026, 1, 31), startDay = 1)
        val february = PeriodCalculator.shift(january, 1)
        val march = PeriodCalculator.shift(february, 1)

        assertEquals(LocalDate.of(2026, 2, 1), february.start)
        assertEquals(LocalDate.of(2026, 2, 28), february.endInclusive)

        assertEquals(LocalDate.of(2026, 3, 1), march.start)
        assertEquals(LocalDate.of(2026, 3, 31), march.endInclusive)
    }

    @Test
    fun `向前平移账期可跨年`() {
        val january = PeriodCalculator.of(LocalDate.of(2026, 1, 10), startDay = 5)
        val december = PeriodCalculator.shift(january, -1)

        assertEquals(LocalDate.of(2025, 12, 5), december.start)
        assertEquals(LocalDate.of(2026, 1, 4), december.endInclusive)
    }

    @Test
    fun `时间戳按设定时区归属账期`() {
        // 上海时间 9 月 4 日 23:30，仍在起始日为 5 的上一个账期内
        val epochMillis = LocalDateTime.of(2026, 9, 4, 23, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val period = PeriodCalculator.of(epochMillis, startDay = 5, zone = zone)

        assertEquals(LocalDate.of(2026, 8, 5), period.start)
        assertEquals(LocalDate.of(2026, 9, 4), period.endInclusive)
    }

    @Test
    fun `账期起止时间戳能还原出同一个账期`() {
        val period = PeriodCalculator.of(LocalDate.of(2026, 9, 18), startDay = 5)

        val fromStart = PeriodCalculator.of(period.startEpochMillis(zone), 5, zone)
        // endExclusive 属于下一个账期，佐证区间是左闭右开
        val fromEndExclusive = PeriodCalculator.of(
            period.endExclusiveEpochMillis(zone),
            5,
            zone,
        )

        assertEquals(period, fromStart)
        assertEquals(LocalDate.of(2026, 10, 5), fromEndExclusive.start)
    }
}
