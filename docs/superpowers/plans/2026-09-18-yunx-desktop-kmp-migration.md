# YunX 桌面版（KMP + Compose Multiplatform）移植计划书

> **状态**：已批准（v1.0，2026-09-18；决策记录见第七节）。下一步：按 Phase 0/1 细化任务级实施计划（逐任务 TDD 步骤）。

**目标（Goal）：** 将 Android 应用 YunX（云析，AGPL-3.0，~4 万行 Kotlin）移植为 Windows / macOS 桌面应用（Linux 后续），最大化复用其网盘协议解析与分片下载核心，同时保持 Android 端不受破坏。

**架构（Architecture）：** 将现有单模块 Android 工程逐步 KMP 化：抽出纯 Kotlin 共享模块 `core`（协议层 + 下载引擎 + 业务仓库），Android 端保留为 `composeApp`，新增 Compose Multiplatform 桌面目标 `desktop`。平台差异点（安全存储、WebView 登录、通知、路径）通过 `expect/actual` 接缝隔离。

**技术栈（Tech Stack）：** Kotlin 2.2.x · Compose Multiplatform 1.9.x（桌面） · OkHttp 4.12（JVM 桌面直接复用） · Room 2.7.x（KMP，桌面走 sqlite-jdbc） · org.jetbrains.androidx.lifecycle（多平台 ViewModel） · KCEF 或 JCEF（桌面 WebView 登录） · java-keyring + DPAPI（桌面凭证安全存储） · compose Gradle 插件 nativeDistributions/jpackage（打包）

## 全局约束

- **许可证**：衍生作品必须继续以 **AGPL-3.0** 开源分发，保留版权与许可声明，注明修改；禁止闭源/收费分发。所有新增依赖须与 AGPL 兼容（优先 Apache-2.0；JCEF 为 LGPL，动态链接可接受）。
- **不变式**：任何阶段结束时，Android 端必须保持可构建、既有 JVM 单元测试（解析器/Range/HLS/路径策略）保持全绿。
- **不做的事**：不改协议行为、不重写下载引擎、不引入 DI 框架（遵循 Agent.md 约定 3.1）。
- **上游同步**：网盘接口易失效，桌面分支须可持续合并上游 `CYQawa/YunX` 的协议修复——这是选择"KMP 化现有仓库"而非"另起纯桌面仓库"的根本原因。
- **命名**：桌面版定名 **YunX Desktop**。

---

## 一、KMP 社区与生态调研结论

### 1.1 成熟度（结论：生产级，非试验性技术）

| 事实 | 依据 |
|---|---|
| KMP 自 2023 年 11 月起 Stable，Google 官方一等支持（Android Studio 内置模板） | kotlinlang.org、developer.android.com |
| 宣称 20,000+ 公司使用；代表采用者：Netflix（2020 年起生产）、Google Docs、McDonald's、Duolingo、Forbes、Cash App、H&M | Kotlin 官方案例页 |
| Compose Multiplatform **桌面端 1.0 即稳定（2021 年）**，是 CMP 最成熟的目标；iOS 2025-05（1.8）转正；1.9（2025-09）起提供多平台 ViewModel 实验性 API | JetBrains 官方博客 |
| 多平台 ViewModel：`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`（含桌面 JVM 目标，版本线跟随 androidx lifecycle，当前 2.9.x/2.11） | klibs.io、Maven Central |
| Room **2.7.0（2025-04）稳定版起支持 KMP，含桌面 JVM**，需自配 SQLite JDBC 驱动；Room 3.0 路线图继续加码 KMP | Android 官方文档、Android 开发者博客 |

### 1.2 关键生态件（本项目全部有维护中的方案）

| 需求 | 方案 | 状态 |
|---|---|---|
| 桌面 WebView（登录） | ① KCEF（`dev.datlag:kcef`，封装 JCEF，2025-08 仍在发版）② JBR 自带 JCEF ③ KevinNzou 的 CMP WebView 库 ④ JxBrowser（商业） | 充足，①/②为主选项 |
| 桌面安全存储 | Kassaforte（KMP，JVM 端封装 java-keyring + windpapi4j/DPAPI）；或直接用 java-keyring（Keychain / DPAPI / Secret Service） | 可用，社区规模偏小但底层库成熟 |
| 偏好设置 | `com.russhwolf:multiplatform-settings`（事实标准）或 DataStore KMP | 成熟 |
| 打包分发 | compose Gradle 插件 `nativeDistributions`（底层 jpackage）→ dmg / msi(WiX) / deb / appimage；官方文档 2026-08 仍在更新 | 官方主线 |
| 网络层 | OkHttp 在桌面 JVM 上原样运行（本项目协议层即 OkHttp，零迁移） | 无风险 |

### 1.3 社区风险提示（如实呈现）

- KMP 桌面方向的**库多样性**不如 Web 技术栈（Electron/Tauri），部分库（如 Kassaforte）维护者单一——因此计划中安全存储保留"直接用 java-keyring / 或复用项目内口令派生加密"的降级路径。
- 桌面端无官方 WebView 组件（YouTrack 有请求未排期），需引入第三方 KCEF/JCEF，这是本项目**最大的单点技术风险**（见风险清单）。
- 总体判断：**生态足以支撑本项目**。本项目核心依赖（OkHttp/Room/CMP/多平台 lifecycle）全部是 Google 或 JetBrains 一方维护，恰恰是 KMP 生态中最厚的部分。

## 二、移植难度评估（按模块）

数据基于对代码库的逐层分析（149 文件 / 40,400 行；111 个文件含 `android.*` import，但深度耦合集中在 UI 边缘与少数平台件）。

| 模块 | 规模 | 难度 | 复用率预估 | 说明 |
|---|---|---|---|---|
| 协议层 `data/network` | 5,583 行 | ★☆☆☆☆ | ~98% | 仅 3 个文件用 `android.util.Base64`（→`java.util.Base64` 一对一替换）+ 1 个设备指纹用 `Context`（expect/actual） |
| 下载引擎 `data/download` | 2,442 行 | ★★☆☆☆ | ~85% | 引擎仅依赖 `Log`；`DownloadSaver` 的 MediaStore/SAF 分支在桌面侧直接走 `java.io.File`（反而更简单）；`DownloadService` 通知壳重写为托盘/后台协程 |
| 业务仓库 `data/repository` | 1,518 行 | ★★☆☆☆ | ~90% | 5 个文件耦合点均为登录 Cookie 来源（WebView→抽象接口） |
| 数据库 `data/db` | 996 行 | ★★☆☆☆ | ~95% | Room 2.6.1→2.7.x KMP：Entity/DAO/Migration 基本原样；需加 SQLite JDBC 驱动与 `BundledSQLiteDriver` 配置 |
| 设置 `data/prefs` | 148 行 | ★☆☆☆☆ | ~80% | SharedPreferences→multiplatform-settings，单文件改造 |
| 凭证安全 `data/security`+`backup` | 504 行 | ★★★☆☆ | ~70% | Android Keystore→java-keyring/DPAPI（expect/actual）；降级方案：复用 `AuthCrypto.kt` 口令派生 AES-GCM（代码已在） |
| UI `ui/` + 入口 | 27,600 行 | ★★★☆☆ | ~70% | import 面几乎全是 `androidx.compose.*`（**与 CMP 桌面包名一致，零改动**）；需处理的是文件内的 `Context`/`WebView`/`LocalContext`、返回键处理、Snackbar 宿主；ViewModel 自定义 Factory 可平移到多平台 lifecycle |
| WebView 登录（新写） | ~1 个组件 | ★★★★☆ | 0%（新写） | KCEF 内嵌 or 系统浏览器+回贴 Cookie。**难度与风险最高项**，但影响面收敛在 `ui/login/*` 的 6 个登录屏共用的一个组件 |
| 打包签名分发 | — | ★★★☆☆ | — | jpackage 出三平台安装包不难；macOS 公证需 Apple Developer 账号（$99/年）；Windows SmartScreen 认证签名证书可选（无证书=首次运行多一步"仍要运行"） |

**综合评估：中等难度（约 6/10），主要不确定性集中在"桌面 WebView 登录"与"三平台分发签名"两处，均为工程问题而非技术不可行。**

## 三、目标工程结构

```
YunXDesktop/
├── composeApp/            # 原 app 模块改造（Android + 共享源集）
│   └── src/
│       ├── commonMain/    # core 逻辑：data/network, data/download, data/repository, data/db, util
│       ├── androidMain/   # actual: SecureStore, WebLogin, Notifier, Paths
│       └── jvmMain/       # actual: 桌面三平台实现
├── desktop/               # CMP 桌面入口：Main.kt(Window)、托盘、桌面主题、jpackage 配置
└── docs/superpowers/plans/
```

expect/actual 接缝（首批，均为小接口）：

```kotlin
// commonMain
expect fun platformBase64Decode(data: String): ByteArray   // android.util.Base64 / java.util.Base64
expect interface SecureStore { fun put(key: String, value: ByteArray); fun get(key: String): ByteArray? }
expect class WebLoginHandle  // WebView 句柄抽象：Android=AndroidView(WebView)，Desktop=KCEF 浏览器
expect fun currentLogSink()  // android.util.Log / slf4j
```

## 四、分阶段实施计划

> 每个 Phase 产出可验证的独立成果；Phase 0–1 完成后才细化任务级 TDD 计划。

### Phase 0：工程 KMP 化改造（Android 端保持全绿）
**范围**：升级 Kotlin 2.1.0→2.2.x、AGP、Room 2.6.1→2.7.x；`app` 改造为 `composeApp` 共享模块（Android target 优先跑通）；建立 `commonMain/androidMain` 源集划分；CI 增加"Android APK 构建成功"门禁。
**交付判据**：Android APK 构建成功并在真机安装冒烟通过；既有 JVM 单测全绿。
**预估**：2–4 天（含 Room 迁移回归：必须沿用既有 Migration，禁止破坏性迁移——Agent.md 约定 3.7）。

### Phase 1：core 去 Android 化（jvmShared 共享层达成）
**范围**：落地 expect/actual 接缝（Base64、日志、设备指纹 KV、下载环境/WakeLock；SecureStore 与 CookieSource 接缝随 Phase 4 落地）；`ShareLinkParser`、`HttpRangePolicy`、`ChunkDownloader`、`HlsDownloader`、六家网盘 API 全部移入 `jvmShared` 中间源集（JVM 系共享层，OkHttp 与 `java.*` 可用）；新增 JVM 目标并让测试在桌面 JVM 源集跑通。**细化任务级计划见 `2026-09-18-phase0-1-kmp-foundation.md`**（注：`data/db` 不在本阶段，Room KMP 化为 Phase 2 首任务）。
**交付判据**：`gradle :composeApp:jvmTest` 全绿（复用现有 7 个测试类 + 协议层新测试）；Android 端行为无变化。
**预估**：2–3 天。

### Phase 2：桌面骨架 + 解析链路打通（首个可见里程碑）
**范围**：首任务 **`data/db` Room KMP 化**（Entity/DAO/AppDatabase 迁 `jvmShared`，桌面端 `BundledSQLiteDriver` + sqlite-jdbc，沿用既有 Migration）；随后 `desktop` 模块：CMP Window、导航壳、Material3 主题；接入真实协议：粘贴分享链接→解析→文件列表→获取直链（先做夸克 + 123 云盘这两条"免 WebView 登录/易验证"的链路，123 用账号密码，夸克可先手工导入 Cookie）；系统文件选择器选目录。
**交付判据**：桌面端对真实分享链接完成"解析→列出文件→拿到直链 URL"（人工验收 + curl 验证直链可 Range 请求）。
**预估**：2–3 天。

### Phase 3：下载引擎桌面落地
**范围**：`DownloadManager` 线程模型改协程作用域注入；并发 32、分片计划、断点续传原样复用；进度持久化节流策略照搬（Agent.md 5.5 的 ANR 教训）；下载管理 UI（暂停/继续/删除/打开文件——桌面"打开"用 `Desktop.open()`）；托盘显示聚合进度。
**交付判据**：断网续传、并发限速、大文件（>1GB）下载在 macOS 实测通过；`.part` 分片与任务恢复语义与 Android 版一致（同一套 `DownloadSaver` 单测覆盖）。
**预估**：3–5 天。

### Phase 4：登录与凭证（风险最高阶段）
**范围**：
- **选定方案：KCEF 内嵌 WebView**（已决策）：`ui/login/*` 六个登录屏接入 `WebLoginHandle` 抽象，Cookie 拦截逻辑与 Android 版共用；
- **降级预案（仅当 KCEF 集成受阻时启用）**：唤起系统浏览器完成网页登录，用户回贴 Cookie/授权串（alist 模式）；
- 桌面 `SecureStore`：macOS Keychain / Windows DPAPI / Linux Secret Service（java-keyring）；密钥丢失场景降级到 `AuthCrypto` 口令派生加密。
- 认证备份/恢复功能平移。
**交付判据**：六家网盘在 macOS 桌面端完成登录→解析→下载全链路；凭证重启后免登录；备份文件与 Android 版互导成功。
**预估**：5–8 天。

### Phase 5：打磨与三平台分发
**范围**：三平台托盘/通知（通知降级为可选）；崩溃处理器→日志文件 + 错误对话框；`UpdateChecker`→检查 GitHub Releases；`LaunchGate` 反篡改自检**不迁移**（桌面无 APK 签名场景，完整性改由 GitHub Releases 官方渠道 + SHA-256 校验和承担，见风险表）；jpackage 配置（dmg/msi/deb，捆绑 JBR）；GitHub Actions 三平台构建矩阵；macOS **直接 dmg 分发（未公证、不上架，已决策不购买开发者账号）**；Windows 可选签名。
**交付判据**：CI 三平台产物可下载；macOS dmg 首次运行可通过右键打开/系统设置放行（发布说明写明操作步骤）；Windows 无证书包首次运行可通过；安装包体积记录在案（预估 80–150MB，捆绑 JVM 所致，如实接受）。
**预估**：3–5 天。

**总量估算：单人专注约 17–28 个工作日（4–6 周自然周）。**

## 五、风险清单与对策

| # | 风险 | 概率/影响 | 对策 |
|---|---|---|---|
| 1 | KCEF/JCEF 桌面集成坑（JBR 兼容性、Linux 依赖） | 中/高 | Phase 4 双方案设计（A 内嵌 / B 系统浏览器回贴）；B 为已完成度高的保底路径 |
| 2 | 网盘接口随时间失效，桌面开发期间上游改动 | 高/中 | KMP 化而非另起仓库；每 Phase 开始先 `git merge upstream` |
| 3 | Room 2.7 桌面端并发锁问题（官方 tracker 有早期 alpha 报告） | 低/中 | 下载进度落盘已有节流设计；问题复现时切 SQLDelight（DAO 层改动收敛） |
| 4 | 未签名分发在 macOS/Windows 的首启阻力（Gatekeeper / SmartScreen 警告） | 确定/低 | **已决策：不公证、不上架、不购签名证书**；dmg 发布说明写明"右键打开/系统设置放行"，Windows 写明"仍要运行"；后续公开发布时再评估是否签名 |
| 5 | 反云注入自检代码（Agent.md §9）在桌面无意义且字符串加密不可读 | 确定/低 | 桌面版不迁移该模块；保留在 Android 端不动 |
| 6 | 4 万行中 UI 占 70%，逐文件清理 android import 工作量被低估 | 中/中 | 已抽样确认 import 面集中在 8 类 API（Context/WebView/Log/Uri/本地通知/返回键/剪贴板/权限）；按类批量替换而非逐文件手改 |
| 7 | 合规：AGPL 义务被无意违反（如引用了倒卖版资源） | 低/高 | 分发前做依赖树审计（`licenseReport`）；仅从上游官方仓库与 Maven Central 取材 |

## 六、许可证合规清单（分发前逐项核对）

- [ ] LICENSE（AGPL-3.0 全文）随安装包分发
- [ ] 源码仓库公开、含全部修改，README 注明基于 YunX (AGPL-3.0) 及修改点
- [ ] 保留上游版权声明；桌面版新名称 + 免责声明
- [ ] 第三方依赖清单及许可证（OkHttp/Room/CMP/multiplatform-settings：Apache-2.0；JCEF：LGPL-2.1 动态链接；java-keyring：Apache-2.0……以构建时 `licenseReport` 为准）
- [ ] 不使用上游"耻辱榜"涉事方的任何二次打包产物

## 七、决策记录（2026-09-18 已拍板）

| # | 决策项 | 结论 |
|---|---|---|
| 1 | 登录方案 | **KCEF 内嵌 WebView**；"系统浏览器回贴 Cookie"仅作为 KCEF 集成受阻时的降级预案 |
| 2 | macOS 分发 | **不做公证、不上架商店，直接 dmg 分发**（不购买 Apple Developer 账号）；首启放行步骤写入发布说明 |
| 3 | 桌面版命名 | **YunX Desktop** |
| 4 | 仓库策略 | **在本人 fork（Oliver13211/YunX）上创建 `desktop` 分支实施**，保持与上游 CYQawa/YunX 的合并能力 |
| 5 | Linux | **预留实施可能性，首批不实施**：jvmMain 源集与跨平台接缝（expect/actual）照常设计，Linux 专属工作（打包、适配测试）延后 |

---

**来源（调研部分）**：[Kotlin Multiplatform 官方文档](https://kotlinlang.org)、[KMP 案例研究](https://kotlinlang.org/lp/multiplatform/)、[JetBrains 博客 CMP 1.9 发布](https://blog.jetbrains.com/kotlin/)、[Set up Room for KMP（Android 官方）](https://developer.android.com)、[Kassaforte（klibs.io）](https://klibs.io/project/N7ghtm4r3/Kassaforte)、[java-keyring](https://github.com/javakeyring/java-keyring)、[KCEF（dev.datlag）](https://mvnrepository.com/artifact/dev.datlag/kcef)、[CMP WebView 讨论（Kotlinlang Slack）](https://slack-chats.kotlinlang.org)、[Native distributions 打包文档](https://kotlinlang.org/docs/native-distributions.html)、[Compose Desktop 生产实践](https://composables.com)。
