/*
 * YunX Desktop - 跨平台桌面版（KMP + Compose Multiplatform）
 * Copyright (C) 2026 Oliver13211
 * AGPL-3.0 licensed. 派生自 YunX（云析）Android 项目（CYQawa 著）。
 */

package com.yunx.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import javax.swing.JFileChooser

/** 平台线程设置行（key 与 DownloadPlatform 常量一致）。 */
private val PLATFORM_LABELS = listOf(
    "quark" to "夸克",
    "uc" to "UC",
    "xunlei" to "迅雷（固定 8）",
    "baidu" to "百度",
    "c139" to "139",
    "pan123" to "123"
)

/**
 * 设置页（对齐原版 SettingsScreen 的桌面子集）：
 * 分片设置（按网盘线程数）/ 保存目录 / 并发 / 限速 / 失败重试 / 主题外观 / 关于。
 * 全部经 Provider 闭包注入改即生效（Agent.md §3.5）。
 */
@Composable
fun SettingsSection(settings: DesktopSettings, darkMode: MutableState<Int> = mutableStateOf(0)) {
    var speedInput by remember {
        mutableStateOf(if (settings.speedLimit > 0) "%.0f".format(settings.speedLimit / 1048576.0) else "")
    }
    // 检查更新状态（函数级：对话框在 Card 外渲染）
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var foundUpdate by remember { mutableStateOf<UpdateChecker.Update?>(null) }
    val checkScope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("下载设置（改即生效）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            // ---------- 保存目录 ----------
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("保存目录", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        settings.downloadDir.ifBlank { "~/Downloads（默认）" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    OutlinedButton(onClick = {
                        val chooser = JFileChooser(settings.downloadDir.ifBlank { null }).apply {
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            dialogTitle = "选择下载保存目录"
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            settings.downloadDir = chooser.selectedFile.absolutePath
                        }
                    }) { Text("更改目录") }
                }
            }

            // ---------- 最大同时下载任务数 ----------
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("最大同时下载任务数", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "${settings.maxConcurrent} 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Slider(
                    value = settings.maxConcurrent.toFloat(),
                    onValueChange = { settings.maxConcurrent = it.toInt().coerceIn(1, 10) },
                    valueRange = 1f..10f,
                    steps = 8
                )
            }

            // ---------- 全局限速 ----------
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("全局限速（MB/s，留空表示不限速）", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = speedInput,
                        onValueChange = { input ->
                            speedInput = input
                            val mbps = input.trim().toDoubleOrNull()
                            settings.speedLimit = if (mbps != null && mbps > 0) (mbps * 1048576).toLong() else 0L
                        },
                        modifier = Modifier.width(160.dp),
                        singleLine = true,
                        placeholder = { Text("不限") }
                    )
                    Text(
                        "当前：${if (settings.speedLimit > 0) formatSize(settings.speedLimit) + "/s" else "不限速"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // ---------- 分片设置：按网盘线程数（对齐原版「下载线程数」按平台设置） ----------
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("分片设置（按网盘分别生效）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "单任务分片并发数；实际分片数还会被文件大小与「单片最小 1MB」策略约束，小文件实际并发可能低于设定",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PLATFORM_LABELS.forEach { (platform, label) ->
                    val isXunlei = platform == "xunlei"
                    val current = settings.threadsFor(platform)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp))
                        Slider(
                            value = current.toFloat(),
                            onValueChange = { settings.setThreads(platform, it.toInt().coerceIn(1, 32)) },
                            valueRange = 1f..32f,
                            steps = 30,
                            enabled = !isXunlei,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "$current 线程",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "迅雷并发超过 8 会被 CDN 降级为整文件单流（速度暴跌），故固定 8 不可修改；以上设置即时生效，无需重启",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------- 失败自动重试（对齐原版） ----------
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("失败自动重试", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "${settings.retryCount} 次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Slider(
                    value = settings.retryCount.toFloat(),
                    onValueChange = { settings.retryCount = it.toInt().coerceIn(0, 10) },
                    valueRange = 0f..10f,
                    steps = 9
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // ---------- 主题与外观（对齐原版深色模式三态） ----------
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("主题与外观", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "跟随系统", 1 to "亮色", 2 to "暗色").forEach { (v, label) ->
                        FilterChip(
                            selected = darkMode.value == v,
                            onClick = {
                                darkMode.value = v
                                settings.darkMode = v // 持久化（DesktopSettings 状态可观察，重启生效）
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // ---------- 关于（对齐原版关于页桌面子集）+ 检查更新（Phase 5） ----------
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("关于", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("YunX Desktop v${AppInfo.VERSION}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        checkScope.launch {
                            updateStatus = "正在检查…"
                            foundUpdate = UpdateChecker.check()
                            updateStatus = if (foundUpdate == null) "已是最新版本" else null
                        }
                    }) { Text("检查更新") }
                    updateStatus?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "基于 YunX（云析）Android 版移植，Kotlin Multiplatform + Compose Multiplatform 构建。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "开源协议：GNU AGPL-3.0（本软件完全免费开源，任何收费版本均为诈骗）",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "协议全文随安装包分发（应用包内 resources/LICENSE），亦可于仓库 LICENSE 查看。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "上游项目：YunX（CYQawa 著）· 本仓库：github.com/Oliver13211/YunX（desktop 分支）",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "支持平台：夸克 / UC / 迅雷 / 百度 / 139 / 123 云盘 · 桌面端：macOS / Windows（Linux 预留）",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "免责声明：本项目仅供个人学习与技术交流，请勿用于商业用途。下载内容版权归原作者所有，" +
                        "请在下载后 24 小时内删除。使用本项目产生的任何后果由使用者自行承担。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "提示：不建议使用百度网盘，可能导致账号被风控（与上游一致的风险警示）。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // 检查到新版本：对话框提示（仅设置页手动触发或启动自动检查时出现）
    foundUpdate?.let { update ->
        UpdateFoundDialog(update) { foundUpdate = null }
    }
}

/** 新版本提示对话框（设置页手动检查与启动自动检查共用） */
@Composable
fun UpdateFoundDialog(update: UpdateChecker.Update, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${update.version}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "当前版本 v${AppInfo.VERSION}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (update.notes.isNotBlank()) {
                    Text(update.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                openBrowser(update.downloadUrl)
            }) { Text("前往下载") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("以后再说") }
        }
    )
}
