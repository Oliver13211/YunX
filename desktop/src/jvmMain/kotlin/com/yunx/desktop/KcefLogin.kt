/*
 * YunX Desktop - 跨平台桌面版（KMP + Compose Multiplatform）
 * Copyright (C) 2026 Oliver13211
 * AGPL-3.0 licensed. 派生自 YunX（云析）Android 项目（CYQawa 著）。
 */

package com.yunx.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.yunx.app.data.network.QuarkConstants
import com.yunx.app.data.network.SharePlatform
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFBuilder
import dev.datlag.kcef.KCEFClient
import dev.datlag.kcef.KCEFCookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * KCEF 内嵌网页登录（Phase 4 选定方案）：
 * 应用窗口内嵌 Chromium（JCEF），用户完成网页登录后一键抓取凭证——
 * - 夸克：从 CefCookieManager 取该域全部 Cookie（含 HttpOnly，与 Android WebView CookieManager 语义一致）；
 * - 123 云盘：执行 JS 读 localStorage 的 authorToken（与 Android Pan123LoginScreen 注入逻辑一致）。
 *
 * 首次使用会下载 JCEF 运行时（~150MB）到 ~/.yunx/kcef；GitHub 直连受限的环境请先
 * 设置环境变量 YUNX_PROXY=host:port（见 main() 的系统属性注入）。
 */

private val kcefInstallDir: File by lazy {
    File(System.getProperty("user.home") ?: ".", ".yunx/kcef")
}

private object KcefHolder {
    @Volatile
    var client: KCEFClient? = null
}

/** 初始化（幂等）：已就绪直接复用；失败抛出由调用方展示。 */
private suspend fun ensureKcef(onPhase: (String, Float?) -> Unit): KCEFClient {
    KcefHolder.client?.let { return it }
    KCEF.init(
        builder = {
            installDir(kcefInstallDir)
            progress(object : KCEFBuilder.InitProgress {
                override fun locating() = onPhase("定位运行时", null)
                override fun downloading(progress: Float) = onPhase("下载运行时", progress)
                override fun extracting() = onPhase("解压运行时", null)
                override fun install() = onPhase("安装运行时", null)
                override fun initializing() = onPhase("初始化内核", null)
                override fun initialized() = onPhase("就绪", null)
            })
        },
        onError = { e -> throw (e ?: IllegalStateException("KCEF 初始化失败")) },
        onRestartRequired = { throw IllegalStateException("JCEF 安装后需要重启应用一次（onRestartRequired）") }
    )
    val client = KCEF.newClient()
    KcefHolder.client = client
    return client
}

private fun loginUrl(platform: SharePlatform): String = when (platform) {
    SharePlatform.PAN123 -> "https://yun.123pan.com/"
    else -> QuarkConstants.LOGIN_URL
}

/** 登录窗口状态。 */
internal sealed interface KcefLoginState {
    data object Idle : KcefLoginState
    data class Initializing(val phase: String, val percent: Float?) : KcefLoginState
    data object Ready : KcefLoginState
    data class Failed(val message: String) : KcefLoginState
}

@Composable
internal fun KcefLoginWindow(
    platform: SharePlatform,
    onClose: () -> Unit,
    onCaptured: (String) -> Unit
) {
    var state by remember { mutableStateOf<KcefLoginState>(KcefLoginState.Idle) }
    var browser by remember { mutableStateOf<KCEFBrowser?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(platform) {
        if (state is KcefLoginState.Idle) {
            state = KcefLoginState.Initializing("初始化", null)
            runCatching {
                ensureKcef { phase, pct -> state = KcefLoginState.Initializing(phase, pct) }
            }.onSuccess { client ->
                state = KcefLoginState.Ready
                browser = client.createBrowser(loginUrl(platform))
            }.onFailure {
                state = KcefLoginState.Failed(it.message ?: "初始化失败")
            }
        }
    }

    Window(
        onCloseRequest = {
            browser?.dispose()
            onClose()
        },
        title = when (platform) {
            SharePlatform.PAN123 -> "123 云盘网页登录 · YunX Desktop"
            else -> "夸克网盘网页登录 · YunX Desktop"
        },
        state = rememberWindowState(width = 1020.dp, height = 720.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val st = state) {
                    is KcefLoginState.Initializing -> {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Column {
                            Text("KCEF 内核：${st.phase}")
                            st.percent?.let {
                                LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    is KcefLoginState.Failed -> Text(
                        "初始化失败：${st.message}（首次使用需下载 JCEF 运行时，请确认 YUNX_PROXY 代理可用后重试）",
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Text("在上方页面完成登录后，点击右侧按钮自动抓取凭证", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = browser != null && !capturing,
                    onClick = {
                        val b = browser ?: return@Button
                        capturing = true
                        captureError = ""
                        scope.launch(Dispatchers.IO) {
                            val result = runCatching {
                                when (platform) {
                                    SharePlatform.PAN123 -> {
                                        val token = b.evaluateJavaScript(
                                            "window.localStorage.getItem('authorToken') || ''"
                                        ).orEmpty().trim().trim('"', '\'')
                                        token.ifBlank { throw IllegalStateException("未读取到 authorToken，请确认已在页面完成登录") }
                                    }
                                    else -> {
                                        val cookies = KCEFCookieManager().getCookiesWhileBlocking(
                                            QuarkConstants.COOKIE_DOMAIN, includeHttpOnly = true
                                        )
                                        val text = cookies.joinToString("; ") { "${it.name}=${it.value}" }
                                        text.ifBlank { throw IllegalStateException("未读取到 Cookie，请确认已在页面完成登录") }
                                    }
                                }
                            }
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                capturing = false
                                result.onSuccess(onCaptured)
                                    .onFailure { captureError = it.message ?: "抓取失败" }
                            }
                        }
                    }
                ) { Text(if (platform == SharePlatform.PAN123) "抓取 Token" else "抓取 Cookie") }
                TextButton(onClick = {
                    browser?.dispose()
                    onClose()
                }) { Text("关闭") }
            }
            if (captureError.isNotBlank()) {
                Text(captureError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            val b = browser
            if (b != null) {
                SwingPanel(
                    factory = { b.uiComponent },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                Column(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state !is KcefLoginState.Failed) {
                        CircularProgressIndicator()
                        Text("正在准备内嵌浏览器…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
