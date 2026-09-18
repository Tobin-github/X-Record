package top.tobin.xrecord.core.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** 日期时间的展示与换算。全部显式传入时区与"今天"，保证可测试、不受运行环境影响。 */
object DateTimeUtils {

    fun toLocalDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun toLocalTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()

    /** 保留原时间点，只把日期换成 [date]。 */
    fun withDate(
        epochMillis: Long,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = date.atTime(toLocalTime(epochMillis, zone))
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    /** 取今天指定时刻的时间戳，用于"今天/昨天/前天"快捷选择。 */
    fun todayAt(
        time: LocalTime,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = today.atTime(time).atZone(zone).toInstant().toEpochMilli()

    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        today.minusDays(2) -> "前天"
        else -> "${date.monthValue}月${date.dayOfMonth}日"
    }

    fun fullDayLabel(date: LocalDate): String =
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"

    fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "星期一"
        DayOfWeek.TUESDAY -> "星期二"
        DayOfWeek.WEDNESDAY -> "星期三"
        DayOfWeek.THURSDAY -> "星期四"
        DayOfWeek.FRIDAY -> "星期五"
        DayOfWeek.SATURDAY -> "星期六"
        DayOfWeek.SUNDAY -> "星期日"
    }

    fun timeLabel(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = toLocalTime(epochMillis, zone)
        return "%02d:%02d".format(time.hour, time.minute)
    }

    /** 明细页顶部的账期标题。起始日为 1 时就是自然月，否则显示完整区间。 */
    fun periodLabel(period: AccountingPeriod, startDay: Int): String {
        if (PeriodCalculator.normalizeStartDay(startDay) == PeriodCalculator.MIN_START_DAY) {
            return "${period.start.year}年${period.start.monthValue}月"
        }
        val startText = if (period.start.year == period.endInclusive.year) {
            "${period.start.monthValue}月${period.start.dayOfMonth}日"
        } else {
            "${period.start.year}年${period.start.monthValue}月${period.start.dayOfMonth}日"
        }
        return "$startText - ${period.endInclusive.monthValue}月${period.endInclusive.dayOfMonth}日"
    }
}

/**
 * Material3 的日期选择器用的是 UTC 零点毫秒，而业务时间戳是本地时区。
 *
 * 直接混用会出现"选了 9 月 18 日，存进去变成 9 月 17 日"这类跨时区错账，
 * 因此两侧都经过这里显式换算。
 */
object DatePickerBridge {

    fun toPickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
