package top.tobin.xrecord.core.util

import java.time.LocalDate
import java.time.YearMonth

/** 日期选择时的取值规则。 */
object DateSelection {

    /**
     * 把"年月日"收拢到该月合法范围内。
     *
     * 滚轮选择器里日和年月是联动的：用户从 3 月 31 日把月份滚到 2 月时，
     * 3 月 31 日这个组合已经不存在了，必须收成 2 月 28 日（闰年 29 日），
     * 否则会直接抛 `DateTimeException`。
     */
    fun clampToValidDay(year: Int, month: Int, day: Int): LocalDate {
        val maxDay = YearMonth.of(year, month).lengthOfMonth()
        return LocalDate.of(year, month, day.coerceIn(1, maxDay))
    }
}
