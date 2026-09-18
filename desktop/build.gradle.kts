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
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
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
