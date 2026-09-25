package com.yunx.app.platform

/** 跨端日志接缝：调用侧约定见 Agent.md §3.6（URL/Cookie/token 必须先过 LogRedactor）。 */
expect object YunXLog {
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String)
    fun e(tag: String, msg: String, tr: Throwable? = null)
}
