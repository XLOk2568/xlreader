package com.xialiangok.xlreader.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** 剥开 ContextWrapper 包装层，找到真正的 Activity（调屏幕亮度要用它）。 */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
