package com.sonkkeut.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NativeOrderLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun smallPortraitShowsWholeOrderWithoutScrolling()=checkLayout(1f)
    @Test fun largeFontLongOrderCanBeReadInPagesWithoutClipping()=checkLayout(2.6f)

    private fun checkLayout(fontScale: Float) {
        val order=NativeOrder((1..12).map { NativeOrderItem("따뜻한 카페라떼 메뉴 $it",2,4500,"hot","라지") },"포장")
        compose.setContent {
            val keyboard=LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) { keyboard?.hide() }
            val current=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(current.density,fontScale)) {
                MaterialTheme {
                    Column(Modifier.width(360.dp).height(640.dp).testTag("viewport").safeDrawingPadding().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        NativeHomeToolbar(true,{},{})
                        Text("키오스크 화면을 비춰 주세요",style=if(fontScale>1.6f) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,maxLines=if(fontScale>1.6f) 1 else 2)
                        Box(Modifier.fillMaxWidth().weight(if(fontScale>1.6f) .08f else .15f).testTag("camera"))
                        NativeOrderConfirmation(order,"담기 확인 0 / 24개",Modifier.fillMaxWidth().weight(if(fontScale>1.6f) .92f else .85f))
                        NativeHomeActions(true,true,true,{},{})
                    }
                }
            }
        }
        assertTrue(listOf("viewport","homeToolbar","homeActions","orderConfirmation","orderTitle","orderTextRegion","orderNavigation").joinToString { "$it=${compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot}" },compose.onNodeWithTag("orderPage").fetchSemanticsNode().boundsInRoot.height>0)
        val capture=File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir,"order-layout-$fontScale.png")
        FileOutputStream(capture).use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        compose.onNodeWithText("주문 확인").assertIsDisplayed()
        val camera=compose.onNodeWithTag("camera").fetchSemanticsNode().boundsInRoot
        val panel=compose.onNodeWithTag("orderConfirmation").fetchSemanticsNode().boundsInRoot
        assertTrue(camera.height>0); assertTrue(camera.height<panel.height)
        compose.onNodeWithText("음성 재인식").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("설정").assertIsDisplayed()
        compose.onNodeWithText("종료").assertIsDisplayed()
        assertTrue(compose.onNodeWithText("설정").fetchSemanticsNode().boundsInRoot.left<compose.onNodeWithText("종료").fetchSemanticsNode().boundsInRoot.left)
        val seen=StringBuilder()
        repeat(100) {
            val results=mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("orderPage").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val text=results.single()
            val bounds=compose.onNodeWithTag("orderPage").fetchSemanticsNode().boundsInRoot
            assertFalse("text=${text.layoutInput.text.text} size=${text.size} bounds=$bounds widthOverflow=${text.didOverflowWidth} heightOverflow=${text.didOverflowHeight} constraints=${text.layoutInput.constraints}",text.hasVisualOverflow)
            assertTrue(text.getLineBottom(text.lineCount-1)<=bounds.height+1)
            assertTrue(bounds.bottom<=panel.bottom+1)
            seen.append(text.layoutInput.text.text)
            if(isNotEnabled().matches(compose.onNodeWithText("다음").fetchSemanticsNode())) {
                assertEquals(order.confirmation()+" 담기 확인 0 / 24개",seen.toString())
                return
            }
            compose.onNodeWithText("다음").assertIsDisplayed().performClick()
        }
        fail("order pagination did not reach the last page")
    }
}
