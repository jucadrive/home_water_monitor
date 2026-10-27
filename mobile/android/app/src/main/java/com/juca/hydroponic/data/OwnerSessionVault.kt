package com.juca.hydroponic.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.time.Clock
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class OwnerSessionVault(context: Context, private val clock: Clock = Clock.systemUTC()) : OwnerSessionStorage {
    private val prefs = context.getSharedPreferences("owner_auth", Context.MODE_PRIVATE)
    private val alias = "com.juca.hydroponic.owner-session.v1"
    private fun cipher(): SessionCipher {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
        return SessionCipher(key)
    }
    override fun read(origin: String): OwnerSession? {
        if (prefs.getString("origin", null) != origin) return null
        return try {
            val session = cipher().decrypt(origin, prefs.getString("sealed", "").orEmpty())
            if (session.valid(clock)) session else { clear(origin); null }
        } catch (_: Exception) {
            // A missing/invalid Keystore key requires login, never deletion of pending business requests.
            clear(origin); null
        }
    }
    override fun save(session: OwnerSession) {
        val sealed = cipher().encrypt(session)
        check(prefs.edit().putString("origin", session.origin).putString("sealed", sealed).commit()) { "인증 정보를 저장하지 못했습니다." }
    }
    override fun clear(origin: String) {
        if (prefs.getString("origin", null) == origin)
            check(prefs.edit().clear().commit()) { "이 기기의 인증 정보를 지우지 못했습니다." }
    }
}
