/*
 * YunX Desktop - 跨平台桌面版（KMP + Compose Multiplatform）
 * Copyright (C) 2026 Oliver13211
 * AGPL-3.0 licensed. 派生自 YunX（云析）Android 项目（CYQawa 著）。
 */

package com.yunx.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.repository.XunleiAccountRepository
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

/**
 * 迅雷账号登录独立窗口：账号密码 →（触发风控时）短信验证码。
 * 流程严格对齐原版 XunleiAccountViewModel.login：
 * - 风控响应 reviewUrl 自带 creditkey 时直接进入短信输入；
 * - 否则由用户点「发送验证码」（creditkey 是 sendSms 的返回物，不能当前置条件）；
 * - reviewUrl 兜底：浏览器打开官方验证页，验证后重试密码登录。
 */
@Composable
fun XunleiLoginWindow(
    repo: XunleiAccountRepository,
    onClose: () -> Unit,
    onStatus: (String) -> Unit
) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var sms by remember { mutableStateOf("") }
    var smsCreditKey by remember { mutableStateOf("") }
    var smsToken by remember { mutableStateOf("") }
    var needSms by remember { mutableStateOf(false) }
    var reviewUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("输入账号密码后点击登录；触发风控时按提示完成短信验证") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Window(
        onCloseRequest = onClose,
        title = "迅雷账号登录 · YunX Desktop",
        state = rememberWindowState(width = 560.dp, height = 560.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("迅雷网盘账号登录", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("手机号 / 邮箱") },
                singleLine = true
            )
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(enabled = !busy && user.isNotBlank() && pass.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        runCatching {
                            val step = repo.loginWithPassword(user.trim(), pass)
                            when {
                                step.needSms -> {
                                    val reviewMap = XunleiApi.parseReviewUrl(step.reviewUrl)
                                    val creditKey = reviewMap["creditkey"].orEmpty()
                                    if (creditKey.isNotBlank()) {
                                        smsCreditKey = creditKey
                                        smsToken = reviewMap["token"].orEmpty()
                                    }
                                    needSms = true
                                    reviewUrl = step.reviewUrl
                                    status = "触发安全验证：请点「发送验证码」获取短信后输入"
                                }
                                step.sessionKey.isNotBlank() && step.sessionId.isNotBlank() &&
                                    repo.finishLogin(step, user.trim()) -> {
                                    status = "登录成功 · ${step.nickname.ifBlank { "迅雷用户" }}"
                                    onStatus("已登录 · ${step.nickname.ifBlank { "迅雷用户" }}")
                                }
                                else -> status = step.message.ifBlank { "登录失败，请检查账号密码" }
                            }
                        }.onFailure { status = "登录失败：${it.message}" }
                        busy = false
                    }
                }) { Text(if (busy) "登录中…" else "登录") }
                OutlinedButton(enabled = !busy && needSms && user.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        runCatching {
                            val smsStep = repo.sendSms(user.trim())
                            if (smsStep.smsCreditKey.isNotBlank()) {
                                smsCreditKey = smsStep.smsCreditKey
                                smsToken = smsStep.smsToken
                                status = "验证码已发送，请查收短信"
                            } else status = smsStep.message.ifBlank { "短信发送失败，请重试或检查网络" }
                        }.onFailure { status = "发送失败：${it.message}" }
                        busy = false
                    }
                }) { Text("发送验证码") }
                if (reviewUrl.isNotBlank()) {
                    TextButton(onClick = { runCatching { Desktop.getDesktop().browse(URI(reviewUrl)) } }) {
                        Text("打开验证页")
                    }
                }
            }
            if (needSms) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = sms,
                        onValueChange = { sms = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("短信验证码") },
                        singleLine = true
                    )
                    Button(enabled = !busy && sms.isNotBlank() && smsCreditKey.isNotBlank(), onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                if (repo.loginWithSms(user.trim(), sms.trim(), smsCreditKey, smsToken)) {
                                    status = "登录成功"
                                    needSms = false
                                    reviewUrl = ""
                                    onStatus("已登录")
                                } else status = "验证码校验失败"
                            }.onFailure { status = "登录失败：${it.message}" }
                            busy = false
                        }
                    }) { Text("提交验证码") }
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(1.dp))
            TextButton(onClick = onClose) { Text("关闭窗口") }
        }
    }
}
