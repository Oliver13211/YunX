package com.yunx.app.platform

import android.util.Log

actual object YunXLog {
    // 块级函数体：Log.x 返回 Int（写入条数），接缝约定返回 Unit
    actual fun d(tag: String, msg: String) { Log.d(tag, msg) }
    actual fun i(tag: String, msg: String) { Log.i(tag, msg) }
    actual fun w(tag: String, msg: String) { Log.w(tag, msg) }
}
