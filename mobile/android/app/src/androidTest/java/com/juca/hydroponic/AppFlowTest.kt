package com.juca.hydroponic

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import android.os.ParcelFileDescriptor
import java.util.UUID

// Explicitly run only against the isolated acceptance server. No production data or physical sensor.
class AppFlowTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var originalOrigin: String? = null
    private var originalDevice: String? = null
    private var configured = false
    @get:Rule(order = 0) val preferences = object : TestWatcher() {
        override fun starting(description: Description) {
            val p = context.getSharedPreferences("monitor", Context.MODE_PRIVATE)
            assertNull("Resolve the existing app request before running isolated acceptance", p.getString("pending", null))
            originalOrigin = p.getString("origin", null); originalDevice = p.getString("device", null)
            check(p.edit().putString("origin", "").putString("device", "").commit())
            configured = true
        }
        override fun finished(description: Description) {
            if (!configured) return
            check(context.getSharedPreferences("monitor", Context.MODE_PRIVATE).edit().putString("origin", originalOrigin)
                .putString("device", originalDevice).remove("pending").commit())
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun waitText(text: String, substring: Boolean = false) {
        compose.waitUntil(30000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun clickIn(list: String, text: String) {
        compose.onNodeWithTag(list).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }
    private fun screenshot(name: String) {
        val file = File(context.getExternalFilesDir("acceptance"), "$name.png")
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        // Gradle removes test APK data; preserve only our generated app image for host-side visual review.
        val directory = "/data/local/tmp/com.juca.hydroponic.acceptance"
        val exported = "$directory/accepted-$name.png"
        fun shell(command: String): ByteArray {
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        // executeShellCommand executes one program; shell operators are not interpreted here.
        shell("mkdir -p $directory")
        shell("cp ${file.absolutePath} $exported")
        check(shell("cat $exported").contentEquals(file.readBytes())) { "App screenshot export bytes differ" }
    }
    @Test fun connectedDeviceCreatesMaintainsMeasuresReadsAndEndsCultivation() {
        val args = InstrumentationRegistry.getArguments()
        val origin = requireNotNull(args.getString("serverOrigin")) { "Pass the isolated server origin explicitly" }
        val device = requireNotNull(args.getString("deviceId")) { "Pass the simulated device ID explicitly" }
        val name = "[시험] 폰 재배 ${UUID.randomUUID().toString().take(8)}"
        screenshot("garden-settings")
        // Invalid local input exercises the error presentation without making an external network request.
        compose.onNodeWithText("서버 주소").performScrollTo().performTextReplacement("invalid")
        compose.onNodeWithText("저장하고 연결").performScrollTo().performClick()
        waitText("잠깐, 연결을 살펴볼까요?")
        screenshot("garden-error")
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithText("서버 주소").performScrollTo().performTextReplacement(origin)
        compose.onNodeWithText("장치 ID · 측정할 때 필요").performTextReplacement(device)
        compose.onNodeWithText("저장하고 연결").performScrollTo().performClick()
        waitText("오늘의 재배 기록")
        screenshot("garden-empty")
        compose.onNodeWithText("재배 등록").performClick()
        screenshot("garden-create")
        compose.onNodeWithText("식물명").performTextInput("바질")
        compose.onNodeWithText("재배 별명").performTextInput(name)
        compose.onNodeWithText("재배 시작 일시").performTextReplacement("2026-10-01 09:00")
        compose.onNodeWithText("메모 · 선택").performTextInput("전용 DB·모의 장치 시험. 실측 데이터 아님.")
        compose.onNodeWithText("재배 등록").performScrollTo().performClick()
        waitText(name)
        screenshot("garden-detail-top")
        compose.onNodeWithText("뒤로").performClick()
        waitText("오늘의 재배 기록")
        screenshot("garden-home")
        clickIn("dashboard-list", name)
        waitText(name)
        clickIn("detail-list", "물·영양액 관리 기록")
        screenshot("garden-maintenance")
        compose.onNodeWithText("작업 일시").performTextReplacement("2026-10-02 09:00")
        compose.onNodeWithText("작업량 ml · 선택").performTextInput("500")
        compose.onNodeWithText("관리 이력 등록").performScrollTo().performClick()
        waitText("마지막 전체 교체  2026.10.02 09:00")
        clickIn("detail-list", "$device · 측정 시작")
        waitText("장치 버퍼  RELEASE_CONFIRMED")
        compose.onNodeWithText("서버 보존  NORMAL").assertIsDisplayed()
        clickIn("command-list", "보존 결과 보기")
        waitText("원본 60/60 · 통계 50개")
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("$device · SIMULATED"))
        compose.onNodeWithText("$device · SIMULATED").assertIsDisplayed()
        screenshot("normal-result")
        compose.onNodeWithText("뒤로").performClick()
        compose.onNodeWithText("뒤로").performClick()
        waitText(name)
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("실측 정상 기록이 쌓이면 추세를 보여드립니다."))
        compose.onNodeWithText("실측 정상 기록이 쌓이면 추세를 보여드립니다.").assertIsDisplayed()
        screenshot("cultivation-detail")
        clickIn("detail-list", "재배 종료")
        compose.onNodeWithText("종료", useUnmergedTree = true).performClick()
        // The lazy list remains at the bottom after closing; reveal its header before asserting the new state.
        compose.onNodeWithTag("detail-list").performScrollToIndex(0)
        waitText("종료한 재배", true)
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("$device · 측정 시작"))
        compose.onNodeWithText("$device · 측정 시작").assertIsNotEnabled()
        compose.onNodeWithText("물·영양액 관리 기록").assertIsNotEnabled()
        compose.onNodeWithText("뒤로").performClick()
        waitText("오늘의 재배 기록")
        clickIn("dashboard-list", "재배에 연결되지 않은 측정 기록")
        waitText("불완전 측정 · 통계 없음")
        compose.onNodeWithText("불완전 측정 · 통계 없음").performClick()
        waitText("불완전 보존 · 통계 제외")
        compose.onNodeWithText("원본 0/60 · 통계 0개").assertIsDisplayed()
        screenshot("legacy-incomplete")
    }
}
