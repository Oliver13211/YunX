/*
 * YunX (云析) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)

    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // 桌面 JVM 目标：Phase 0 仅保证空目标可编译，Phase 2 起接入 Compose Multiplatform
    jvm()

    sourceSets {
        // JVM 系共享中间层：协议层 / 下载引擎等可用 java.* 与 OkHttp 的代码放这里
        // （Agent.md §10.2 判定规则：纯 Kotlin 进 commonMain，需 java.*/okhttp 进 jvmShared）
        val commonMain by getting
        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                // 协议层与下载引擎的 HTTP 栈（Android/桌面 JVM 均可用）
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                // Room KMP DAO 装饰器用到 Dispatchers/Flow
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                // Room 2.7 起运行时为 KMP 构件（room-ktx 已并入 room-runtime）
                implementation(libs.room.runtime)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("androidx.compose.material:material-icons-extended")
                implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation(libs.material)   // 官方 Material 主库（含 color.utilities 包）

                implementation(libs.room.runtime)
                implementation(libs.room.ktx)          // 协程扩展：Flow / suspend

                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.androidx.activity.compose)
                // Gradle Kotlin DSL 2.2 起顶层 platform() 已废弃，用 project.dependencies.platform()
                implementation(project.dependencies.platform(libs.androidx.compose.bom))
                implementation(libs.androidx.ui)
                implementation(libs.androidx.ui.graphics)
                implementation(libs.androidx.ui.tooling.preview)
                implementation(libs.androidx.material3)
                // 原为 debugImplementation；KMP 源集块不支持变体配置，改为普通 implementation
                implementation(libs.androidx.ui.tooling)
                implementation(libs.androidx.ui.test.manifest)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit)
            }
        }
        val jvmMain by getting {
            dependencies {
                // Compose 编译器插件对全项目所有 Kotlin 编译生效，jvm 目标类路径上也必须有
                // Compose runtime，否则版本检查直接报错；Phase 2 引入 org.jetbrains.compose 插件后统一接管
                implementation("org.jetbrains.compose.runtime:runtime:1.9.0")
                // jvmShared 协议层用了 Android 平台内置的 org.json；桌面 JVM 需要等价构件（API 兼容）
                implementation("org.json:json:20240303")
                // Room KMP 桌面端内置 SQLite 驱动（纯 Kotlin，免 JNI/JDBC）
                implementation("androidx.sqlite:sqlite-bundled:2.5.2")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.junit)
            }
        }

        // 共享测试层：commonTest 供纯 Kotlin 测试；jvmSharedTest 为 JVM 系共享测试（沿用 junit4）
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmSharedTest by creating {
            dependsOn(commonTest)
            dependencies {
                implementation(libs.junit)
            }
        }
        // 接线：jvmShared 同时供 Android 与桌面编译（须在全部 by getting 声明之后）
        androidMain.dependsOn(jvmShared)
        jvmMain.dependsOn(jvmShared)
        androidUnitTest.dependsOn(jvmSharedTest)
        jvmTest.dependsOn(jvmSharedTest)
    }
}

// Room 注解处理只作用于 Android 编译（KMP 下 ksp 需按目标声明）
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}

android {
    namespace = "com.yunx.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yunx.app"
        minSdk = 23
        targetSdk = 34
        versionCode = 10
        versionName = "1.2.6"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
