/*
 * YunX Desktop - 跨平台桌面版（KMP + Compose Multiplatform）
 * Copyright (C) 2026 Oliver13211
 * AGPL-3.0 licensed. 派生自 YunX（云析）Android 项目（CYQawa 著）。
 */

package com.yunx.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 更新检查：查询 GitHub Releases 最新发布（`desktop-v*` 标签才参与，
 * 与上游 Android 发布互不干扰），版本号高于当前时提示前往下载。
 *
 * 失败（无网络/限流）一律静默返回 null——更新检查绝不能影响正常使用。
 * 受限网络可用 YUNX_PROXY（main() 已注入系统属性，OkHttp 自动跟随）。
 */
object UpdateChecker {

    private const val API_URL = "https://api.github.com/repos/${AppInfo.GITHUB_REPO}/releases/latest"

    data class Update(val version: String, val downloadUrl: String, val notes: String)

    suspend fun check(currentVersion: String = AppInfo.VERSION): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "${AppInfo.APP_NAME}/${AppInfo.VERSION}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                parse(body)?.takeIf { isNewer(it.version, currentVersion) }
            }
        }.getOrNull()
    }

    /** 宽松解析：只取 tag_name 与 body 两个字段，避免引 JSON 依赖 */
    internal fun parse(json: String): Update? {
        val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: return null
        if (!tag.startsWith(AppInfo.RELEASE_TAG_PREFIX)) return null
        val version = tag.removePrefix(AppInfo.RELEASE_TAG_PREFIX)
        val notes = Regex("\"body\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)
            ?.groupValues?.get(1)
            ?.unescapeJson()
            ?.trim()
            ?.take(600)
            .orEmpty()
        return Update(
            version = version,
            downloadUrl = "https://github.com/${AppInfo.GITHUB_REPO}/releases/tag/$tag",
            notes = notes
        )
    }

    /** x.y.z 数值逐段比较，缺段按 0 处理；相等不算更新 */
    internal fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val currentParts = current.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    private fun String.unescapeJson(): String =
        replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\t", "  ")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
}
