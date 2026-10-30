package com.juca.hydroponic

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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
        compose.onNodeWithText("0.00 ppm").assertDoesNotExist()
    }
    @Test fun trendDoesNotMixSimulatedOrIncompleteValuesIntoRealSeries() {
        val sessions = listOf(summary("NORMAL", "ADS1115", BigDecimal("100")),
            summary("NORMAL", "SIMULATED", BigDecimal("900")), summary("INCOMPLETE", "ADS1115", null))
        compose.setContent { GardenTheme { TrendChart(sessions, emptyList(),end=java.time.Instant.parse("2026-10-30T00:00:00Z")) } }
        compose.onNodeWithText("100.00 ppm").assertIsDisplayed()
        compose.onNodeWithContentDescription("실측 정상 TDS 중앙값 추세, 1개 관측, 단위 ppm").assertExists()
        compose.onNodeWithText("900.00 ppm").assertDoesNotExist()
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
    @Test fun loginHidesServerDeviceAndDevelopmentControls() {
        compose.setContent { GardenTheme { SettingsScreen(MonitorState(), { _, _ -> }, {}) } }
        compose.onNodeWithText("서버 주소").assertDoesNotExist()
        compose.onNodeWithText("장치 ID · 측정할 때 필요").assertDoesNotExist()
        compose.onNodeWithText("사용한 글꼴과 라이선스").assertDoesNotExist()
        compose.onNodeWithText("개발 빌드:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("계정 이름").assertIsDisplayed()
        compose.onNodeWithText("비밀번호").assertIsDisplayed()
        snapshot("login")
    }
    @Test fun deviceSettingsRemainAvailableAfterLogin() {
        var saved = ""
        compose.setContent { GardenTheme { SettingsScreen(MonitorState(authenticated = true, origin = "https://fixture.invalid"),
            { origin, device -> saved = "$origin/$device" }, {}) } }
        compose.onNodeWithText("장치 ID · 측정할 때 필요").performTextInput("esp32-bench")
        compose.onNodeWithText("장치 설정 저장").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("https://fixture.invalid/esp32-bench", saved) }
    }
    @Test fun narrowLargeFontResultKeepsProvenanceAndExpandsIdentifiers() {
        val session = summary("NORMAL", "SIMULATED", BigDecimal("120"))
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                GardenTheme { Box(Modifier.width(320.dp)) { SessionScreen(SessionDetail(session, emptyList())) } }
            }
        }
        compose.onNodeWithText("120.00 ppm").assertExists()
        compose.onNodeWithText("가상 측정 · 실측 아님").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("25°C 가정 · 실측 수온 아님").performScrollTo().assertIsDisplayed()
        snapshot("result-large")
        compose.onNodeWithText("세션 ID").assertDoesNotExist()
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("상세 정보 보기"))
        compose.onNodeWithText("상세 정보 보기").performScrollTo().performClick()
        compose.onNodeWithText("교정 버전").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("s-SIMULATED").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("상세 정보 접기"))
        compose.onNodeWithText("상세 정보 접기").performScrollTo().performClick()
        compose.onNodeWithText("세션 ID").assertDoesNotExist()
        compose.onNodeWithText("보존 원본").performScrollTo().assertIsDisplayed()
    }
    @Test fun measuredResultKeepsCalibrationInDetailsAndRawSamples() {
        val session = summary("NORMAL", "ADS1115", BigDecimal("120")).copy(calibrationVersion = "sen0244-uncalibrated-v1")
        compose.setContent { GardenTheme { SessionScreen(SessionDetail(session,
            listOf(Sample(1, 1000, BigDecimal("121"), true, BigDecimal("300"))))) } }
        compose.onNodeWithText("TDS 미교정").assertDoesNotExist()
        compose.onNodeWithText("ADS1115").assertDoesNotExist()
        snapshot("result")
        compose.onNodeWithText("상세 정보 보기").performScrollTo().performClick()
        compose.onNodeWithText("TDS 미교정").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ADS1115").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("안정화 원본 · 통계 제외"))
        compose.onNodeWithText("안정화 원본 · 통계 제외").performScrollTo().assertIsDisplayed()
    }
    @Test fun introUsesFullWidthAtLargeFont() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                GardenTheme { Box(Modifier.width(320.dp)) { GardenIntro("오늘도, 초록 한 스푼", "작은 돌봄이 모여 나의 정원이 자라요.") } }
            }
        }
        compose.onNodeWithText("뽀싹 돌봄 일지").assertIsDisplayed()
        compose.onNodeWithText("오늘도, 초록 한 스푼").assertIsDisplayed()
        snapshot("intro-large")
    }
    @Test fun dashboardUsesCareHeadingWithPlantRecords() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val vm = MonitorViewModel(context.applicationContext as android.app.Application)
        val plant = Cultivation("fixture", "바질", "창가의 바질", "2026-10-01T00:00:00Z", null, "ACTIVE", "")
        val state = MonitorState(cultivations = listOf(Overview(plant, null, summary("NORMAL", "ADS1115", BigDecimal("120")))))
        compose.setContent { GardenTheme { DashboardScreen(state, vm) } }
        compose.onNodeWithText("뽀싹 돌봄 일지").assertIsDisplayed()
        compose.onNodeWithText("오늘도, 초록 한 스푼").assertIsDisplayed()
        compose.onNodeWithText("창가의 바질").assertExists()
        snapshot("dashboard")
    }
    private fun snapshot(name: String) {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.getExternalFilesDir(null), "ui-$name.png")
        val bitmap = compose.onRoot().captureToImage()
        file.outputStream().use { bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        shell("mkdir -p /data/local/tmp/ppossak-ui")
        shell("cp ${file.absolutePath} /data/local/tmp/ppossak-ui/$name.png")
    }
    @Test fun ownerPasswordIsClearedAfterEachLoginSubmission() {
        val submitted = mutableListOf<String>()
        compose.setContent { GardenTheme { OwnerLogin(false) { _, password -> submitted += password } } }
        compose.onNodeWithText("계정 이름").performTextInput("owner")
        compose.onNodeWithText("비밀번호").performTextInput("fixture-only")
        compose.onNodeWithText("로그인", substring = false).performClick()
        compose.onNodeWithText("비밀번호").performTextInput("second")
        compose.onNodeWithText("로그인", substring = false).performClick()
        compose.runOnIdle { assertEquals(listOf("fixture-only", "second"), submitted) }
    }
    @Test fun realKeystoreProtectsTokenAndRecoversFromCorruptCiphertext() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("owner_auth", android.content.Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        try {
            val vault = OwnerSessionVault(context)
            val session = OwnerSession("https://fixture.invalid", "a".repeat(43), java.time.Instant.now().plusSeconds(60).toString())
            vault.save(session)
            assertFalse(prefs.getString("sealed", "")!!.contains(session.accessToken))
            assertEquals(session, vault.read(session.origin))
            assertNull(vault.read("https://other.invalid"))
            check(prefs.edit().putString("sealed", "corrupt").commit())
            assertNull(vault.read(session.origin))
        } finally {
            val editor = prefs.edit().clear()
            before.forEach { (key, value) -> check(value is String); editor.putString(key, value) }
            check(editor.commit())
        }
    }
    private fun summary(result: String, source: String, median: BigDecimal?) = SessionSummary("s-$source", "d", null,
        "2026-10-26T00:00:00Z", "REQUESTED", result, if (result == "NORMAL") 60 else 0,
        if (result == "NORMAL") 50 else 0, median, BigDecimal("25"), "test-version", source)
}
