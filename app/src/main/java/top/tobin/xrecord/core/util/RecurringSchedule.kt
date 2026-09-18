package top.tobin.xrecord.core.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import top.tobin.xrecord.data.local.entity.RecurringFrequency

/**
 * 定期账单的下一次触发时间。
 *
 * 关键点在"每月 N 日"：直接对时间戳做 `plusMonths(1)` 会一路漂移——
 * 1 月 31 日推到 2 月 28 日，再推就变成 3 月 28 日，本该是 3 月 31 日。
 * 因此递推时始终以用户设定的锚定日为准，在短月钳位、到大月再回到原位。
 */
object RecurringSchedule {

    fun nextOccurrence(
        current: Long,
        frequency: RecurringFrequency,
        interval: Int,
        anchorDay: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val step = interval.coerceAtLeast(1).toLong()
        val dateTime = Instant.ofEpochMilli(current).atZone(zone).toLocalDateTime()

        val next = when (frequency) {
            RecurringFrequency.DAILY -> dateTime.plusDays(step)
            RecurringFrequency.WEEKLY -> dateTime.plusWeeks(step)
            RecurringFrequency.MONTHLY -> dateTime.plusMonths(step).withAnchorDay(anchorDay)
            RecurringFrequency.YEARLY -> dateTime.plusYears(step).withAnchorDay(anchorDay)
        }
        return next.atZone(zone).toInstant().toEpochMilli()
    }

    private fun LocalDateTime.withAnchorDay(anchorDay: Int): LocalDateTime =
        withDayOfMonth(anchorDay.coerceIn(1, toLocalDate().lengthOfMonth()))
}
