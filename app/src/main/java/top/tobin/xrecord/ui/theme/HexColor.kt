package top.tobin.xrecord.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 解析数据库中存的 `#RRGGBB` 颜色。
 *
 * 数据来自用户可编辑的分类/账户，格式非法时回退到 [fallback]，不允许因为一条脏数据崩溃。
 */
fun parseHexColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (exception: IllegalArgumentException) {
        fallback
    }
}

/** 在该颜色上可读的前景色：亮色底用深色字，暗色底用白色字。 */
fun Color.onColorFor(): Color =
    if (luminance() > 0.5f) Color(0xFF1B1B1B) else Color.White
