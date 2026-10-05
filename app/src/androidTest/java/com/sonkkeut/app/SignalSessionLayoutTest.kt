package com.sonkkeut.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic state fixtures, not camera / microphone / hand-recognition evidence. */
class SignalSessionLayoutTest {
    @get:Rule val compose=createComposeRule()
    private val sample=NativeOrder(listOf(NativeOrderItem("아메리카노",1,3000,"hot","")),"포장")
    private val frame: Map<String,Any?> = mapOf("found" to true,"tip" to listOf(.5,.5),"tip_conf" to .9,"tip_pointing" to .9,
        "event" to mapOf("type" to "direction","dir" to "right","target_id" to "coffee"))
    private val movement=SignalSnapshot(flow="S4",hasOrder=true,frame=frame,targetId="coffee",attempt=1,frameAttempt=1,frameAt=1000,now=1100)

    @Test fun directionChangesDoNotMoveCameraFooterOrRemountCamera() {
        var state by mutableStateOf(movement)
        var mounts=0
        compose.setContent { SignalSessionTheme(false) {
            SignalSession(signalPresentation(state),"음성과 진동 안내를 따라 주세요.",true,{},{},{},camera={ modifier ->
                DisposableEffect(Unit) { mounts++; onDispose { } }
                PlaceholderCamera(modifier)
            },order={SignalOrderCard(sample,"담기 확인 0 / 1개",{})},controls={})
        } }
        val camera=compose.onNodeWithTag("fixtureCamera").fetchSemanticsNode().boundsInRoot
        val footer=compose.onNodeWithTag("homeActions").fetchSemanticsNode().boundsInRoot
        capture("stage2-direction-fixture")
        compose.runOnIdle { state=state.copy(frame=frame+("event" to mapOf("type" to "no_hand","target_id" to "coffee"))) }
        compose.onNodeWithText("검지를 화면 앞에\n보여 주세요").assertExists()
        assertEquals(camera,compose.onNodeWithTag("fixtureCamera").fetchSemanticsNode().boundsInRoot)
        assertEquals(footer,compose.onNodeWithTag("homeActions").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(1,mounts) }
        capture("stage2-no-hand-fixture")
        compose.runOnIdle { state=movement.copy(now=3000) }
        compose.onNodeWithText("오른쪽으로\n천천히 이동해요").assertDoesNotExist()
        compose.runOnIdle { state=movement.copy(frame=frame+("event" to mapOf("type" to "press","target_id" to "coffee"))) }
        compose.onNodeWithText("지금\n눌러 주세요").assertExists()
        capture("stage2-press-fixture")
        compose.runOnIdle { state=movement.copy(flow="S5") }
        compose.onNodeWithText("화면 반응을\n확인하고 있어요").assertExists()
        capture("stage2-result-fixture")
        compose.runOnIdle { state=movement.copy(flow="SE") }
        compose.onNodeWithText("화면을 다시\n확인해 주세요").assertExists()
        capture("stage2-error-fixture")
    }

    @Test fun voiceControlsHaveDistinctRecordProcessAndCancelActions() {
        var recording by mutableStateOf(true)
        var busy by mutableStateOf(true)
        var finish=0; var cancel=0
        compose.setContent { SignalSessionTheme(false) {
            SignalSession(signalPresentation(SignalSnapshot(recording=recording,busy=busy)),"주문을 말씀해 주세요.",false,{},{},{},camera={PlaceholderCamera(it)},controls={
                SignalSpeechControls(recording,busy,false,true,true,{},{},{finish++; recording=false},{cancel++; busy=false},{})
            })
        } }
        compose.onNodeWithText("말하기 완료").assertIsDisplayed()
        compose.onNodeWithText("음성 재인식").assertIsNotEnabled()
        capture("stage2-listening-fixture")
        compose.onNodeWithText("말하기 완료").performClick()
        compose.onNodeWithText("말하기 완료").assertDoesNotExist()
        compose.onNodeWithText("주문을\n확인하고 있어요").assertExists()
        capture("stage2-processing-fixture")
        compose.onNodeWithText("음성 작업 취소").performClick()
        compose.runOnIdle { assertEquals(1,finish); assertEquals(1,cancel) }
        compose.onNodeWithText("주문 말하기").assertIsDisplayed()
    }

    @Test fun lightOrderPopupPreservesCameraAndCanReopen()=orderLayout(1.15f,true)
    @Test fun largeOrderPagesRemainReadableAndReachable()=orderLayout(2.6f,false)

    private fun orderLayout(scale: Float, light: Boolean) {
        val order=if(scale>2f) NativeOrder((1..4).map { NativeOrderItem("따뜻한 카페라떼 $it",2,4500,"hot","라지") },"포장") else sample
        var open by mutableStateOf(false)
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) { SignalSessionTheme(light) {
                SignalSession(signalPresentation(movement),"주문을 확인했어요.",true,{},{},{},camera={ modifier ->
                    BoxWithConstraints(modifier.testTag("fixtureCamera").background(Color(0xFF080F1E))) {
                        Text("테스트 영상 영역",color=Color.White,modifier=Modifier.align(Alignment.Center))
                        if(open) SignalOrderPanel(order,"담기 확인 0 / ${order.items.sumOf { it.qty }}개",
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max=maxHeight*.5f),{open=false})
                    }
                },order={ SignalOrderCard(order,"담기 확인 0 / ${order.items.sumOf { it.qty }}개",{open=true}) },controls={})
            } }
        }
        val cameraSize=compose.onNodeWithTag("fixtureCamera").getUnclippedBoundsInRoot().let { (it.right-it.left) to (it.bottom-it.top) }
        if(scale>2f) compose.onNodeWithTag("signalOrderCard").performScrollTo()
        compose.onNodeWithTag("signalOrderCard").performClick()
        compose.onNodeWithText("주문 확인").assertIsDisplayed()
        val afterSize=compose.onNodeWithTag("fixtureCamera").getUnclippedBoundsInRoot().let { (it.right-it.left) to (it.bottom-it.top) }
        assertEquals(cameraSize.first.value,afterSize.first.value,.1f)
        assertEquals(cameraSize.second.value,afterSize.second.value,.1f)
        compose.onNodeWithText("설정").assertIsDisplayed()
        compose.onNodeWithText("재안내").assertIsDisplayed()
        capture(if(light) "stage2-order-light-fixture" else "stage2-order-large-fixture")
        val seen=StringBuilder()
        repeat(100) {
            val result=mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("orderPage").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
            assertFalse("order text clipped: ${result.single()}",result.single().hasVisualOverflow)
            val bounds=compose.onNodeWithTag("orderPage").fetchSemanticsNode().boundsInRoot
            assertTrue(result.single().getLineBottom(result.single().lineCount-1)<=bounds.height+1)
            seen.append(result.single().layoutInput.text.text)
            if(compose.onAllNodesWithText("다음").fetchSemanticsNodes().isEmpty() || isNotEnabled().matches(compose.onNodeWithText("다음").fetchSemanticsNode())) {
                assertEquals(order.confirmation()+" 담기 확인 0 / ${order.items.sumOf { item -> item.qty }}개",seen.toString())
                compose.onNodeWithText("닫기").performClick()
                compose.onNodeWithTag("orderConfirmation").assertDoesNotExist()
                if(scale>2f) compose.onNodeWithTag("signalOrderCard").performScrollTo()
                compose.onNodeWithTag("signalOrderCard").performClick()
                compose.onNodeWithText("주문 확인").assertIsDisplayed()
                return
            }
            compose.onNodeWithText("다음").assertIsDisplayed().performClick()
        }
        fail("pagination did not finish")
    }
    @Composable private fun PlaceholderCamera(modifier: Modifier) {
        Box(modifier.testTag("fixtureCamera").background(Color(0xFF080F1E)),contentAlignment=Alignment.Center) { Text("테스트 영상 영역",color=Color.White) }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir,"$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
