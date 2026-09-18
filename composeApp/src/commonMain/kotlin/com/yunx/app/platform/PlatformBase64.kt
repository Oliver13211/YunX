package com.yunx.app.platform

/**
 * 跨端 Base64 接缝。
 * Android 端不能直接用 java.util.Base64（API 26+，本项目 minSdk 23），故走 expect/actual。
 */
expect object PlatformBase64 {
    /** 无换行编码（对应 android.util.Base64.NO_WRAP 语义）。 */
    fun encodeToString(data: ByteArray): String

    /** 解码，容忍标准与 URL-safe 字母表。 */
    fun decode(data: String): ByteArray
}
