package top.tobin.xrecord.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountInputTest {

    @Test
    fun `首位零被替换而不是保留`() {
        var value = "0"
        value = AmountInput.appendDigit(value, '7')
        assertEquals("7", value)
    }

    @Test
    fun `连续按零仍保持单个零`() {
        assertEquals("0", AmountInput.appendDigit("0", '0'))
    }

    @Test
    fun `小数点最多两位`() {
        var value = "1"
        value = AmountInput.appendDecimalPoint(value)
        value = AmountInput.appendDigit(value, '2')
        value = AmountInput.appendDigit(value, '3')
        value = AmountInput.appendDigit(value, '4')

        assertEquals("1.23", value)
        // 已经有小数点时再按一次不生效
        assertEquals("1.23", AmountInput.appendDecimalPoint(value))
    }

    @Test
    fun `运算符后按小数点自动补零`() {
        assertEquals("12+0.", AmountInput.appendDecimalPoint("12+"))
    }

    @Test
    fun `连按运算符时替换上一个`() {
        assertEquals("12×", AmountInput.appendOperator("12+", '×'))
        assertEquals("12+", AmountInput.appendOperator("12.", '+'))
    }

    @Test
    fun `退格删空后回到零`() {
        assertEquals("0", AmountInput.backspace("1"))
        assertEquals("0", AmountInput.backspace("0"))
        assertEquals("12", AmountInput.backspace("123"))
    }

    @Test
    fun `整数位数超限后不再追加`() {
        val value = "123456789"
        assertEquals(value, AmountInput.appendDigit(value, '0'))
    }

    @Test
    fun `加法求值`() {
        assertEquals(3000L, AmountInput.evaluate("10+20"))
    }

    @Test
    fun `乘法优先于加法`() {
        // 10 + 2 × 3 = 16
        assertEquals(1600L, AmountInput.evaluate("10+2×3"))
    }

    @Test
    fun `小数运算不出现浮点误差`() {
        // 用 Double 计算会得到 0.30000000000000004
        assertEquals(30L, AmountInput.evaluate("0.1+0.2"))
    }

    @Test
    fun `除法四舍五入到分`() {
        assertEquals(3333L, AmountInput.evaluate("100÷3"))
        assertEquals(333L, AmountInput.evaluate("10÷3"))
    }

    @Test
    fun `除以零返回空`() {
        assertNull(AmountInput.evaluate("10÷0"))
    }

    @Test
    fun `结果可为负由调用方判断`() {
        assertEquals(-300L, AmountInput.evaluate("5-8"))
    }

    @Test
    fun `未完成的表达式按可求值部分计算`() {
        assertEquals(1200L, AmountInput.evaluate("12+"))
        assertTrue(AmountInput.isIncomplete("12+"))
        assertEquals(1234L, AmountInput.evaluate("12.34"))
    }

    @Test
    fun `非法表达式返回空`() {
        assertNull(AmountInput.evaluate(""))
        assertNull(AmountInput.evaluate("+"))
        assertNull(AmountInput.evaluate("1.2.3"))
    }
}
