package com.yunx.app.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.yunx.app.data.security.CredentialCipher
import com.yunx.app.data.security.DesktopCredentialCipher
import java.io.File

/**
 * 桌面端数据库工厂：库文件位于 ~/.yunx/yunx.db，使用 Room KMP 内置 SQLite 驱动
 * （纯 Kotlin，免 JNI/JDBC），迁移策略与 Android 端共用同一份 MIGRATIONS。
 */
internal fun AppDatabase.Companion.get(cipher: CredentialCipher = DesktopCredentialCipher()): AppDatabase =
    instance ?: synchronized(this) {
        instance ?: run {
            val dbDir = File(System.getProperty("user.home"), ".yunx").apply { mkdirs() }
            val dbFile = File(dbDir, "yunx.db")
            Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .addMigrations(*AppDatabase.MIGRATIONS)
                // 与 Android 端同策略：v1-8 允许破坏性重建（桌面首装为全新库，通常不触发）
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, *AppDatabase.LEGACY_DESTRUCTIVE_VERSIONS)
                .build()
                .also { database ->
                    database.credentialCipher = cipher
                    instance = database
                }
        }
    }

@Volatile
private var instance: AppDatabase? = null
