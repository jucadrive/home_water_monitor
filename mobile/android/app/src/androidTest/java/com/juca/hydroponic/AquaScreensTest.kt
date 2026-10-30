package com.juca.hydroponic

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.asAndroidBitmap
import com.juca.hydroponic.data.*
import com.juca.hydroponic.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class AquaScreensTest {
    @get:Rule val compose=createComposeRule()
    private val end=Instant.parse("2026-10-30T12:00:00Z")
    private fun row(id:String,time:Instant,value:String)=SessionSummary(id,"d","c",time.toString(),"REQUESTED","NORMAL",60,50,
        BigDecimal(value),null,"sen0244-uncalibrated-v1","ADS1115",BigDecimal("98"),BigDecimal("140"),BigDecimal("115"),
        "DS18B20","sen0244-voltage-atc-v1",BigDecimal("16.125"))
    @Test fun periodAndAccessiblePointSelectionUseOnlyActualObservations() {
        val rows=listOf(row("old",end.minusSeconds(2*86400),"100"),row("new",end.minusSeconds(3600),"130"))
        compose.setContent {
            var period by remember { mutableStateOf(TrendPeriod.DAY) }
            GardenTheme { TrendChart(rows,emptyList(),period,end,onPeriod={period=it}) }
        }
        compose.onNodeWithText("130.00 ppm").assertIsDisplayed()
        compose.onNodeWithText("이전 관측값").assertIsNotEnabled()
        compose.onNodeWithText("7일").performClick()
        compose.onNodeWithText("이전 관측값").performClick()
        compose.onNodeWithText("100.00 ppm").assertIsDisplayed()
        compose.onNodeWithText("다음 관측값").performClick()
        compose.onNodeWithText("130.00 ppm").assertIsDisplayed()
        snapshot("chart")
    }
    @Test fun chartErrorHasReadRetryAndDoesNotRenderInventedData() {
        var retry:TrendPeriod?=null
        compose.setContent { GardenTheme { TrendChart(emptyList(),emptyList(),TrendPeriod.MONTH,end,
            error="연결 오류",onPeriod={retry=it}) } }
        compose.onNodeWithText("연결 오류").assertIsDisplayed()
        compose.onNodeWithText("그래프 다시 불러오기").performClick()
        compose.runOnIdle { assertEquals(TrendPeriod.MONTH,retry) }
        compose.onNodeWithContentDescription("실측 정상 TDS 중앙값 추세",substring=true).assertDoesNotExist()
    }
    @Test fun partialWindowAndSkeletonAreExplicitInsteadOfFakeMetrics() {
        compose.setContent { GardenTheme { Column {
            TrendChart(emptyList(),emptyList(),TrendPeriod.MONTH,end,limited=true)
            CareLoading()
        } } }
        compose.onNodeWithText("조회 한도에 도달",substring=true).assertIsDisplayed()
        compose.onNodeWithText("기록을 불러오고 있어요").assertExists()
        compose.onNodeWithText("0.00 ppm").assertDoesNotExist()
    }
    @Test fun smallScreenAtDoubleFontKeepsRealTemperatureAndExpandableMetadata() {
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,2f)) {
                GardenTheme { Box(Modifier.width(320.dp)) { SessionScreen(SessionDetail(row("large",end,"1000"),emptyList())) } }
            }
        }
        compose.onNodeWithTag("session-list").performScrollToNode(hasTestTag("metric-value"))
        compose.onNodeWithTag("metric-value").assertIsDisplayed()
        snapshot("result-double-font")
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("실측 평균 수온 · 16.13°C"))
        compose.onNodeWithText("실측 평균 수온 · 16.13°C").assertIsDisplayed()
        compose.onNodeWithText("세션 ID").assertDoesNotExist()
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("상세 정보 보기"))
        compose.onNodeWithText("상세 정보 보기").performClick()
        compose.onNodeWithTag("session-list").performScrollToNode(hasText("세션 ID"))
        compose.onNodeWithText("세션 ID").assertIsDisplayed()
    }
    @Test fun detailTimelineOpensActualMaintenanceMemoAndRetainsHistoryAccess() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val vm=MonitorViewModel(context.applicationContext as android.app.Application)
        val plant=Cultivation("fixture","바질","창가의 바질","2026-10-01T00:00:00Z",null,"ACTIVE","햇빛이 잘 드는 창가에서 돌보고 있어요.")
        val latest=row("last",end.minusSeconds(3600),"136.67")
        val state=MonitorState(detail=Overview(plant,end.minusSeconds(7200).toString(),latest),sessions=listOf(latest),
            maintenance=listOf(Maintenance("water","REPLACE","FULL",end.minusSeconds(7200).toString(),BigDecimal("1000"),"용기 정리")),
            trend=listOf(latest,row("old",end.minusSeconds(7200),"142.58")),trendEnd=end.toString(),device="esp32-bench")
        compose.setContent { GardenTheme { DetailScreen(state,vm) } }
        compose.onNodeWithTag("metric-value").assertIsDisplayed()
        snapshot("detail")
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("전체 물 교체"))
        snapshot("timeline")
        compose.onNodeWithText("전체 물 교체").performClick()
        compose.onNodeWithText("용기 정리").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("전체 측정 이력 보기"))
        compose.onNodeWithText("전체 측정 이력 보기").performClick()
        compose.onNodeWithText("측정 이력").assertIsDisplayed()
        compose.onNode(hasText("136.67 ppm") and hasAnyAncestor(hasTestTag("history-list"))).assertExists()
    }
    private fun snapshot(name:String) {
        val file=java.io.File("/data/local/tmp/ppossak-ui/aqua-$name.png")
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val local=java.io.File(context.getExternalFilesDir(null),"aqua-$name.png")
        local.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("mkdir -p /data/local/tmp/ppossak-ui")).use { it.readBytes() }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cp ${local.absolutePath} ${file.absolutePath}")).use { it.readBytes() }
    }
}
