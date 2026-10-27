package com.juca.hydroponic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juca.hydroponic.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = MonitorPreferences(application)
    private val vault = OwnerSessionVault(application)
    private val mutable = MutableStateFlow(MonitorState(origin = prefs.origin, device = prefs.device, pending = prefs.pending,
        authenticated = vault.read(prefs.origin) != null))
    val state = mutable.asStateFlow()
    private fun remote(origin: String) = MonitorApi(origin, BuildConfig.DEBUG, vault.read(origin)?.accessToken)
    private val runner = MutationRunner(prefs, ::remote)
    private val owner = OwnerSessionClient(vault, { origin, token -> MonitorApi(origin, BuildConfig.DEBUG, token) })
    private fun api() = remote(mutable.value.origin)
    init { if (mutable.value.authenticated) list() }

    private fun call(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is ApiException && e.status == 401) {
                    vault.clear(mutable.value.origin)
                    mutable.value = MonitorState(origin = prefs.origin, device = prefs.device, pending = prefs.pending,
                        busy = true, error = "로그인이 필요합니다. 확인 중인 요청은 보존했습니다.")
                    return@launch
                }
                val text = if (e is ApiException) "요청 실패: ${e.code} (${e.status})" else e.message ?: "서버에 연결하지 못했습니다."
                mutable.update { it.copy(error = text) }
            } finally { mutable.update { it.copy(busy = false, pending = prefs.pending) } }
        }
    }
    fun settings() { if (!mutable.value.busy) mutable.update { it.copy(screen = Screen.SETTINGS, error = null) } }
    fun configure(origin: String, device: String) = call {
        val normalized = Endpoint.origin(origin, BuildConfig.DEBUG)
        check(prefs.pending == null || prefs.pending?.origin == normalized) { "확인 중인 요청의 서버 주소를 유지하세요." }
        if (device.isNotEmpty()) Endpoint.device(device)
        prefs.origin = normalized; prefs.device = device
        val authenticated = vault.read(normalized) != null
        mutable.value = MonitorState(origin = normalized, device = device, pending = prefs.pending, busy = true, authenticated = authenticated)
        if (authenticated) loadList(false)
    }
    fun login(username: String, password: String) = call {
        val origin = Endpoint.origin(mutable.value.origin, BuildConfig.DEBUG)
        owner.login(origin, username, password)
        mutable.update { it.copy(authenticated = true) }
        loadList(false)
    }
    fun logout() = call {
        try { owner.logout(mutable.value.origin) }
        finally { mutable.value = MonitorState(origin = prefs.origin, device = prefs.device, pending = prefs.pending, busy = true) }
    }
    fun list(more: Boolean = false) = call { loadList(more) }
    private suspend fun loadList(more: Boolean) {
        val previous = if (more) mutable.value.cultivations else emptyList()
        val page = JSONArray(api().request("GET", "/api/cultivations?limit=50&offset=${previous.size}")).items(Overview::parse)
        mutable.update { it.copy(screen = Screen.LIST, cultivations = (previous + page).distinctBy { c -> c.cultivation.id }, moreCultivations = page.size == 50) }
    }
    fun createForm() { if (!mutable.value.busy) mutable.update { it.copy(screen = Screen.CREATE, error = null) } }
    fun maintenanceForm() { if (!mutable.value.busy) mutable.update { it.copy(screen = Screen.MAINTENANCE, error = null) } }
    fun detail(id: String) = call { loadDetail(id) }
    private suspend fun loadDetail(id: String) {
        val path = "/api/cultivations/$id"
        val overview = Overview.parse(JSONObject(api().request("GET", path)))
        val events = JSONArray(api().request("GET", "$path/water-maintenances?limit=50")).items(Maintenance::parse)
        val samples = JSONArray(api().request("GET", "$path/measurement-sessions?limit=50")).items(SessionSummary::parse)
        val trend = JSONArray(api().request("GET", "$path/tds-trend?limit=50")).items(SessionSummary::parse)
        mutable.update { it.copy(screen = Screen.DETAIL, detail = overview, maintenance = events, sessions = samples, trend = trend,
            moreMaintenance = events.size == 50, moreSessions = samples.size == 50) }
    }
    fun moreHistory(maintenance: Boolean) = call {
        val s = mutable.value; val id = checkNotNull(s.detail).cultivation.id
        if (maintenance) {
            val page = JSONArray(api().request("GET", "/api/cultivations/$id/water-maintenances?limit=50&offset=${s.maintenance.size}")).items(Maintenance::parse)
            mutable.update { it.copy(maintenance = (s.maintenance + page).distinctBy { e -> e.id }, moreMaintenance = page.size == 50) }
        } else {
            val page = JSONArray(api().request("GET", "/api/cultivations/$id/measurement-sessions?limit=50&offset=${s.sessions.size}")).items(SessionSummary::parse)
            mutable.update { it.copy(sessions = (s.sessions + page).distinctBy { e -> e.sessionId }, moreSessions = page.size == 50) }
        }
    }
    fun unlinked(more: Boolean = false) = call {
        val previous = if (more) mutable.value.unlinked else emptyList()
        val page = JSONArray(api().request("GET", "/api/measurement-sessions?unlinked=true&limit=50&offset=${previous.size}")).items(SessionSummary::parse)
        mutable.update { it.copy(screen = Screen.UNLINKED, unlinked = (previous + page).distinctBy { x -> x.sessionId }, moreUnlinked = page.size == 50) }
    }
    fun session(id: String) = call {
        val parent = mutable.value.screen.let { if (it == Screen.SESSION) mutable.value.sessionParent else it }
        val data = SessionDetail.parse(JSONObject(api().request("GET", "/api/measurement-sessions/$id")))
        mutable.update { it.copy(screen = Screen.SESSION, sessionParent = parent, session = data) }
    }
    fun checkDevice() = call {
        val device = Endpoint.device(mutable.value.device)
        val data = JSONObject(api().request("GET", "/api/devices/$device/status"))
        mutable.update { it.copy(deviceStatus = "${data.optString("liveness")} · ${data.optJSONObject("snapshot")?.optString("state") ?: "관측 없음"}") }
    }
    fun refreshCommand() = call {
        val id = checkNotNull(mutable.value.command).commandId
        val command = CommandStatus.parse(JSONObject(api().request("GET", "/api/measurement-commands/$id")))
        mutable.update { it.copy(command = command) }
        command.deviceId?.let { device ->
            val status = JSONObject(api().request("GET", "/api/devices/$device/status")).optJSONObject("snapshot")
            mutable.update { it.copy(command = command.copy(deviceState = status?.text("state"), deviceFault = status?.text("fault"))) }
        }
    }
    fun create(plant: String, name: String, time: String, memo: String) = mutate("CREATE", "/api/cultivations", null) {
        JSONObject().put("plantName", plant).put("name", name).put("startedAt", Dates.toInstant(time)).put("memo", memo)
    }
    fun maintenance(type: String, scope: String?, time: String, amount: String, memo: String) {
        val id = mutable.value.detail?.cultivation?.id ?: return
        mutate("MAINTENANCE", "/api/cultivations/$id/water-maintenances", id) {
            JSONObject().put("type", type).put("replacementScope", scope ?: JSONObject.NULL).put("occurredAt", Dates.toInstant(time))
                .put("amountMl", if (amount.isBlank()) JSONObject.NULL else amount.toBigDecimal()).put("memo", memo)
        }
    }
    fun end() {
        val id = mutable.value.detail?.cultivation?.id ?: return
        mutate("END", "/api/cultivations/$id/end", id) { JSONObject() }
    }
    fun start() {
        val id = mutable.value.detail?.cultivation?.id ?: return
        mutate("START", "/api/cultivations/$id/measurement-commands", id) { JSONObject().put("deviceId", Endpoint.device(mutable.value.device)) }
    }
    fun retryResult() {
        val s = mutable.value
        val session = s.command?.sessionId ?: return
        val device = s.command.deviceId ?: return
        mutate("RETRY", "/api/devices/$device/measurement-commands", s.detail?.cultivation?.id) {
            JSONObject().put("type", "RETRY").put("sessionId", session)
        }
    }
    private fun mutate(kind: String, path: String, context: String?, body: () -> JSONObject) = call {
        handle(runner.execute(mutable.value.origin, path, body().toString(), kind, context))
    }
    fun retryPending() = call { check(mutable.value.authenticated) { "먼저 로그인하세요." }; handle(runner.retry(mutable.value.origin)) }
    fun abandonPending() { if (!mutable.value.busy) { prefs.pending = null; mutable.update { it.copy(pending = null) } } }
    private suspend fun handle(result: MutationResult) {
        when (result.request.kind) {
            "CREATE" -> loadDetail(JSONObject(result.response).getString("id"))
            "START", "RETRY" -> {
                val device = if (result.request.kind == "START") JSONObject(result.request.body).getString("deviceId") else result.request.path.split("/")[3]
                mutable.update { it.copy(screen = Screen.COMMAND, command = CommandStatus.parse(JSONObject(result.response)).copy(deviceId = device)) }
            }
            else -> loadDetail(checkNotNull(result.request.contextId))
        }
    }
    fun back() {
        if (mutable.value.busy) return
        when (mutable.value.screen) {
            Screen.SESSION -> mutable.update { it.copy(screen = it.sessionParent) }
            Screen.MAINTENANCE, Screen.COMMAND -> if (mutable.value.detail != null) detail(mutable.value.detail!!.cultivation.id) else unlinked()
            Screen.SETTINGS -> if (mutable.value.authenticated) list()
            else -> list()
        }
    }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    fun commandScreen() { if (!mutable.value.busy) mutable.update { it.copy(screen = Screen.COMMAND) } }
}
