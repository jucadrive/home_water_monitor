package com.juca.hydroponic.data

import java.util.UUID

data class PendingMutation(val origin: String, val path: String, val body: String, val key: String,
    val kind: String, val contextId: String?)
interface MutationStorage { var pending: PendingMutation? }
data class MutationResult(val request: PendingMutation, val response: String)

// Persist the exact request before sending it. A timeout must not silently create a different request key.
class MutationRunner(private val storage: MutationStorage, private val remote: (String) -> Remote) {
    suspend fun execute(origin: String, path: String, body: String, kind: String, contextId: String?): MutationResult {
        check(storage.pending == null) { "결과 확인이 필요한 요청을 먼저 처리하세요." }
        val request = PendingMutation(origin, path, body, UUID.randomUUID().toString(), kind, contextId)
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
            val result = remote(request.origin).request("POST", request.path, request.body, request.key)
            storage.pending = null
            return MutationResult(request, result)
        } catch (e: ApiException) {
            // 408/429 and server/transport failures remain uncertain; all retries keep the exact key and payload.
            if (e.status in 400..499 && e.status !in listOf(401, 403, 408, 429)) storage.pending = null
            throw e
        }
    }
}
