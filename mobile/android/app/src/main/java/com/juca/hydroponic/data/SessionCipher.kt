package com.juca.hydroponic.data

import org.json.JSONObject
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// The origin is authenticated data: ciphertext cannot be transplanted to another server.
class SessionCipher(private val key: SecretKey) {
    fun encrypt(session: OwnerSession): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(session.origin.toByteArray(Charsets.UTF_8))
        val json = JSONObject().put("origin", session.origin).put("token", session.accessToken).put("expiry", session.expiresAt).toString()
        val bytes = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv) + "." + Base64.getEncoder().encodeToString(bytes)
    }
    fun decrypt(origin: String, encoded: String): OwnerSession {
        val parts = encoded.split('.')
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])))
        cipher.updateAAD(origin.toByteArray(Charsets.UTF_8))
        val json = JSONObject(String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), Charsets.UTF_8))
        require(json.getString("origin") == origin)
        return OwnerSession(origin, json.getString("token"), json.getString("expiry"))
    }
}
