package com.yunx.desktop

import com.yunx.app.data.db.AppDatabase
import com.yunx.app.data.db.BookmarkEntity
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.db.get
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面端 Room KMP 端到端冒烟：验证 BundledSQLiteDriver 建库、迁移策略装配、
 * 加密 DAO（DesktopCredentialCipher）读写全链路。库文件落在 ~/.yunx/yunx.db。
 */
class DatabaseSmokeTest {
    @Test
    fun bundledDriverOpensAndWrites() = runBlocking {
        val db = AppDatabase.get()
        // 普通表：建库与读写
        val bookmark = BookmarkEntity(
            link = "https://pan.quark.cn/s/test-smoke",
            title = "smoke",
            platform = "quark",
            pwd = "",
            category = "测试",
            createTime = System.currentTimeMillis()
        )
        db.bookmarkDao().insert(bookmark)
        assertTrue(db.bookmarkDao().observeAll().first().any { it.link == bookmark.link })

        // 凭证表：验证软件密钥加密写入后可解密读回
        val dao = db.pan123AccountDao()
        dao.upsert(Pan123AccountEntity(accessToken = "smoke-jwt-token"))
        assertEquals("smoke-jwt-token", dao.getAccount()?.accessToken)
    }
}
