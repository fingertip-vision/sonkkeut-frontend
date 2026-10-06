package com.sonkkeut.app

import org.junit.Assert.*
import org.junit.Test

class SignalRecoveryTest {
    @Test fun repeatCannotSpeakIntoRecordingOrReplayPausedDirection() {
        assertFalse(canRepeatGuidance(true,true,false,true,false))
        assertFalse(canRepeatGuidance(true,true,false,false,true))
        assertFalse(canRepeatGuidance(true,true,true,false,false))
        assertTrue(canRepeatGuidance(true,true,false,false,false))
        assertTrue(canRepeatGuidance(true,false,true,false,false))
    }
    @Test fun permissionDenialSeparatesRequestAndSystemSettings() {
        assertEquals("카메라 권한 허용",signalRecovery(false,false,false,false).action)
        assertEquals("카메라 권한 설정",signalRecovery(false,true,true,false).action)
    }
    @Test fun cameraFailureHasOneRetryAndBackgroundHasManualRestart() {
        assertEquals("카메라 다시 연결",signalRecovery(true,false,true,false).action)
        assertEquals("카메라 다시 시작",signalRecovery(true,false,false,false).action)
    }
    @Test fun completedGuidanceNeverOffersCameraRestartOrPermission() {
        assertNull(signalRecovery(true,false,false,true).action)
        assertNull(signalRecovery(false,true,true,true).action)
    }
    @Test fun failedInitializationIsRetryableButLoadingIsNot() {
        assertTrue(WelcomeState(false,"AI 준비 실패: unavailable",true).canRetry)
        assertEquals("AI 다시 준비",WelcomeState(false,"AI 준비 실패",true).action)
        assertFalse(WelcomeState(false,"AI를 준비 중입니다",true).canRetry)
    }
    @Test fun accessibleDirectionIsIncludedAndExpiredPressCannotBeRead() {
        val frame=mapOf("found" to true,"tip" to listOf(.3,.4),"tip_conf" to .8,"tip_pointing" to .8,
            "event" to mapOf("type" to "direction","dir" to "right","target_id" to "a"))
        val current=SignalSnapshot(flow="S4",hasOrder=true,frame=frame,targetId="a",attempt=1,frameAttempt=1,frameAt=100,now=200)
        assertTrue(signalAccessibilityMessage(current,signalPresentation(current),"지금 누르세요").contains("오른쪽"))
        val expired=current.copy(now=3000)
        assertFalse(signalAccessibilityMessage(expired,signalPresentation(expired),"지금 누르세요").contains("누르세요"))
    }
}
