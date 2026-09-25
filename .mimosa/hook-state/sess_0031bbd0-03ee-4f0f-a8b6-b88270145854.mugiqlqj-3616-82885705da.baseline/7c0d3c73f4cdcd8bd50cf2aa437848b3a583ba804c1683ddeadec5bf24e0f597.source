package com.yunx.app.data.download

import java.io.File

/**
 * 下载引擎的平台环境接缝（Agent.md §10.3）。
 * 覆盖五类平台能力：分片缓存目录 / 临时目录 / 锁屏保活（WakeLock）/ 前台下载状态通知 / 删除已保存文件。
 * 行为语义与 Android 版严格一致：
 * - chunkCacheBase：§5.4 分片缓存根（Android externalCacheDir ?: cacheDir，系统可自动清理）；
 * - tempCacheDir：合并/HLS 大临时文件目录（Android 内部缓存 data 分区，大文件 IO 快于 FUSE）；
 * - 通知三钩子：桌面端首版为空实现（Phase 3 接系统托盘）。
 */
interface DownloadEnvironment {
    /** 分片临时文件缓存根目录。 */
    fun chunkCacheBase(): File

    /** HLS 拉取与合并产物目录（下载完成即删，不占长期空间）。 */
    fun tempCacheDir(): File

    /** 批量下载开始时调用（Android: PARTIAL_WAKE_LOCK "yunx:download"，引用计数关闭）；桌面端为空实现。 */
    fun acquireWakeLock(tag: String)

    /** 任务计数归零时调用。 */
    fun releaseWakeLock()

    /** 有任务进入下载态（Android: 启动/保活前台服务）；桌面端为空实现。 */
    fun onTaskFlowStarted(title: String)

    /** 任务计数归零（Android: 停止前台服务）；桌面端为空实现。 */
    fun onTaskFlowStopped()

    /** 下载进度通知（调用侧已按 2 秒节流）；桌面端为空实现。 */
    fun updateProgressNotification(title: String, percent: Int, speedText: String, showSpeed: Boolean)

    /** 删除已保存文件（savePath 可能为普通路径或 Android MediaStore/SAF uri 字符串）；失败返回 false 不抛异常。 */
    fun deleteLocalFile(savePath: String): Boolean

    /** 保存下载完成的文件，返回保存标识（Android: MediaStore/SAF uri 或绝对路径）；失败返回 null。
     *  Android 端内部含 DownloadPathPolicy 重名防撞与存储权限分支。 */
    fun saveDownloadFile(fileName: String, source: File, targetDirUri: String?): String?
}
