package com.sonkkeut.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

class SignalStage3Test {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val store="stage3_settings_fixture"
    @Before fun prepare() { context.getSharedPreferences(store,0).edit().clear().commit() }
    @After fun cleanup() { context.getSharedPreferences(store,0).edit().clear().commit() }

    @Test fun settingsSelectionAndSingleSwitchSemanticsPersist() {
        val preferences=AccessibilityPreferences(context,store)
        compose.setContent { SignalSessionTheme(preferences.light) { SignalSettings(preferences,{}) } }
        capture("stage3-settings-dark-fixture")
        compose.onNodeWithContentDescription("글자 크기, 더 크게").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("글자 크기, 기본").assertIsNotSelected()
        compose.onNodeWithContentDescription("화면 색상, 밝게").performScrollTo().performClick().assertIsSelected()
        capture("stage3-settings-light-fixture")
        val voice=compose.onNodeWithText("음성 안내")
        voice.performScrollTo().assertIsToggleable().assertIsOn().performClick().assertIsOff()
        voice.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Switch))
        compose.onAllNodes(hasText("음성 안내") and hasClickAction()).assertCountEquals(1)
        compose.onNodeWithText("진동 안내").performScrollTo().performClick().assertIsOff()
        compose.onNodeWithContentDescription("안내 속도, 느리게").performScrollTo().performClick().assertIsSelected()
        capture("stage3-settings-voice-fixture")
        val saved=AccessibilityPreferences(context,store)
        assertEquals(2,saved.textSize); assertTrue(saved.light); assertFalse(saved.voice); assertFalse(saved.vibration); assertEquals(0,saved.speed)
        compose.onNodeWithText("‹  메인 화면으로").assertIsDisplayed()
    }

    @Test fun largeTextSettingsRemainReachableWithoutShrinkingLabels() {
        val preferences=AccessibilityPreferences(context,store)
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2.6f)) {
                SignalSessionTheme(false) { SignalSettings(preferences,{}) }
            }
        }
        capture("stage3-settings-large-fixture")
        compose.onNodeWithContentDescription("글자 크기, 더 크게").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("가까이서 화면 전체 담기").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("안내 속도, 빠르게").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("‹  메인 화면으로").assertIsDisplayed()
        capture("stage3-settings-large-voice-fixture")
    }

    @Test fun recoveryActionIsUniqueAndCompletionCannotRestart() {
        var complete by mutableStateOf(false); var retries=0
        compose.setContent { SignalSessionTheme(false) { Box(Modifier.fillMaxWidth().height(400.dp)) {
            SignalRecovery(signalRecovery(true,false,true,complete),true,{retries++})
        } } }
        compose.onAllNodesWithText("카메라 다시 연결").assertCountEquals(1)
        compose.onNodeWithText("카메라 다시 연결").performClick()
        compose.runOnIdle { assertEquals(1,retries) }
        capture("stage3-camera-recovery-fixture")
        compose.runOnIdle { complete=true }
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        capture("stage3-complete-fixture")
    }

    @Test fun aiFailureOffersRetryWithoutCallingStart() {
        var starts=0; var retries=0
        compose.setContent { SignalWelcome(WelcomeState(false,"AI 준비 실패: 테스트",true),false,false,{starts++},{},{},{retries++}) }
        compose.onNodeWithText("AI 다시 준비").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(0,starts); assertEquals(1,retries) }
        capture("stage3-ai-recovery-fixture")
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        File(context.externalCacheDir,"$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
