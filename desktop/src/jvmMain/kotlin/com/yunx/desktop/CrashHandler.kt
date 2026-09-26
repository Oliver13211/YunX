/*
 * YunX Desktop - 跨平台桌面版（KMP + Compose Multiplatform）
 * Copyright (C) 2026 Oliver13211
 * AGPL-3.0 licensed. 派生自 YunX（云析）Android 项目（CYQawa 著）。
 */

package com.yunx.desktop

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * 全局崩溃处理器：未捕获异常（含 EDT）→ 写入 ~/.yunx/logs/crash-<时间>.txt，
 * 弹错误对话框（无头/弹窗失败时静默），随后退出进程。
 *
 * 日志只保留最近 KEEP 份；内容为堆栈与运行环境，不包含账号凭证（Agent.md §8）。
 */
object CrashHandler {

    private const val KEEP = 10
    private val handling = AtomicBoolean(false)

    private val logsDir: File
        get() = File(System.getProperty("user.home"), ".yunx").resolve("logs")

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handle(thread, throwable)
        }
    }

    private fun handle(thread: Thread, throwable: Throwable) {
        // 处理本身再抛异常不能形成递归（对话框异常、日志 IO 异常等）
        if (!handling.compareAndSet(false, true)) return
        try {
            val file = writeLog(thread, throwable)
            showError(file, throwable)
        } finally {
            kotlin.system.exitProcess(1)
        }
    }

    private fun writeLog(thread: Thread, throwable: Throwable): File? = try {
        logsDir.mkdirs()
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = logsDir.resolve("crash-$stamp.txt")
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        file.writeText(
            buildString {
                appendLine("YunX Desktop 崩溃报告")
                appendLine("时间：").append(LocalDateTime.now()).appendLine()
                appendLine("版本：").append(AppInfo.VERSION).appendLine()
                appendLine("线程：").append(thread.name).appendLine()
                appendLine("系统：").append(System.getProperty("os.name")).append(' ')
                    .append(System.getProperty("os.version")).append(' ')
                    .append(System.getProperty("os.arch")).appendLine()
                appendLine("Java：").append(System.getProperty("java.version")).append(" (")
                    .append(System.getProperty("java.vendor")).append(')').appendLine()
                appendLine()
                append(stack)
                throwable.suppressed.forEach { suppressed ->
                    appendLine()
                    appendLine("Suppressed:")
                    append(StringWriter().also { suppressed.printStackTrace(PrintWriter(it)) }.toString())
                }
            }
        )
        pruneOld()
        file
    } catch (_: Throwable) {
        null
    }

    private fun pruneOld() {
        logsDir.listFiles { f -> f.isFile && f.name.startsWith("crash-") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { runCatching { it.delete() } }
    }

    private fun showError(file: File?, throwable: Throwable) {
        val message = buildString {
            append("程序遇到未处理的错误，即将退出。\n\n")
            file?.let { append("崩溃日志：").append(it.absolutePath).append("\n\n") }
            append(throwable.javaClass.name)
            throwable.message?.takeIf { it.isNotBlank() }?.let { append("：").append(it.lineSequence().firstOrNull() ?: "") }
        }
        val show = {
            try {
                if (!java.awt.GraphicsEnvironment.isHeadless()) {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
                    JOptionPane.showMessageDialog(null, message, "${AppInfo.APP_NAME} 遇到错误", JOptionPane.ERROR_MESSAGE)
                }
            } catch (_: Throwable) {
                // 对话框失败不阻塞退出
            }
        }
        try {
            if (SwingUtilities.isEventDispatchThread()) show() else SwingUtilities.invokeAndWait(show)
        } catch (_: Throwable) {
            // EDT 已死等极端场景：直接退出
        }
    }
}
