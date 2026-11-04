package com.juca.hydroponic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import com.juca.hydroponic.data.Cultivation
import com.juca.hydroponic.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CultivationHeaderTest {
    @get:Rule val compose=createComposeRule()
    private val c=Cultivation("c","바질","춘식이","2026-06-13T06:00:00Z",null,"ACTIVE","수경 재배로 전환 완료!")
    @Test fun activeHeaderUsesOverflowMenuForEditAndEnd() {
        var edits=0;var ends=0
        compose.setContent { GardenTheme { CultivationHeader(c,140,true,{edits++},{ends++}) } }
        compose.onNodeWithText("춘식이").assertIsDisplayed()
        compose.onNodeWithText("나의 식물 일지").assertDoesNotExist()
        compose.onNodeWithText("재배 정보 편집").assertDoesNotExist()
        compose.onNodeWithText("재배 종료").assertDoesNotExist()
        compose.onNodeWithContentDescription("재배 메뉴").performClick()
        compose.onNodeWithText("재배 정보 편집").performClick()
        compose.onNodeWithContentDescription("재배 메뉴").performClick()
        compose.onNodeWithText("재배 종료").performClick()
        compose.runOnIdle { assertEquals(1,edits);assertEquals(1,ends) }
    }
    @Test fun endedHeaderKeepsEditButDoesNotOfferEndingAgain() {
        compose.setContent { GardenTheme { CultivationHeader(c.copy(status="ENDED",endedAt="2026-10-30T00:00:00Z"),140,true,{},{}) } }
        compose.onNodeWithContentDescription("재배 메뉴").performClick()
        compose.onNodeWithText("재배 정보 편집").assertIsEnabled()
        compose.onNodeWithText("재배 종료").assertDoesNotExist()
    }
    @Test fun narrowDoubleFontHeaderExpandsLongMemoAndProtectsPendingMenu() {
        val note="돌봄 메모를 길게 적어도 전체 내용을 읽을 수 있어요. ".repeat(20)
        compose.setContent {
            val d=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(d,2f)) {
                GardenTheme { Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    CultivationHeader(c.copy(memo=note),140,false,{fail()},{fail()})
                } }
            }
        }
        compose.onNodeWithContentDescription("재배 메뉴").assertIsNotEnabled()
        compose.onNodeWithText("메모 더 보기").performScrollTo().performClick()
        compose.onNodeWithText(note).assertExists()
        compose.onNodeWithText("메모 접기").performScrollTo().performClick()
        compose.onNodeWithText("메모 더 보기").assertIsDisplayed()
    }
}
