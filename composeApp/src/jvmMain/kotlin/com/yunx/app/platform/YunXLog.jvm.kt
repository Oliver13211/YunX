package com.yunx.app.platform

import java.util.logging.Level
import java.util.logging.Logger

actual object YunXLog {
    private val logger = Logger.getLogger("com.yunx.app")

    actual fun d(tag: String, msg: String) = logger.fine("$tag: $msg")
    actual fun i(tag: String, msg: String) = logger.info("$tag: $msg")
    actual fun w(tag: String, msg: String) = logger.log(Level.WARNING, "$tag: $msg")
}
