/*
 * YunX Desktop - 跨平台桌面版（KMP + Compose Multiplatform）
 * Copyright (C) 2026 Oliver13211
 * AGPL-3.0 licensed. 派生自 YunX（云析）Android 项目（CYQawa 著）。
 */

package com.yunx.desktop

import com.yunx.app.data.download.DownloadEnvironment
import java.io.File

/**
 * 桌面端下载环境（Agent.md §10.3 接缝的桌面 actual）：
 * - 缓存/临时目录收敛到 ~/.yunx 下，与数据库同根，便于整体清理；
 * - WakeLock/前台服务通知为空实现（桌面常驻前台，进度由窗口/托盘展示）；
 * - 保存文件走普通文件系统，重名自动加序号（语义对齐 Android DownloadPathPolicy 防撞）。
 */
class DesktopDownloadEnvironment(private val settings: DesktopSettings) : DownloadEnvironment {

    private val baseDir = File(System.getProperty("user.home"), ".yunx")

    override fun chunkCacheBase(): File = File(baseDir, "cache").apply { mkdirs() }

    override fun tempCacheDir(): File = File(baseDir, "tmp").apply { mkdirs() }

    override fun acquireWakeLock(tag: String) {}

    override fun releaseWakeLock() {}

    override fun onTaskFlowStarted(title: String) {}

    override fun onTaskFlowStopped() {}

    override fun updateProgressNotification(title: String, percent: Int, speedText: String, showSpeed: Boolean) {}

    override fun deleteLocalFile(savePath: String): Boolean =
        runCatching { File(savePath).delete() }.getOrDefault(false)

    override fun saveDownloadFile(fileName: String, source: File, targetDirUri: String?): String? =
        runCatching {
            val configured: File? = targetDirUri?.takeIf { it.isNotBlank() }?.let { File(it) }
            val home = System.getProperty("user.home") ?: "."
            val dir = configured ?: File(home, "Downloads").apply { mkdirs() }
            if (!dir.isDirectory) return@runCatching null
            // 重名防撞：name.ext -> name(1).ext -> name(2).ext ...
            val dot = fileName.lastIndexOf('.')
            val stem = if (dot > 0) fileName.substring(0, dot) else fileName
            val ext = if (dot > 0) fileName.substring(dot) else ""
            var dest = File(dir, fileName)
            var n = 1
            while (dest.exists()) {
                dest = File(dir, "$stem($n)$ext")
                n++
            }
            source.copyTo(dest, overwrite = false)
            dest.absolutePath
        }.getOrNull()
}
