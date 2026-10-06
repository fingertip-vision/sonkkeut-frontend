package com.sonkkeut.app

import org.junit.Assert.*
import org.junit.Test

class SignalWelcomeStateTest {
    @Test fun readinessIsNeverInferredFromPermission() {
        val pending=WelcomeState(false,"",true)
        assertFalse(pending.ready)
        assertFalse(pending.status.contains("준비가 됐어요"))
        assertEquals("AI를 준비하지 못했어요. 다시 시도해 주세요",pending.copy(message="AI 준비 실패: test").status)
    }
    @Test fun deniedPermissionHasAnExplicitRecoveryRoute() {
        val denied=WelcomeState(true,"",false,true)
        assertEquals("카메라 권한 허용",denied.action)
        assertEquals("카메라 권한 설정",denied.copy(permissionSettingsRequired=true).action)
        val granted=denied.copy(cameraGranted=true,permissionSettingsRequired=true)
        assertEquals("손끝길 시작",granted.action)
        assertEquals("시작할 준비가 됐어요",granted.status)
    }
    @Test fun bindingDoesNotClaimFramesOrRecognition() {
        assertEquals("촬영 준비 · 카메라 연결 중",cameraConnectionLabel("",false))
        assertEquals("카메라 연결됨 · 영상 대기",cameraConnectionLabel("카메라 연결됨",false))
        assertEquals("촬영 중 · 화면 확인",cameraConnectionLabel("카메라 연결됨",true))
        assertEquals("카메라 연결을 확인해 주세요",cameraConnectionLabel("카메라 오류: test",true))
    }
}
