package com.juca.hydroponic.data

import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import java.time.Instant

/** Existing paged API only. Stops at the selected boundary, end, page cap, or time budget. */
class TrendReader(private val remote:Remote) {
    suspend fun load(cultivation:String,period:TrendPeriod,end:Instant):TrendWindow=withTimeout(20_000L) {
        require(cultivation.matches(Regex("[A-Za-z0-9-]{1,64}")))
        val rows=linkedMapOf<String,SessionSummary>()
        var offset=0
        var complete=false
        repeat(8) {
            if(!complete) {
                val page=JSONArray(remote.request("GET","/api/cultivations/$cultivation/tds-trend?limit=200&offset=$offset")).items(SessionSummary::parse)
                check(page.size<=200) { "차트 응답 범위를 확인하지 못했습니다." }
                page.forEach { rows.putIfAbsent(it.sessionId,it) }
                offset+=page.size
                complete=page.size<200 || page.any { Instant.parse(it.displayAt).isBefore(period.start(end)) }
            }
        }
        TrendWindow(inTrendWindow(rows.values.toList(),period,end),period,end,!complete)
    }
}
