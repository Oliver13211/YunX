# YunX Desktop Phase 0/1 实施计划：工程 KMP 化与 core 去 Android 化

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> 本计划是总计划书 `2026-09-18-yunx-desktop-kmp-migration.md` 的 Phase 0/1 细化。**执行前先通读 `Agent.md` §10**（源集划分规则、接缝登记表、双端验证门禁）。

**Goal:** 把单模块 Android 工程改造为 KMP 工程（Android + 桌面 JVM 双目标），并将协议层与下载引擎的纯逻辑全部移入跨端共享源集，Android 端零回归。

**Architecture:** `app` 模块转 KMP（`com.android.application` + `org.jetbrains.kotlin.multiplatform` 双插件，JetBrains CMP 模板模式），建立 `commonMain → jvmShared → {androidMain, jvmMain}` 分层源集。`jvmShared` 是本项目的"核心共享层"（可用 OkHttp 与 `java.*`）；平台能力通过 `expect/actual` 接缝隔离（登记于 Agent.md §10.3）。Phase 0/1 **不动** `data/db`、`ui/`、`DownloadService`、`DownloadSaver`。

**Tech Stack:** Kotlin 2.2.20 · KSP 2.2.20-2.0.2 · AGP 8.13.0（不变）· Room 2.7.2 · OkHttp 4.12.0（不变）· junit 4.13.2（共享测试沿用）

## 全局约束

- 每个任务提交前跑通三门禁（Agent.md §10.5）：`./gradlew :app:assembleDebug`、`:app:testDebugUnitTest`、`:app:jvmTest`（T0.3 后路径前缀变 `:composeApp:`）。
- 提交信息：中文 Conventional Commits；分支 `desktop`；GitHub 操作只用 `gh`（Agent.md §6.4）。
- 协议行为、分片规划（`chunkCountFor`）、并发上限（迅雷 8）**一个字节都不改**——本计划只搬家与替换平台 API。
- 版本锚点见 Agent.md §10.4；`YUNX_USE_MIRROR=false` 环境变量在 CI 已设置，本地构建若走阿里云镜像慢可同样设置。
- KSP 版本若 `2.2.20-2.0.2` 在 Maven Central 不存在，取 Kotlin 2.2.20 配套的最新版（查询 https://central.sonatype.com/artifact/com.google.devtools.ksp/com.google.devtools.ksp.gradle.plugin 的 releases），其余步骤不变。

---

## Phase 0：工程 KMP 化

### Task 0.1: 版本基线升级（Kotlin 2.2 / KSP / Room 2.7）

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`（仅依赖别名整理，无结构变化）

**Interfaces:**
- Produces: `libs.junit`（junit 4.13.2 别名，后续任务引用）；Kotlin 2.2.20 基线（后续 KMP 插件依赖）

- [ ] **Step 1: 修改 `gradle/libs.versions.toml` 的 versions 块**

```toml
[versions]
agp = "8.13.0"
kotlin = "2.2.20"
coreKtx = "1.17.0"
lifecycleRuntimeKtx = "2.9.2"
activityCompose = "1.11.0"
composeBom = "2025.10.01"

room = "2.7.2"
ksp = "2.2.20-2.0.2"
material = "1.14.0"   # 官方 Material 主库，自带 com.google.android.material.color.utilities 包
junit = "4.13.2"
```

- [ ] **Step 2: 在 `[libraries]` 块末尾追加 junit 别名**

```toml
junit = { group = "junit", name = "junit", version.ref = "junit" }
```

- [ ] **Step 3: 在 `[plugins]` 块追加 KMP 插件别名（T0.2 使用）**

```toml
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
```

- [ ] **Step 4: `app/build.gradle.kts` 中 `testImplementation("junit:junit:4.13.2")` 改为 `testImplementation(libs.junit)`**

- [ ] **Step 5: 构建验证（Room 2.6.1→2.7.x 升级回归重点：schema 编译不报错）**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL，13 个既有测试全绿。若 Room KSP 报 schema 相关错误，检查 `room` 版本号是否正确写入（2.7.x 起注解处理对未导出 schema 更严格，如报 `schema export directory` 警告可忽略，非 error 不阻塞）。

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: 升级 Kotlin 2.2.20 / KSP / Room 2.7.2，为 KMP 化建立版本基线"
```

### Task 0.2: app 模块转 KMP（Android + jvm 双目标）

**Files:**
- Modify: `app/build.gradle.kts`（重写插件与源集配置）
- Create: `app/src/jvmMain/kotlin/com/yunx/app/DesktopSmoke.kt`
- Create: `app/src/jvmTest/kotlin/com/yunx/app/DesktopSmokeTest.kt`
- Move: `app/src/main/kotlin/` → `app/src/androidMain/kotlin/`；`app/src/main/res/` → `app/src/androidMain/res/`；`app/src/main/AndroidManifest.xml` → `app/src/androidMain/AndroidManifest.xml`；`app/src/test/kotlin/` → `app/src/androidUnitTest/kotlin/`

**Interfaces:**
- Produces: Gradle 任务 `jvmTest` / `jvmJar`（后续所有任务的三门禁之一）；源集骨架 androidMain/androidUnitTest/jvmMain/jvmTest

- [ ] **Step 1: 迁移源码目录**

```bash
mkdir -p app/src/androidMain/kotlin app/src/androidUnitTest/kotlin
git mv app/src/main/kotlin app/src/androidMain/kotlin
git mv app/src/main/res app/src/androidMain/res
git mv app/src/main/AndroidManifest.xml app/src/androidMain/AndroidManifest.xml
git mv app/src/test/kotlin app/src/androidUnitTest/kotlin
ls app/src/main 2>/dev/null   # 应为空或不存在；若有残留文件（如 .xml 菜单），同样 git mv 到 androidMain 下对应位置
```

- [ ] **Step 2: 重写 `app/build.gradle.kts`（保留文件头 AGPL 许可注释，替换正文）**

```kotlin
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
                implementation(platform(libs.androidx.compose.bom))
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
        val jvmMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}

// Room 注解处理只作用于 Android 编译（KMP 下 ksp 需按目标声明）
dependencies {
    add("kspAndroid", libs.room.compiler)
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
```

- [ ] **Step 3: 新建 `app/src/jvmMain/kotlin/com/yunx/app/DesktopSmoke.kt`**

```kotlin
package com.yunx.app

/** 桌面 JVM 目标编译冒烟对象：T0.2 验证 jvm 目标工具链可用，Phase 2 起由真实桌面代码取代。 */
object DesktopSmoke {
    fun ok(): String = "desktop-jvm-ok"
}
```

- [ ] **Step 4: 新建 `app/src/jvmTest/kotlin/com/yunx/app/DesktopSmokeTest.kt`**

```kotlin
package com.yunx.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopSmokeTest {
    @Test
    fun jvmTargetCompiles() {
        assertEquals("desktop-jvm-ok", DesktopSmoke.ok())
    }
}
```

- [ ] **Step 5: 三门禁验证（首次 jvmTest）**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:jvmTest`
Expected: 全部 BUILD SUCCESSFUL。常见报错处理：
- `Unresolved reference: debugImplementation` → Step 2 中已改为 implementation，确认没有旧行残留；
- jvm 编译报 android 类缺失 → 说明有文件误入 jvmMain（当前 jvmMain 只有 DesktopSmoke.kt，检查路径）。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: app 模块转 KMP 双目标（androidTarget + jvm），源码迁入 androidMain 源集"
```

### Task 0.3: 模块重命名 `app` → `composeApp`

**Files:**
- Modify: `settings.gradle.kts`（include 行）
- Modify: `.github/workflows/ci.yml`（T0.4 一并做，本任务先改产物路径）
- Modify: `Agent.md` §1 表格「源码根」行
- Move: `app/` → `composeApp/`

**Interfaces:**
- Produces: 模块路径 `:composeApp:`，后续所有任务与 CI 使用该坐标

- [ ] **Step 1: 重命名并更新引用**

```bash
git mv app composeApp
```

`settings.gradle.kts` 末行 `include(":app")` 改为：

```kotlin
include(":composeApp")
```

`Agent.md` §1 表格中「源码根」行的值改为：`composeApp/src/androidMain/kotlin/com/yunx/app`（表格追加一行「共享源集」：`composeApp/src/jvmShared/kotlin/com/yunx/app`）。

- [ ] **Step 2: 构建验证**

Run: `./gradlew :composeApp:assembleDebug :composeApp:jvmTest`
Expected: BUILD SUCCESSFUL（debug.keystore 相对路径 `../debug.keystore` 以模块目录为基准，重命名后仍指向仓库根，无需改动）。

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: 模块 app 重命名为 composeApp，对齐 KMP 工程惯例"
```

### Task 0.4: CI 门禁升级（desktop 分支 + jvmTest）

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: CI 在 desktop 分支的 push/PR 上运行，并执行桌面 JVM 测试

- [ ] **Step 1: `ci.yml` 四处修改**

① `on.push.branches` 与 `on.pull_request.branches` 均改为：

```yaml
    branches: [ "master", "desktop" ]
```

② 构建与测试步骤（替换原「Build debug APK」「Run unit tests」两步）：

```yaml
      - name: Build debug APK
        run: ./gradlew :composeApp:assembleDebug

      - name: Run Android unit tests
        run: ./gradlew :composeApp:testDebugUnitTest

      - name: Run desktop JVM tests
        run: ./gradlew :composeApp:jvmTest
```

③ 上传产物步骤的 path 改为 `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

- [ ] **Step 2: 本地等价验证**

Run: `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest :composeApp:jvmTest`
Expected: 全绿。

- [ ] **Step 3: Commit + 推送验证 CI**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: desktop 分支纳入门禁，新增桌面 JVM 测试步骤"
git -c http.proxy=http://127.0.0.1:7890 push origin desktop
gh pr list --repo Oliver13211/YunX --head desktop || true   # 确认 gh 可用；不开 PR，仅验证命令链路
```

---

## Phase 1：core 去 Android 化（jvmShared 源集填充）

> 执行顺序有依赖：T1.2/T1.3/T1.4（接缝）必须先于 T1.5/T1.6（消费接缝的文件迁移）。
> 共享测试统一放 `jvmSharedTest`（JVM 系源集，**沿用 junit4，不做 kotlin-test 改写**）；`commonMain` 仅放 expect 接缝声明。

### Task 1.1: 源集骨架 + 纯逻辑测试迁移

**Files:**
- Modify: `composeApp/build.gradle.kts`（源集骨架）
- Move: 6 个纯 JVM 测试 → `composeApp/src/jvmSharedTest/kotlin/`（路径保持原包名）

**Interfaces:**
- Produces: 源集 `jvmShared`（main）与 `jvmSharedTest`（test），后续任务向其迁移文件

- [ ] **Step 1: `composeApp/build.gradle.kts` 的 `kotlin { sourceSets { ... } }` 内追加骨架（保留 T0.2 已有块）**

```kotlin
        val commonMain by getting
        val jvmShared by creating {
            dependsOn(commonMain)
        }
        androidMain.get().dependsOn(jvmShared)
        jvmMain.get().dependsOn(jvmShared)

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
        androidUnitTest.get().dependsOn(jvmSharedTest)
        jvmTest.get().dependsOn(jvmSharedTest)
```

- [ ] **Step 2: 迁移 6 个纯 JVM 测试（保持包路径）**

```bash
cd composeApp/src
mkdir -p jvmSharedTest/kotlin/com/yunx/app/data/network jvmSharedTest/kotlin/com/yunx/app/data/download jvmSharedTest/kotlin/com/yunx/app/ui/login jvmSharedTest/kotlin/com/yunx/app/util
git mv androidUnitTest/kotlin/com/yunx/app/data/network/ShareLinkParserTest.kt jvmSharedTest/kotlin/com/yunx/app/data/network/
git mv androidUnitTest/kotlin/com/yunx/app/data/download/HttpRangePolicyTest.kt jvmSharedTest/kotlin/com/yunx/app/data/download/
git mv androidUnitTest/kotlin/com/yunx/app/data/download/HlsRequestPolicyTest.kt jvmSharedTest/kotlin/com/yunx/app/data/download/
git mv androidUnitTest/kotlin/com/yunx/app/data/download/DownloadPathPolicyTest.kt jvmSharedTest/kotlin/com/yunx/app/data/download/
git mv androidUnitTest/kotlin/com/yunx/app/ui/login/XunleiVerificationPolicyTest.kt jvmSharedTest/kotlin/com/yunx/app/ui/login/
git mv androidUnitTest/kotlin/com/yunx/app/util/LogRedactorTest.kt jvmSharedTest/kotlin/com/yunx/app/util/
# SecureAccountDaosTest 依赖 Android Keystore/Cipher，留在 androidUnitTest 不动
```

- [ ] **Step 3: 验证（先只跑共享测试与 Android 全量）**

Run: `./gradlew :composeApp:jvmTest :composeApp:testDebugUnitTest`
Expected: jvmTest 包含 DesktopSmokeTest + 6 个迁移测试全绿；testDebugUnitTest 全绿。若 KAGP 未把 jvmSharedTest 编入 androidUnitTest（现象：testDebugUnitTest 用例数明显变少），属已知 KAGP 自定义源集联编差异——接受，共享测试以 jvmTest 为准，并在提交信息中注明。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: 建立 jvmShared/jvmSharedTest 源集骨架，纯逻辑测试迁入共享源集"
```

### Task 1.2: `PlatformBase64` 接缝

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/yunx/app/platform/PlatformBase64.kt`
- Create: `composeApp/src/androidMain/kotlin/com/yunx/app/platform/PlatformBase64.android.kt`
- Create: `composeApp/src/jvmMain/kotlin/com/yunx/app/platform/PlatformBase64.jvm.kt`
- Modify: `Agent.md` §10.3 表（此接缝已在表内，无需改；若实现签名有出入则回改表格）

**Interfaces:**
- Produces: `PlatformBase64.encodeToString(data: ByteArray): String`、`PlatformBase64.decode(data: String): ByteArray`（T1.4/T1.6 的 Base64 调用点使用）

- [ ] **Step 1: `commonMain` 声明（`.../platform/PlatformBase64.kt`）**

```kotlin
package com.yunx.app.platform

/**
 * 跨端 Base64 接缝。
 * Android 端不能直接用 java.util.Base64（API 26+，本项目 minSdk 23），故走 expect/actual。
 */
expect object PlatformBase64 {
    /** 无换行编码（对应 android.util.Base64.NO_WRAP 语义）。 */
    fun encodeToString(data: ByteArray): String

    /** 解码，容忍标准与 URL-safe 字母表。 */
    fun decode(data: String): ByteArray
}
```

- [ ] **Step 2: `androidMain` 实现（`.../platform/PlatformBase64.android.kt`）**

```kotlin
package com.yunx.app.platform

import android.util.Base64

actual object PlatformBase64 {
    actual fun encodeToString(data: ByteArray): String =
        Base64.encodeToString(data, Base64.NO_WRAP)

    actual fun decode(data: String): ByteArray =
        Base64.decode(data, Base64.DEFAULT)
}
```

- [ ] **Step 3: `jvmMain` 实现（`.../platform/PlatformBase64.jvm.kt`）**

```kotlin
package com.yunx.app.platform

import java.util.Base64

actual object PlatformBase64 {
    actual fun encodeToString(data: ByteArray): String =
        Base64.getEncoder().encodeToString(data)

    actual fun decode(data: String): ByteArray {
        val decoder = if (data.contains('-') || data.contains('_')) Base64.getUrlDecoder() else Base64.getDecoder()
        return decoder.decode(data)
    }
}
```

- [ ] **Step 4: 验证 + Commit**

Run: `./gradlew :composeApp:assembleDebug :composeApp:jvmTest`

```bash
git add -A
git commit -m "feat: PlatformBase64 跨端接缝（android.util.Base64 / java.util.Base64）"
```

### Task 1.3: `YunXLog` 接缝

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/yunx/app/platform/YunXLog.kt`
- Create: `composeApp/src/androidMain/kotlin/com/yunx/app/platform/YunXLog.android.kt`
- Create: `composeApp/src/jvmMain/kotlin/com/yunx/app/platform/YunXLog.jvm.kt`

**Interfaces:**
- Produces: `YunXLog.d/i/w(tag: String, msg: String)`（T1.5/T1.6 迁移文件替换 `android.util.Log` 调用）
- 约束：调用侧必须已过 `LogRedactor`（Agent.md §3.6），本接缝不做二次脱敏

- [ ] **Step 1: `commonMain` 声明**

```kotlin
package com.yunx.app.platform

/** 跨端日志接缝：调用侧约定见 Agent.md §3.6（URL/Cookie/token 必须先过 LogRedactor）。 */
expect object YunXLog {
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String)
}
```

- [ ] **Step 2: `androidMain` 实现**

```kotlin
package com.yunx.app.platform

import android.util.Log

actual object YunXLog {
    actual fun d(tag: String, msg: String) = Log.d(tag, msg)
    actual fun i(tag: String, msg: String) = Log.i(tag, msg)
    actual fun w(tag: String, msg: String) = Log.w(tag, msg)
}
```

- [ ] **Step 3: `jvmMain` 实现**

```kotlin
package com.yunx.app.platform

import java.util.logging.Level
import java.util.logging.Logger

actual object YunXLog {
    private val logger = Logger.getLogger("com.yunx.app")

    actual fun d(tag: String, msg: String) = logger.fine("$tag: $msg")
    actual fun i(tag: String, msg: String) = logger.info("$tag: $msg")
    actual fun w(tag: String, msg: String) = logger.log(Level.WARNING, "$tag: $msg")
}
```

- [ ] **Step 4: 验证 + Commit**

Run: `./gradlew :composeApp:assembleDebug :composeApp:jvmTest`

```bash
git add -A
git commit -m "feat: YunXLog 跨端日志接缝（android.util.Log / java.util.logging）"
```

### Task 1.4: `KeyValueStore` 接缝 + `XunleiDeviceFingerprint` 迁移

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/yunx/app/platform/KeyValueStore.kt`
- Create: `composeApp/src/androidMain/kotlin/com/yunx/app/platform/KeyValueStore.android.kt`
- Create: `composeApp/src/jvmMain/kotlin/com/yunx/app/platform/KeyValueStore.jvm.kt`
- Move: `composeApp/src/androidMain/kotlin/com/yunx/app/data/network/XunleiDeviceFingerprint.kt` → `composeApp/src/jvmShared/kotlin/com/yunx/app/data/network/`
- Modify: `composeApp/src/androidMain/kotlin/com/yunx/app/YunXApp.kt:85`（init 调用点）

**Interfaces:**
- Consumes: 无
- Produces: `defaultKeyValueStore(): KeyValueStore`（`getString/putString/getLong/putLong/contains/remove`）；`XunleiDeviceFingerprint.init()` 无参化

- [ ] **Step 1: `commonMain` 声明**

```kotlin
package com.yunx.app.platform

/** 轻量跨端 KV（设备指纹等少量持久化；业务设置仍走 SettingsRepository，见 §3.4）。 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getLong(key: String): Long?
    fun putLong(key: String, value: Long)
    fun contains(key: String): Boolean
    fun remove(key: String)
}

/** 各端默认存储实例（Android: SharedPreferences；桌面: java.util.prefs）。 */
expect fun defaultKeyValueStore(): KeyValueStore
```

- [ ] **Step 2: `androidMain` 实现（沿用设备指纹原 SharedPreferences 文件名）**

```kotlin
package com.yunx.app.platform

import android.content.Context
import android.content.SharedPreferences

private lateinit var store: KeyValueStore

/** 由 YunXApp.onCreate 调用一次（Application Context）。 */
fun initAndroidKeyValueStore(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("yunx_device_fingerprint", Context.MODE_PRIVATE)
    store = object : KeyValueStore {
        override fun getString(key: String): String? = prefs.getString(key, null)
        override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
        override fun getLong(key: String): Long? =
            if (prefs.contains(key)) prefs.getLong(key, 0L) else null
        override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
        override fun contains(key: String): Boolean = prefs.contains(key)
        override fun remove(key: String) { prefs.edit().remove(key).apply() }
    }
}

actual fun defaultKeyValueStore(): KeyValueStore = store
```

- [ ] **Step 3: `jvmMain` 实现**

```kotlin
package com.yunx.app.platform

import java.util.prefs.Preferences

actual fun defaultKeyValueStore(): KeyValueStore {
    val node = Preferences.userNodeForPackage(KeyValueStore::class.java)
    return object : KeyValueStore {
        override fun getString(key: String): String? = node.get(key, null)
        override fun putString(key: String, value: String) = node.put(key, value)
        override fun getLong(key: String): Long? =
            if (node.get(key, null) != null) node.getLong(key, Long.MIN_VALUE) else null
        override fun putLong(key: String, value: Long) = node.putLong(key, value)
        override fun contains(key: String): Boolean = node.get(key, null) != null
        override fun remove(key: String) = node.remove(key)
    }
}
```

- [ ] **Step 4: 迁移 `XunleiDeviceFingerprint.kt` 并去 Context 化**

```bash
mkdir -p composeApp/src/jvmShared/kotlin/com/yunx/app/data/network
git mv composeApp/src/androidMain/kotlin/com/yunx/app/data/network/XunleiDeviceFingerprint.kt \
       composeApp/src/jvmShared/kotlin/com/yunx/app/data/network/
```

文件内改动（保持其余逻辑逐字不动）：
1. 删除 `import android.content.Context`；追加 `import com.yunx.app.platform.defaultKeyValueStore`。
2. 原 `fun init(context: Context)` 整体改为 `fun init()`，函数体中 `context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)` 一行替换为持久化对象改用 `defaultKeyValueStore()`（原"读不到就生成并写回"的分支逻辑保持不变，只是 get/put 走接口）。
3. 文件内若使用 `android.util.Base64`，改为 `com.yunx.app.platform.PlatformBase64`（T1.2 接缝）。

- [ ] **Step 5: 更新调用点 `YunXApp.kt:85`**

`com.yunx.app.data.network.XunleiDeviceFingerprint.init(this)` 改为：

```kotlin
com.yunx.app.platform.initAndroidKeyValueStore(this)
com.yunx.app.data.network.XunleiDeviceFingerprint.init()
```

- [ ] **Step 6: 验证（重点：迅雷设备指纹在升级安装后仍复用旧值）**

Run: `./gradlew :composeApp:assembleDebug :composeApp:jvmTest :composeApp:testDebugUnitTest`
人工核验：grep 确认 `getSharedPreferences("yunx_device_fingerprint"` 的旧文件名已原样保留在 androidMain 实现里（保证真机升级后指纹不变，迅雷登录态不失效）。

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: KeyValueStore 跨端接缝落地，XunleiDeviceFingerprint 迁入 jvmShared"
```

### Task 1.5: `DownloadEnvironment` 接缝 + `DownloadManager` 去 Context

**Files:**
- Create: `composeApp/src/jvmShared/kotlin/com/yunx/app/data/download/DownloadEnvironment.kt`
- Create: `composeApp/src/androidMain/kotlin/com/yunx/app/data/download/AndroidDownloadEnvironment.kt`
- Move+Modify: `composeApp/src/androidMain/kotlin/com/yunx/app/data/download/DownloadManager.kt` → `composeApp/src/jvmShared/kotlin/com/yunx/app/data/download/`

**Interfaces:**
- Consumes: `YunXLog`（T1.3）
- Produces: `DownloadEnvironment`（`chunkCacheBase()/tempCacheDir()/acquireWakeLock()/releaseWakeLock()`）；`DownloadManager` 构造参数由 `context: Context` 变为 `env: DownloadEnvironment`（调用点：`DownloadService` 及 ViewModel Provider 闭包，grep 定位）

- [ ] **Step 1: `jvmShared` 接口（`.../download/DownloadEnvironment.kt`）**

```kotlin
package com.yunx.app.data.download

import java.io.File

/**
 * 下载引擎的平台环境接缝（Agent.md §5.4 分片缓存目录语义在此收敛）。
 * WakeLock 语义：Android 端锁屏保活下载（PARTIAL_WAKE_LOCK）；桌面端为空实现。
 */
interface DownloadEnvironment {
    /** 分片缓存根（Android: externalCacheDir ?: cacheDir，即 §5.4 的 download_tmp 父目录）。 */
    fun chunkCacheBase(): File

    /** HLS 合并等大临时文件目录（Android: cacheDir；桌面: java.io.tmpdir/yunx）。 */
    fun tempCacheDir(): File

    /** 开始批量下载前调用；桌面端为空实现。 */
    fun acquireWakeLock(tag: String)

    /** 下载暂停/停止/任务清空后调用；桌面端为空实现。 */
    fun releaseWakeLock()
}
```

- [ ] **Step 2: `androidMain` 实现（原 DownloadManager 中 WakeLock 逻辑整体平移至此，不改变行为）**

```kotlin
package com.yunx.app.data.download

import android.content.Context
import android.os.PowerManager
import java.io.File

class AndroidDownloadEnvironment(context: Context) : DownloadEnvironment {
    private val appContext = context.applicationContext

    @Volatile
    private var wakeLock: PowerManager.WakeLock? = null

    override fun chunkCacheBase(): File = appContext.externalCacheDir ?: appContext.cacheDir

    override fun tempCacheDir(): File = appContext.cacheDir

    override fun acquireWakeLock(tag: String) {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
                .apply { setReferenceCounted(false) }
        }
        wakeLock?.let { if (!it.isHeld) it.acquire() }
    }

    override fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
```

- [ ] **Step 3: 迁移并改写 `DownloadManager.kt`**

```bash
git mv composeApp/src/androidMain/kotlin/com/yunx/app/data/download/DownloadManager.kt \
       composeApp/src/jvmShared/kotlin/com/yunx/app/data/download/
```

文件内改动（**仅限以下四类，逐字保留其余逻辑**，Agent.md §5 各机制一行不动）：
1. 删除 `import android.content.Context`、`import android.util.Log`；追加 `import com.yunx.app.platform.YunXLog`。
2. 构造参数与字段 `context: Context` → `env: DownloadEnvironment`。
3. `private fun acquireWakeLockIfNeeded()` 整个函数体替换为 `env.acquireWakeLock("yunx:download")`；原 WakeLock 字段与释放逻辑删除，原调用 `releaseWakeLock` 的位置改调 `env.releaseWakeLock()`（grep `wakeLock` 确认无残留）。
4. 缓存目录调用点替换：`context.externalCacheDir ?: context.cacheDir`（`cacheBase()`）→ `env.chunkCacheBase()`；`File(context.cacheDir, "hls_$id")`、`File(context.cacheDir, "merged_$id")` → `File(env.tempCacheDir(), "hls_$id")`、`File(env.tempCacheDir(), "merged_$id")`；所有 `Log.d/i/w(` → `YunXLog.d/i/w(`。

- [ ] **Step 4: 更新 Android 侧构造点**

`grep -rn "DownloadManager(" composeApp/src/androidMain --include="*.kt"` 定位构造调用（预期在 `DownloadService.kt` 与 ViewModel Factory/Provider 闭包），将传入的 `context` 参数改为 `AndroidDownloadEnvironment(context)`。**Android 端构造时机不变**（仍在有 Context 的位置构建）。

- [ ] **Step 5: 验证（下载引擎回归重点：跑一次真机下载确认暂停/继续/锁屏保活）**

Run: `./gradlew :composeApp:assembleDebug :composeApp:jvmTest :composeApp:testDebugUnitTest`
人工验收（真机）：任一下载任务 暂停→继续 正常；锁屏 1 分钟进度仍增长；`分片规划:` 日志正常输出（Agent.md §6.3 要求给出可验证方法）。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: DownloadEnvironment 接缝落地，DownloadManager 迁入 jvmShared 去 Context 化"
```

### Task 1.6: 协议层与下载层整体迁移

**Files:**
- Move: `data/network/` 整包（除已迁的 XunleiDeviceFingerprint）→ `jvmShared/kotlin/com/yunx/app/data/network/`，含 `model/` 子目录
- Move: `data/download/` 中 `ChunkDownloader.kt`、`HlsDownloader.kt`、`HttpRangePolicy.kt`、`HlsRequestPolicy.kt`、`DownloadPathPolicy.kt`、`DownloadPlatform.kt` → `jvmShared/`
- 不动: `DownloadService.kt`、`DownloadSaver.kt`（永久/Phase 3 前留在 androidMain）

**Interfaces:**
- Consumes: `PlatformBase64`（T1.2）、`YunXLog`（T1.3）
- Produces: `jvmShared` 内完整的协议层（六家网盘 Api/Constants/异常/model/HttpClients/QuarkCdn/ShareLinkParser）与下载纯逻辑

- [ ] **Step 1: 整包迁移**

```bash
cd composeApp/src
git mv androidMain/kotlin/com/yunx/app/data/network/model jvmShared/kotlin/com/yunx/app/data/network/model
for f in $(ls androidMain/kotlin/com/yunx/app/data/network/*.kt); do
  git mv "$f" jvmShared/kotlin/com/yunx/app/data/network/
done
for f in ChunkDownloader HlsDownloader HttpRangePolicy HlsRequestPolicy DownloadPathPolicy DownloadPlatform; do
  git mv androidMain/kotlin/com/yunx/app/data/download/$f.kt jvmShared/kotlin/com/yunx/app/data/download/
done
```

- [ ] **Step 2: 批量替换三处平台 API（仅这些，逐文件确认替换点）**

1. `import android.util.Base64` + `Base64.encodeToString(x, Base64.NO_WRAP)` → `import com.yunx.app.platform.PlatformBase64` + `PlatformBase64.encodeToString(x)`（涉及 `C139Constants.kt`、`Pan123Api.kt`、`C139Api.kt`，注意 `Base64.URL_SAFE` 等旗标语义并入口径以 T1.2 的 decode 容忍 URL-safe）。
2. `import android.util.Log` + `Log.x(` → `YunXLog.x(`（`ChunkDownloader.kt`、`HlsDownloader.kt`）。
3. 任何残留 `android.` import → 逐个判断：若为漏网平台调用，停下补接缝并回 Agent.md §10.3 登记；不许在 jvmShared 里留 android import。

- [ ] **Step 3: 门禁 grep（必须零命中）**

Run: `grep -rn "import android\." composeApp/src/jvmShared composeApp/src/commonMain`
Expected: 无输出。

- [ ] **Step 4: 验证 + 真机冒烟（协议行为未变，抽两个平台实测）**

Run: `./gradlew :composeApp:assembleDebug :composeApp:jvmTest :composeApp:testDebugUnitTest`
人工冒烟（真机）：夸克与 123 云盘各解析一条分享链接 → 获取直链 → 下载 1 个小文件成功。

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: 协议层与下载纯逻辑整体迁入 jvmShared，Base64/Log 走跨端接缝"
```

### Task 1.7: 解析仓库层迁移（11 个干净文件）

**Files:**
- Move: `data/repository/` 中 `ShareResolveRepository.kt` + 6 个 `{X}ResolveRepository.kt` + `BookmarkEntity` 无关的其余**无 android import** 文件 → `jvmShared/`
- 不动: 5 个 `{X}AccountRepository.kt`（`android.webkit.CookieManager/WebStorage`，Phase 4 以 `CookieSource` 接缝迁移，见 Agent.md §10.3）

**Interfaces:**
- Consumes: T1.6 的协议层（jvmShared 内互引用 OK）
- Produces: 解析链路（ShareLinkParser → ResolveRepository → DownloadLink）全部在 jvmShared

- [ ] **Step 1: 确定迁移清单（确定性判定，不许凭感觉）**

Run: `grep -L "^import android" composeApp/src/androidMain/kotlin/com/yunx/app/data/repository/*.kt`
Expected 输出含 `ShareResolveRepository.kt` 与全部 6 个 `*ResolveRepository.kt`。**只迁移列出的文件**；`grep -l`（含 android import）命中的 5 个 Account 仓库留在原地。

- [ ] **Step 2: 迁移 + 构建修复**

```bash
cd composeApp/src
mkdir -p jvmShared/kotlin/com/yunx/app/data/repository
git mv androidMain/kotlin/com/yunx/app/data/repository/ShareResolveRepository.kt jvmShared/kotlin/com/yunx/app/data/repository/
git mv androidMain/kotlin/com/yunx/app/data/repository/QuarkResolveRepository.kt jvmShared/kotlin/com/yunx/app/data/repository/
git mv androidMain/kotlin/com/yunx/app/data/repository/UCResolveRepository.kt jvmShared/kotlin/com/yunx/app/data/repository/
git mv androidMain/kotlin/com/yunx/app/data/repository/XunleiResolveRepository.kt jvmShared/kotlin/com/yunx/app/data/repository/
git mv androidMain/kotlin/com/yunx/app/data/repository/BaiduResolveRepository.kt jvmShared/kotlin/com/yunx/app/data/repository/
git mv androidMain/kotlin/com/yunx/app/data/repository/C139ResolveRepository.kt jvmShared/kotlin/com/yunx/app/data/repository/
git mv androidMain/kotlin/com/yunx/app/data/repository/Pan123ResolveRepository.kt jvmShared/kotlin/com/yunx/app/data/repository/
```

若编译报解析仓库引用了 androidMain 侧类型（如 Account Entity/DAO）：该依赖记录到提交信息中，把对应文件**退回 androidMain**并在 `docs/` 下记 TODO 行（Phase 2 DB KMP 化后重试），不许为此临时改接口。

- [ ] **Step 3: 验证 + Commit**

Run: `./gradlew :composeApp:assembleDebug :composeApp:jvmTest :composeApp:testDebugUnitTest`

```bash
git add -A
git commit -m "refactor: 解析仓库层迁入 jvmShared（Account 仓库待 Phase 4 CookieSource 接缝）"
```

### Task 1.8: Phase 1 收尾（依赖清理 + 全局门禁）

**Files:**
- Modify: `composeApp/build.gradle.kts`（依赖清理）
- Modify: `Agent.md`（如实现与 §10.3 表格签名有出入则回改）

- [ ] **Step 1: 依赖清理**

检查 `jvmShared` 是否已声明 OkHttp（T1.6 后 ChunkDownloader/HttpClients 需要）：

```kotlin
        jvmShared.dependencies {
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
        }
```

若 androidMain 内已无任何 okhttp import（`grep -rl "okhttp3" composeApp/src/androidMain` 为空），删除 androidMain 的 okhttp 行；否则保留并在 T2.x（DB KMP 化后）再清。

- [ ] **Step 2: 全局门禁（三项全跑）**

```bash
grep -rn "import android\." composeApp/src/jvmShared composeApp/src/commonMain   # 必须零命中
grep -rn "^import java\.\|^import javax\." composeApp/src/commonMain             # 必须零命中
./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest :composeApp:jvmTest
```

- [ ] **Step 3: 真机回归冒烟 + Commit**

冒烟清单：夸克/百度/139/123 四平台各解析一条链接；下载管理页 暂停/继续/删除；设置页改并发数即时生效（§3.5 Provider 闭包）。

```bash
git add -A
git commit -m "chore: Phase 1 收尾——jvmShared OkHttp 依赖归位，全局平台 import 门禁通过"
```

---

## 交付后动作

- 推送：`git -c http.proxy=http://127.0.0.1:7890 push origin desktop`；确认 GitHub Actions desktop 分支三门禁 job 全绿（`gh run list --repo Oliver13211/YunX --branch desktop`）。
- 向用户汇报：jvmShared 现有文件数/行数、三门禁状态、真机冒烟结果、遇到的 KAGP 源集差异等实现偏差。
- 下一阶段入口：Phase 2 首任务 = Room KMP 化（Agent.md §10.5），随后桌面骨架与解析链路。
