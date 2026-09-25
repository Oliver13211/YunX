package com.yunx.app.data.download

import android.content.Context
import android.os.PowerManager
import java.io.File

/**
 * Android 端下载环境实现：WakeLock / 前台服务通知 / DownloadSaver 删除均从原
 * DownloadManager 内联逻辑原样平移，行为不变。
 */
class AndroidDownloadEnvironment(context: Context) : DownloadEnvironment {
    private val appContext = context.applicationContext

    @Volatile
    private var wakeLock: PowerManager.WakeLock? = null

    override fun chunkCacheBase(): File = appContext.externalCacheDir ?: appContext.cacheDir

    override fun tempCacheDir(): File = appContext.cacheDir

    override fun acquireWakeLock(tag: String) {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
                .apply { setReferenceCounted(false) }
        }
        wakeLock?.let { if (!it.isHeld) it.acquire() }
    }

    override fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onTaskFlowStarted(title: String) {
        DownloadService.start(appContext, title)
    }

    override fun onTaskFlowStopped() {
        DownloadService.stop(appContext)
    }

    override fun updateProgressNotification(title: String, percent: Int, speedText: String, showSpeed: Boolean) {
        DownloadService.update(appContext, title, percent, speedText, showSpeed)
    }

    override fun deleteLocalFile(savePath: String): Boolean =
        DownloadSaver.delete(appContext, savePath)

    override fun saveDownloadFile(fileName: String, source: File, targetDirUri: String?): String? =
        DownloadSaver.save(appContext, fileName, source, targetDirUri)
}
