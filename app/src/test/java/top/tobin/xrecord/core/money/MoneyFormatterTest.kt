package top.tobin.xrecord.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun `格式化补足两位小数`() {
        assertEquals("0.00", MoneyFormatter.format(0))
        assertEquals("0.05", MoneyFormatter.format(5))
        assertEquals("0.50", MoneyFormatter.format(50))
        assertEquals("12.34", MoneyFormatter.format(1234))
    }

    @Test
    fun `格式化添加千分位`() {
        assertEquals("1,234.56", MoneyFormatter.format(123456))
        assertEquals("1,000,000.00", MoneyFormatter.format(100_000_000))
        assertEquals("999.99", MoneyFormatter.format(99_999))
    }

    @Test
    fun `负数金额保留符号`() {
        assertEquals("-0.01", MoneyFormatter.format(-1))
        assertEquals("-1,234.56", MoneyFormatter.format(-123456))
    }

    @Test
    fun `解析带小数的输入`() {
        assertEquals(1234L, MoneyFormatter.parseToCents("12.34"))
        assertEquals(1230L, MoneyFormatter.parseToCents("12.3"))
        assertEquals(1200L, MoneyFormatter.parseToCents("12"))
        assertEquals(5L, MoneyFormatter.parseToCents("0.05"))
        assertEquals(0L, MoneyFormatter.parseToCents("0"))
    }

    @Test
    fun `解析时容忍千分位与首尾空格`() {
        assertEquals(123456L, MoneyFormatter.parseToCents(" 1,234.56 "))
    }

    @Test
    fun `拒绝非法输入`() {
        assertNull(MoneyFormatter.parseToCents(""))
        assertNull(MoneyFormatter.parseToCents("abc"))
        assertNull(MoneyFormatter.parseToCents("-5"))
        assertNull(MoneyFormatter.parseToCents("12.345"))
        assertNull(MoneyFormatter.parseToCents("1.2.3"))
        assertNull(MoneyFormatter.parseToCents("1e5"))
        assertNull(MoneyFormatter.parseToCents("1234567890123"))
    }

    @Test
    fun `解析与格式化可以往返`() {
        listOf(0L, 1L, 99L, 100L, 1234L, 100_000_000L).forEach { cents ->
            assertEquals(cents, MoneyFormatter.parseToCents(MoneyFormatter.format(cents)))
        }
    }
}
