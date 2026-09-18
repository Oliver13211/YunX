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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.db.AppDatabase
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.download.DownloadManager
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File

/**
 * 下载管理区（Phase 3）：任务列表 + 暂停/继续/删除/打开。
 * 进度展示走内存 stats 高频流（Agent.md §5.5：DB 低频落盘，UI 高频内存态）。
 */

private fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1L shl 20 -> "%.1f MB/s".format(bytesPerSec.toDouble() / (1L shl 20))
    bytesPerSec >= 1L shl 10 -> "%.0f KB/s".format(bytesPerSec.toDouble() / (1L shl 10))
    bytesPerSec > 0 -> "$bytesPerSec B/s"
    else -> ""
}

private fun openInSystem(path: String, openParent: Boolean = false) {
    runCatching {
        val file = File(path)
        val target = if (openParent) file.parentFile else file
        if (target != null && target.exists()) Desktop.getDesktop().open(target)
    }
}

@Composable
fun DownloadsSection(db: AppDatabase, manager: DownloadManager, settings: DesktopSettings) {
    val tasks by db.downloadTaskDao().observeAll().collectAsState(initial = emptyList())
    val stats by manager.stats.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("下载管理", style = MaterialTheme.typography.titleMedium)
            Text(
                "保存目录：${settings.downloadDir.ifBlank { "~/Downloads（默认）" }}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            OutlinedButton(onClick = {
                // 模态目录选择器；CMP 暂无跨平台 Picker，用 Swing 标准件（EDT 上阻塞至选择完成）
                val chooser = javax.swing.JFileChooser(settings.downloadDir.ifBlank { null })
                    .apply { fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY; dialogTitle = "选择下载保存目录" }
                if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                    settings.downloadDir = chooser.selectedFile.absolutePath
                }
            }) { Text("更改目录") }
        }

        if (tasks.isEmpty()) {
            Text("暂无下载任务", style = MaterialTheme.typography.bodySmall)
        } else {
            HorizontalDivider()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(tasks, key = { it.id }) { task ->
                    val stat = stats[task.id]
                    DownloadTaskRow(
                        task = task,
                        speed = stat?.speed ?: 0L,
                        onPause = { manager.pause(task.id) },
                        onStart = { scope.launch { runCatching { manager.start(task.id) } } },
                        onRemove = { keepFile -> scope.launch { runCatching { manager.remove(task.id, !keepFile) } } }
                    )
                }
            }
        }
    }
}

/** 任务行：文件名 + 进度/状态 + 控制按钮（语义对齐 Android DownloadScreen）。 */
@Composable
private fun DownloadTaskRow(
    task: DownloadTaskEntity,
    speed: Long,
    onPause: () -> Unit,
    onStart: () -> Unit,
    onRemove: (keepFile: Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(task.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val progress = if (task.totalSize > 0) {
                "${formatSize(task.downloadedSize)} / ${formatSize(task.totalSize)}（${task.downloadedSize * 100 / task.totalSize}%）"
            } else {
                formatSize(task.downloadedSize)
            }
            val speedText = if (task.status == DownloadTaskEntity.STATUS_DOWNLOADING && speed > 0) " · ${formatSpeed(speed)}" else ""
            val errorText = if (task.status == DownloadTaskEntity.STATUS_FAILED && task.errorMsg.isNotBlank()) "：${task.errorMsg}" else ""
            Text(
                "${DownloadTaskEntity.statusText(task.status)} · $progress$speedText$errorText",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when (task.status) {
            DownloadTaskEntity.STATUS_DOWNLOADING -> TextButton(onClick = onPause) { Text("暂停") }
            DownloadTaskEntity.STATUS_PENDING, DownloadTaskEntity.STATUS_PAUSED, DownloadTaskEntity.STATUS_FAILED ->
                TextButton(onClick = onStart) { Text(if (task.status == DownloadTaskEntity.STATUS_FAILED) "重试" else "继续") }
            DownloadTaskEntity.STATUS_COMPLETED -> TextButton(onClick = { openInSystem(task.savePath) }) { Text("打开") }
        }
        if (task.status == DownloadTaskEntity.STATUS_COMPLETED) {
            TextButton(onClick = { openInSystem(task.savePath, openParent = true) }) { Text("所在目录") }
        }
        Button(onClick = { onRemove(task.status != DownloadTaskEntity.STATUS_COMPLETED) }) { Text("删除") }
    }
}

