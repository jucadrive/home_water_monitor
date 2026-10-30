package com.juca.hydroponic.data

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TrendReaderTest {
    private val end=Instant.parse("2026-10-30T12:00:00Z")
    private fun json(id:Int,time:Instant=end.minusSeconds(id.toLong()))=JSONObject().put("sessionId","s-$id")
        .put("deviceId","d").put("cultivationId","c").put("displayAt",time.toString()).put("timeBasis","REQUESTED")
        .put("preservationResult","NORMAL").put("preservedSampleCount",60).put("statsSampleCount",50)
        .put("medianTdsPpm",100).put("temperatureAssumedC",25).put("calibrationVersion","sen0244-uncalibrated-v1").put("source","ADS1115")
    private class Fake(val respond:(Int)->JSONArray):Remote {
        val offsets=mutableListOf<Int>()
        override suspend fun request(method:String,path:String,body:String?,key:String?):String {
            assertEquals("GET",method);assertNull(body);assertNull(key);assertTrue(path.contains("limit=200"))
            val offset=path.substringAfter("offset=").toInt();offsets+=offset
            return respond(offset).toString()
        }
    }
    @Test fun boundedEightPagesNeverFetchesUnlimitedHistory()=runTest {
        val fake=Fake { offset->JSONArray((offset until offset+200).map { json(it) }) }
        val result=TrendReader(fake).load("c",TrendPeriod.MONTH,end)
        assertEquals(1600,result.rows.size);assertTrue(result.limited)
        assertEquals((0..7).map { it*200 },fake.offsets)
    }
    @Test fun shortPageStopsImmediatelyAndFutureIsNotInventedAsCurrent()=runTest {
        val fake=Fake { JSONArray(listOf(json(1),json(2,end.plusSeconds(1)))) }
        val result=TrendReader(fake).load("c",TrendPeriod.DAY,end)
        assertEquals(1,result.rows.size);assertFalse(result.limited);assertEquals(listOf(0),fake.offsets)
    }
    @Test fun crossingSelectedBoundaryStopsWithoutRequestingOlderPages()=runTest {
        val fake=Fake { JSONArray((0..199).map { json(it,end.minusSeconds(86401L+it)) }) }
        val result=TrendReader(fake).load("c",TrendPeriod.DAY,end)
        assertTrue(result.rows.isEmpty());assertFalse(result.limited);assertEquals(listOf(0),fake.offsets)
    }
    @Test fun duplicatedOffsetRowsDoNotCorruptFollowingOffsetOrCreateExtraPoints()=runTest {
        val fake=Fake { when(it) {0->JSONArray((0..199).map { n->json(n) });200->JSONArray((199..398).map { n->json(n) });else->JSONArray()} }
        val result=TrendReader(fake).load("c",TrendPeriod.MONTH,end)
        assertEquals(399,result.rows.size);assertEquals(listOf(0,200,400),fake.offsets);assertFalse(result.limited)
    }
    @Test fun totalBudgetTerminatesAStalledRead()=runTest {
        val remote=object:Remote { override suspend fun request(method:String,path:String,body:String?,key:String?):String { awaitCancellation() } }
        try { TrendReader(remote).load("c",TrendPeriod.DAY,end);fail("Expected timeout") }
        catch(_:TimeoutCancellationException) { }
    }
    @Test fun authenticationFailureIsNotSwallowedOrTurnedIntoEmptyData()=runTest {
        val remote=object:Remote { override suspend fun request(method:String,path:String,body:String?,key:String?):String { throw ApiException(401,"AUTHENTICATION_REQUIRED") } }
        try { TrendReader(remote).load("c",TrendPeriod.DAY,end);fail("Expected auth error") }
        catch(e:ApiException){assertEquals(401,e.status)}
    }
}
