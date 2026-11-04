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

class CareEditingScreensTest {
    @get:Rule val compose=createComposeRule()
    private val c=Cultivation("c","바질","거실 바질","2026-10-01T00:00:00Z",null,"ACTIVE","원래 메모")
    private val m=Maintenance("m","REPLACE","FULL","2026-10-02T01:00:35.123456Z",BigDecimal("1000"),"원래 기록")
    @Test fun profileEditsBothNamesAndClearsMemoAtLargeFont() {
        var saved:List<String>?=null
        compose.setContent {
            val d=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(d,2f)) {
                GardenTheme { Box(Modifier.width(320.dp)) { ProfileEditForm(c,false,false,null,{},{},{},{p,n,memo->saved=listOf(p,n,memo)}) } }
            }
        }
        compose.onNodeWithText("식물명").performScrollTo().performTextReplacement("민트")
        compose.onNodeWithText("재배 별명").performScrollTo().performTextReplacement("창가 민트")
        compose.onNodeWithText("메모 · 선택").performScrollTo().performTextClearance()
        compose.onNodeWithText("변경 저장").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("민트","창가 민트",""),saved) }
    }
    @Test fun conflictRefreshPreservesDraftAndRequiresExplicitDecision() {
        var server by mutableStateOf(c);var conflict by mutableStateOf(true);var latest by mutableStateOf<Cultivation?>(null)
        var saved:String?=null
        compose.setContent { GardenTheme { ProfileEditForm(server,false,conflict,latest,
            {latest=c.copy(name="서버 최신 이름",version=1)}, {server=latest!!;conflict=false}, {}, {_,name,_->saved=name}) } }
        compose.onNodeWithText("재배 별명").performScrollTo().performTextReplacement("작성 중인 이름")
        compose.onNodeWithText("변경 저장").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("최신 내용 확인").performScrollTo().performClick()
        compose.onNodeWithText("서버 최신 이름",substring=true).assertExists()
        compose.onNodeWithText("내 입력으로 수정 계속").performScrollTo().performClick()
        compose.onNodeWithText("변경 저장").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("작성 중인 이름",saved) }
    }
    @Test fun maintenanceEditorPreservesExactTimeAndOptionalUnknownAmount() {
        var saved:List<String?>?=null
        compose.setContent { GardenTheme { MaintenanceEditForm(m,false,false,null,{},{},{},{type,scope,time,amount,memo->saved=listOf(type,scope,time,amount,memo)}) } }
        compose.onNodeWithText("물 보충").performClick()
        compose.onNodeWithText("작업량 ml · 선택").performScrollTo().performTextClearance()
        compose.onNodeWithText("메모 · 선택").performScrollTo().performTextReplacement("수정 메모")
        compose.onNodeWithText("변경 저장").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("TOP_UP",null,m.occurredAt,"","수정 메모"),saved) }
    }
    @Test fun pendingWriteDisablesSubmissionAndCancel() {
        compose.setContent { GardenTheme { ProfileEditForm(c,true,false,null,{},{},{},{_,_,_->fail("disabled")}) } }
        compose.onNodeWithText("변경 저장").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("취소").performScrollTo().assertIsNotEnabled()
    }
    @Test fun historyHasBoundedPageNavigationAndUnknownAmount() {
        var previous=0;var next=0
        compose.setContent { GardenTheme { CareRecordsSheet(listOf(m.copy(amountMl=null)),50,false,false,null,{previous++},{next++},{},{}) } }
        compose.onNodeWithText("작업량 미상").assertIsDisplayed()
        compose.onNodeWithTag("care-history-list").performScrollToNode(hasText("이전 50개"))
        compose.onNodeWithText("이전 50개").performClick()
        compose.onNodeWithText("다음 50개").assertIsNotEnabled()
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val file=java.io.File(context.getExternalFilesDir(null),"care-history-component.png")
        file.outputStream().use { compose.onNodeWithTag("care-history-list").captureToImage().asAndroidBitmap()
            .compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        val shell=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(shell.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/care-history-component.png")).use{it.readBytes()}
        compose.runOnIdle { assertEquals(1,previous);assertEquals(0,next) }
    }
}
