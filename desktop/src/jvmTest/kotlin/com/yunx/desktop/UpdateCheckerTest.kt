/*
 * YunX Desktop - 跨平台桌面版（KMP + Compose Multiplatform）
 * Copyright (C) 2026 Oliver13211
 * AGPL-3.0 licensed. 派生自 YunX（云析）Android 项目（CYQawa 著）。
 */

package com.yunx.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    // ---------- 版本比较 ----------

    @Test
    fun `新版本各段更高时判定为更新`() {
        assertTrue(UpdateChecker.isNewer("0.2.0", "0.1.0"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.9.9"))
        assertTrue(UpdateChecker.isNewer("0.1.1", "0.1.0"))
        assertTrue(UpdateChecker.isNewer("0.10.0", "0.9.0"))
    }

    @Test
    fun `相同或更低版本不提示更新`() {
        assertFalse(UpdateChecker.isNewer("0.1.0", "0.1.0"))
        assertFalse(UpdateChecker.isNewer("0.1.0", "0.2.0"))
        assertFalse(UpdateChecker.isNewer("0.0.9", "0.1.0"))
    }

    @Test
    fun `缺段按零处理`() {
        assertTrue(UpdateChecker.isNewer("0.2", "0.1.9"))
        assertFalse(UpdateChecker.isNewer("0.1", "0.1.0"))
    }

    @Test
    fun `非数字段过滤后比较`() {
        assertTrue(UpdateChecker.isNewer("0.2.0-rc1", "0.1.0"))
    }

    // ---------- JSON 解析 ----------

    @Test
    fun `desktop-v 标签解析出版本与下载地址`() {
        val json = """
            {"url":"x","tag_name":"desktop-v0.2.0","name":"YunX Desktop 0.2.0",
             "html_url":"https://github.com/Oliver13211/YunX/releases/tag/desktop-v0.2.0",
             "body":"### 更新\n* 修复崩溃"}
        """.trimIndent()
        val update = UpdateChecker.parse(json)!!
        assertEquals("0.2.0", update.version)
        assertEquals("https://github.com/Oliver13211/YunX/releases/tag/desktop-v0.2.0", update.downloadUrl)
        assertTrue(update.notes.contains("修复崩溃"))
        assertTrue(UpdateChecker.isNewer(update.version, "0.1.0"))
    }

    @Test
    fun `非 desktop 标签忽略（上游 Android 发布不参与）`() {
        assertNull(UpdateChecker.parse("""{"tag_name":"v1.2.3","body":"android"}"""))
        assertNull(UpdateChecker.parse("""{"tag_name":"desktop-0.2.0","body":"x"}"""))
    }

    @Test
    fun `无 tag_name 返回 null`() {
        assertNull(UpdateChecker.parse("""{"message":"Not Found"}"""))
    }
}
