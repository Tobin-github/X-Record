package top.tobin.xrecord.core.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DateTimeUtilsTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 9, 18)

    private fun epochOf(dateTime: LocalDateTime): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `近三天显示相对日期`() {
        assertEquals("今天", DateTimeUtils.dayLabel(today, today))
        assertEquals("昨天", DateTimeUtils.dayLabel(today.minusDays(1), today))
        assertEquals("前天", DateTimeUtils.dayLabel(today.minusDays(2), today))
        assertEquals("9月15日", DateTimeUtils.dayLabel(today.minusDays(3), today))
        assertEquals("8月31日", DateTimeUtils.dayLabel(LocalDate.of(2026, 8, 31), today))
    }

    @Test
    fun `星期与时间格式固定`() {
        assertEquals("星期五", DateTimeUtils.weekdayLabel(today))
        assertEquals("星期日", DateTimeUtils.weekdayLabel(LocalDate.of(2026, 9, 20)))
        assertEquals("09:05", DateTimeUtils.timeLabel(epochOf(LocalDateTime.of(2026, 9, 18, 9, 5)), zone))
    }

    @Test
    fun `改日期保留原有时分`() {
        val original = epochOf(LocalDateTime.of(2026, 9, 18, 14, 30))

        val changed = DateTimeUtils.withDate(original, LocalDate.of(2026, 8, 1), zone)

        val local = Instant.ofEpochMilli(changed).atZone(zone).toLocalDateTime()
        assertEquals(LocalDateTime.of(2026, 8, 1, 14, 30), local)
    }

    @Test
    fun `起点为 1 号时账期标题是自然月`() {
        val period = PeriodCalculator.of(today, startDay = 1)

        assertEquals("2026年9月", DateTimeUtils.periodLabel(period, startDay = 1))
    }

    @Test
    fun `自定义起始日时账期标题显示完整区间`() {
        val period = PeriodCalculator.of(today, startDay = 5)

        assertEquals("9月5日 - 10月4日", DateTimeUtils.periodLabel(period, startDay = 5))
    }

    @Test
    fun `跨年账期标题带上年份`() {
        val period = PeriodCalculator.of(LocalDate.of(2026, 1, 3), startDay = 5)

        assertEquals("2025年12月5日 - 1月4日", DateTimeUtils.periodLabel(period, startDay = 5))
    }

    @Test
    fun `日期选择器换算不跨时区偏移`() {
        val date = LocalDate.of(2026, 9, 18)

        val millis = DatePickerBridge.toPickerMillis(date)

        assertEquals(date, DatePickerBridge.fromPickerMillis(millis))
    }

}
