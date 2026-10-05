package com.sonkkeut.app

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NativeAppTest {
    @get:Rule(order=0) val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO)
    @get:Rule(order=1) val compose = createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(compose.activity)[NativeAppModel::class.java]
    @Test fun pureKotlinAppLoadsModelsAndAutomaticallyConnectsMenu() {
        compose.waitUntil(60000) { model().ready && model().menu.isNotEmpty() }
        assertThrows(ClassNotFoundException::class.java) { Class.forName("com.facebook.react.ReactActivity") }
        assertTrue(model().connectionMessage.startsWith("연결됨"))
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        compose.onNodeWithText("설정").assertExists()
    }
    @Test fun cameraRunsAndPauseResumeDoesNotCrash() {
        compose.waitUntil(60000) { model().ready }
        compose.runOnUiThread { model().start() }
        compose.waitUntil(45000) { model().frames>=3 }
        compose.runOnUiThread { model().pause() }
        val before=model().frames
        Thread.sleep(1200)
        assertEquals(before,model().frames)
        compose.onNodeWithText("카메라 다시 시작").assertExists()
        compose.runOnUiThread { model().start() }
        compose.waitUntil(45000) { model().frames>=before+2 }
    }
    @Test fun nativeTypingAutomaticallyStartsGuidanceAndShowsOnlyOrderSummary() {
        compose.waitUntil(60000) { model().ready && model().menu.isNotEmpty() }
        compose.runOnUiThread { model().start(); model().toggleTextOrder() }
        val name=model().menu.first { !it.soldOut }.name
        val before=model().frames
        try { compose.waitUntil(45000) { model().frames>=before+2 } }
        catch(e: Exception) { throw AssertionError("input camera: frames=${model().frames}, before=$before, paused=${model().paused}, running=${model().running}, active=${model().cameraActive}, state=${model().flowState}, message=${model().message}, box=${compose.onNodeWithTag("mainCamera").fetchSemanticsNode().boundsInRoot}",e) }
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("$name 한 잔 포장해 주세요")
        compose.onNodeWithText("입력한 주문 확인").performScrollTo().performClick()
        compose.waitUntil(10000) { model().order!=null && !model().speechBusy }
        assertNotEquals("S3",model().flowState)
        compose.onNodeWithText("네, 이 주문으로 안내 시작").assertDoesNotExist()
        compose.onNodeWithText("주문 확인").assertExists()
        compose.onNodeWithText("기기 음성 인식으로 말하기").assertDoesNotExist()
        compose.onNodeWithText("설정").assertExists()
        assertFalse(model().paused)
    }
    @Test fun settingsPauseGuidanceAndServerIsLockedUntilEnd() {
        compose.waitUntil(60000) { model().ready && model().menu.isNotEmpty() }
        compose.runOnUiThread { model().start() }
        compose.onNodeWithText("설정").performClick()
        assertTrue(model().paused)
        val old=model().server
        compose.runOnUiThread { model().saveConnection("https://example.com","ABC123") }
        assertEquals(old,model().server)
        compose.onNodeWithText("환경 설정").assertExists()
        compose.onNodeWithText("서버·매장 설정").assertDoesNotExist()
        compose.onNodeWithText("가까이서 상세 글자 읽기").assertDoesNotExist()
        compose.onNodeWithText("‹  메인 화면으로").performClick()
        assertFalse(model().paused)
        compose.onNodeWithText("종료").performClick()
        assertFalse(model().running); assertNull(model().order); assertNull(model().screen)
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        compose.onAllNodesWithText("손끝길 시작").assertCountEquals(2)
    }
    @Test fun recommendationSelectionNeedsEditedOrderBeforeAutomaticGuidance() {
        compose.waitUntil(60000) { model().ready && model().menu.isNotEmpty() }
        compose.runOnUiThread { model().start(); model().submit("커피 두 잔 주세요") }
        compose.waitUntil(10000) { !model().speechBusy && model().recommendations.isNotEmpty() }
        assertNull(model().order)
        val selected=model().recommendations.first()
        compose.onNodeWithText("후보 선택 · ${selected.menu.name}").performScrollTo().performClick()
        assertNull(model().order); assertTrue(model().textOrderOpen); assertEquals(selected.menu.name,model().orderDraft)
        compose.onNode(hasSetTextAction()).performScrollTo().performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("${selected.menu.name} 두 잔 포장해 주세요")
        compose.onNodeWithText("입력한 주문 확인").performScrollTo().performClick()
        compose.waitUntil(10000) { !model().speechBusy && model().order!=null }
        assertEquals(2,model().order!!.items.single().qty); assertNotEquals("S3",model().flowState)
        compose.onNodeWithText("네, 이 주문으로 안내 시작").assertDoesNotExist()
    }
    @Test fun recognizedSpeechStartsOnlyForValidOrdersAndHidesRawTranscript() {
        compose.waitUntil(60000) { model().ready && model().menu.isNotEmpty() }
        val name=model().menu.first { !it.soldOut }.name
        compose.runOnUiThread { model().start(); model().submit("$name 한 잔 포장해 주세요",true) }
        compose.waitUntil(10000) { !model().speechBusy && model().order!=null }
        assertFalse(model().paused); assertNotEquals("S3",model().flowState)
        compose.onNodeWithText("주문 확인").assertExists()
        compose.onAllNodes(hasText("들은 문장:",substring=true)).assertCountEquals(0)
        compose.onAllNodes(hasText("보정 문장:",substring=true)).assertCountEquals(0)
        compose.runOnUiThread { model().submit("$name 열한 잔 포장해 주세요",true) }
        compose.waitUntil(10000) { !model().speechBusy }
        assertNull(model().order); assertEquals("S3",model().flowState)
    }
    @Test fun settingsAreSameBeforeStartAndRerecognitionIsOnlyADisabledPlaceholder() {
        compose.waitUntil(60000) { model().ready && model().menu.isNotEmpty() }
        compose.onNodeWithText("종료").assertDoesNotExist()
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("환경 설정").assertExists()
        compose.onNodeWithText("화면 설정").assertExists()
        compose.onNodeWithText("카메라 설정").assertExists()
        compose.onNodeWithText("음성 설정").assertExists()
        compose.onNodeWithText("‹  메인 화면으로").performClick()
        assertFalse(model().running)
        compose.runOnUiThread { model().start(); model().submit("${model().menu.first { !it.soldOut }.name} 한 잔 포장해 주세요") }
        compose.waitUntil(10000) { model().order!=null && !model().speechBusy }
        compose.onNodeWithText("음성 재인식").assertIsNotEnabled()
        compose.onNodeWithText("안내 계속").assertDoesNotExist()
        compose.onNodeWithText("안내 중지").assertDoesNotExist()
        compose.onNodeWithText("설정").performClick()
        compose.runOnUiThread { model().stopForBackground() }
        compose.onNodeWithText("‹  메인 화면으로").performClick()
        assertTrue(model().paused)
        compose.onNodeWithText("카메라 다시 시작").assertIsDisplayed()
        compose.onNodeWithText("주문 확인").assertDoesNotExist()
    }
    @Test fun detailReadingNeedsExplicitStartAndLateResultsCannotEscapeCancelOrNavigation() {
        compose.waitUntil(60000) { model().ready }
        compose.runOnUiThread { model().prepareDetailRead() }
        assertTrue(model().paused); assertTrue(model().detailMode); assertFalse(model().detailBusy)
        compose.onNodeWithText("상세 읽기 시작").performScrollTo().performClick()
        compose.waitUntil(20000) { !model().detailBusy }
        assertTrue(model().paused); assertNull(model().order); assertTrue(model().frame.isEmpty())
        compose.runOnUiThread { model().requestDetailRead(); model().cancelDetailRead() }
        compose.waitForIdle(); assertFalse(model().detailBusy)
        compose.runOnUiThread { model().requestDetailRead(); model().open("accessibility") }
        Thread.sleep(1000)
        assertFalse(model().detailMode); assertFalse(model().detailBusy); assertTrue(model().detailLines.isEmpty())
        assertEquals("accessibility",model().page)
    }
}
