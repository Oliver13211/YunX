package com.yunx.app.platform

import android.content.Context
import android.content.SharedPreferences

private lateinit var store: KeyValueStore

/** 由 YunXApp.onCreate 调用一次（Application Context）。
 * 文件名沿用迅雷设备指纹原有的 SharedPreferences（xunlei_device_fp），保证升级安装后指纹不变。 */
fun initAndroidKeyValueStore(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("xunlei_device_fp", Context.MODE_PRIVATE)
    store = object : KeyValueStore {
        override fun getString(key: String): String? = prefs.getString(key, null)
        override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
        override fun getLong(key: String): Long? =
            if (prefs.contains(key)) prefs.getLong(key, 0L) else null
        override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
        override fun contains(key: String): Boolean = prefs.contains(key)
        override fun remove(key: String) { prefs.edit().remove(key).apply() }
    }
}

actual fun defaultKeyValueStore(): KeyValueStore = store
