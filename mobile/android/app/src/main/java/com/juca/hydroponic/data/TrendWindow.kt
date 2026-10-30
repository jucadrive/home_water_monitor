package com.juca.hydroponic.data

import java.time.Duration
import java.time.Instant
import kotlin.math.*

enum class TrendPeriod(val label:String,val days:Long) {
    DAY("24시간",1), WEEK("7일",7), MONTH("30일",30);
    fun start(end:Instant):Instant=end.minus(Duration.ofDays(days))
}
data class TrendWindow(val rows:List<SessionSummary>,val period:TrendPeriod,val end:Instant,val limited:Boolean)
data class TrendScale(val low:Double,val high:Double) {
    companion object {
        fun forValues(values:List<Double>):TrendScale {
            if(values.isEmpty()) return TrendScale(0.0,10.0)
            val minimum=values.min();val maximum=values.max();val pad=max(5.0,(maximum-minimum)*.15)
            val rough=(maximum-minimum+pad*2)/4
            val power=10.0.pow(floor(log10(rough)))
            val step=listOf(1.0,2.0,5.0,10.0).first { it>=rough/power }*power
            val low=max(0.0,floor((minimum-pad)/step)*step)
            return TrendScale(low,max(low+step,ceil((maximum+pad)/step)*step))
        }
    }
}
fun inTrendWindow(rows:List<SessionSummary>,period:TrendPeriod,end:Instant)=rows
    .filter { it.eligibleForRealTrend() && !Instant.parse(it.displayAt).isBefore(period.start(end)) && !Instant.parse(it.displayAt).isAfter(end) }
    .distinctBy { it.sessionId }.sortedWith(compareBy<SessionSummary> { Instant.parse(it.displayAt) }.thenBy { it.sessionId })
