package com.juca.hydroponic.data

import org.json.JSONObject
import java.time.Clock
import java.time.Instant

data class OwnerSession(val origin: String, val accessToken: String, val expiresAt: String) {
    fun valid(clock: Clock): Boolean = accessToken.matches(Regex("[A-Za-z0-9_-]{43}")) &&
        runCatching { Instant.parse(expiresAt).isAfter(clock.instant()) }.getOrDefault(false)
    override fun toString() = "OwnerSession[redacted]"
}
interface OwnerSessionStorage {
    fun read(origin: String): OwnerSession?
    fun save(session: OwnerSession)
    fun clear(origin: String)
}

class OwnerSessionClient(
    private val storage: OwnerSessionStorage,
    private val remote: (String, String?) -> Remote,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun login(origin: String, username: String, password: String) {
        require(username.matches(Regex("[A-Za-z0-9_-]{1,64}")) && password.isNotEmpty() && password.toByteArray(Charsets.UTF_8).size <= 72) {
            "계정 이름과 비밀번호를 확인하세요."
        }
        val body = remote(origin, null).request("POST", "/api/auth/login",
            JSONObject().put("username", username).put("password", password).toString())
        val session = try {
            val response = JSONObject(body)
            check(response.getString("tokenType") == "Bearer")
            OwnerSession(origin, response.getString("accessToken"), response.getString("expiresAt")).also { check(it.valid(clock)) }
        } catch (_: Exception) {
            // JSON parser diagnostics may contain the response, which contains a credential.
            throw java.io.IOException("서버 인증 응답이 유효하지 않습니다. 기기 시각도 확인하세요.")
        }
        storage.save(session)
    }
    suspend fun logout(origin: String) {
        val current = storage.read(origin)
        try {
            if (current != null) remote(origin, current.accessToken).request("POST", "/api/auth/logout")
        } catch (e: ApiException) {
            if (e.status != 401) throw e
        } finally {
            storage.clear(origin)
        }
    }
}
