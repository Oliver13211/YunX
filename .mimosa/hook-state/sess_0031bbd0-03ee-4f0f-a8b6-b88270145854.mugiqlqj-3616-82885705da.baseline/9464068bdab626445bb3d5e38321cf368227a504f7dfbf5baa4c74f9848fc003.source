package com.yunx.app.platform

import android.util.Base64

actual object PlatformBase64 {
    actual fun encodeToString(data: ByteArray): String =
        Base64.encodeToString(data, Base64.NO_WRAP)

    actual fun decode(data: String): ByteArray =
        Base64.decode(data, Base64.DEFAULT)
}
