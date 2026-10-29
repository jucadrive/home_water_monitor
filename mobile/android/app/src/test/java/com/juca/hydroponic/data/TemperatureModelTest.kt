package com.juca.hydroponic.data

import com.juca.hydroponic.ui.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class TemperatureModelTest {
    private fun input() = JSONObject("""{"session":{"sessionId":"s","deviceId":"d","cultivationId":null,
        "displayAt":"2026-10-28T00:00:00Z","timeBasis":"REQUESTED","preservationResult":"NORMAL",
        "preservedSampleCount":60,"statsSampleCount":50,"medianTdsPpm":120,"temperatureAssumedC":25,
        "calibrationVersion":"sen0244-uncalibrated-v1","source":"ADS1115"},
        "measurements":[{"seq":1,"elapsedMs":1000,"tdsPpm":120,"isWarmup":true,"rawVoltageMv":750}]}""")
    @Test fun legacyResponseKeepsAssumedTemperatureAndAbsentSamples() {
        val data = SessionDetail.parse(input())
        assertNull(data.session.temperatureSource)
        assertNull(data.measurements.first().temperatureC)
        assertEquals("25°C 가정 · 실측 수온 아님", temperatureLabel(data.session))
        assertTrue(data.session.eligibleForRealTrend())
    }
    @Test fun measuredTemperatureKeepsResolutionAndObservationTime() {
        val json = input()
        json.getJSONObject("session").put("temperatureAssumedC", JSONObject.NULL).put("temperatureSource", "DS18B20")
            .put("compensationVersion", "sen0244-voltage-atc-v1").put("averageTemperatureC", BigDecimal("25.0625"))
        json.getJSONArray("measurements").getJSONObject(0).put("temperatureC", BigDecimal("25.0625")).put("temperatureElapsedMs", 900)
        val data = SessionDetail.parse(json)
        assertEquals(BigDecimal("25.0625"), data.measurements.first().temperatureC)
        assertEquals(900, data.measurements.first().temperatureElapsedMs)
        assertEquals("실측 평균 수온 · 25.06°C", temperatureLabel(data.session))
        assertEquals("25°C 기준 온도 보정 · TDS 미교정", compensationLabel(data.session))
    }
    @Test fun simulatedOrUnknownTemperatureIsExcludedFromRealTrend() {
        val real = SessionDetail.parse(input()).session
        assertTrue(real.copy(temperatureSource = "DS18B20").eligibleForRealTrend())
        assertFalse(real.copy(temperatureSource = "SIMULATED").eligibleForRealTrend())
        assertFalse(real.copy(temperatureSource = "UNKNOWN").eligibleForRealTrend())
        assertFalse(real.copy(source = "SIMULATED", temperatureSource = "DS18B20").eligibleForRealTrend())
    }
    @Test fun unknownFormulaAndMissingAverageAreExplicit() {
        val s = SessionDetail.parse(input()).session.copy(temperatureSource = "SIMULATED", compensationVersion = "future")
        assertEquals("모의 평균 수온 · 평균 없음", temperatureLabel(s))
        assertEquals("보정 공식 확인 필요", compensationLabel(s))
    }
}
