package com.juca.hydroponic.data

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class EndpointTest {
    @Test fun releaseRejectsAllCleartextAndDebugAllowsOnlyExplicitLoopbacks() {
        for (host in listOf("localhost", "127.0.0.1", "10.0.2.2")) {
            assertEquals("http://$host:8080", Endpoint.origin("http://$host:8080/", true))
            assertThrows(IllegalArgumentException::class.java) { Endpoint.origin("http://$host:8080", false) }
        }
        for (host in listOf("192.168.0.10", "example.com", "localhost.example.com", "127.0.0.2"))
            assertThrows(IllegalArgumentException::class.java) { Endpoint.origin("http://$host", true) }
        assertEquals("https://example.com:8443", Endpoint.origin(" https://example.com:8443/ ", false))
    }
    @Test fun rejectsEmbeddedCredentialsPathsQueriesAndInvalidDeviceIds() {
        for (url in listOf("https://user:pass@example.com", "https://example.com/api", "https://example.com?q=x", "https://example.com#x", "https://example.com:0"))
            assertThrows(IllegalArgumentException::class.java) { Endpoint.origin(url, true) }
        for (id in listOf("", "../device", "a/b", "a b", "a".repeat(65)))
            assertThrows(IllegalArgumentException::class.java) { Endpoint.device(id) }
        assertEquals("esp32-bench", Endpoint.device("esp32-bench"))
    }
    @Test fun datesUseDeviceZoneWithoutNormalizingInvalidDates() {
        assertEquals("2026-10-26T00:30:00Z", Dates.toInstant("2026-10-26 09:30", ZoneId.of("Asia/Seoul")))
        for (value in listOf("2026-02-30 10:00", "2026-10-26 25:00", "2026-10-26"))
            assertThrows(java.time.format.DateTimeParseException::class.java) { Dates.toInstant(value) }
    }
}
