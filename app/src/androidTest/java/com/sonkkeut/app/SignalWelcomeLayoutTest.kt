package com.sonkkeut.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SignalWelcomeLayoutTest {
    @get:Rule val compose=createComposeRule()

    @Test fun startIsSingleAndDisabledUntilActualReadiness() {
        var ready by mutableStateOf(false)
        var starts=0
        compose.setContent { SignalWelcome(WelcomeState(ready,"",true),false,false,{starts++},{},{}) }
        compose.onAllNodesWithText("손끝길 시작").assertCountEquals(1)
        compose.onNodeWithTag("welcomeStart").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0,starts); ready=true }
        compose.onNodeWithTag("welcomeStart").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1,starts) }
        compose.onNodeWithText("종료").assertDoesNotExist()
    }
    @Test fun permissionRecoveryAndSettingsRemainOperable() {
        var starts=0; var settings=0; var repeats=0
        compose.setContent {
            SignalWelcome(WelcomeState(true,"",false,true,true),true,true,{starts++},{settings++},{repeats++})
        }
        compose.onNodeWithText("카메라 권한 설정").performClick()
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("재안내").performClick()
        compose.runOnIdle { assertEquals(1,starts); assertEquals(1,settings); assertEquals(1,repeats) }
        capture("stage1-light-fixture")
    }
    @Test fun largeTextKeepsStartAndSettingsReachable() {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2.6f)) {
                SignalWelcome(WelcomeState(true,"",true),false,false,{},{},{})
            }
        }
        compose.onNodeWithTag("welcomeStart").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("설정").assertIsDisplayed()
        val viewport=compose.onNodeWithTag("signalWelcome").fetchSemanticsNode().boundsInRoot
        val action=compose.onNodeWithTag("welcomeStart").fetchSemanticsNode().boundsInRoot
        assertTrue(action.left>=viewport.left && action.right<=viewport.right && action.bottom<=viewport.bottom)
        capture("stage1-large-2.6-fixture")
        assertNoScrolling()
    }

    @Test fun tallAndCompactPhoneRatiosKeepBrandingFixedWithoutScrollActions() {
        var height by mutableStateOf(760.dp)
        var scale by mutableStateOf(1f)
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                Box(Modifier.requiredSize(360.dp,height)) {
                    SignalWelcome(WelcomeState(true,"",true),false,false,{},{},{})
                }
            }
        }
        for((h,font) in listOf(760.dp to 1f,640.dp to 1f,760.dp to 2.6f,640.dp to 2.6f)) {
            compose.runOnIdle { height=h; scale=font }
            compose.onNodeWithTag("welcomeStart").assertIsDisplayed()
            compose.onNodeWithText("설정").assertIsDisplayed()
            compose.onNodeWithText("재안내").assertIsDisplayed()
            assertNoScrolling()
            val image=compose.onNodeWithTag("welcomeBranding",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("signalWelcome").performTouchInput { swipeUp() }
            assertEquals(image,compose.onNodeWithTag("welcomeBranding",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot)
        }
    }
    private fun assertNoScrolling() {
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy),useUnmergedTree=true).assertCountEquals(0)
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val output=File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir,"$name.png")
        FileOutputStream(output).use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
