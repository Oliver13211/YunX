/*
 * YunX Desktop - 跨平台桌面版（KMP + Compose Multiplatform）
 * Copyright (C) 2026 Oliver13211
 * AGPL-3.0 licensed. 派生自 YunX（云析）Android 项目（CYQawa 著）。
 */

package com.yunx.desktop

/**
 * 版本单源：jpackage 的 packageVersion 由 build.gradle.kts 从这里的 VERSION 正则解析，
 * 更新版本只改这一处（必须是 x.y.z 纯数字，jpackage 对版本格式有硬校验）。
 */
object AppInfo {
    const val APP_NAME = "YunX Desktop"
    // 注意：必须是 x.y.z 且 x>0（jpackage 对 macOS dmg 的 build version 硬校验 MAJOR > 0）
    const val VERSION = "1.0.0"
    const val GITHUB_REPO = "Oliver13211/YunX"
    const val RELEASES_PAGE = "https://github.com/$GITHUB_REPO/releases/latest"

    /** 发布标签前缀：只有 desktop-v* 标签的 Release 参与更新检查，与上游 Android 发布互不干扰 */
    const val RELEASE_TAG_PREFIX = "desktop-v"
}
