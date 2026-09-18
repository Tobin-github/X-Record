package top.tobin.xrecord.core.util

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import top.tobin.xrecord.data.local.entity.RecurringFrequency

class RecurringScheduleTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millisOf(dateTime: LocalDateTime): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    private fun dateTimeOf(millis: Long): LocalDateTime =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    private fun next(
        from: LocalDateTime,
        frequency: RecurringFrequency,
        interval: Int = 1,
        anchorDay: Int = 1,
    ): LocalDateTime = dateTimeOf(
        RecurringSchedule.nextOccurrence(millisOf(from), frequency, interval, anchorDay, zone),
    )

    @Test
    fun `每月 31 日在短月钳位后能回到 31 日`() {
        val january = LocalDateTime.of(2026, 1, 31, 9, 0)

        val february = next(january, RecurringFrequency.MONTHLY, anchorDay = 31)
        val march = next(february, RecurringFrequency.MONTHLY, anchorDay = 31)

        assertEquals(LocalDateTime.of(2026, 2, 28, 9, 0), february)
        // 若只按上一次结果递推，这里会退化成 3 月 28 日
        assertEquals(LocalDateTime.of(2026, 3, 31, 9, 0), march)
    }

    @Test
    fun `每月 30 日在二月钳位到月末`() {
        val january = LocalDateTime.of(2026, 1, 30, 8, 30)

        assertEquals(
            LocalDateTime.of(2026, 2, 28, 8, 30),
            next(january, RecurringFrequency.MONTHLY, anchorDay = 30),
        )
    }

    @Test
    fun `闰年二月二十九日按锚定日处理`() {
        val january = LocalDateTime.of(2028, 1, 31, 0, 0)

        assertEquals(
            LocalDateTime.of(2028, 2, 29, 0, 0),
            next(january, RecurringFrequency.MONTHLY, anchorDay = 31),
        )
    }

    @Test
    fun `每日与每周按间隔累加`() {
        val start = LocalDateTime.of(2026, 9, 18, 12, 0)

        assertEquals(
            LocalDateTime.of(2026, 9, 20, 12, 0),
            next(start, RecurringFrequency.DAILY, interval = 2),
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 2, 12, 0),
            next(start, RecurringFrequency.WEEKLY, interval = 2),
        )
    }

    @Test
    fun `每年按锚定日递推并跨年`() {
        val start = LocalDateTime.of(2026, 3, 15, 7, 0)

        assertEquals(
            LocalDateTime.of(2027, 3, 15, 7, 0),
            next(start, RecurringFrequency.YEARLY, anchorDay = 15),
        )
    }

    @Test
    fun `间隔小于一时按一处理，不会原地打转`() {
        val start = LocalDateTime.of(2026, 9, 18, 12, 0)

        assertEquals(
            LocalDateTime.of(2026, 9, 19, 12, 0),
            next(start, RecurringFrequency.DAILY, interval = 0),
        )
    }

    @Test
    fun `保留原有时间点而不是归零`() {
        val start = LocalDateTime.of(2026, 9, 18, 23, 45)

        val nextDay = next(start, RecurringFrequency.DAILY)

        assertEquals(23, nextDay.hour)
        assertEquals(45, nextDay.minute)
    }
}
