package top.tobin.xrecord.core.money

/**
 * 金额的展示与解析。金额在内存和数据库里一律是"分"（[Long]）。
 */
object MoneyFormatter {

    /** 允许的整数部分最大位数，防止用户输入天文数字后溢出。 */
    private const val MAX_INTEGER_DIGITS = 12

    private val AMOUNT_PATTERN = Regex("^\\d{1,$MAX_INTEGER_DIGITS}(\\.\\d{1,2})?$")

    /**
     * 分 → 带千分位的字符串，不含货币符号。例如 `123456` → `"1,234.56"`。
     *
     * 手写而不用 DecimalFormat，是为了不受运行环境 Locale 影响：同一个数字在任何设备上
     * 都得到同样的输出，单元测试也不必依赖 JVM 默认区域。
     */
    fun format(cents: Long): String {
        val negative = cents < 0
        val absolute = if (negative) -cents else cents
        val yuan = absolute / 100
        val fen = absolute % 100
        val grouped = yuan.toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()

        return buildString {
            if (negative) append('-')
            append(grouped)
            append('.')
            append(fen.toString().padStart(2, '0'))
        }
    }

    /** 不带千分位的字符串，用于回填到输入框（"1,234.56" 无法被键盘继续编辑）。 */
    fun toPlainString(cents: Long): String {
        val negative = cents < 0
        val absolute = if (negative) -cents else cents
        val text = "${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
        return if (negative) "-$text" else text
    }

    /**
     * 用户输入 → 分。无法解析或超过两位小数时返回 null。
     *
     * 不接受科学计数法、负号、千分位以外的符号，避免 `input` 之类的东西被当成金额。
     */
    fun parseToCents(text: String): Long? {
        val normalized = text.trim().replace(",", "")
        if (!AMOUNT_PATTERN.matches(normalized)) return null

        val parts = normalized.split('.')
        val yuan = parts[0].toLongOrNull() ?: return null
        // "12.3" 要按 1230 分处理，右侧补零而不是直接拼字符串
        val fen = parts.getOrNull(1)?.padEnd(2, '0')?.toLongOrNull() ?: 0L
        return yuan * 100 + fen
    }
}
