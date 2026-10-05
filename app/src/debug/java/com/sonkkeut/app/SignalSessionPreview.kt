package com.sonkkeut.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

/** Editor fixtures only. No camera, microphone, network or AI side effects. */
@Preview(name="2차 · 음성 주문",widthDp=393,heightDp=873,showBackground=true)
@Composable
private fun SignalVoicePreview() = SessionPreview(false,true)

@Preview(name="2차 · 밝은 화면",widthDp=393,heightDp=873,showBackground=true)
@Composable
private fun SignalLightPreview() = SessionPreview(true,false)

@Composable
private fun SessionPreview(light: Boolean, recording: Boolean) {
    SignalSessionTheme(light) {
        SignalSession(signalPresentation(SignalSnapshot(recording=recording,busy=recording)),
            if(recording) "주문을 말씀해 주세요." else "카메라로 키오스크 전체 화면을 비춰 주세요.",true,{},{},{},
            camera={ modifier -> Box(modifier.background(Color(0xFF080F1E)),contentAlignment=Alignment.Center) { Text("미리보기 · 실제 영상 아님",color=Color.White) } },
            controls={ if(recording) SignalSpeechControls(true,true,false,true,true,{},{},{},{},{}) })
    }
}
