package top.tobin.xrecord.core.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateSelectionTest {

    @Test
    fun `合法日期原样返回`() {
        assertEquals(
            LocalDate.of(2026, 9, 18),
            DateSelection.clampToValidDay(2026, 9, 18),
        )
    }

    @Test
    fun `三月三十一日切到二月会被收拢到月末`() {
        // 滚轮里把月份从 3 滚到 2 时会走到这里，否则 LocalDate.of 直接抛异常
        assertEquals(
            LocalDate.of(2026, 2, 28),
            DateSelection.clampToValidDay(2026, 2, 31),
        )
    }

    @Test
    fun `闰年二月收拢到二十九日`() {
        assertEquals(
            LocalDate.of(2028, 2, 29),
            DateSelection.clampToValidDay(2028, 2, 31),
        )
    }

    @Test
    fun `三十日的月份收拢到三十号`() {
        assertEquals(
            LocalDate.of(2026, 4, 30),
            DateSelection.clampToValidDay(2026, 4, 31),
        )
    }

    @Test
    fun `日小于一时收拢到一号`() {
        assertEquals(
            LocalDate.of(2026, 9, 1),
            DateSelection.clampToValidDay(2026, 9, 0),
        )
    }
}
