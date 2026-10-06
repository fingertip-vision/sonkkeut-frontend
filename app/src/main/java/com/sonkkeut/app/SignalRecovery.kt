package com.sonkkeut.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

internal data class SignalRecoveryState(val title: String,val detail: String,val action: String?)
internal fun canRepeatGuidance(hasText: Boolean, running: Boolean, paused: Boolean, recording: Boolean, busy: Boolean) =
    hasText && !(running && paused) && !recording && !busy
internal fun signalRecovery(cameraGranted: Boolean, settingsRequired: Boolean, cameraError: Boolean, complete: Boolean): SignalRecoveryState = when {
    complete -> SignalRecoveryState("키오스크에서 결제해 주세요","안내가 끝났어요. 결제를 마친 뒤 상단 종료를 눌러 주세요.",null)
    !cameraGranted -> SignalRecoveryState("카메라 권한이 필요해요","키오스크 화면을 확인하려면 카메라 접근을 허용해 주세요.",if(settingsRequired) "카메라 권한 설정" else "카메라 권한 허용")
    cameraError -> SignalRecoveryState("카메라를 연결하지 못했어요","카메라를 사용하는 다른 앱을 닫고 다시 연결해 주세요.","카메라 다시 연결")
    else -> SignalRecoveryState("안내를 잠시 멈췄어요","휴대폰이 키오스크 화면을 향하도록 하고 다시 시작해 주세요.","카메라 다시 시작")
}

@Composable
internal fun SignalRecovery(state: SignalRecoveryState, enabled: Boolean, onAction: () -> Unit) {
    Surface(color=SignalInk,contentColor=SignalPaper) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("signalRecovery")
            .semantics { isTraversalGroup=true },verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(state.title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics { heading() })
            Text(state.detail,style=MaterialTheme.typography.bodyLarge)
            state.action?.let { label -> Button(onClick=onAction,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=64.dp),
                colors=ButtonDefaults.buttonColors(containerColor=SignalGold,contentColor=SignalInk)) { Text(label) } }
        }
    }
}
