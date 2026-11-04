package com.juca.hydroponic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.juca.hydroponic.data.*
import com.juca.hydroponic.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class HistoryGestureTest {
    @get:Rule val compose=createComposeRule()
    private val time="2026-10-30T01:00:00Z"
    @Test fun careCardDragScrollsListWithoutMovingSheet() {
        var selections=0
        val rows=(0 until 50).map { Maintenance("m$it","TOP_UP",null,time,BigDecimal.ONE,"note") }
        compose.setContent { GardenTheme { CareRecordsSheet(rows,0,true,false,null,{},{},{selections++},{}) } }
        checkCardGestures("care-history-list")
        compose.runOnIdle { assertEquals("Dragging must not open a record",0,selections) }
    }
    @Test fun measurementCardDragScrollsListWithoutMovingSheet() {
        var selections=0
        val rows=(0 until 50).map { SessionSummary("s$it","fixture","c",time,"MEASURED","NORMAL",60,50,
            BigDecimal("120"),null,"fixture","ADS1115") }
        compose.setContent { GardenTheme { CareHistorySheet(rows,true,false,{},{selections++},{}) } }
        checkCardGestures("history-list")
        compose.runOnIdle { assertEquals("Dragging must not open a measurement",0,selections) }
    }
    private fun checkCardGestures(tag:String) {
        compose.waitForIdle()
        val list=compose.onNodeWithTag(tag)
        fun top()=list.fetchSemanticsNode().boundsInRoot.top
        fun position()=list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val anchor=top();val initial=position()
        repeat(4) {
            val bounds=list.fetchSemanticsNode().boundsInRoot
            // Start inside a real clickable card, not in the side gutter or with ScrollTo semantics.
            val card=compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(tag)))
                .fetchSemanticsNodes().first { n->n.boundsInRoot.height>60f && n.boundsInRoot.top>=bounds.top && n.boundsInRoot.bottom<=bounds.bottom }
            val start=card.boundsInRoot.center-Offset(bounds.left,bounds.top)
            list.performTouchInput { swipe(start,Offset(start.x,8f),600) }
            compose.waitForIdle()
            assertEquals("The sheet must stay anchored during card swipes",anchor,top(),2f)
        }
        assertTrue("Card swipes must advance actual list content",position()>initial)
        val advanced=position()
        repeat(3) {
            val bounds=list.fetchSemanticsNode().boundsInRoot
            val card=compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(tag)))
                .fetchSemanticsNodes().first { n->n.boundsInRoot.height>60f && n.boundsInRoot.top>=bounds.top && n.boundsInRoot.bottom<=bounds.bottom }
            val start=card.boundsInRoot.center-Offset(bounds.left,bounds.top)
            list.performTouchInput { swipe(start,Offset(start.x,bounds.height-8f),600) }
            compose.waitForIdle()
            assertEquals("Reverse card swipes must not drag the sheet",anchor,top(),2f)
        }
        assertTrue("Reverse swipes must return toward earlier records",position()<advanced)
    }
}
