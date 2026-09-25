/*
 * YunX Desktop - 跨平台桌面版（KMP + Compose Multiplatform）
 * Copyright (C) 2026 Oliver13211
 * AGPL-3.0 licensed. 派生自 YunX（云析）Android 项目（CYQawa 著）。
 */

package com.yunx.desktop

import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.platform.KeyValueStore
import com.yunx.app.platform.defaultKeyValueStore

/**
 * 桌面端轻量设置：以 KeyValueStore（java.util.prefs）持久化。
 * 与 Android 的 SettingsRepository（§3.4）职责一致，但桌面版当前只暴露下载所需最小集。
 */
class DesktopSettings(private val store: KeyValueStore = defaultKeyValueStore()) {

    /** 自定义保存目录（绝对路径）；空 = 系统下载目录 */
    var downloadDir: String
        get() = store.getString(KEY_DOWNLOAD_DIR) ?: ""
        set(value) = store.putString(KEY_DOWNLOAD_DIR, value)

    var maxConcurrent: Int
        get() = (store.getLong(KEY_MAX_CONCURRENT) ?: DEFAULT_MAX_CONCURRENT.toLong()).toInt().coerceIn(1, 10)
        set(value) = store.putLong(KEY_MAX_CONCURRENT, value.coerceIn(1, 10).toLong())

    var threadCount: Int
        get() = (store.getLong(KEY_THREADS) ?: DEFAULT_THREADS.toLong()).toInt().coerceIn(1, 32)
        set(value) = store.putLong(KEY_THREADS, value.coerceIn(1, 32).toLong())

    /** 按平台分片线程数（对齐原版 downloadThreadsFor；迅雷受 CDN 限制固定 8，Agent.md §5.3） */
    fun threadsFor(platform: String): Int = when (platform) {
        DownloadPlatform.XUNLEI -> 8
        else -> (store.getLong("download.threads.$platform") ?: DEFAULT_THREADS.toLong()).toInt().coerceIn(1, 32)
    }

    fun setThreads(platform: String, value: Int) {
        if (platform == DownloadPlatform.XUNLEI) return // 迅雷固定 8，设置不生效（§5.3）
        store.putLong("download.threads.$platform", value.coerceIn(1, 32).toLong())
    }

    /** 失败自动重试次数（0-10，默认 3） */
    var retryCount: Int
        get() = (store.getLong(KEY_RETRY) ?: 3L).toInt().coerceIn(0, 10)
        set(value) = store.putLong(KEY_RETRY, value.coerceIn(0, 10).toLong())

    /** 深色模式：0=跟随系统 1=亮色 2=暗色（对齐原版 darkMode 三态） */
    var darkMode: Int
        get() = (store.getLong(KEY_DARK_MODE) ?: 0L).toInt().coerceIn(0, 2)
        set(value) = store.putLong(KEY_DARK_MODE, value.coerceIn(0, 2).toLong())

    /** 内嵌登录组件（KCEF/JBR 运行时）是否已下载启用 */
    var embeddedLoginEnabled: Boolean
        get() = store.getLong(KEY_EMBEDDED_LOGIN) == 1L
        set(value) = store.putLong(KEY_EMBEDDED_LOGIN, if (value) 1L else 0L)

    var speedLimit: Long
        get() = store.getLong(KEY_SPEED_LIMIT) ?: 0L
        set(value) = store.putLong(KEY_SPEED_LIMIT, value)

    private companion object {
        const val KEY_DOWNLOAD_DIR = "download.dir"
        const val KEY_MAX_CONCURRENT = "download.maxConcurrent"
        const val KEY_THREADS = "download.threads"
        const val KEY_SPEED_LIMIT = "download.speedLimit"
        const val KEY_EMBEDDED_LOGIN = "login.embeddedEnabled"
        const val KEY_RETRY = "download.retryCount"
        const val KEY_DARK_MODE = "ui.darkMode"
        const val DEFAULT_MAX_CONCURRENT = 3
        const val DEFAULT_THREADS = 32
    }
}
