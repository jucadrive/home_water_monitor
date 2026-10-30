package com.juca.hydroponic.data

import com.juca.hydroponic.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.*

class TrendWindowTest {
    private val end=Instant.parse("2026-10-30T12:00:00Z")
    private fun row(id:String,time:Instant,value:String="100")=SessionSummary(id,"d","c",time.toString(),"REQUESTED","NORMAL",60,50,
        BigDecimal(value),null,"sen0244-uncalibrated-v1","ADS1115",temperatureSource="DS18B20")
    @Test fun rollingWindowsUseActualInstantsAndInclusiveBoundaries() {
        val rows=listOf(row("old",end.minusSeconds(86401)),row("edge",end.minusSeconds(86400)),row("now",end),row("future",end.plusSeconds(1)))
        assertEquals(listOf("edge","now"),inTrendWindow(rows,TrendPeriod.DAY,end).map { it.sessionId })
        assertEquals(3,inTrendWindow(rows,TrendPeriod.WEEK,end).size)
        assertEquals(end.minusSeconds(30*86400L),TrendPeriod.MONTH.start(end))
    }
    @Test fun unknownSimulatedAndIncompleteNeverBecomeRealTrendPoints() {
        val real=row("real",end)
        val rows=listOf(real,real.copy(sessionId="sim",source="SIMULATED"),real.copy(sessionId="temp",temperatureSource="SIMULATED"),
            real.copy(sessionId="unknown",source="UNKNOWN"),real.copy(sessionId="bad",preservationResult="INCOMPLETE"))
        assertEquals(listOf(real),inTrendWindow(rows,TrendPeriod.DAY,end))
    }
    @Test fun repeatedOffsetsAreDeduplicatedAndEqualTimesSortStably() {
        val a=row("a",end);val b=row("b",end)
        assertEquals(listOf(a,b),inTrendWindow(listOf(b,a,a),TrendPeriod.DAY,end))
    }
    @Test fun constantAndZeroReadingsHaveNonzeroAxisWithoutChangingMeasurements() {
        val zero=TrendScale.forValues(listOf(0.0,0.0));assertEquals(0.0,zero.low,0.0);assertTrue(zero.high>zero.low)
        val scale=TrendScale.forValues(listOf(100.0,100.0));assertTrue(scale.low<100);assertTrue(scale.high>100)
        assertEquals("0.00 ppm",ppm(BigDecimal.ZERO));assertEquals("측정 기록 없음",ppm(null))
        val original=BigDecimal("16.1250");assertEquals("16.13°C",degrees(original));assertEquals("16.1250",original.toPlainString())
    }
    @Test fun timelineUsesActualEventsAndPreservesUnknownAmounts() {
        val plant=Cultivation("c","basil","tank",end.minusSeconds(86400).toString(),null,"ACTIVE","")
        val water=Maintenance("m","TOP_UP",null,end.toString(),null,"note")
        val rows=careEvents(plant,listOf(row("s",end.minusSeconds(60))),listOf(water))
        assertEquals(listOf("m-m","s-s","start-c"),rows.map { it.key })
        assertEquals("작업량 미상",rows.first().detail)
        assertEquals("오늘",careDayLabel(LocalDate.of(2026,10,30),LocalDate.of(2026,10,30)))
        assertEquals("어제",careDayLabel(LocalDate.of(2026,10,29),LocalDate.of(2026,10,30)))
    }
}
