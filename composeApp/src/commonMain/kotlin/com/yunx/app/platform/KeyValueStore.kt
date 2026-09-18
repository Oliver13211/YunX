package com.yunx.app.platform

/** 轻量跨端 KV（设备指纹等少量持久化；业务设置仍走 SettingsRepository，见 §3.4）。 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getLong(key: String): Long?
    fun putLong(key: String, value: Long)
    fun contains(key: String): Boolean
    fun remove(key: String)
}

/** 各端默认存储实例（Android: SharedPreferences；桌面: java.util.prefs）。 */
expect fun defaultKeyValueStore(): KeyValueStore
