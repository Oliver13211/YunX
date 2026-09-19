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
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.db.QuarkAccountEntity
import com.yunx.app.data.db.get
import com.yunx.app.data.download.ChunkDownloader
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.network.HttpClients
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.Pan123Constants
import com.yunx.app.data.network.QuarkApi
import com.yunx.app.data.network.QuarkConstants
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import com.yunx.app.data.repository.Pan123ResolveRepository
import com.yunx.app.data.repository.QuarkResolveRepository
import com.yunx.app.data.repository.ShareResolveRepository
import com.yunx.app.data.security.DesktopCredentialCipher
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

/** 系统浏览器打开登录页（Phase 4 方案B：网页登录 → 回贴 Cookie/Token；KCEF 待网络条件允许后接入）。 */
private fun openBrowser(url: String) {
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

/** 程序化托盘图标（避免引入图片资源）：紫色圆点，对齐 Material 主色。 */
private val yunxTrayIcon = object : Painter() {
    override val intrinsicSize = Size(24f, 24f)
    override fun DrawScope.onDraw() {
        drawCircle(Color(0xFF6750A4), radius = 11f, center = Offset(12f, 12f))
        drawCircle(Color.White, radius = 4f, center = Offset(12f, 12f))
    }
}

fun main(args: Array<String>) {
    // KCEF 首次下载 JCEF 运行时走 Java Http 层：设置 YUNX_PROXY=host:port 即走代理
    System.getenv("YUNX_PROXY")?.takeIf { it.contains(':') }?.let { proxy ->
        val host = proxy.substringBefore(':')
        val port = proxy.substringAfter(':')
        System.setProperty("https.proxyHost", host)
        System.setProperty("https.proxyPort", port)
        System.setProperty("http.proxyHost", host)
        System.setProperty("http.proxyPort", port)
    }
    // 无头自检：YUNX_PROXY=... gradle :desktop:run --args="--kcef-smoke"
    if ("--kcef-smoke" in args) {
        val result = kotlinx.coroutines.runBlocking { kcefSmoke() }
        println("KCEF_SMOKE_RESULT: $result")
        kotlin.system.exitProcess(if (result.startsWith("OK")) 0 else 1)
    }
    application {
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
                colorScheme = lightColorScheme(
                    primary = Color(0xFF6750A4),
                    secondary = Color(0xFF625B71),
                    surfaceVariant = Color(0xFFE7E0EC),
                    background = Color(0xFFF7F4FA)
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DesktopApp(trayText)
                }
            }
        }
    }
}

/** KCEF 无头自检：初始化 → 建浏览器 → 执行 JS，验证运行时加载与 JS 通路。 */
internal suspend fun kcefSmoke(): String = try {
    ensureKcef { phase, pct -> println("KCEF_SMOKE_PHASE: $phase ${pct?.let { "%.0f%%".format(it * 100) } ?: ""}") }
    val client = dev.datlag.kcef.KCEF.newClient()
    val browser = client.createBrowser("about:blank")
    var eval: String? = null
    repeat(10) {
        if (eval.isNullOrBlank()) {
            Thread.sleep(800)
            eval = runCatching { browser.evaluateJavaScript("21*2") }.getOrNull()
        }
    }
    browser.dispose()
    if (eval == "42") "OK: JS 通路正常（21*2=42）" else "FAIL: JS 返回异常：$eval"
} catch (e: Throwable) {
    "FAIL: ${e.message}"
}

@Composable
private fun DesktopApp(trayText: MutableState<String>) {
    val db = remember { AppDatabase.get() }
    val settings = remember { DesktopSettings() }
    val downloadManager = remember {
        DownloadManager(
            env = DesktopDownloadEnvironment(settings),
            dao = db.downloadTaskDao(),
            downloader = ChunkDownloader { HttpClients.downloadClient() },
            threadProvider = { settings.threadCount },
            saveDirProvider = { settings.downloadDir },
            concurrencyProvider = { settings.maxConcurrent },
            speedLimitProvider = { settings.speedLimit },
            retryCountProvider = { 3 },
            keepWhenLockedProvider = { false },
            showSpeedProvider = { true },
            credentialCipher = DesktopCredentialCipher()
        )
    }
    val quarkDao = remember { db.quarkAccountDao() }
    val pan123Dao = remember { db.pan123AccountDao() }
    val quarkApi = remember { QuarkApi() }
    val pan123Api = remember { Pan123Api() }
    val clipboard = LocalClipboardManager.current

    var quarkCookie by remember { mutableStateOf("") }
    var quarkStatus by remember { mutableStateOf("未登录") }
    var panToken by remember { mutableStateOf("") }
    var panStatus by remember { mutableStateOf("未登录") }

    LaunchedEffect(Unit) {
        quarkDao.getAccount()?.let {
            if (it.cookie.isNotBlank()) { quarkCookie = it.cookie; quarkStatus = "已登录（读取自本地加密库）" }
        }
        pan123Dao.getAccount()?.let {
            if (it.accessToken.isNotBlank()) { panToken = it.accessToken; panStatus = "已登录（读取自本地加密库）" }
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
    val scope = rememberCoroutineScope()
    var kcefLoginFor by remember { mutableStateOf<SharePlatform?>(null) }

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

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---------- 标题 ----------
        Column {
            Text("YunX Desktop", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "网盘分享解析与高速下载 · 开源（AGPL-3.0）· 仅供个人学习与技术交流",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---------- 登录卡 ----------
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("① 网盘登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LoginRow(
                    label = "夸克网盘",
                    status = quarkStatus,
                    onEmbeddedLogin = { kcefLoginFor = SharePlatform.QUARK },
                    loginUrl = QuarkConstants.LOGIN_URL,
                    hint = "网页登录后：F12 → 网络 → 任一请求 → 复制整段 Cookie 粘贴到此处",
                    value = quarkCookie,
                    onValueChange = { quarkCookie = it },
                    onSave = {
                        val cookie = quarkCookie.trim()
                        if (cookie.isEmpty()) { quarkStatus = "Cookie 为空"; return@LoginRow }
                        scope.launch {
                            runCatching { quarkDao.upsert(QuarkAccountEntity(cookie = cookie)) }
                                .onSuccess { quarkStatus = "已保存（AES-GCM 加密）" }
                                .onFailure { quarkStatus = "保存失败：${it.message}" }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                LoginRow(
                    label = "123 云盘",
                    status = panStatus,
                    onEmbeddedLogin = { kcefLoginFor = SharePlatform.PAN123 },
                    loginUrl = "https://yun.123pan.com/",
                    hint = "网页登录后：F12 → 应用/网络中复制 authorToken（JWT）粘贴到此处",
                    value = panToken,
                    onValueChange = { panToken = it },
                    onSave = {
                        val token = panToken.trim()
                        if (token.isEmpty()) { panStatus = "Token 为空"; return@LoginRow }
                        scope.launch {
                            runCatching { pan123Dao.upsert(Pan123AccountEntity(accessToken = token)) }
                                .onSuccess { panStatus = "已保存（AES-GCM 加密）" }
                                .onFailure { panStatus = "保存失败：${it.message}" }
                        }
                    }
                )
            }
        }

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
                                SharePlatform.PAN123 -> Pan123ResolveRepository(pan123Api) { panToken.trim().ifBlank { null } }
                                else -> { message = "桌面版当前仅支持夸克 / 123 云盘链接"; return@Button }
                            }
                            val useCookie = when (parsed.platform) {
                                SharePlatform.QUARK -> quarkCookie.trim()
                                SharePlatform.PAN123 -> panToken.trim()
                                else -> ""
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
                                            val platformConst =
                                                if (platform == SharePlatform.PAN123) DownloadPlatform.PAN123 else DownloadPlatform.QUARK
                                            val headers = if (platform == SharePlatform.PAN123) {
                                                mapOf(
                                                    "User-Agent" to Pan123Constants.WEB_UA,
                                                    "Referer" to Pan123Constants.DOWNLOAD_REFERER
                                                )
                                            } else {
                                                mapOf(
                                                    "Cookie" to sessionCookie,
                                                    "User-Agent" to QuarkConstants.API_USER_AGENT,
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

        // ---------- 下载卡 ----------
        Card(Modifier.fillMaxWidth().height(250.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                DownloadsSection(db, downloadManager, settings)
            }
        }
    }

    // KCEF 内嵌登录窗（Phase 4 选定方案）
    kcefLoginFor?.let { platform ->
        KcefLoginWindow(
            platform = platform,
            onClose = { kcefLoginFor = null },
            onCaptured = { credential ->
                when (platform) {
                    SharePlatform.PAN123 -> {
                        panToken = credential
                        scope.launch {
                            runCatching { pan123Dao.upsert(Pan123AccountEntity(accessToken = credential)) }
                                .onSuccess { panStatus = "已保存（AES-GCM 加密）" }
                                .onFailure { panStatus = "保存失败：${it.message}" }
                        }
                    }
                    else -> {
                        quarkCookie = credential
                        scope.launch {
                            runCatching { quarkDao.upsert(QuarkAccountEntity(cookie = credential)) }
                                .onSuccess { quarkStatus = "已保存（AES-GCM 加密）" }
                                .onFailure { quarkStatus = "保存失败：${it.message}" }
                        }
                    }
                }
                kcefLoginFor = null
            }
        )
    }
}

/** 登录行：平台名 + 打开登录页 + 凭证粘贴 + 保存。 */
@Composable
private fun LoginRow(
    label: String,
    status: String,
    onEmbeddedLogin: () -> Unit,
    loginUrl: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            TextButton(onClick = { openBrowser(loginUrl) }) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("打开登录页")
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSave) { Text("保存粘贴的凭证") }
            TextButton(onClick = onEmbeddedLogin) { Text("内嵌窗口登录（推荐）") }
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
