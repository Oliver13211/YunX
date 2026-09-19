/*
 * YunX Desktop - 跨平台桌面版（KMP + Compose Multiplatform），基于 AGPL-3.0 开源。
 * 派生自 YunX（云析）Android 项目（CYQawa 著）。
 */

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

// 桌面应用配置：声明主类后插件才会注册 :desktop:run 任务（jpackage 打包配置在 Phase 5 追加）
// JCEF 在 JDK 16+ 必须开放这些内部包（CefBrowserWindowMac 访问 sun.awt.AWTAccessor）
val jcefJvmArgs = listOf(
    "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED"
)

tasks.withType<JavaExec>().configureEach { jvmArgs(jcefJvmArgs) }
tasks.withType<Test>().configureEach { jvmArgs(jcefJvmArgs) }

compose.desktop {
    application {
        mainClass = "com.yunx.desktop.MainKt"
    }
}
