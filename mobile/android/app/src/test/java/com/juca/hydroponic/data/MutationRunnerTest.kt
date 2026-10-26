package com.juca.hydroponic.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MutationRunnerTest {
    class Memory : MutationStorage { override var pending: PendingMutation? = null }
    @Test fun lostResponseAndProcessRestartRetryExactPersistedRequest() = runTest {
        val storage = Memory()
        val calls = mutableListOf<Pair<String?, String?>>()
        var first = true
        val remote = object : Remote {
            override suspend fun request(method: String, path: String, body: String?, key: String?): String {
                assertNotNull(storage.pending)
                calls += key to body
                if (first) { first = false; throw IOException("response lost after server commit") }
                return "{\"id\":\"existing\"}"
            }
        }
        val runner = MutationRunner(storage) { remote }
        try { runner.execute("https://example.com", "/api/cultivations", "original", "CREATE", null); fail() } catch (_: IOException) { }
        val original = storage.pending!!
        val restarted = MutationRunner(storage) { remote }
        restarted.retry("https://example.com")
        assertEquals(2, calls.size); assertEquals(calls[0], calls[1]); assertEquals(original.key, calls.last().first)
        assertNull(storage.pending)
    }
    @Test fun pendingRequestCannotBeReplacedOrSentToDifferentServer() = runTest {
        val storage = Memory().apply { pending = PendingMutation("https://one.example", "/api/cultivations", "body", "key", "CREATE", null) }
        val runner = MutationRunner(storage) { error("Network must not be called") }
        try { runner.execute("https://one.example", "/api/cultivations", "changed", "CREATE", null); fail() } catch (_: IllegalStateException) { }
        try { runner.retry("https://two.example"); fail() } catch (_: IllegalStateException) { }
        assertEquals("body", storage.pending!!.body)
    }
    @Test fun definiteRejectionClearsPendingButUnavailableRemainsRetryable() = runTest {
        for (status in listOf(400, 404, 409, 408, 429, 503)) {
            val storage = Memory()
            val runner = MutationRunner(storage) { object : Remote {
                override suspend fun request(method: String, path: String, body: String?, key: String?): String = throw ApiException(status, "TEST")
            } }
            try { runner.execute("https://example.com", "/api/cultivations", "body", "CREATE", null); fail() } catch (_: ApiException) { }
            assertEquals(status in listOf(408, 429, 503), storage.pending != null)
        }
    }
    @Test fun storageFailurePreventsNetworkSubmission() = runTest {
        val storage = object : MutationStorage {
            override var pending: PendingMutation?
                get() = null
                set(value) { throw IOException("disk failure") }
        }
        val runner = MutationRunner(storage) { error("Must persist before network") }
        try { runner.execute("https://example.com", "/api/cultivations", "body", "CREATE", null); fail() } catch (_: IOException) { }
    }
}
