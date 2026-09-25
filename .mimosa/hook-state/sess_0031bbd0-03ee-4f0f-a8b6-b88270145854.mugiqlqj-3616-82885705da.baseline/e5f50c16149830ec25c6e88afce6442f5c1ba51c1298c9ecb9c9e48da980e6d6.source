package com.yunx.app.data.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 桌面端凭证加密：AES-GCM 软件密钥，密钥首次生成后经 KeyValueStore 持久化（base64）。
 * 密文格式与 Android Keystore 版一致（yunx:v1:iv:ct，purpose 作 AAD），认证备份可互导。
 *
 * 安全性说明：这是「口令/系统钥匙环方案」就位前（Phase 4，Keychain/DPAPI/Secret Service）
 * 的过渡实现，密钥与数据库同机存放，防网络窃取不防本机提权。
 */
class DesktopCredentialCipher : CredentialCipher {

    private val key: SecretKey by lazy { loadOrCreateKey() }

    override fun encrypt(plaintext: String, purpose: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return listOf(
            PREFIX,
            com.yunx.app.platform.PlatformBase64.encodeToString(cipher.iv),
            com.yunx.app.platform.PlatformBase64.encodeToString(ciphertext)
        ).joinToString(":")
    }

    override fun decrypt(stored: String, purpose: String): String {
        if (!isEncrypted(stored)) return stored
        val parts = stored.split(':', limit = 4)
        require(parts.size == 4 && parts[0] == "yunx" && parts[1] == "v1") {
            "Unsupported encrypted credential format"
        }
        val iv = com.yunx.app.platform.PlatformBase64.decode(parts[2])
        val ciphertext = com.yunx.app.platform.PlatformBase64.decode(parts[3])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    override fun isEncrypted(stored: String): Boolean = stored.startsWith("$PREFIX:")

    private fun loadOrCreateKey(): SecretKey {
        val store = com.yunx.app.platform.defaultKeyValueStore()
        store.getString(KEY_STORE_KEY)?.let { saved ->
            return SecretKeySpec(com.yunx.app.platform.PlatformBase64.decode(saved), "AES")
        }
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256, SecureRandom())
        val secret = generator.generateKey()
        store.putString(KEY_STORE_KEY, com.yunx.app.platform.PlatformBase64.encodeToString(secret.encoded))
        return secret
    }

    private companion object {
        const val KEY_STORE_KEY = "credential.key.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "yunx:v1"
    }
}
