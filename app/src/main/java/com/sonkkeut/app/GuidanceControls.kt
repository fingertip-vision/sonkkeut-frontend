package com.sonkkeut.app

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun GuidanceControls(output: GuidanceOutput, enabled: Boolean, onStop: () -> Unit, allowTests: Boolean = true) {
    val context = LocalContext.current
    var settingsError by remember { mutableStateOf("") }
    var showTest by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("음성·진동 안내", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(output.speechStatus)
        Text(output.outputStatus, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(if (output.hasVibrator) "진동 출력 지원" else "진동 미지원 기기입니다. 화면과 음성 안내를 사용합니다.")
        OutlinedButton(onClick = { output.repeat() }, enabled = enabled && output.lastText != null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("마지막 안내 다시 듣기") }
        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("안내와 카메라 멈추기") }
        if (!output.ready) {
            TextButton(onClick = {
                try { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                catch (_: ActivityNotFoundException) { settingsError = "음성 설정 화면을 열 수 없습니다. 기기 설정에서 음성 출력을 찾아 주세요." }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("기기 음성 출력 설정 열기") }
            if (settingsError.isNotEmpty()) Text(settingsError)
        }
        TextButton(onClick = { showTest = !showTest }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (showTest) "테스트 안내 닫기" else "테스트 안내 펼치기") }
        if (showTest && allowTests) {
            Text("아래 버튼은 만들어 둔 입력을 재생합니다. 실제 키오스크 인식·주문·누름 지시가 아닙니다.")
            OutlinedButton(onClick = { output.announce("test-far", "테스트 안내입니다. 오른쪽 위로 이동해 주세요.", direction = true, vibration = GuidancePhrases.vibration(false)) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("방향·진동 테스트") }
            OutlinedButton(onClick = { output.announce("test-near", "테스트 안내입니다. 오른쪽 위로 조금.", direction = true, vibration = GuidancePhrases.vibration(true)) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("가까움·진동 테스트") }
            OutlinedButton(onClick = { output.announce("test-arrived", "테스트 안내입니다. 목표 위치에 도달했습니다. 실제 누름 지시는 아닙니다.", vibration = longArrayOf(0, 180)) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("도달 안내 테스트") }
        }
    }
}
