package com.sonkkeut.app

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After

/** Real model / CameraX entry, not an assertion of kiosk or hand recognition. */
class SignalEntryIntegrationTest {
    @get:Rule(order=0) val permission: GrantPermissionRule=GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(compose.activity)[NativeAppModel::class.java]
    @After fun releaseNativeSessionBeforeNextTest() {
        compose.activityRule.scenario.close()
        awaitNativeEngineRelease()
    }

    @Test fun actualStartConnectsCameraAndExitRestoresWelcome() {
        compose.waitUntil(60000) { model().ready }
        compose.onNodeWithTag("welcomeStart").performClick()
        compose.runOnIdle { assertTrue(model().running); assertTrue(model().cameraActive) }
        compose.onNodeWithTag("mainCamera").assertIsDisplayed()
        compose.waitUntil(15000) {
            compose.onAllNodes(hasText("카메라 연결됨 · 영상 대기") or hasText("촬영 중 · 화면 확인"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("signalWelcome").assertDoesNotExist()
        compose.onNodeWithText("종료").performClick()
        compose.onNodeWithTag("signalWelcome").assertIsDisplayed()
        compose.runOnIdle { assertFalse(model().running); assertFalse(model().cameraActive) }
    }
    @Test fun backgroundReturnStillRequiresExplicitRestart() {
        compose.waitUntil(60000) { model().ready }
        compose.onNodeWithTag("welcomeStart").performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle { assertTrue(model().paused); assertFalse(model().cameraActive) }
        compose.onNodeWithText("카메라 다시 시작").performClick()
        compose.runOnIdle { assertFalse(model().paused); assertTrue(model().cameraActive) }
    }
}
