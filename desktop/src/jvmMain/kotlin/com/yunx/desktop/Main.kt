/*
 * YunX Desktop - 跨平台桌面版（KMP + Compose Multiplatform）
 * Copyright (C) 2026 Oliver13211
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

package com.yunx.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yunx.app.data.db.AppDatabase
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.db.BaiduAccountEntity
import com.yunx.app.data.db.C139AccountEntity
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.db.QuarkAccountEntity
import com.yunx.app.data.db.UCAccountEntity
import com.yunx.app.data.db.XunleiAccountEntity
import com.yunx.app.data.repository.XunleiAccountRepository
import com.yunx.app.data.db.get
import com.yunx.app.data.download.ChunkDownloader
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.network.BaiduApi
import com.yunx.app.data.network.BaiduConstants
import com.yunx.app.data.network.C139Api
import com.yunx.app.data.network.C139Constants
import com.yunx.app.data.network.HttpClients
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.Pan123Constants
import com.yunx.app.data.network.QuarkApi
import com.yunx.app.data.network.QuarkConstants
import com.yunx.app.data.network.UCApi
import com.yunx.app.data.network.UCConstants
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.network.XunleiConstants
import com.yunx.app.data.network.XunleiLoginStep
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import com.yunx.app.data.repository.BaiduResolveRepository
import com.yunx.app.data.repository.C139ResolveRepository
import com.yunx.app.data.repository.Pan123ResolveRepository
import com.yunx.app.data.repository.QuarkResolveRepository
import com.yunx.app.data.repository.ShareResolveRepository
import com.yunx.app.data.repository.UCResolveRepository
import com.yunx.app.data.repository.XunleiResolveRepository
import com.yunx.app.data.security.DesktopCredentialCipher
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

/** 系统浏览器打开登录页（Phase 4 方案B：网页登录 → 回贴 Cookie/Token；KCEF 待网络条件允许后接入）。 */
internal fun openBrowser(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.2f MB".format(bytes.toDouble() / (1L shl 20))
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes.toDouble() / (1L shl 10))
    else -> "$bytes B"
}

/** 各平台分享根目录 fid（对齐 Android ResolveViewModel.currentDefaultDirFid）。 */
internal fun rootDirFid(platform: SharePlatform): String = when (platform) {
    SharePlatform.BAIDU -> ""
    else -> "0"
}

/** 主界面左侧导航的 4 个选项卡（对齐原版 MainTab.kt；图标在 rail 处直接引用避免与常量名冲突）。 */
private enum class MainTab(val title: String) {
    Resolve("解析"), Drive("网盘"), Download("下载"), Settings("设置")
}

/** 程序化托盘图标（避免引入图片资源）：紫色圆点，对齐 Material 主色。 */
private val yunxTrayIcon = object : Painter() {
    override val intrinsicSize = Size(24f, 24f)
    override fun DrawScope.onDraw() {
        drawCircle(Color(0xFF6750A4), radius = 11f, center = Offset(12f, 12f))
        drawCircle(Color.White, radius = 4f, center = Offset(12f, 12f))
    }
}

fun main(args: Array<String>) {
    // 崩溃处理器最先安装：任何后续初始化阶段的未捕获异常都要落日志（~/.yunx/logs）
    CrashHandler.install()
    // 内嵌登录组件（KCEF）首次下载走 Java Http 层：设置 YUNX_PROXY=host:port 可走代理
    // （运行时源为 JetBrains 官方 CDN，一般无需代理）
    System.getenv("YUNX_PROXY")?.takeIf { it.contains(':') }?.let { proxy ->
        val host = proxy.substringBefore(':')
        val port = proxy.substringAfter(':')
        System.setProperty("https.proxyHost", host)
        System.setProperty("https.proxyPort", port)
        System.setProperty("http.proxyHost", host)
        System.setProperty("http.proxyPort", port)
    }
    // 无头自检：gradle :desktop:run --args="--kcef-smoke"
    if ("--kcef-smoke" in args) {
        val result = kotlinx.coroutines.runBlocking { kcefSmoke() }
        println("KCEF_SMOKE_RESULT: $result")
        kotlin.system.exitProcess(if (result.startsWith("OK")) 0 else 1)
    }
    application {
        val appSettings = remember { DesktopSettings() }
        val darkMode = remember { mutableStateOf(appSettings.darkMode) }
        val trayText = remember { mutableStateOf("YunX Desktop") }
        // Tray 是 ApplicationScope 扩展，必须在 application 块内调用
        Tray(icon = yunxTrayIcon, tooltip = trayText.value) {
            Item("退出", onClick = ::exitApplication)
        }
        Window(
            onCloseRequest = ::exitApplication,
        title = "YunX Desktop（开源版 · AGPL-3.0）",
            state = rememberWindowState(width = 900.dp, height = 780.dp)
        ) {
            MaterialTheme(
                colorScheme = when (darkMode.value) {
                    1 -> lightColorScheme(
                        primary = Color(0xFF6750A4), secondary = Color(0xFF625B71),
                        surfaceVariant = Color(0xFFE7E0EC), background = Color(0xFFF7F4FA)
                    )
                    2 -> darkColorScheme(
                        primary = Color(0xFFD0BCFF), secondary = Color(0xFFCCC2DC),
                        surfaceVariant = Color(0xFF49454F), background = Color(0xFF1C1B1F)
                    )
                    else -> lightColorScheme(
                        primary = Color(0xFF6750A4), secondary = Color(0xFF625B71),
                        surfaceVariant = Color(0xFFE7E0EC), background = Color(0xFFF7F4FA)
                    )
                }
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DesktopApp(appSettings, darkMode, trayText)
                }
            }
        }
    }
}

/** KCEF 自检：初始化 → 建浏览器 → 挂进可见窗口 → 执行 JS，验证完整通路。 */
internal suspend fun kcefSmoke(): String {
    val frame = java.awt.Frame("KCEF 自检")
    var browser: dev.datlag.kcef.KCEFBrowser? = null
    return try {
        ensureKcef { phase, pct -> println("KCEF_SMOKE_PHASE: $phase ${pct?.let { "%.0f%%".format(it * 100) } ?: ""}") }
        val client = dev.datlag.kcef.KCEF.newClient()
        val b = client.createBrowser("about:blank")
        browser = b
        // 窗口模式的原生浏览器（CefBrowserWindowMac，原崩溃点）要等组件挂进可见窗口、
        // peer 创建后才真正建立；不显示窗口则 JS 回调永远不会到来（进程挂起不退出）
        java.awt.EventQueue.invokeAndWait {
            frame.add(b.uiComponent, java.awt.BorderLayout.CENTER)
            frame.setSize(800, 600)
            frame.isVisible = true
        }
        var eval: String? = null
        repeat(15) {
            if (eval.isNullOrBlank()) {
                Thread.sleep(1000)
                eval = kotlinx.coroutines.withTimeoutOrNull(5000) {
                    runCatching { b.evaluateJavaScript("21*2") }.getOrNull()
                }
            }
        }
        if (eval == "42") "OK: JS 通路正常（21*2=42）" else "FAIL: JS 返回异常：$eval"
    } catch (e: Throwable) {
        "FAIL: ${e.message}"
    } finally {
        runCatching { browser?.dispose() }
        runCatching { java.awt.EventQueue.invokeAndWait { frame.dispose() } }
    }
}

@Composable
private fun DesktopApp(settings: DesktopSettings, darkMode: MutableState<Int>, trayText: MutableState<String>) {
    val db = remember { AppDatabase.get() }
    // settings 用调用方传入的单例（main 里 remember 创建）：这里若再 new 一个，
    // 两个实例的可观察状态互不相通（设置页改动对下载引擎/其他页不可见）
    val downloadManager = remember {
        DownloadManager(
            env = DesktopDownloadEnvironment(settings),
            dao = db.downloadTaskDao(),
            downloader = ChunkDownloader { HttpClients.downloadClient() },
            threadProvider = { platform -> settings.threadsFor(platform) },
            saveDirProvider = { settings.downloadDir },
            concurrencyProvider = { settings.maxConcurrent },
            speedLimitProvider = { settings.speedLimit },
            retryCountProvider = { settings.retryCount },
            keepWhenLockedProvider = { false },
            showSpeedProvider = { true },
            credentialCipher = DesktopCredentialCipher()
        )
    }
    val quarkDao = remember { db.quarkAccountDao() }
    val pan123Dao = remember { db.pan123AccountDao() }
    val ucDao = remember { db.ucAccountDao() }
    val baiduDao = remember { db.baiduAccountDao() }
    val c139Dao = remember { db.c139AccountDao() }
    val xunleiDao = remember { db.xunleiAccountDao() }
    val quarkApi = remember { QuarkApi() }
    val pan123Api = remember { Pan123Api() }
    val ucApi = remember { UCApi() }
    val baiduApi = remember { BaiduApi() }
    val c139Api = remember { C139Api() }
    val xunleiApi = remember { XunleiApi() }
    val xunleiRepo = remember { XunleiAccountRepository(xunleiDao, xunleiApi) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var embeddedReady by remember { mutableStateOf(settings.embeddedLoginEnabled) }
    var embeddedPhase by remember { mutableStateOf<String?>(null) }

    var quarkCookie by remember { mutableStateOf("") }
    var quarkStatus by remember { mutableStateOf("未登录") }
    var panToken by remember { mutableStateOf("") }
    var panStatus by remember { mutableStateOf("未登录") }
    var ucCookie by remember { mutableStateOf("") }
    var ucStatus by remember { mutableStateOf("未登录") }
    var baiduCookie by remember { mutableStateOf("") }
    var baiduStatus by remember { mutableStateOf("未登录") }
    var c139Cookie by remember { mutableStateOf("") }
    var c139Status by remember { mutableStateOf("未登录") }
    var xlStatus by remember { mutableStateOf("未登录") }
    var showXunleiLogin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        quarkDao.getAccount()?.let {
            if (it.cookie.isNotBlank()) { quarkCookie = it.cookie; quarkStatus = "已登录（读取自本地加密库）" }
        }
        pan123Dao.getAccount()?.let {
            if (it.accessToken.isNotBlank()) { panToken = it.accessToken; panStatus = "已登录（读取自本地加密库）" }
        }
        ucDao.getAccount()?.let {
            if (it.cookie.isNotBlank()) { ucCookie = it.cookie; ucStatus = "已登录（读取自本地加密库）" }
        }
        baiduDao.getAccount()?.let {
            if (it.cookie.isNotBlank()) { baiduCookie = it.cookie; baiduStatus = "已登录（读取自本地加密库）" }
        }
        c139Dao.getAccount()?.let {
            if (it.cookie.isNotBlank()) { c139Cookie = it.cookie; c139Status = "已登录（读取自本地加密库）" }
        }
        xunleiDao.getAccount()?.let {
            if (it.accessToken.isNotBlank()) { xlStatus = "已登录 · ${it.nickname}" }
        }
    }

    // 剪贴板自动捕获（登录页控制台脚本 copy(...) 之后回到应用即自动识别入库）
    var lastAutoCaptured by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            val text = clipboard.getText()?.toString()?.trim() ?: continue
            if (text == lastAutoCaptured) continue
            when {
                text.contains("__pus=") && text.contains("__puus=") && text != quarkCookie.trim() -> {
                    lastAutoCaptured = text
                    quarkCookie = text
                    scope.launch {
                        runCatching { quarkDao.upsert(QuarkAccountEntity(cookie = text)) }
                            .onSuccess { quarkStatus = "已自动捕获剪贴板 Cookie 并加密保存" }
                            .onFailure { quarkStatus = "保存失败：${it.message}" }
                    }
                }
                text.startsWith("eyJ") && text.contains(".") && text.length > 80 && text != panToken.trim() -> {
                    lastAutoCaptured = text
                    panToken = text
                    scope.launch {
                        runCatching { pan123Dao.upsert(Pan123AccountEntity(accessToken = text)) }
                            .onSuccess { panStatus = "已自动捕获剪贴板 Token 并加密保存" }
                            .onFailure { panStatus = "保存失败：${it.message}" }
                    }
                }
            }
        }
    }

    var shareText by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("粘贴分享链接开始解析（当前支持夸克 / 123 云盘）") }
    var files by remember { mutableStateOf<List<ShareFile>>(emptyList()) }
    var session by remember { mutableStateOf<ShareSession?>(null) }
    var sessionRepo by remember { mutableStateOf<ShareResolveRepository?>(null) }
    var sessionCookie by remember { mutableStateOf("") }
    var sessionPlatform by remember { mutableStateOf<SharePlatform?>(null) }
    var currentDirFid by remember { mutableStateOf("0") }
    val dirStack = remember { mutableStateListOf<Pair<String, String>>() } // fid to 名称
    var directLink by remember { mutableStateOf("") }
    var kcefLoginFor by remember { mutableStateOf<SharePlatform?>(null) }

    // ---------- 界面导航（左侧 4 选项卡，对齐原版 MainTab） ----------
    var tab by remember { mutableStateOf(0) }
    var cloudFiles by remember { mutableStateOf<List<ShareFile>>(emptyList()) }
    var cloudDirStack = remember { mutableStateListOf<Pair<String, String>>() } // fid to 名称
    var cloudLoading by remember { mutableStateOf(false) }
    var cloudMessage by remember { mutableStateOf("登录夸克后可浏览自己的网盘文件") }

    // 启动自动检查更新（Phase 5）：延迟 3 秒避开首屏，失败静默，发现新版本才提示
    var autoUpdate by remember { mutableStateOf<UpdateChecker.Update?>(null) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        autoUpdate = UpdateChecker.check()
    }

    /** 下载并初始化 KCEF 内嵌登录组件（一次性）；进度写 embeddedPhase，失败回调平台状态。 */
    fun startKcefDownload(onFail: (String) -> Unit) {
        if (embeddedPhase != null) return
        embeddedPhase = "准备下载"
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                ensureKcef { phase, pct ->
                    embeddedPhase = if (pct != null) "$phase %.0f%%".format(pct * 100) else phase
                }
            }.onSuccess {
                settings.embeddedLoginEnabled = true
                embeddedReady = true
                embeddedPhase = null
            }.onFailure {
                embeddedPhase = null
                onFail("组件下载失败：${it.message}（可重试）")
            }
        }
    }

    /** 加载个人盘指定目录（根目录 fid="0"）。Cookie 以 DB 为唯一事实源
     *  （对齐原版 Repository 层语义），不依赖 UI 状态变量；listCloudFiles
     *  返回 null 视为 Cookie 失效。 */
    fun loadCloudDir(fid: String) {
        cloudLoading = true
        scope.launch {
            val cookie = runCatching { quarkDao.getAccount()?.cookie.orEmpty().trim() }
                .getOrDefault("")
            if (cookie.isBlank()) {
                cloudMessage = "未检测到登录凭证，请先在 ① 登录夸克"
            } else {
                quarkCookie = cookie // 回填到输入框，便于用户查看/手动复制
                runCatching { quarkApi.listCloudFiles(fid, cookie) }
                    .onSuccess { list ->
                        if (list == null) {
                            cloudMessage = "列取失败：Cookie 可能已失效，请重新登录"
                        } else {
                            cloudFiles = list
                            cloudMessage = "当前目录 ${list.size} 项"
                        }
                    }
                    .onFailure {
                        it.printStackTrace()
                        cloudMessage = "列取失败：${it.message}"
                    }
            }
            cloudLoading = false
        }
    }

    val allTasks by db.downloadTaskDao().observeAll().collectAsState(initial = emptyList())
    LaunchedEffect(allTasks) {
        val active = allTasks.count { it.status == DownloadTaskEntity.STATUS_DOWNLOADING }
        trayText.value = if (active > 0) "YunX Desktop · $active 个下载中" else "YunX Desktop"
    }

    /** 加载指定目录（进入子目录 / 返回上级共用）。 */
    fun loadFiles(dirFid: String) {
        val s = session ?: return
        val repo = sessionRepo ?: return
        resolving = true
        scope.launch {
            runCatching { repo.listFiles(s, dirFid, sessionCookie).getOrThrow() }
                .onSuccess {
                    files = it
                    currentDirFid = dirFid
                    message = "「${s.title}」当前目录 ${it.size} 项"
                }
                .onFailure {
                    it.printStackTrace()
                    message = "列取文件失败：${it.message}"
                }
            resolving = false
        }
    }

    // 网盘页进入时自动加载根目录
    LaunchedEffect(tab) {
        if (tab == 1 && cloudFiles.isEmpty()) loadCloudDir("0")
    }

    Row(Modifier.fillMaxSize()) {
        NavigationRail {
            NavigationRailItem(
                selected = tab == 0, onClick = { tab = 0 },
                icon = { Icon(if (tab == 0) Icons.Filled.Link else Icons.Outlined.Link, contentDescription = "解析") },
                label = { Text("解析") }
            )
            NavigationRailItem(
                selected = tab == 1, onClick = { tab = 1 },
                icon = { Icon(if (tab == 1) Icons.Filled.Cloud else Icons.Outlined.Cloud, contentDescription = "网盘") },
                label = { Text("网盘") }
            )
            NavigationRailItem(
                selected = tab == 2, onClick = { tab = 2 },
                icon = { Icon(if (tab == 2) Icons.Filled.Download else Icons.Outlined.Download, contentDescription = "下载") },
                label = { Text("下载") }
            )
            NavigationRailItem(
                selected = tab == 3, onClick = { tab = 3 },
                icon = { Icon(if (tab == 3) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = "设置") },
                label = { Text("设置") }
            )
        }
        Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---------- 标题 ----------
        Column {
            Text("YunX Desktop", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "网盘分享解析与高速下载 · 开源（AGPL-3.0）· 仅供个人学习与技术交流",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (tab == 1) {
        // ---------- 登录卡（手风琴折叠：收起=名称+状态，展开=授权方式） ----------
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("① 网盘登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("点击网盘展开授权方式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                var expandedLogin by remember { mutableStateOf<String?>(null) }
                fun toggle(key: String) { expandedLogin = if (expandedLogin == key) null else key }

                AccountCard("夸克网盘", Color(0xFF6750A4), quarkStatus, expandedLogin == "quark", { toggle("quark") }) {
                    LoginBody(
                        loginUrl = QuarkConstants.LOGIN_URL,
                        captureScript = """copy(document.cookie);'云析：Cookie 已复制，回到应用自动保存'""",
                        embeddedReady = embeddedReady, embeddedPhase = embeddedPhase,
                        onEnableEmbedded = { startKcefDownload { err -> quarkStatus = err } },
                        onEmbeddedLogin = { kcefLoginFor = SharePlatform.QUARK },
                        hint = "手动粘贴整段 Cookie",
                        value = quarkCookie, onValueChange = { quarkCookie = it },
                        onSave = {
                            val cookie = quarkCookie.trim()
                            if (cookie.isNotEmpty() && QuarkConstants.isValidCookie(cookie)) {
                                scope.launch {
                                    runCatching {
                                        val nickname = quarkApi.fetchNickname(cookie) ?: "夸克用户"
                                        quarkDao.upsert(QuarkAccountEntity(cookie = cookie, nickname = nickname))
                                    }
                                        .onSuccess { quarkStatus = "已保存（AES-GCM 加密）" }
                                        .onFailure { quarkStatus = "保存失败：${it.message}" }
                                }
                            } else if (cookie.isEmpty()) quarkStatus = "Cookie 为空"
                            else quarkStatus = "未检测到登录态（缺少 __pus/__puus）"
                        }
                    )
                }
                AccountCard("123 云盘", Color(0xFF3B82F6), panStatus, expandedLogin == "pan123", { toggle("pan123") }) {
                    LoginBody(
                        loginUrl = "https://yun.123pan.com/",
                        captureScript = """copy(localStorage.getItem('authorToken')||'');'云析：Token 已复制，回到应用自动保存'""",
                        embeddedReady = embeddedReady, embeddedPhase = embeddedPhase,
                        onEnableEmbedded = { startKcefDownload { err -> panStatus = err } },
                        onEmbeddedLogin = { kcefLoginFor = SharePlatform.PAN123 },
                        hint = "手动粘贴 authorToken（JWT）",
                        value = panToken, onValueChange = { panToken = it },
                        onSave = {
                            val token = panToken.trim()
                            if (token.isNotEmpty()) {
                                scope.launch {
                                    runCatching { pan123Dao.upsert(Pan123AccountEntity(accessToken = token)) }
                                        .onSuccess { panStatus = "已保存（AES-GCM 加密）" }
                                        .onFailure { panStatus = "保存失败：${it.message}" }
                                }
                            } else panStatus = "Token 为空"
                        }
                    )
                }
                AccountCard("UC 网盘", Color(0xFFEF7C00), ucStatus, expandedLogin == "uc", { toggle("uc") }) {
                    LoginBody(
                        loginUrl = UCConstants.LOGIN_URL,
                        captureScript = """copy(document.cookie);'云析：Cookie 已复制，回到应用自动保存'""",
                        embeddedReady = embeddedReady, embeddedPhase = embeddedPhase,
                        onEnableEmbedded = { startKcefDownload { err -> ucStatus = err } },
                        onEmbeddedLogin = { kcefLoginFor = SharePlatform.UC },
                        hint = "手动粘贴整段 Cookie",
                        value = ucCookie, onValueChange = { ucCookie = it },
                        onSave = {
                            val cookie = ucCookie.trim()
                            if (cookie.isNotEmpty() && UCConstants.isValidCookie(cookie)) {
                                scope.launch {
                                    runCatching {
                                        val nickname = ucApi.fetchNickname(cookie) ?: "UC用户"
                                        ucDao.upsert(UCAccountEntity(cookie = cookie, nickname = nickname))
                                    }
                                        .onSuccess { ucStatus = "已保存（AES-GCM 加密）" }
                                        .onFailure { ucStatus = "保存失败：${it.message}" }
                                }
                            } else if (cookie.isEmpty()) ucStatus = "Cookie 为空"
                            else ucStatus = "未检测到登录态"
                        }
                    )
                }
                AccountCard("百度网盘", Color(0xFF2932E1), baiduStatus, expandedLogin == "baidu", { toggle("baidu") }) {
                    LoginBody(
                        loginUrl = BaiduConstants.LOGIN_URL,
                        captureScript = """copy(document.cookie);'云析：Cookie 已复制，回到应用自动保存'""",
                        embeddedReady = embeddedReady, embeddedPhase = embeddedPhase,
                        onEnableEmbedded = { startKcefDownload { err -> baiduStatus = err } },
                        onEmbeddedLogin = { kcefLoginFor = SharePlatform.BAIDU },
                        hint = "手动粘贴含 BDUSS 的整段 Cookie",
                        value = baiduCookie, onValueChange = { baiduCookie = it },
                        onSave = {
                            val cookie = baiduCookie.trim()
                            if (cookie.isNotEmpty() && BaiduConstants.isValidCookie(cookie)) {
                                scope.launch {
                                    runCatching {
                                        val nickname = baiduApi.fetchNickname(cookie) ?: "百度用户"
                                        baiduDao.upsert(BaiduAccountEntity(cookie = cookie, nickname = nickname))
                                    }
                                        .onSuccess { baiduStatus = "已保存（AES-GCM 加密）" }
                                        .onFailure { baiduStatus = "保存失败：${it.message}" }
                                }
                            } else if (cookie.isEmpty()) baiduStatus = "Cookie 为空"
                            else baiduStatus = "未检测到登录态（缺少 BDUSS）"
                        }
                    )
                }
                AccountCard("139 网盘（和彩云）", Color(0xFF0EA5E9), c139Status, expandedLogin == "c139", { toggle("c139") }) {
                    LoginBody(
                        loginUrl = C139Constants.LOGIN_URL,
                        captureScript = """copy(document.cookie);'云析：Cookie 已复制，回到应用自动保存'""",
                        embeddedReady = embeddedReady, embeddedPhase = embeddedPhase,
                        onEnableEmbedded = { startKcefDownload { err -> c139Status = err } },
                        onEmbeddedLogin = { kcefLoginFor = SharePlatform.C139 },
                        hint = "手动粘贴整段 Cookie（需已开通个人云盘）",
                        value = c139Cookie, onValueChange = { c139Cookie = it },
                        onSave = {
                            val cookie = c139Cookie.trim()
                            if (cookie.isNotEmpty() && C139Constants.isValidCookie(cookie)) {
                                scope.launch {
                                    runCatching { c139Dao.upsert(C139AccountEntity(cookie = cookie, nickname = "139用户")) }
                                        .onSuccess { c139Status = "已保存（AES-GCM 加密）" }
                                        .onFailure { c139Status = "保存失败：${it.message}" }
                                }
                            } else if (cookie.isEmpty()) c139Status = "Cookie 为空"
                            else c139Status = "未检测到登录态"
                        }
                    )
                }
                AccountCard("迅雷网盘", Color(0xFF1E6FFF), xlStatus, expandedLogin == "xunlei", { toggle("xunlei") }) {
                    Text(
                        "迅雷使用账号密码登录（可能触发短信验证），无需网页授权",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { showXunleiLogin = true }) { Text("打开登录窗口") }
                        Text(xlStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        }

        if (tab == 0) {
        // ---------- 解析卡 ----------
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("② 分享解析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = shareText,
                        onValueChange = { shareText = it },
                        label = { Text("粘贴分享链接（可含提取码）") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) }
                    )
                    Button(
                        enabled = !resolving,
                        onClick = {
                            val parsed = ShareLinkParser.parse(shareText)
                            if (parsed == null) { message = "无法识别分享链接"; return@Button }
                            val repo: ShareResolveRepository = when (parsed.platform) {
                                SharePlatform.QUARK -> QuarkResolveRepository(quarkApi)
                                SharePlatform.UC -> UCResolveRepository(ucApi)
                                SharePlatform.XUNLEI -> XunleiResolveRepository(
                                    xunleiApi,
                                    { xunleiRepo.getAccount()?.accessToken },
                                    { xunleiRepo.getAccount()?.deviceId },
                                    { xunleiRepo.getAccount()?.captchaToken },
                                    // token 过期自动用 refresh_token 刷新并持久化（对齐 Android MainScreen）
                                    refreshProvider = {
                                        val acc = xunleiRepo.getAccount()
                                        if (acc == null || acc.refreshToken.isBlank()) null
                                        else xunleiApi.refreshToken(acc.refreshToken, acc.deviceId)?.also { (a, r) ->
                                            xunleiRepo.updateTokens(a, r)
                                        }
                                    }
                                )
                                SharePlatform.BAIDU -> BaiduResolveRepository(baiduApi)
                                SharePlatform.C139 -> C139ResolveRepository(c139Api)
                                SharePlatform.PAN123 -> Pan123ResolveRepository(pan123Api) { panToken.trim().ifBlank { null } }
                            }
                            val useCookie = when (parsed.platform) {
                                SharePlatform.QUARK -> quarkCookie.trim()
                                SharePlatform.UC -> ucCookie.trim()
                                SharePlatform.BAIDU -> baiduCookie.trim()
                                SharePlatform.C139 -> c139Cookie.trim()
                                SharePlatform.PAN123 -> panToken.trim()
                                // 迅雷凭证由 accountProvider 从加密 DAO 读取，不走 cookie 参数
                                SharePlatform.XUNLEI -> ""
                            }
                            if (useCookie.isEmpty()) { message = "请先登录（保存 Cookie/Token）"; return@Button }
                            resolving = true
                            message = "解析中…"
                            files = emptyList()
                            directLink = ""
                            dirStack.clear()
                            currentDirFid = rootDirFid(parsed.platform)
                            scope.launch {
                                runCatching {
                                    // 与 Android 语义一致：传原始文本，仓库层内部自行解析
                                    repo.createSession(shareText, parsed.pwd, useCookie).getOrThrow()
                                }.onSuccess { s ->
                                    session = s
                                    sessionRepo = repo
                                    sessionCookie = useCookie
                                    sessionPlatform = parsed.platform
                                    repo.listFiles(s, currentDirFid, useCookie)
                                        .onSuccess {
                                            message = "「${s.title}」共 ${it.size} 项（根目录）"
                                            files = it
                                        }
                                        .onFailure {
                                            it.printStackTrace()
                                            message = "列取文件失败：${it.message}"
                                        }
                                }.onFailure {
                                    it.printStackTrace()
                                    message = "解析失败：${it.message}"
                                }
                                resolving = false
                            }
                        }
                    ) {
                        if (resolving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (resolving) "解析中" else "解析")
                    }
                }
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---------- 文件卡 ----------
        val s = session
        if (s != null) {
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("③ 分享内容", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            dirStack.joinToString(" / ") { it.second }.ifBlank { "根目录" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (dirStack.isNotEmpty()) {
                            OutlinedButton(onClick = {
                                val popped = dirStack.removeAt(dirStack.lastIndex)
                                loadFiles(popped.first)
                            }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("上级")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        items(files) { file ->
                            FileRow(
                                file = file,
                                resolving = resolving,
                                onEnterDir = {
                                    dirStack.add(file.fid to file.fname)
                                    loadFiles(file.fid)
                                },
                                onGetLink = {
                                    val repo = sessionRepo ?: return@FileRow
                                    resolving = true; directLink = ""
                                    scope.launch {
                                        runCatching { repo.getShareDownloadLink(s, file, sessionCookie).getOrThrow() }
                                            .onSuccess {
                                                directLink = it.downloadUrl
                                                clipboard.setText(AnnotatedString(it.downloadUrl))
                                                message = "直链已复制到剪贴板"
                                            }
                                            .onFailure { message = "取直链失败：${it.message}" }
                                        resolving = false
                                    }
                                },
                                onDownload = {
                                    val repo = sessionRepo ?: return@FileRow
                                    val platform = sessionPlatform ?: return@FileRow
                                    resolving = true
                                    scope.launch {
                                        runCatching {
                                            val link = repo.getShareDownloadLink(s, file, sessionCookie).getOrThrow()
                                            // 请求头语义对齐 Android ResolveViewModel.enqueueDownload（§5.3 CDN 约束）
                                            val platformConst = when (platform) {
                                                SharePlatform.XUNLEI -> DownloadPlatform.XUNLEI
                                                SharePlatform.UC -> DownloadPlatform.UC
                                                SharePlatform.BAIDU -> DownloadPlatform.BAIDU
                                                SharePlatform.C139 -> DownloadPlatform.C139
                                                SharePlatform.PAN123 -> DownloadPlatform.PAN123
                                                else -> DownloadPlatform.QUARK
                                            }
                                            val headers = when (platform) {
                                                // 迅雷直链必须官方 app UA，浏览器 UA 触发 CDN 降级（200整文件）
                                                SharePlatform.XUNLEI -> mapOf("User-Agent" to XunleiConstants.APP_UA)
                                                SharePlatform.BAIDU -> mapOf(
                                                    "Cookie" to sessionCookie, "User-Agent" to BaiduConstants.UA_NETDISK
                                                )
                                                SharePlatform.C139 -> mapOf("User-Agent" to C139Constants.PC_UA)
                                                SharePlatform.UC -> mapOf(
                                                    "Cookie" to sessionCookie, "User-Agent" to UCConstants.USER_AGENT,
                                                    "Referer" to UCConstants.DOWNLOAD_REFERER, "Origin" to UCConstants.WEB_ORIGIN
                                                )
                                                SharePlatform.PAN123 -> mapOf(
                                                    "User-Agent" to Pan123Constants.WEB_UA, "Referer" to Pan123Constants.DOWNLOAD_REFERER
                                                )
                                                else -> mapOf(
                                                    "Cookie" to sessionCookie, "User-Agent" to QuarkConstants.API_USER_AGENT,
                                                    "Referer" to QuarkConstants.DOWNLOAD_REFERER
                                                )
                                            }
                                            downloadManager.enqueue(link.downloadUrl, link.filename, headers, link.size, platformConst)
                                        }.onSuccess {
                                            message = "已加入下载任务"
                                            directLink = ""
                                        }.onFailure {
                                            it.printStackTrace()
                                            message = "加入下载失败：${it.message}"
                                        }
                                        resolving = false
                                    }
                                }
                            )
                        }
                    }
                    if (directLink.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("直链：$directLink", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { directLink = "" }) { Text("关闭直链显示") }
                    }
                }
            }
        }

        }

        // ---------- 我的网盘视图 ----------

        // ---------- 我的网盘视图 ----------
        if (tab == 1) {
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("④ 我的夸克网盘", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            cloudDirStack.joinToString(" / ") { it.second }.ifBlank { "根目录" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (cloudDirStack.isNotEmpty()) {
                            OutlinedButton(onClick = {
                                val popped = cloudDirStack.removeAt(cloudDirStack.lastIndex)
                                loadCloudDir(popped.first)
                            }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("上级")
                            }
                        }
                        OutlinedButton(
                            enabled = quarkCookie.isNotBlank() && !cloudLoading,
                            onClick = { loadCloudDir(cloudDirStack.lastOrNull()?.first ?: "0") }
                        ) { Text("刷新") }
                    }
                    run {
                        Text(cloudMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                            items(cloudFiles) { file ->
                                FileRow(
                                    file = file,
                                    resolving = cloudLoading,
                                    onEnterDir = {
                                        cloudDirStack.add(file.fid to file.fname)
                                        loadCloudDir(file.fid)
                                    },
                                    onGetLink = {
                                        cloudLoading = true
                                        scope.launch {
                                            runCatching { quarkApi.getDownloadLink(file.fid, quarkCookie.trim()) }
                                                .onSuccess { link ->
                                                    if (link != null) {
                                                        clipboard.setText(AnnotatedString(link.downloadUrl))
                                                        cloudMessage = "直链已复制到剪贴板"
                                                    } else cloudMessage = "未取到直链"
                                                }
                                                .onFailure { cloudMessage = "取直链失败：${it.message}" }
                                            cloudLoading = false
                                        }
                                    },
                                    onDownload = {
                                        cloudLoading = true
                                        scope.launch {
                                            runCatching {
                                                val link = quarkApi.getDownloadLink(file.fid, quarkCookie.trim())
                                                    ?: throw IllegalStateException("未取到直链")
                                                // 个人盘直链请求头：与分享下载一致（Cookie+UA+Referer 防盗链）
                                                downloadManager.enqueue(
                                                    link.downloadUrl,
                                                    link.filename,
                                                    mapOf(
                                                        "Cookie" to quarkCookie.trim(),
                                                        "User-Agent" to QuarkConstants.API_USER_AGENT,
                                                        "Referer" to QuarkConstants.DOWNLOAD_REFERER
                                                    ),
                                                    link.size,
                                                    DownloadPlatform.QUARK
                                                )
                                            }.onSuccess { cloudMessage = "已加入下载任务" }
                                                .onFailure { cloudMessage = "加入下载失败：${it.message}" }
                                            cloudLoading = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- 下载卡 ----------
        if (tab == 2) {
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    DownloadsSection(db, downloadManager, settings)
                }
            }
        }

        // ---------- 设置卡 ----------
        if (tab == 3) {
            SettingsSection(settings, darkMode)
        }
    }

    // 迅雷账号登录独立窗口
    if (showXunleiLogin) {
        XunleiLoginWindow(
            repo = xunleiRepo,
            onClose = { showXunleiLogin = false },
            onStatus = { xlStatus = it }
        )
    }

    // 启动自动检查更新的提示框
    autoUpdate?.let { update ->
        UpdateFoundDialog(update) { autoUpdate = null }
    }

    // KCEF 内嵌登录窗（可选组件：下载启用后可用）
    kcefLoginFor?.let { platform ->
        KcefLoginWindow(
            platform = platform,
            onClose = { kcefLoginFor = null },
            onCaptured = { credential ->
                // 校验门槛与昵称落库对齐 Android 端各 AccountRepository.save*Account
                scope.launch {
                    when (platform) {
                        SharePlatform.UC -> runCatching {
                            val cookie = credential.trim()
                            if (!UCConstants.isValidCookie(cookie)) throw IllegalStateException("未检测到登录态，请重新登录")
                            ucDao.upsert(UCAccountEntity(cookie = cookie, nickname = ucApi.fetchNickname(cookie) ?: "UC用户"))
                            ucCookie = cookie
                            ucStatus = "已保存（AES-GCM 加密）"
                            kcefLoginFor = null
                        }.onFailure { ucStatus = "保存失败：${it.message}" }
                        SharePlatform.BAIDU -> runCatching {
                            val cookie = credential.trim()
                            if (!BaiduConstants.isValidCookie(cookie)) throw IllegalStateException("未检测到登录态（缺少 BDUSS），请重新登录")
                            baiduDao.upsert(BaiduAccountEntity(cookie = cookie, nickname = baiduApi.fetchNickname(cookie) ?: "百度用户"))
                            baiduCookie = cookie
                            baiduStatus = "已保存（AES-GCM 加密）"
                            kcefLoginFor = null
                        }.onFailure { baiduStatus = "保存失败：${it.message}" }
                        SharePlatform.C139 -> runCatching {
                            val cookie = credential.trim()
                            if (!C139Constants.isValidCookie(cookie)) throw IllegalStateException("未检测到登录态，请重新登录")
                            c139Dao.upsert(C139AccountEntity(cookie = cookie, nickname = "139用户"))
                            c139Cookie = cookie
                            c139Status = "已保存（AES-GCM 加密）"
                            kcefLoginFor = null
                        }.onFailure { c139Status = "保存失败：${it.message}" }
                        SharePlatform.PAN123 -> runCatching {
                            val token = credential.trim()
                            val nickname = pan123Api.fetchNickname(token)
                                ?: throw IllegalStateException("Token 校验失败（user/info 不通过），请重新登录")
                            pan123Dao.upsert(Pan123AccountEntity(accessToken = token, nickname = nickname))
                            panToken = token
                            panStatus = "已保存（AES-GCM 加密）· ${nickname}"
                            kcefLoginFor = null
                        }.onFailure { panStatus = "保存失败：${it.message}" }
                        else -> runCatching {
                            val cookie = credential.trim()
                            if (!QuarkConstants.isValidCookie(cookie)) {
                                throw IllegalStateException("未检测到登录态（缺少 __pus/__puus），请重新登录")
                            }
                            val nickname = quarkApi.fetchNickname(cookie) ?: "夸克用户"
                            quarkDao.upsert(QuarkAccountEntity(cookie = cookie, nickname = nickname))
                            quarkCookie = cookie
                            val fields = cookie.split("; ").count { it.contains('=') }
                            // 关键字段自检：原版个人盘/分享 API 依赖的 Cookie 键
                            val required = listOf("__pus", "__puus", "__kp", "__kps", "__ktd", "__uid")
                            val missing = required.filter { !cookie.contains("$it=") }
                            quarkStatus = "已保存 · ${nickname} · ${fields}个字段" +
                                (if (missing.isEmpty()) " · 关键字段✓" else " · 缺少${missing.joinToString("/")}")
                            kcefLoginFor = null
                        }.onFailure { quarkStatus = "保存失败：${it.message}" }
                    }
                }
            }
        )
    }
}

    }
/** 登录授权方式区（折叠展开内容）：打开登录页 / 抓取脚本 / 内嵌组件 / 粘贴保存。 */
@Composable
private fun LoginBody(
    loginUrl: String,
    captureScript: String,
    embeddedReady: Boolean,
    embeddedPhase: String?,
    onEnableEmbedded: () -> Unit,
    onEmbeddedLogin: () -> Unit,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { openBrowser(loginUrl) }) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("打开登录页")
            }
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(captureScript))
                openBrowser(loginUrl)
            }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("复制抓取脚本")
            }
        }
        Text(
            "三步：① 在打开的网页完成登录 ② F12 打开控制台，粘贴刚复制的脚本并回车 ③ 回到本应用，自动识别保存",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true
            )
            TextButton(onClick = onSave) { Text("手动保存") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                embeddedReady -> TextButton(onClick = onEmbeddedLogin) { Text("内嵌窗口登录") }
                embeddedPhase != null -> {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        "登录组件：$embeddedPhase",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> TextButton(onClick = onEnableEmbedded) {
                    Text("下载内嵌登录组件（一次性）")
                }
            }
        }
    }
}

/** 文件行：目录可点击进入，文件支持取直链/下载。 */
@Composable
private fun FileRow(
    file: ShareFile,
    resolving: Boolean,
    onEnterDir: () -> Unit,
    onGetLink: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().clickable(enabled = file.isdir, onClick = onEnterDir),
        shape = RoundedCornerShape(10.dp),
        color = if (file.isdir) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (file.isdir) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isdir) Color(0xFFB892E0) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(file.fname, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (file.isdir) "目录 · 点击进入" else formatSize(file.fsize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (file.isdir) {
                Text("进入", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(enabled = !resolving, onClick = onGetLink) { Text("直链") }
                OutlinedButton(enabled = !resolving, onClick = onDownload) { Text("下载") }
            }
        }
    }
}

/** 网盘账号折叠卡（手风琴）：收起=图标+名称+状态，展开=授权方式区。 */
@Composable
private fun AccountCard(
    label: String,
    tint: Color,
    status: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            androidx.compose.animation.AnimatedVisibility(expanded) {
                Column(
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) { content() }
            }
        }
    }
}
