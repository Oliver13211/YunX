package com.yunx.app.platform

import java.util.prefs.Preferences

actual fun defaultKeyValueStore(): KeyValueStore {
    val node = Preferences.userNodeForPackage(KeyValueStore::class.java)
    return object : KeyValueStore {
        override fun getString(key: String): String? = node.get(key, null)
        override fun putString(key: String, value: String) = node.put(key, value)
        override fun getLong(key: String): Long? =
            if (node.get(key, null) != null) node.getLong(key, Long.MIN_VALUE) else null
        override fun putLong(key: String, value: Long) = node.putLong(key, value)
        override fun contains(key: String): Boolean = node.get(key, null) != null
        override fun remove(key: String) = node.remove(key)
    }
}
