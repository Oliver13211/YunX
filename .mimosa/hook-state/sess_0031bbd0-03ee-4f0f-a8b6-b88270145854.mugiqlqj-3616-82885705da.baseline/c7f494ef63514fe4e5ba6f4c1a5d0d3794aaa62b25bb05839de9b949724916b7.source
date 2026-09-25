package com.yunx.app.platform

import java.util.Base64

actual object PlatformBase64 {
    actual fun encodeToString(data: ByteArray): String =
        Base64.getEncoder().encodeToString(data)

    actual fun decode(data: String): ByteArray {
        // URL-safe 字母表（含 - 或 _）走 URL 解码器，与 Android 端 Base64.DEFAULT 的宽松行为对齐
        val decoder = if (data.contains('-') || data.contains('_')) Base64.getUrlDecoder() else Base64.getDecoder()
        return decoder.decode(data)
    }
}
