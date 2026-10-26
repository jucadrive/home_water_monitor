package com.juca.hydroponic

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.juca.hydroponic.data.*
import com.juca.hydroponic.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class FormsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cultivationFormSubmitsEnteredNamesAndDate() {
        var submitted: List<String>? = null
        compose.setContent { GardenTheme { CultivationForm(false) { plant, name, time, memo -> submitted = listOf(plant, name, time, memo) } } }
        compose.onNodeWithText("식물명").performTextInput("바질")
        compose.onNodeWithText("재배 별명").performTextInput("첫 용기")
        compose.onNodeWithText("재배 시작 일시").performTextReplacement("2026-10-01 09:00")
        compose.onNodeWithText("재배 등록").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("바질", "첫 용기", "2026-10-01 09:00", ""), submitted) }
    }
    @Test fun topUpDoesNotSubmitReplacementScopeOrFabricateUnknownAmount() {
        var type: String? = null; var scope: String? = "unexpected"; var amount: String? = null
        compose.setContent { GardenTheme { MaintenanceForm(false) { t, s, _, a, _ -> type = t; scope = s; amount = a } } }
        compose.onNodeWithText("물 보충").performClick()
        compose.onNodeWithText("관리 이력 등록").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("TOP_UP", type); assertNull(scope); assertEquals("", amount) }
    }
    @Test fun incompleteSessionShowsAbsenceOfStatisticsRatherThanZero() {
        val session = summary("INCOMPLETE", "ADS1115", null)
        compose.setContent { GardenTheme { SessionScreen(SessionDetail(session, emptyList())) } }
        compose.onNodeWithText("불완전 보존 · 통계 제외").assertIsDisplayed()
        compose.onNodeWithText("측정 기록 없음").assertIsDisplayed()
        compose.onNodeWithText("0 ppm").assertDoesNotExist()
    }
    @Test fun trendDoesNotMixSimulatedOrIncompleteValuesIntoRealSeries() {
        val sessions = listOf(summary("NORMAL", "ADS1115", BigDecimal("100")),
            summary("NORMAL", "SIMULATED", BigDecimal("900")), summary("INCOMPLETE", "ADS1115", null))
        compose.setContent { GardenTheme { TrendChart(sessions, emptyList()) } }
        compose.onNodeWithText("100 ~ 100 ppm").assertIsDisplayed()
        compose.onNodeWithContentDescription("실측 정상 TDS 중앙값 추세, 최저 100.0, 최고 100.0 ppm").assertExists()
    }
    @Test fun enlargedFontFormStillSubmitsThroughScrollableLayout() {
        var submitted = false
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                GardenTheme { Box(Modifier.width(320.dp)) { CultivationForm(false) { _, _, _, _ -> submitted = true } } }
            }
        }
        compose.onNodeWithText("식물명").performScrollTo().performTextInput("상추")
        compose.onNodeWithText("재배 별명").performScrollTo().performTextInput("나의 작은 정원")
        compose.onNodeWithText("재배 등록").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(submitted) }
    }
    @Test fun bundledFontLicensesAreReadableFromSettings() {
        compose.setContent { GardenTheme { SettingsScreen(MonitorState(), { _, _ -> }, {}) } }
        compose.onNodeWithText("사용한 글꼴과 라이선스").performScrollTo().performClick()
        compose.onNodeWithText("고운돋움 · 감자꽃").assertIsDisplayed()
        compose.onNodeWithText("SIL OPEN FONT LICENSE", substring = true).assertExists()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("고운돋움 · 감자꽃").assertDoesNotExist()
    }
    private fun summary(result: String, source: String, median: BigDecimal?) = SessionSummary("s-$source", "d", null,
        "2026-10-26T00:00:00Z", "REQUESTED", result, if (result == "NORMAL") 60 else 0,
        if (result == "NORMAL") 50 else 0, median, BigDecimal("25"), "test-version", source)
}
