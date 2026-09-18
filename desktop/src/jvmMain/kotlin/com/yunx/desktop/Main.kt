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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yunx.app.data.db.AppDatabase
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.db.QuarkAccountEntity
import com.yunx.app.data.db.get
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.QuarkApi
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import com.yunx.app.data.repository.Pan123ResolveRepository
import com.yunx.app.data.repository.QuarkResolveRepository
import com.yunx.app.data.repository.ShareResolveRepository
import kotlinx.coroutines.launch

/**
 * YunX Desktop 桌面壳（Phase 2 里程碑）：
 * 登录区（夸克 Cookie / 123 authorToken 粘贴）→ 解析区（链接 → 文件列表 → 直链）。
 * 登录态存 Room（与 Android 同构的 SecureAccountDaos 加密 DAO），Phase 4 起接入 KCEF 网页登录。
 */

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.2f MB".format(bytes.toDouble() / (1L shl 20))
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes.toDouble() / (1L shl 10))
    else -> "$bytes B"
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "YunX Desktop（开源版 · AGPL-3.0）",
        state = rememberWindowState()
    ) {
        MaterialTheme {
            DesktopApp()
        }
    }
}

@Composable
private fun DesktopApp() {
    val db = remember { AppDatabase.get() }
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
        quarkDao.getAccount()?.let { if (it.cookie.isNotBlank()) { quarkCookie = it.cookie; quarkStatus = "已登录（读取自本地库）" } }
        pan123Dao.getAccount()?.let { if (it.accessToken.isNotBlank()) { panToken = it.accessToken; panStatus = "已登录（读取自本地库）" } }
    }

    var shareText by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("粘贴分享链接开始解析（当前支持夸克 / 123 云盘）") }
    var files by remember { mutableStateOf<List<ShareFile>>(emptyList()) }
    var session by remember { mutableStateOf<ShareSession?>(null) }
    var sessionRepo by remember { mutableStateOf<ShareResolveRepository?>(null) }
    var sessionCookie by remember { mutableStateOf("") }
    var directLink by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("YunX Desktop", style = MaterialTheme.typography.titleLarge)
        Text(
            "个人学习与技术交流用途，请遵守 AGPL-3.0 与上游免责声明；不支持离线使用以外的任何商业用途。",
            style = MaterialTheme.typography.bodySmall
        )

        // ---------- 登录区 ----------
        Text("网盘登录", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = quarkCookie,
            onValueChange = { quarkCookie = it },
            label = { Text("夸克 Cookie（浏览器登录后整段粘贴）") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val cookie = quarkCookie.trim()
                if (cookie.isEmpty()) { quarkStatus = "Cookie 为空"; return@Button }
                scope.launch {
                    runCatching { quarkDao.upsert(QuarkAccountEntity(cookie = cookie)) }
                        .onSuccess { quarkStatus = "夸克 Cookie 已加密保存" }
                        .onFailure { quarkStatus = "保存失败：${it.message}" }
                }
            }) { Text("保存夸克 Cookie") }
            Text(quarkStatus, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = panToken,
            onValueChange = { panToken = it },
            label = { Text("123 云盘 authorToken / JWT（网页登录后粘贴）") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val token = panToken.trim()
                if (token.isEmpty()) { panStatus = "Token 为空"; return@Button }
                scope.launch {
                    runCatching { pan123Dao.upsert(Pan123AccountEntity(accessToken = token)) }
                        .onSuccess { panStatus = "123 Token 已加密保存" }
                        .onFailure { panStatus = "保存失败：${it.message}" }
                }
            }) { Text("保存 123 Token") }
            Text(panStatus, style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider()

        // ---------- 解析区 ----------
        Text("分享解析", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = shareText,
            onValueChange = { shareText = it },
            label = { Text("粘贴分享链接（可含提取码）") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    scope.launch {
                        val result = runCatching {
                            repo.createSession(parsed.shareId, parsed.pwd, useCookie).getOrThrow()
                        }
                        result.onSuccess { s ->
                            session = s
                            sessionRepo = repo
                            sessionCookie = useCookie
                            repo.listFiles(s, "0", useCookie)
                                .onSuccess { message = "「${s.title}」共 ${it.size} 项（根目录）"; files = it }
                                .onFailure { message = "列取文件失败：${it.message}" }
                        }.onFailure {
                            message = "解析失败：${it.message}"
                        }
                        resolving = false
                    }
                }
            ) { Text(if (resolving) "解析中…" else "解析") }
            if (resolving) CircularProgressIndicator(Modifier.padding(start = 4.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        if (directLink.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("直链：", style = MaterialTheme.typography.bodySmall)
                Text(directLink, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                TextButton(onClick = { clipboard.setText(AnnotatedString(directLink)) }) { Text("复制") }
                TextButton(onClick = { directLink = "" }) { Text("关闭") }
            }
        }

        // ---------- 文件列表 ----------
        val s = session
        if (s != null) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(files) { file ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(file.fname, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (file.isdir) "目录" else formatSize(file.fsize),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(
                            enabled = !file.isdir && !resolving,
                            onClick = {
                                val repo = sessionRepo ?: return@TextButton
                                resolving = true; directLink = ""
                                scope.launch {
                                    runCatching {
                                        repo.getShareDownloadLink(s, file, sessionCookie).getOrThrow()
                                    }.onSuccess {
                                        directLink = it.downloadUrl
                                        message = "已取得直链（转存清理由关闭/删除任务时处理）"
                                    }.onFailure { message = "取直链失败：${it.message}" }
                                    resolving = false
                                }
                            }
                        ) { Text("取直链") }
                    }
                }
            }
        }
    }
}
