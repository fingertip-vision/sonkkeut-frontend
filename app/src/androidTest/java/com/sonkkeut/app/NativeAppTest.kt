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
        compose.onNodeWithText("메뉴·설정").performClick()
        compose.onNodeWithText("음성·직접 입력 주문").performClick()
        val name=model().menu.first { !it.soldOut }.name
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("$name 한 잔 포장해 주세요")
        compose.onNodeWithText("입력한 주문 확인").performScrollTo().performClick()
        compose.waitUntil(10000) { model().order!=null && !model().speechBusy }
        assertEquals("S3",model().flowState)
        compose.onNodeWithText("네, 이 주문으로 안내 시작").performScrollTo().performClick()
        compose.onNodeWithText("메뉴·설정").assertExists()
        assertFalse(model().paused)
    }
    @Test fun knowledgeEditorAndReceiptReaderAreReachableAndReaderStopsOnExit() {
        compose.waitUntil(60000) { model().ready && model().menu.isNotEmpty() }
        compose.onNodeWithText("메뉴·설정").performClick()
        compose.onNodeWithText("이 매장 메뉴 검색 정보").performScrollTo().performClick()
        compose.onNodeWithText(model().menu.first().name+if(model().menu.first().soldOut) " · 품절" else "").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onNodeWithText("‹ 뒤로").performScrollTo().performClick()
        compose.onNodeWithText("결제 완료·주문 번호 읽기").performScrollTo().performClick()
        compose.waitUntil(10000) { model().readingReceipt }
        assertEquals("S6",model().orderFlow.state)
        compose.onNodeWithText("메뉴·설정").performClick()
        assertFalse(model().readingReceipt)
        compose.runOnUiThread { model().pause() }
    }
}
