package com.sonkkeut.tooling

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.sonkkeut.tooling.design.Scene
import com.sonkkeut.tooling.design.SignalDesignActivity
import com.sonkkeut.tooling.design.SignalStudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignalDesignTest {
    @get:Rule val compose = createAndroidComposeRule<SignalDesignActivity>()

    private fun show(scene: Scene, scale: Float = 1f) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { SignalStudio(scene, scale, true) }
        }
        compose.waitForIdle()
    }

    @Test fun startExitAndRecoveryAreReversible() {
        compose.onNodeWithText("손끝길 시작").performClick()
        compose.onNodeWithText(Scene.READY.headline).assertIsDisplayed()
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("안내 중지").performClick()
        compose.onNodeWithText("카메라 다시 시작").performClick()
        compose.onNodeWithText(Scene.READY.headline).assertIsDisplayed()
        compose.onNodeWithText("종료").performClick()
        compose.onNodeWithText("손끝길 시작").assertIsDisplayed()
    }

    @Test fun guidanceKeepsOrderAccessibleAndUnsupportedVoiceDisabled() {
        show(Scene.GUIDANCE)
        compose.onNodeWithText("음성 재인식").assertIsNotEnabled()
        compose.onNodeWithText("주문 보기").performClick()
        compose.onNodeWithText("주문 확인").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("재안내").assertIsDisplayed()
    }

    @Test fun primaryControlStaysAtSameLocationAcrossCameraStates() {
        show(Scene.GUIDANCE)
        val guideBounds = compose.onNodeWithText("재안내").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("화면 분석 중").performClick()
        val analyzingBounds = compose.onNodeWithText("재안내").fetchSemanticsNode().boundsInRoot
        assertEquals(guideBounds.center.x, analyzingBounds.center.x, 1f)
        assertEquals(guideBounds.center.y, analyzingBounds.center.y, 1f)
    }

    @Test fun largeTextKeepsExitAndPrimaryActionOnScreen() {
        show(Scene.GUIDANCE, 2f)
        compose.onNodeWithText("종료").assertIsDisplayed()
        compose.onNodeWithText("재안내").assertIsDisplayed()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithText("재안내").fetchSemanticsNode().boundsInRoot
        assertTrue(action.left >= root.left && action.right <= root.right && action.bottom <= root.bottom)
        compose.onNodeWithText("주문 보기").performScrollTo().performClick()
        compose.onNodeWithText("주문 확인").assertIsDisplayed()
    }

    @Test fun backgroundReturnRequiresExplicitRestart() {
        show(Scene.GUIDANCE)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("카메라 다시 시작").assertIsDisplayed()
        compose.onNodeWithText("카메라 다시 시작").performClick()
        compose.onNodeWithText(Scene.READY.headline).assertIsDisplayed()
    }

    @Test fun extremeTextKeepsFullInstructionAndOrderReachable() {
        show(Scene.GUIDANCE, 2.6f)
        compose.onNodeWithText("오른쪽으로\n조금만\n이동해요").assertIsDisplayed()
        compose.onNodeWithText("종료").assertIsDisplayed()
        compose.onNodeWithText("재안내").assertIsDisplayed()
        compose.onNodeWithText("주문 보기").performScrollTo().performClick()
        compose.onNodeWithText("주문 확인").assertIsDisplayed()
    }
}
