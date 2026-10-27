package com.juca.hydroponic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val status: Int, val code: String) : IOException(code)
interface Remote {
    suspend fun request(method: String, path: String, body: String? = null, key: String? = null): String
}
class MonitorApi(rawOrigin: String, debug: Boolean, private val accessToken: String? = null) : Remote {
    private val origin = Endpoint.origin(rawOrigin, debug)
    override suspend fun request(method: String, path: String, body: String?, key: String?): String = withContext(Dispatchers.IO) {
        require(path.startsWith("/api/") && !path.contains(".."))
        val connection = URL(origin + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (path != "/api/auth/login" && accessToken != null) connection.setRequestProperty("Authorization", "Bearer $accessToken")
            key?.let { connection.setRequestProperty("Idempotency-Key", it) }
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val buffer = CharArray(2048)
                val result = StringBuilder()
                while (true) {
                    val size = reader.read(buffer)
                    if (size < 0) break
                    result.append(buffer, 0, size)
                    if (result.length > 1048576) throw IOException("서버 응답이 너무 큽니다.")
                }
                result.toString()
            }.orEmpty()
            if (status !in 200..299) {
                val code = runCatching { JSONObject(text).optString("code", "HTTP_$status") }.getOrDefault("HTTP_$status")
                throw ApiException(status, code)
            }
            text
        } finally { connection.disconnect() }
    }
}
