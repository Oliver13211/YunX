/*
 * YunX Desktop - 跨平台桌面版（KMP + Compose Multiplatform），基于 AGPL-3.0 开源。
 * 派生自 YunX（云析）Android 项目（CYQawa 著）。
 */

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                // 共享核心（协议层/下载引擎/db，见 composeApp 的 jvmShared 源集）
                implementation(project(":composeApp"))
                // AppDatabase 的父类 RoomDatabase 需要对外可见（composeApp 的 implementation 依赖不导出）
                implementation(libs.room.runtime)
                // ChunkDownloader 工厂 lambda 的签名引用 OkHttpClient（composeApp 的 implementation 依赖不导出）
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                // 桌面窗口/输入/渲染后端（skiko），Window/application 所需
                implementation(compose.desktop.currentOs)
                // 提供 Dispatchers.Main（= Swing EDT），KCEF 抓取回 UI 必需；
                // 版本必须与 coroutines-core 一致，否则 ServiceLoader 注册不生效
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                // KCEF：内嵌 Chromium 网页登录（JCEF 运行时首启从 GitHub 下载，受限网络设 YUNX_PROXY）
                // 2025.03.23 在 macOS 换用 cef_server 新布局且框架路径解析不匹配（dlopen 失败 SIGSEGV），回退经典布局
                implementation("dev.datlag:kcef:2024.04.20.4")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.junit)
                // 数据库冒烟测试用 runBlocking / Flow
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
    }
}

// JCEF 在 JDK 16+ 必须开放这些内部包（CefBrowserWindowMac 访问 sun.awt.AWTAccessor 等；
// JetBrains Runtime 出厂即开放这些包，普通 JDK 需手动对齐同一组）。
// 注意：运行参数必须走 compose.desktop.application.jvmArgs DSL——对 JavaExec 任务用
// withType 追加 jvmArgs 会被 Compose 插件的 setter（注入 -Dcompose.application.* 整组
// 参数时整体替换）覆盖，追加从未生效（jvmTest 不受影响，保留 withType<Test>）。
val jcefJvmArgs = listOf(
    "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.awt.datatransfer=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.awt.event=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.awt.image=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.awt.util=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED"
)

tasks.withType<Test>().configureEach { jvmArgs(jcefJvmArgs) }

// 版本单源：jpackage 的 packageVersion 从 AppInfo.kt 解析（jpackage 要求 x.y.z 纯数字，
// 不能带 -desktop 等后缀），改版本只动 AppInfo.kt 一处
val appVersion = Regex("VERSION\\s*=\\s*\"([0-9]+\\.[0-9]+\\.[0-9]+)\"")
    .find(file("src/jvmMain/kotlin/com/yunx/desktop/AppInfo.kt").readText())
    ?.groupValues?.get(1) ?: error("无法从 AppInfo.kt 解析 VERSION")

// 桌面应用配置：声明主类后插件才会注册 :desktop:run 任务
compose.desktop {
    application {
        mainClass = "com.yunx.desktop.MainKt"
        jvmArgs(*jcefJvmArgs.toTypedArray())
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "YunX Desktop"
            packageVersion = appVersion
            vendor = "Oliver13211"
            description = "云析：网盘分享链接解析与高速下载（桌面版）"
            copyright = "© 2026 Oliver13211 · AGPL-3.0"
            // 合规清单第 1 项：AGPL-3.0 全文随安装包分发
            licenseFile = rootProject.file("LICENSE")
            // 全模块捆绑：sqlite-bundled 驱动 / coroutines-swing 的 ServiceLoader 都依赖非必需
            // JDK 模块，逐个挑 modules() 容易漏，用体积换稳定性
            includeAllModules = true
            // 合规清单第 1 项：AGPL-3.0 全文随安装包分发（jpackage 的 licenseFile 在 macOS
            // 上不落盘，改经 appResources 打进 Contents/app/resources/LICENSE）。
            // desktop/resources/common/LICENSE 为根 LICENSE 的同步副本，上游更新协议时需同步
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            // 运行时说明：jpackage 捆绑的是构建用 JDK（temurin 17，与 :desktop:run 同 JVM，
            // 行为已实测）。KCEF 的 JCEF 运行时不进包——它由 KCEF 在首次使用内嵌登录时自行
            // 下载到 ~/.yunx/kcef；若把含 JCEF 的 JBR 设为 javaHome 捆进去，其 org.cef 类会与
            // KCEF 自下载的运行时重复加载，存在冲突风险（未验证，不采用）。
            // macOS 未公证分发（决策 #2）：不配置 signing，dmg 放行步骤写入发布说明。

            macOS {
                bundleID = "com.yunx.desktop"
            }
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                // 固定 upgradeUuid：后续版本原地升级，不残留旧条目
                upgradeUuid = "7f2d8c4a-3b6e-4d59-9a1c-8e5f2b7d4c13"
            }
        }
    }
}
