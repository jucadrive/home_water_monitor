package com.juca.hydroponic.data

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MonitorApiTest {
    @Test fun postsUtf8KeyAndBodyAndSurfacesStructuredErrorsWithoutFollowingRedirects() = runTest {
        val server = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 5000
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        val paths = java.util.concurrent.CopyOnWriteArrayList<String>()
        val body = "{\"plantName\":\"바질\"}"
        val served = worker.submit {
            repeat(4) {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = java.io.DataInputStream(socket.getInputStream().buffered())
                    fun line(): String {
                        val chars = StringBuilder()
                        while (true) {
                            val value = input.read()
                            check(value >= 0)
                            if (value == 10) return chars.toString().trimEnd('\r')
                            chars.append(value.toChar())
                        }
                    }
                    val request = line().split(" ")
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val header = line()
                        if (header.isEmpty()) break
                        headers[header.substringBefore(":").lowercase()] = header.substringAfter(":").trim()
                    }
                    val data = ByteArray(headers["content-length"]?.toInt() ?: 0)
                    input.readFully(data)
                    paths += request[1]
                    val (status, response) = when (request[1]) {
                        "/api/create" -> {
                            assertEquals("POST", request[0]); assertEquals("same-key", headers["idempotency-key"])
                            assertEquals("Bearer " + "a".repeat(43), headers["authorization"])
                            assertEquals(body, data.toString(Charsets.UTF_8)); 201 to body
                        }
                        "/api/conflict" -> 409 to "{\"code\":\"IDEMPOTENCY_CONFLICT\"}"
                        "/api/redirect" -> 302 to ""
                        "/api/auth/login" -> { assertNull(headers["authorization"]); 200 to "{}" }
                        else -> error("Redirect must not be followed")
                    }
                    val bytes = response.toByteArray(Charsets.UTF_8)
                    val head = "HTTP/1.1 $status Test\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nLocation: /api/never\r\n\r\n"
                    socket.getOutputStream().use { out -> out.write(head.toByteArray()); out.write(bytes); out.flush() }
                }
            }
        }
        try {
            val api = MonitorApi("http://127.0.0.1:${server.localPort}", true, "a".repeat(43))
            assertEquals("바질", JSONObject(api.request("POST", "/api/create", body, "same-key")).getString("plantName"))
            try { api.request("GET", "/api/conflict"); fail() } catch (e: ApiException) { assertEquals(409, e.status); assertEquals("IDEMPOTENCY_CONFLICT", e.code) }
            try { api.request("GET", "/api/redirect"); fail() } catch (e: ApiException) { assertEquals(302, e.status) }
            api.request("POST", "/api/auth/login", "{}")
            served.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertFalse(paths.contains("/api/never"))
        } finally { server.close(); worker.shutdownNow() }
    }
    @Test fun incompleteAndUnknownSourcesStayNullAndExplicit() {
        val json = JSONObject("""{"sessionId":"s","deviceId":"d","cultivationId":null,"displayAt":"2026-10-26T00:00:00Z","timeBasis":"INGESTED","preservationResult":"INCOMPLETE","preservedSampleCount":0,"statsSampleCount":0,"medianTdsPpm":null,"temperatureAssumedC":25,"calibrationVersion":"unknown","source":"UNKNOWN"}""")
        val session = SessionSummary.parse(json)
        assertNull(session.medianTdsPpm); assertNull(session.cultivationId)
        assertEquals("UNKNOWN", session.source); assertEquals("INGESTED", session.timeBasis)
        assertEquals(0, session.statsSampleCount)
        val detail = SessionDetail.parse(JSONObject().put("session", json).put("measurements", org.json.JSONArray()
            .put(JSONObject().put("seq", 1).put("elapsedMs", 1000).put("tdsPpm", 123.45).put("isWarmup", true).put("rawVoltageMv", JSONObject.NULL))))
        assertEquals(java.math.BigDecimal("123.45"), detail.measurements.single().tdsPpm)
        assertTrue(detail.measurements.single().isWarmup)
        assertNull(detail.measurements.single().rawVoltageMv)
    }
}
