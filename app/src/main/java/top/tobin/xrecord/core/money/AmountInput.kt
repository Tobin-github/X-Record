package top.tobin.xrecord.core.money

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 记账键盘的输入状态机与表达式求值。
 *
 * 键盘支持 `+ - × ÷` 连续运算。全部计算走 [BigDecimal]：用 Double 计算时
 * `0.1 + 0.2` 会得到 0.30000000000000004，落到金额上就是实实在在的错账。
 */
object AmountInput {

    private val OPERATORS = charArrayOf('+', '-', '×', '÷')
    private const val MAX_INTEGER_DIGITS = 9
    private const val MAX_DECIMALS = 2

    fun isOperator(char: Char): Boolean = char in OPERATORS

    fun appendDigit(current: String, digit: Char): String {
        require(digit.isDigit())

        val trailing = current.trailingNumber()
        if (trailing.contains('.')) {
            if (trailing.substringAfter('.').length >= MAX_DECIMALS) return current
        } else if (trailing.length >= MAX_INTEGER_DIGITS) {
            return current
        }

        return when {
            // 避免出现 "007" 这类输入
            current == "0" -> digit.toString()
            // 运算符后面开始一个新数字，同样不允许前导零
            current.isEmpty() || current.last() in OPERATORS -> current + digit
            else -> current + digit
        }
    }

    fun appendDecimalPoint(current: String): String {
        val trailing = current.trailingNumber()
        if (trailing.contains('.')) return current

        return when {
            current.isEmpty() -> "0."
            current.last() in OPERATORS -> current + "0."
            else -> "$current."
        }
    }

    fun appendOperator(current: String, operator: Char): String {
        require(isOperator(operator))
        if (current.isEmpty()) return current

        // 连按运算符时替换掉上一个，而不是堆成 "1+×"
        if (current.last() in OPERATORS) {
            return current.dropLast(1) + operator
        }
        // "12." 后面接运算符时，先补全成 "12"
        if (current.endsWith(".")) {
            return current.dropLast(1) + operator
        }
        return current + operator
    }

    fun backspace(current: String): String {
        val next = current.dropLast(1)
        return when {
            next.isEmpty() -> "0"
            else -> next
        }
    }

    /**
     * 求值，返回分。表达式非法、除零或结果超出范围时返回 null。
     *
     * 结果允许为负：是否合法由调用方判断（记账金额必须为正）。
     */
    fun evaluate(expression: String): Long? {
        val tokens = tokenize(expression.trim().trimEnd(*OPERATORS)) ?: return null
        val result = calculate(tokens) ?: return null

        val cents = try {
            result.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (exception: ArithmeticException) {
            return null
        }
        return cents
    }

    /** 表达式是否以运算符结尾，即用户还没输入完整。 */
    fun isIncomplete(expression: String): Boolean =
        expression.isEmpty() || expression.last() in OPERATORS

    private fun String.trailingNumber(): String =
        substringAfterLast('+').substringAfterLast('-').substringAfterLast('×').substringAfterLast('÷')

    private fun tokenize(expression: String): List<String>? {
        if (expression.isEmpty()) return null

        val tokens = mutableListOf<String>()
        val number = StringBuilder()

        for (char in expression) {
            when {
                char.isDigit() || char == '.' -> number.append(char)
                isOperator(char) -> {
                    if (number.isEmpty()) return null
                    tokens += number.toString()
                    number.clear()
                    tokens += char.toString()
                }
                else -> return null
            }
        }
        if (number.isNotEmpty()) tokens += number.toString()

        if (tokens.isEmpty() || tokens.last().length == 1 && isOperator(tokens.last()[0])) return null
        return tokens
    }

    private fun calculate(tokens: List<String>): BigDecimal? {
        // 第一遍处理 × ÷，第二遍处理 + −
        val reduced = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token == "×" || token == "÷") {
                val left = reduced.removeLastOrNull()?.toBigDecimalOrNull() ?: return null
                val right = tokens.getOrNull(index + 1)?.toBigDecimalOrNull() ?: return null
                val value = when (token) {
                    "×" -> left.multiply(right)
                    else -> {
                        if (right.compareTo(BigDecimal.ZERO) == 0) return null
                        left.divide(right, MathContext(16, RoundingMode.HALF_UP))
                    }
                }
                reduced += value.toPlainString()
                index += 2
            } else {
                reduced += token
                index += 1
            }
        }

        var result = reduced.firstOrNull()?.toBigDecimalOrNull() ?: return null
        var position = 1
        while (position < reduced.size) {
            val operator = reduced[position]
            val operand = reduced.getOrNull(position + 1)?.toBigDecimalOrNull() ?: return null
            result = when (operator) {
                "+" -> result.add(operand)
                "-" -> result.subtract(operand)
                else -> return null
            }
            position += 2
        }
        return result
    }
}
