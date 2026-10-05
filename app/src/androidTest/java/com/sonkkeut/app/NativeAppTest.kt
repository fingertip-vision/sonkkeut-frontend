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
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO)
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(compose.activity)[NativeAppModel::class.java]
    @Test fun pureKotlinAppLoadsModelsAndAutomaticallyConnectsMenu() {
        compose.waitUntil(60000) { model().ready && model().menu.isNotEmpty() }
        assertThrows(ClassNotFoundException::class.java) { Class.forName("com.facebook.react.ReactActivity") }
        assertTrue(model().connectionMessage.startsWith("연결됨"))
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        compose.onNodeWithText("메뉴·설정").assertExists()
    }
    @Test fun cameraRunsAndPauseResumeDoesNotCrash() {
        compose.waitUntil(60000) { model().ready }
        compose.runOnUiThread { model().start() }
        compose.waitUntil(45000) { model().frames>=3 }
        compose.runOnUiThread { model().pause() }
        val before=model().frames
        Thread.sleep(1200)
        assertEquals(before,model().frames)
        compose.onNodeWithText("안내 계속").assertExists()
        compose.runOnUiThread { model().start() }
        compose.waitUntil(45000) { model().frames>=before+2 }
    }
    @Test fun nativeTypingRequiresConfirmationAndReturnsToCamera() {
        compose.waitUntil(60000) { model().ready && model().menu.isNotEmpty() }
        compose.runOnUiThread { model().start(); model().toggleTextOrder() }
        val name=model().menu.first { !it.soldOut }.name
        val before=model().frames
        compose.waitUntil(45000) { model().frames>=before+2 }
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("$name 한 잔 포장해 주세요")
        compose.onNodeWithText("입력한 주문 확인").performScrollTo().performClick()
        compose.waitUntil(10000) { model().order!=null && !model().speechBusy }
        assertEquals("S3",model().flowState)
        compose.onNodeWithText("네, 이 주문으로 안내 시작").performScrollTo().performClick()
        compose.onNodeWithText("메뉴·설정").assertExists()
        assertFalse(model().paused)
    }
    @Test fun settingsPauseGuidanceAndServerIsLockedUntilEnd() {
        compose.waitUntil(60000) { model().ready && model().menu.isNotEmpty() }
        compose.runOnUiThread { model().start() }
        compose.onNodeWithText("메뉴·설정").performClick()
        assertTrue(model().paused)
        val old=model().server
        compose.runOnUiThread { model().saveConnection("https://example.com","ABC123") }
        assertEquals(old,model().server)
        compose.onNodeWithText("주문 안내 종료").performScrollTo().performClick()
        assertFalse(model().running); assertNull(model().order); assertNull(model().screen)
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        compose.onAllNodesWithText("손끝길 시작").assertCountEquals(2)
    }
    @Test fun recommendationSelectionRequiresANewEditedOrderAndConfirmation() {
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
        assertEquals(2,model().order!!.items.single().qty); assertEquals("S3",model().flowState)
        compose.onNodeWithText("네, 이 주문으로 안내 시작").performScrollTo().assertExists()
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
        compose.runOnUiThread { model().requestDetailRead(); model().open("menu") }
        Thread.sleep(1000)
        assertFalse(model().detailMode); assertFalse(model().detailBusy); assertTrue(model().detailLines.isEmpty())
        assertEquals("menu",model().page)
    }
}
