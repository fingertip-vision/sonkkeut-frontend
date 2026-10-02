package com.sonkkeut.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun ReplayControls(output: GuidanceOutput, enabled: Boolean) {
    val replay = remember { SyntheticReplay() }
    var events by remember { mutableStateOf<List<ReplayEvent>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(ReplayState.PAUSED) }
    var trace by remember { mutableStateOf(listOf<String>()) }
    var message by remember { mutableStateOf("재생할 시나리오를 선택해 주세요.") }
    fun show(text: String) { message = text; trace = (trace + text).takeLast(12); state = replay.state }
    fun start(recovery: Boolean, payment: Boolean) {
        output.resetAttempt()
        replay.start()
        events = ReplayFixtures.events(replay.session, recovery, payment)
        index = 0; trace = emptyList()
        show("합성 재생을 시작했습니다. 다음 단계 버튼으로 고정 결과를 재생합니다.")
    }
    LaunchedEffect(enabled) {
        if (!enabled) { replay.pause(); events = emptyList(); index = 0; show("재생이 중지되었습니다. 카메라를 다시 시작한 뒤 시나리오를 선택해 주세요.") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("합성 결과 재생 · T07", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text("카메라 영상과 연결되지 않은 고정 결과입니다. AI 계산·인식·실제 주문은 실행하지 않습니다. 실제 키오스크 버튼은 누르지 마세요.")
        Text("상태: ${when (state) {
            ReplayState.GUIDING -> "안내 재생"; ReplayState.AWAITING_RESULT -> "고정 결과 대기"
            ReplayState.RECOVERY -> "복구 필요"; ReplayState.RESULT -> "합성 결과 확인"
            ReplayState.PAYMENT -> "결제 화면 안내 종료"; ReplayState.PAUSED -> "중지"
        }}")
        Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        listOf("정상 흐름 시작" to Pair(false, false), "손끝 누락 흐름 시작" to Pair(true, false), "결제 종료 흐름 시작" to Pair(false, true)).forEach { (label, scenario) ->
            OutlinedButton(onClick = { start(scenario.first, scenario.second) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(label) }
        }
        Button(onClick = {
            val event = events[index++]
            val feedback = replay.accept(event)
            if (feedback != null) {
                // Each manual step must be observable even if tapped faster than the direction throttle.
                output.stop()
                output.announce("replay:${event.session}:${event.sequence}", feedback.text, feedback.direction, feedback.press, feedback.near?.let { GuidancePhrases.vibration(it) })
                show(feedback.text)
            } else show("합성 재생: ${event.decision?.reason ?: "결과"} · 추가 누름 안내를 출력하지 않았습니다.")
        }, enabled = enabled && index < events.size && state !in listOf(ReplayState.RECOVERY, ReplayState.PAYMENT, ReplayState.PAUSED), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("다음 합성 단계 (${index}/${events.size})") }
        OutlinedButton(onClick = {
            val stale = ReplayEvent(replay.session - 1, 1, "fixture-menu", 100, ReplayDecision("press"))
            val rejected = replay.accept(stale) == null
            show(if (rejected) "이전 세션의 오래된 결과를 거부했습니다. 누름 안내 없음." else "오래된 결과 거부 실패")
        }, enabled = enabled && state != ReplayState.PAUSED, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("오래된 결과 거부 확인") }
        OutlinedButton(onClick = { replay.pause(); output.stop(); events = emptyList(); show("합성 재생과 출력을 중지했습니다.") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("합성 재생 중지") }
        if (trace.isNotEmpty()) Text("재생 기록\n" + trace.mapIndexed { i, line -> "${i + 1}. $line" }.joinToString("\n"))
    }
}
