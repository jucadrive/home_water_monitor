package com.juca.hydroponic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.juca.hydroponic.data.*
import com.juca.hydroponic.ui.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

// Rendering fixture only: no physical DS18B20 acquisition is asserted here.
class TemperatureScreensTest {
    @get:Rule val compose = createComposeRule()
    private fun summary() = SessionSummary("s", "d", null, "2026-10-28T00:00:00Z", "REQUESTED", "NORMAL", 60, 50,
        BigDecimal("120"), null, "sen0244-uncalibrated-v1", "ADS1115", temperatureSource = "DS18B20",
        compensationVersion = "sen0244-voltage-atc-v1", averageTemperatureC = BigDecimal("26.0625"))
    @Test fun measuredTemperatureAndRawObservationRemainVisible() {
        compose.setContent { GardenTheme { SessionScreen(SessionDetail(summary(),
            listOf(Sample(1,1000,BigDecimal("120"),true,BigDecimal("750"),BigDecimal("26.0625"),900)))) } }
        compose.onNodeWithText("실측 평균 수온 · 26.06°C").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("25°C 기준 온도 보정 · TDS 미교정").assertDoesNotExist()
        compose.onNodeWithText("TDS 미교정").assertDoesNotExist()
        compose.onNodeWithText("DS18B20").assertDoesNotExist()
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("수온 26.06°C · 관측 차 100 ms"))
        compose.onNodeWithText("수온 26.06°C · 관측 차 100 ms").assertIsDisplayed()
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("상세 정보 보기"))
        compose.onNodeWithText("상세 정보 보기").performScrollTo().performClick()
        compose.onNodeWithText("25°C 기준 온도 보정 · TDS 미교정").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("TDS 미교정").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("DS18B20").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("sen0244-voltage-atc-v1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("상세 정보 접기").performScrollTo().performClick()
        compose.onNodeWithText("25°C 기준 온도 보정 · TDS 미교정").assertDoesNotExist()
    }
    @Test fun legacyAssumedTemperatureRemainsVisibleWithoutOpeningDetails() {
        val legacy = summary().copy(temperatureSource=null, compensationVersion=null,
            temperatureAssumedC=BigDecimal("25"), averageTemperatureC=null)
        compose.setContent { GardenTheme { SessionScreen(SessionDetail(legacy, emptyList())) } }
        compose.onNodeWithText("25°C 가정 · 실측 수온 아님").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("TDS 미교정").assertDoesNotExist()
    }
    @Test fun simulatedTemperatureIsNotPlottedAsRealTds() {
        compose.setContent { GardenTheme { TrendChart(listOf(summary().copy(temperatureSource = "SIMULATED")), emptyList()) } }
        compose.onNodeWithText("실측 정상 기록이 쌓이면 추세를 보여드립니다.").assertIsDisplayed()
        compose.onNodeWithContentDescription("실측 정상 TDS 중앙값 추세", substring = true).assertDoesNotExist()
    }
    @Test fun mixedLegacyAndCompensatedTrendIdentifiesBothTemperatureBases() {
        val legacy=summary().copy(sessionId="legacy",temperatureSource=null,compensationVersion=null,temperatureAssumedC=BigDecimal("25"))
        compose.setContent { GardenTheme { TrendChart(listOf(legacy,summary()),emptyList(),end=java.time.Instant.parse("2026-10-30T00:00:00Z")) } }
        compose.onNodeWithText("수온 기준: 가정 수온 · 실측 수온 보정 기록").assertIsDisplayed()
    }
}
