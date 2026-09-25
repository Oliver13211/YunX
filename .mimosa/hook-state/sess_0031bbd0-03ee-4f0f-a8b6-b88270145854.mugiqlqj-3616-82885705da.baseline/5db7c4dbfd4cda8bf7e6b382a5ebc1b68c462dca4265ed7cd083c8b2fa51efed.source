package com.yunx.app.platform

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** PlatformBase64 接缝测试：jvmSharedTest 按目标分别编译，可在 Android 与桌面 JVM 上各自验证 actual。 */
class PlatformBase64Test {
    @Test
    fun roundTrip() {
        val data = "yunx-云析-base64:😀".toByteArray(Charsets.UTF_8)
        val encoded = PlatformBase64.encodeToString(data)
        assertArrayEquals(data, PlatformBase64.decode(encoded))
    }

    @Test
    fun encodeIsNoWrap() {
        // 足够长以触发换行（若误用带换行模式则不含换行断言失败）
        val data = ByteArray(64) { it.toByte() }
        val encoded = PlatformBase64.encodeToString(data)
        assertFalse("编码结果不应包含换行", encoded.contains('\n') || encoded.contains('\r'))
        assertEquals(88, encoded.length)
    }

    @Test
    fun decodeAcceptsUrlSafeAlphabet() {
        // "-_" 为 URL-safe 字母表特征；Android 端 Base64.DEFAULT 宽松接受，桌面端需 URL 解码器
        val standard = PlatformBase64.encodeToString(byteArrayOf(251.toByte(), 239.toByte(), 190.toByte()))
        val urlSafe = standard.replace('+', '-').replace('/', '_')
        assertArrayEquals(byteArrayOf(251.toByte(), 239.toByte(), 190.toByte()), PlatformBase64.decode(urlSafe))
    }
}
