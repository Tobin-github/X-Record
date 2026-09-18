package top.tobin.xrecord.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 一个账期，左闭右闭的日期区间。例如每月起始日为 5 时，9 月账期为 09-05 ~ 10-04。
 */
data class AccountingPeriod(
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    /** 右开区间端点，便于构造 `occurredAt < endExclusive` 的查询。 */
    val endExclusive: LocalDate get() = endInclusive.plusDays(1)

    /** 账期标识，取起始月，例如 "2026-09"。 */
    val key: String get() = "%04d-%02d".format(start.year, start.monthValue)

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(endInclusive)

    fun startEpochMillis(zone: ZoneId): Long =
        start.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endExclusiveEpochMillis(zone: ZoneId): Long =
        endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
}

/**
 * 账期计算，全应用唯一的"每月起始日"实现。
 *
 * 明细分组、图表聚合、预算周期必须全部经过这里。各页面自行用 `LocalDate.now().month`
 * 计算正是本类要消除的隐患：一旦用户把起始日改成 5 号，那些地方会与账单页统计口径不一致。
 */
object PeriodCalculator {

    const val MIN_START_DAY = 1

    /**
     * 起始日上限取 28 而非 31。
     *
     * 取 28 可以让"上月同日"在任何月份都存在，彻底规避 31 号在 2 月的补位规则歧义。
     */
    const val MAX_START_DAY = 28

    fun normalizeStartDay(day: Int): Int = day.coerceIn(MIN_START_DAY, MAX_START_DAY)

    fun of(date: LocalDate, startDay: Int): AccountingPeriod {
        val day = normalizeStartDay(startDay)
        // day 恒在 1..28，withDayOfMonth 不会越界，因此无需处理月末补位
        val start = if (date.dayOfMonth >= day) {
            date.withDayOfMonth(day)
        } else {
            date.minusMonths(1).withDayOfMonth(day)
        }
        return AccountingPeriod(start, start.plusMonths(1).minusDays(1))
    }

    fun of(
        epochMillis: Long,
        startDay: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): AccountingPeriod = of(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate(), startDay)

    fun currentPeriod(
        startDay: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): AccountingPeriod = of(LocalDate.now(zone), startDay)

    /**
     * 前后平移账期。
     *
     * 必须从 [AccountingPeriod.start] 推算新区间的结束日，不能直接对 end 调 `plusMonths`：
     * 起始日为 1 时 end 是月末（如 1-31），`plusMonths` 会把 2 月钳到 28 号，
     * 再平移一次就会得到 3-28 这种错误结果。
     */
    fun shift(period: AccountingPeriod, months: Long): AccountingPeriod {
        val newStart = period.start.plusMonths(months)
        return AccountingPeriod(newStart, newStart.plusMonths(1).minusDays(1))
    }
}
