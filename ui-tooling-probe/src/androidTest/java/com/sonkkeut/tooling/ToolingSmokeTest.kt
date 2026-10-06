package com.sonkkeut.tooling

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.sonkkeut.app.UiToolingProbeActivity
import org.junit.Rule
import org.junit.Test

class ToolingSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<UiToolingProbeActivity>()

    @Test fun stateTransitionCompletesAndReturnsToReady() {
        compose.onNodeWithText("준비 완료").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("상태 전환 확인").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("전환 완료").assertIsDisplayed()
        compose.onNodeWithText("준비 완료").assertDoesNotExist()
        compose.onNodeWithText("준비 상태로 복귀").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("준비 완료").assertIsDisplayed()
        compose.onNodeWithText("전환 완료").assertDoesNotExist()
    }
}
