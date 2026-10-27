package com.juca.hydroponic.data

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import javax.crypto.KeyGenerator

class OwnerSessionTest {
    private val origin = "https://example.com"
    private val clock = Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC)
    private val session = OwnerSession(origin, "a".repeat(43), "2026-10-27T08:00:00Z")
    private class Storage : OwnerSessionStorage {
        var value: OwnerSession? = null
        override fun read(origin: String) = value?.takeIf { it.origin == origin }
        override fun save(session: OwnerSession) { value = session }
        override fun clear(origin: String) { if (value?.origin == origin) value = null }
    }
    @Test fun encryptsWithFreshIvAndRejectsTamperingWrongKeyAndOtherOrigin() {
        fun cipher() = SessionCipher(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
        val codec = cipher(); val first = codec.encrypt(session); val second = codec.encrypt(session)
        assertNotEquals(first, second); assertFalse(first.contains(session.accessToken))
        assertEquals(session, codec.decrypt(origin, first))
        assertThrows(Exception::class.java) { codec.decrypt("https://other.example", first) }
        assertThrows(Exception::class.java) { cipher().decrypt(origin, first) }
        val parts = first.split('.'); val bytes = java.util.Base64.getDecoder().decode(parts[1]); bytes[0] = (bytes[0].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { codec.decrypt(origin, parts[0] + "." + java.util.Base64.getEncoder().encodeToString(bytes)) }
    }
    @Test fun expirationIsExclusiveAndDiagnosticsNeverContainTheToken() {
        assertTrue(session.valid(clock))
        assertFalse(session.valid(Clock.fixed(Instant.parse(session.expiresAt), ZoneOffset.UTC)))
        assertFalse(session.copy(accessToken = "bad").valid(clock))
        assertFalse(session.toString().contains(session.accessToken))
    }
    @Test fun loginHasNoAuthorizationAndPersistsOnlyValidatedOriginBoundSession() = runTest {
        val storage = Storage()
        val client = OwnerSessionClient(storage, { server, token ->
            assertEquals(origin, server); assertNull(token)
            object : Remote {
                override suspend fun request(method: String, path: String, body: String?, key: String?): String {
                    assertEquals("POST", method); assertEquals("/api/auth/login", path)
                    assertEquals("fixture-only", JSONObject(body!!).getString("password"))
                    return JSONObject().put("accessToken", session.accessToken).put("expiresAt", session.expiresAt).put("tokenType", "Bearer").toString()
                }
            }
        }, clock)
        client.login(origin, "owner", "fixture-only")
        assertEquals(session, storage.read(origin)); assertNull(storage.read("https://other.example"))
    }
    @Test fun logoutAlwaysClearsLocalTokenButNeverTouchesBusinessPendingRequest() = runTest {
        val storage = Storage().apply { value = session }
        var pendingRequest: PendingMutation? = null
        val mutations = object : MutationStorage { override var pending: PendingMutation?
            get() = pendingRequest
            set(value) { pendingRequest = value }
        }
        val unavailable = object : Remote {
            override suspend fun request(method: String, path: String, body: String?, key: String?): String = throw ApiException(401, "AUTH_REQUIRED")
        }
        val runner = MutationRunner(mutations) { unavailable }
        try { runner.execute(origin, "/api/cultivations", "same-body", "CREATE", null) } catch (_: ApiException) { }
        val retained = checkNotNull(pendingRequest)
        val client = OwnerSessionClient(storage, { _, _ -> unavailable }, clock)
        client.logout(origin)
        assertNull(storage.read(origin)); assertEquals(retained, pendingRequest)
        var retriedKey: String? = null
        val retryRunner = MutationRunner(mutations) { object : Remote {
            override suspend fun request(method: String, path: String, body: String?, key: String?): String {
                retriedKey = key; assertEquals(retained.body, body); return "{}"
            }
        } }
        retryRunner.retry(origin); assertEquals(retained.key, retriedKey); assertNull(pendingRequest)
    }
    @Test fun failedLoginDoesNotPersistCredentialsOrModifyExistingSession() = runTest {
        val storage = Storage()
        val client = OwnerSessionClient(storage, { _, _ -> object : Remote {
            override suspend fun request(method: String, path: String, body: String?, key: String?): String = throw ApiException(401, "AUTH_INVALID_CREDENTIALS")
        } }, clock)
        try { client.login(origin, "owner", "fixture-only"); fail() } catch (_: ApiException) { }
        assertNull(storage.value)
    }
    @Test fun malformedAuthenticationResponsesNeverBecomeErrorMessagesContainingTokens() = runTest {
        val storage = Storage()
        val client = OwnerSessionClient(storage, { _, _ -> object : Remote {
            override suspend fun request(method: String, path: String, body: String?, key: String?) = "{broken:" + session.accessToken
        } }, clock)
        try { client.login(origin, "owner", "fixture-only"); fail() }
        catch (e: java.io.IOException) { assertFalse(e.message.orEmpty().contains(session.accessToken)) }
        assertNull(storage.value)
    }
}
