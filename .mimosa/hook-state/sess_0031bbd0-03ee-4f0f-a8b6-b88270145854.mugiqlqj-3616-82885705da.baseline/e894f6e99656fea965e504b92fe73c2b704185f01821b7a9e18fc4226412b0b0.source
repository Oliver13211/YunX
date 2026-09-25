package com.yunx.app.data.db

import android.content.Context
import androidx.room.Room
import com.yunx.app.data.security.AndroidKeystoreCredentialCipher

/**
 * Android 端数据库工厂：沿用原 `AppDatabase.get(context)` 调用形态（companion 扩展），
 * 构建参数与迁移策略与 KMP 化前逐字一致。
 */
fun AppDatabase.Companion.get(context: Context): AppDatabase =
    instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "yunx.db"
        )
            .addMigrations(*AppDatabase.MIGRATIONS)
            // 早期开发版（1-8）无可靠 schema；从 v9 起必须保留用户凭证与下载任务
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, *AppDatabase.LEGACY_DESTRUCTIVE_VERSIONS)
            .build()
            .also { database ->
                database.credentialCipher = AndroidKeystoreCredentialCipher()
                instance = database
            }
    }

@Volatile
private var instance: AppDatabase? = null
