package top.tobin.xrecord.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** 从任意 Context 向上找到宿主 Activity，用于需要 Activity 的 API（例如生物识别）。 */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
