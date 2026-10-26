package com.juca.hydroponic.data

import android.content.Context
import org.json.JSONObject
import java.io.IOException

class MonitorPreferences(context: Context) : MutationStorage {
    private val prefs = context.getSharedPreferences("monitor", Context.MODE_PRIVATE)
    var origin: String
        get() = prefs.getString("origin", "").orEmpty()
        set(value) { check(prefs.edit().putString("origin", value).commit()) }
    var device: String
        get() = prefs.getString("device", "").orEmpty()
        set(value) { check(prefs.edit().putString("device", value).commit()) }
    override var pending: PendingMutation?
        get() = prefs.getString("pending", null)?.let { text ->
            val j = JSONObject(text)
            PendingMutation(j.getString("origin"), j.getString("path"), j.getString("body"), j.getString("key"),
                j.getString("kind"), j.text("contextId"))
        }
        set(value) {
            val text = value?.let {
                JSONObject().put("origin", it.origin).put("path", it.path).put("body", it.body).put("key", it.key)
                    .put("kind", it.kind).put("contextId", it.contextId ?: JSONObject.NULL).toString()
            }
            if (!prefs.edit().putString("pending", text).commit()) throw IOException("요청 기록을 저장하지 못했습니다.")
        }
}
