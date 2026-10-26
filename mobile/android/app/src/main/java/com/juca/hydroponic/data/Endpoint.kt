package com.juca.hydroponic.data

import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object Endpoint {
    fun origin(raw: String, debug: Boolean): String {
        val uri = URI(raw.trim())
        val scheme = uri.scheme?.lowercase()
        val debugLoopback = debug && scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2")
        require(scheme == "https" || debugLoopback) { "HTTPS 주소를 입력하세요. 디버그 HTTP는 루프백만 허용합니다." }
        require(uri.host != null && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath in listOf("", "/") && (uri.port == -1 || uri.port in 1..65535)) { "서버 주소에는 경로·계정·쿼리를 넣지 마세요." }
        return URI(scheme, null, uri.host, uri.port, null, null, null).toASCIIString()
    }
    fun device(raw: String): String {
        require(raw.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))) { "등록된 장치 ID를 입력하세요." }
        return raw
    }
}
object Dates {
    val inputFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)
    fun toInstant(input: String, zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDateTime.parse(input, inputFormat).atZone(zone).toInstant().toString()
    fun nowInput(): String = LocalDateTime.now().format(inputFormat)
}
