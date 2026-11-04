package com.juca.hydroponic.data

import java.util.UUID

data class PendingMutation(val origin: String, val path: String, val body: String, val key: String,
    val kind: String, val contextId: String?, val method:String="POST") {
    init { require(method in setOf("POST","PUT")) }
    fun encode():String=org.json.JSONObject().put("origin",origin).put("path",path).put("body",body).put("key",key)
        .put("kind",kind).put("contextId",contextId?:org.json.JSONObject.NULL).put("method",method).toString()
    companion object {
        fun decode(text:String):PendingMutation {
            val j=org.json.JSONObject(text)
            return PendingMutation(j.getString("origin"),j.getString("path"),j.getString("body"),j.getString("key"),
                j.getString("kind"),j.text("contextId"),j.optString("method","POST"))
        }
    }
}
interface MutationStorage { var pending: PendingMutation? }
data class MutationResult(val request: PendingMutation, val response: String)

// Persist the exact request before sending it. A timeout must not silently create a different request key.
class MutationRunner(private val storage: MutationStorage, private val remote: (String) -> Remote) {
    suspend fun execute(origin: String, path: String, body: String, kind: String, contextId: String?,method:String="POST"): MutationResult {
        check(storage.pending == null) { "결과 확인이 필요한 요청을 먼저 처리하세요." }
        val request = PendingMutation(origin, path, body, UUID.randomUUID().toString(), kind, contextId,method)
        storage.pending = request
        return perform(request)
    }
    suspend fun retry(origin: String): MutationResult {
        val request = checkNotNull(storage.pending)
        check(request.origin == origin) { "요청 당시 서버 주소로 돌아가서 재시도하세요." }
        return perform(request)
    }
    private suspend fun perform(request: PendingMutation): MutationResult {
        try {
            val result = remote(request.origin).request(request.method, request.path, request.body, request.key)
            storage.pending = null
            return MutationResult(request, result)
        } catch (e: ApiException) {
            // 408/429 and server/transport failures remain uncertain; all retries keep the exact key and payload.
            if (e.status in 400..499 && e.status !in listOf(401, 403, 408, 429)) storage.pending = null
            throw e
        }
    }
}
