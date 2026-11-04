package com.juca.hydroponic.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.IOException

class CareEditingTest {
    @Test fun legacyPendingWithoutMethodRemainsPostAndPutRoundTripsExactly() {
        val old=JSONObject().put("origin","https://example.com").put("path","/api/cultivations").put("body","original")
            .put("key","key").put("kind","CREATE").put("contextId",JSONObject.NULL)
        assertEquals("POST",PendingMutation.decode(old.toString()).method)
        val put=PendingMutation("https://example.com","/api/cultivations/c/profile","exact body","key","EDIT_PROFILE","c","PUT")
        assertEquals(put,PendingMutation.decode(put.encode()))
        assertThrows(IllegalArgumentException::class.java){put.copy(method="DELETE")}
    }
    @Test fun lostPutResponseAndUnauthorizedKeepMethodOriginBodyAndKeyAcrossRestart()=runTest {
        val storage=MutationRunnerTest.Memory();val calls=mutableListOf<List<String?>>()
        var status=503
        val remote=object:Remote {
            override suspend fun request(method:String,path:String,body:String?,key:String?):String {
                calls+=listOf(method,path,body,key)
                if(status!=200)throw ApiException(status,"TEST")
                return "{}"
            }
        }
        val runner=MutationRunner(storage){remote}
        try { runner.execute("https://one.example","/api/cultivations/c/profile","exact","EDIT_PROFILE","c","PUT");fail() }catch(_:IOException){}
        val original=storage.pending!!
        status=401
        try { MutationRunner(storage){remote}.retry("https://one.example");fail() }catch(_:IOException){}
        assertEquals(original,storage.pending)
        assertThrows(IllegalStateException::class.java){ kotlinx.coroutines.runBlocking { runner.retry("https://two.example") } }
        status=200;MutationRunner(storage){remote}.retry("https://one.example")
        assertEquals(3,calls.size);assertTrue(calls.all{it==calls.first()});assertEquals("PUT",calls.first().first());assertNull(storage.pending)
    }
    @Test fun unchangedDisplayedTimePreservesSecondsAndOriginalDraftAfterConflict() {
        val original="2026-10-30T01:02:35.123456Z"
        assertEquals(original,Dates.correctedInstant(Dates.input(original),original))
        assertEquals(original,Dates.correctedInstant(original,"2026-10-30T02:03:42Z"))
    }
    @Test fun versionAndTimestampAreReadWithoutChangingLegacyFields() {
        val json=JSONObject("""{"id":"m","type":"TOP_UP","replacementScope":null,"occurredAt":"2026-10-30T01:00:00Z","amountMl":null,"memo":"fixed","version":3,"updatedAt":"2026-10-30T02:00:00Z"}""")
        val value=Maintenance.parse(json);assertEquals(3,value.version);assertEquals("fixed",value.memo);assertNull(value.amountMl)
        assertEquals(0,Maintenance.parse(json.apply{remove("version");remove("updatedAt")}).version)
    }
}
