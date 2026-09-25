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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import javax.swing.JFileChooser

/**
 * 设置页（对齐原版 SettingsScreen 的桌面相关子集）：
 * 保存目录 / 最大同时下载任务数 / 单任务分片线程数 / 全局限速。
 * 经 DownloadManager 的 Provider 闭包注入，改即生效（Agent.md §3.5）。
 */
@Composable
fun SettingsSection(settings: DesktopSettings) {
    var speedInput by remember { mutableStateOf(
        if (settings.speedLimit > 0) "%.0f".format(settings.speedLimit / 1048576.0) else ""
    ) }

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

            // ---------- 单任务分片线程数 ----------
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("单任务分片线程数", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "${settings.threadCount} 线程",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Slider(
                    value = settings.threadCount.toFloat(),
                    onValueChange = { settings.threadCount = it.toInt().coerceIn(1, 32) },
                    valueRange = 1f..32f,
                    steps = 30
                )
                Text(
                    "迅雷分片并发受 CDN 限制固定上限 8（并发超过会被降级为整文件单流），设置更高仅对其他平台生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text("当前：${if (settings.speedLimit > 0) formatSize(settings.speedLimit) + "/s" else "不限速"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
