package com.juca.hydroponic.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
fun JSONObject.decimal(key: String): BigDecimal? = text(key)?.toBigDecimal()
fun <T> JSONArray.items(parse: (JSONObject) -> T): List<T> = (0 until length()).map { parse(getJSONObject(it)) }

data class Cultivation(val id: String, val plantName: String, val name: String, val startedAt: String,
    val endedAt: String?, val status: String, val memo: String) {
    companion object {
        fun parse(j: JSONObject) = Cultivation(j.getString("id"), j.getString("plantName"), j.getString("name"),
            j.getString("startedAt"), j.text("endedAt"), j.getString("status"), j.optString("memo"))
    }
}
data class SessionSummary(val sessionId: String, val deviceId: String, val cultivationId: String?,
    val displayAt: String, val timeBasis: String, val preservationResult: String, val preservedSampleCount: Int,
    val statsSampleCount: Int, val medianTdsPpm: BigDecimal?, val temperatureAssumedC: BigDecimal?,
    val calibrationVersion: String, val source: String, val minTdsPpm: BigDecimal? = null,
    val maxTdsPpm: BigDecimal? = null, val avgTdsPpm: BigDecimal? = null) {
    companion object {
        fun parse(j: JSONObject) = SessionSummary(j.getString("sessionId"), j.getString("deviceId"), j.text("cultivationId"),
            j.getString("displayAt"), j.getString("timeBasis"), j.getString("preservationResult"), j.getInt("preservedSampleCount"),
            j.getInt("statsSampleCount"), j.decimal("medianTdsPpm"), j.decimal("temperatureAssumedC"),
            j.getString("calibrationVersion"), j.getString("source"), j.decimal("minTdsPpm"), j.decimal("maxTdsPpm"), j.decimal("avgTdsPpm"))
    }
}
data class Overview(val cultivation: Cultivation, val lastFullReplacementAt: String?, val latestMeasurement: SessionSummary?) {
    companion object {
        fun parse(j: JSONObject) = Overview(Cultivation.parse(j.getJSONObject("cultivation")), j.text("lastFullReplacementAt"),
            j.optJSONObject("latestMeasurement")?.let(SessionSummary::parse))
    }
}
data class Maintenance(val id: String, val type: String, val replacementScope: String?, val occurredAt: String,
    val amountMl: BigDecimal?, val memo: String) {
    companion object {
        fun parse(j: JSONObject) = Maintenance(j.getString("id"), j.getString("type"), j.text("replacementScope"),
            j.getString("occurredAt"), j.decimal("amountMl"), j.optString("memo"))
    }
}
data class Sample(val seq: Int, val elapsedMs: Int, val tdsPpm: BigDecimal, val isWarmup: Boolean, val rawVoltageMv: BigDecimal?)
data class SessionDetail(val session: SessionSummary, val measurements: List<Sample>) {
    companion object {
        fun parse(j: JSONObject) = SessionDetail(SessionSummary.parse(j.getJSONObject("session")),
            j.getJSONArray("measurements").items { Sample(it.getInt("seq"), it.getInt("elapsedMs"),
                it.get("tdsPpm").toString().toBigDecimal(), it.getBoolean("isWarmup"), it.decimal("rawVoltageMv")) })
    }
}
data class CommandStatus(val commandId: String, val sessionId: String, val commandState: String, val deviceId: String?,
    val serverPreservation: String, val deviceBuffer: String, val liveness: String, val reason: String?,
    val deviceState: String? = null, val deviceFault: String? = null) {
    companion object {
        fun parse(j: JSONObject) = CommandStatus(j.getString("commandId"), j.getString("sessionId"), j.getString("commandState"), j.text("deviceId"),
            j.optString("serverPreservation", "NOT_OBSERVED"), j.optString("deviceBuffer", "NOT_OBSERVED"),
            j.optString("liveness", "UNKNOWN"), j.text("reason"))
    }
}
